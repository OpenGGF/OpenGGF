package com.openggf.mods.code;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModType;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.VersionRange;
import com.openggf.mods.validation.ModValidationFinding;
import com.openggf.mods.validation.ModValidationReport;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModRuntime {
    @TempDir Path temp;

    @Test
    void factoryFreezesValidatedTrustedCodeOwnersAndLoadOwnedIsOwnerExact() throws Exception {
        ModDescriptor dependency = descriptor("dependency", List.of(), "example.shared.Same");
        ModDescriptor owner = descriptor("owner", List.of(new ModDependency("dependency", VersionRange.parse("*"))),
                "example.shared.Same", "example.owner.Entry");
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(dependency, owner));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(catalog, Set.of("dependency", "owner"))) {
            Class<?> dependencyType = runtime.loadOwned("dependency", "example.shared.Same");
            Class<?> ownerType = runtime.loadOwned("owner", "example.shared.Same");
            assertNotSame(dependencyType, ownerType);
            assertEquals("owner", runtime.owners().get(1));
            assertThrows(UnsupportedOperationException.class, () -> runtime.owners().add("later"));
            assertThrows(ClassNotFoundException.class, () -> runtime.loadOwned("missing", "example.shared.Same"));
        }
    }

    @Test
    void productionGateIsEmptyAndValidationErrorsNeverCreateLoaders() throws Exception {
        ModDescriptor descriptor = descriptor("owner", List.of(), "example.owner.Entry");
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(descriptor));

        try (ModRuntime production = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog)) {
            assertTrue(production.owners().isEmpty());
        }

        ModValidationFinding error = new ModValidationFinding(ModValidationFinding.Severity.ERROR,
                "INVALID", "", "", "invalid fixture");
        ModClassLoaderFactory rejecting = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> new ModValidationReport(List.of(error)));
        try (ModRuntime runtime = rejecting.create(catalog, Set.of("owner"))) {
            assertTrue(runtime.owners().isEmpty());
            assertEquals(ModRuntime.RejectionReason.VALIDATION_FAILED,
                    runtime.rejectedOwners().get("owner").reason());
            assertEquals("INVALID", runtime.rejectedOwners().get("owner").detail());
            assertThrows(ClassNotFoundException.class,
                    () -> runtime.loadOwned("owner", "example.owner.Entry"));
        }
    }

    @Test
    void rejectsDigestMismatchMissingDirectDependencyAndClosesPartialConstruction() throws Exception {
        ModDescriptor valid = descriptor("valid", List.of(), "example.valid.Entry");
        ModDescriptor mismatch = new ModDescriptor(valid.jarPath(), valid.manifest(), "0".repeat(64),
                true, List.of());
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> new ModValidationReport(List.of()));
        try (ModRuntime runtime = factory.create(new EffectiveModCatalog(List.of(mismatch)), Set.of("valid"))) {
            assertTrue(runtime.owners().isEmpty());
            assertEquals(ModRuntime.RejectionReason.HASH_MISMATCH,
                    runtime.rejectedOwners().get("valid").reason());
        }

        ModDescriptor missing = descriptor("needs-missing",
                List.of(new ModDependency("absent", VersionRange.parse("*"))), "example.needs.Entry");
        try (ModRuntime runtime = factory.create(
                new EffectiveModCatalog(List.of(valid, missing)), Set.of("valid", "needs-missing"))) {
            assertEquals(List.of("valid"), runtime.owners());
            assertEquals(ModRuntime.RejectionReason.DEPENDENCY_UNAVAILABLE,
                    runtime.rejectedOwners().get("needs-missing").reason());
        }

        Path moved = temp.resolve("valid-after-failed-build.jar");
        Files.move(valid.jarPath(), moved);
        assertTrue(Files.exists(moved));

        ModDescriptor first = descriptor("first", List.of(), "example.first.Entry");
        ModDescriptor exploding = descriptor("exploding", List.of(), "example.exploding.Entry");
        java.util.concurrent.atomic.AtomicInteger validations = new java.util.concurrent.atomic.AtomicInteger();
        ModClassLoaderFactory partialFailure = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> {
                    if (validations.incrementAndGet() == 2) throw new IllegalStateException("boom");
                    return new ModValidationReport(List.of());
                });
        assertThrows(IllegalStateException.class, () -> partialFailure.create(
                new EffectiveModCatalog(List.of(first, exploding)), Set.of("first", "exploding")));
        Files.move(first.jarPath(), temp.resolve("first-after-partial-failure.jar"));

        ModDescriptor brokenSnapshot = descriptor("broken-snapshot", List.of(), "example.broken.Entry");
        Files.delete(brokenSnapshot.jarPath());
        ModDescriptor independent = descriptor("independent", List.of(), "example.independent.Entry");
        try (ModRuntime runtime = factory.create(new EffectiveModCatalog(List.of(brokenSnapshot, independent)),
                Set.of("broken-snapshot", "independent"))) {
            assertEquals(List.of("independent"), runtime.owners());
            assertEquals(ModRuntime.RejectionReason.SNAPSHOT_FAILED,
                    runtime.rejectedOwners().get("broken-snapshot").reason());
        }

        ModDescriptor rejectedDependencySource = descriptor("rejected-dependency", List.of(),
                "example.rejected.Entry");
        ModDescriptor rejectedDependency = new ModDescriptor(rejectedDependencySource.jarPath(),
                rejectedDependencySource.manifest(), "0".repeat(64), true, List.of());
        ModDescriptor dependent = descriptor("dependent", List.of(new ModDependency(
                "rejected-dependency", VersionRange.parse("*"))), "example.dependent.Entry");
        ModDescriptor laterIndependent = descriptor("later-independent", List.of(),
                "example.later.Entry");
        try (ModRuntime runtime = factory.create(new EffectiveModCatalog(List.of(
                rejectedDependency, dependent, laterIndependent)),
                Set.of("rejected-dependency", "dependent", "later-independent"))) {
            assertEquals(List.of("later-independent"), runtime.owners());
            assertEquals(ModRuntime.RejectionReason.HASH_MISMATCH,
                    runtime.rejectedOwners().get("rejected-dependency").reason());
            assertEquals(ModRuntime.RejectionReason.DEPENDENCY_UNAVAILABLE,
                    runtime.rejectedOwners().get("dependent").reason());
        }
    }

    @Test
    void closeIsIdempotentAndAReplacementRuntimeUsesFreshLoaders() throws Exception {
        ModDescriptor descriptor = descriptor("owner", List.of(), "example.owner.Entry");
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(descriptor));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> new ModValidationReport(List.of()));

        ModRuntime first = factory.create(catalog, Set.of("owner"));
        Path sourceMovedWhileOpen = temp.resolve("owner-source-moved.jar");
        Files.move(descriptor.jarPath(), sourceMovedWhileOpen);
        Class<?> firstClass = first.loadOwned("owner", "example.owner.Entry");
        first.close();
        first.close();
        assertTrue(first.isClosed());
        assertThrows(ClassNotFoundException.class, () -> first.loadOwned("owner", "example.owner.Entry"));

        Files.move(sourceMovedWhileOpen, descriptor.jarPath());
        ModRuntime replacement = factory.create(catalog, Set.of("owner"));
        assertNotSame(firstClass, replacement.loadOwned("owner", "example.owner.Entry"));
        replacement.close();
        Path replacementJar = temp.resolve("replacement.jar");
        Files.move(descriptor.jarPath(), replacementJar);
        assertTrue(Files.exists(replacementJar));
    }

    @Test
    void enforcesBootWideInspectionBudgetBeforeSnapshotAndKeepsLaterFittingOwner() throws Exception {
        ModDescriptor first = descriptor("budget-first", List.of(), "example.budget.First");
        ModDescriptor grown = descriptor("budget-grown", List.of(), "example.budget.Grown");
        ModDescriptor later = descriptor("budget-later", List.of(), "example.budget.Later");
        long firstSize = Files.size(first.jarPath());
        long laterSize = Files.size(later.jarPath());
        int grownSize = Math.toIntExact(Math.max(Files.size(grown.jarPath()), laterSize) + 128);
        Files.write(grown.jarPath(), new byte[grownSize]);
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxRepositoryValidationBytes(firstSize + laterSize).build();
        java.util.concurrent.atomic.AtomicInteger validations = new java.util.concurrent.atomic.AtomicInteger();
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (ignored, snapshot) -> {
                    validations.incrementAndGet();
                    return new ModValidationReport(List.of());
                }, limits);

        try (ModRuntime runtime = factory.create(new EffectiveModCatalog(List.of(first, grown, later)),
                Set.of("budget-first", "budget-grown", "budget-later"))) {
            assertEquals(List.of("budget-first", "budget-later"), runtime.owners());
            assertEquals(ModRuntime.RejectionReason.INSPECTION_BUDGET_EXCEEDED,
                    runtime.rejectedOwners().get("budget-grown").reason());
            assertEquals("boot loader inspection budget exceeded",
                    runtime.rejectedOwners().get("budget-grown").detail());
            assertEquals(2, validations.get());
        }
    }

    private ModDescriptor descriptor(String id, List<ModDependency> dependencies,
                                     String... binaryNames) throws Exception {
        Path jar = temp.resolve(id + ".jar");
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            for (String binaryName : binaryNames) {
                archive.putNextEntry(new JarEntry(binaryName.replace('.', '/') + ".class"));
                archive.write(emptyClass(binaryName.replace('.', '/')));
                archive.closeEntry();
            }
        }
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("test"),
                "test", VersionRange.parse("*"), ModType.PATCH, "s1", "example.owner.Entry",
                dependencies, Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(jar, manifest, sha256(jar), true, List.of());
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private static byte[] emptyClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
