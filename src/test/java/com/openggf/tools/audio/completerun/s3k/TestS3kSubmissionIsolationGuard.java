package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS3kSubmissionIsolationGuard {
    private static final String RAW_ADAPTER =
            S3kCompleteRunReferenceRawAdapter.class.getName();
    private static final String PROJECTOR =
            S3kCompleteRunReferenceProjector.class.getName();

    @TempDir
    Path temporaryDirectory;

    @Test
    void unboundSubmissionEntriesRemainPackagePrivate() throws NoSuchMethodException {
        int adapterModifiers = S3kCompleteRunReferenceRawAdapter.class.getDeclaredMethod(
                "scanSubmissionV2PrefixForTesting", Path.class,
                S3kCompleteRunReferenceRawAdapter.Sink.class).getModifiers();
        int projectorModifiers = S3kCompleteRunReferenceProjector.class.getDeclaredMethod(
                "projectSubmissionV2PrefixForTesting", Path.class, Path.class).getModifiers();

        assertPackagePrivate(adapterModifiers);
        assertPackagePrivate(projectorModifiers);
    }

    @Test
    void compiledProductionCannotReachUnboundSubmissionEntries() {
        JavaClasses production = new ClassFileImporter()
                .importPath(Path.of("target/classes"));

        assertEquals(List.of(), unauthorizedSubmissionCalls(production),
                "the UNBOUND_TEST_ONLY S3K raw-v2 path must remain unreachable from"
                        + " production entry points and bindings");
        assertEquals(List.of(), reflectiveSubmissionAccesses(production),
                "reflection and private MethodHandles lookups must not bypass the"
                        + " S3K raw-v2 caller boundary");
    }

    @Test
    void bytecodeGuardRejectsAnUnauthorizedBinding() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                UnauthorizedBinding.class);

        List<String> violations = unauthorizedSubmissionCalls(mutation);

        assertEquals(1, violations.size(), violations.toString());
        assertFalse(violations.getFirst().isBlank());
    }

    @Test
    void bytecodeGuardRejectsAnotherMethodOnTheApprovedOwner() throws IOException {
        JavaClasses mutation = compileApprovedOwnerMutation();

        List<String> violations = unauthorizedSubmissionCalls(mutation);

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.getFirst().contains("otherEntry"), violations.toString());
    }

    @Test
    void bytecodeGuardRejectsReflectiveAccessibilityAndInvocation() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                ReflectiveBinding.class);

        List<String> violations = reflectiveSubmissionAccesses(mutation);

        assertEquals(1, violations.size(), violations.toString());
    }

    @Test
    void bytecodeGuardRejectsPrivateMethodHandleLookup() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                MethodHandleBinding.class);

        List<String> violations = reflectiveSubmissionAccesses(mutation);

        assertEquals(1, violations.size(), violations.toString());
    }

    @Test
    void bytecodeGuardRejectsPackageLookupWithoutPrivateLookupIn() {
        JavaClasses mutation = new ClassFileImporter().importClasses(
                PackageLookupBinding.class);

        List<String> violations = reflectiveSubmissionAccesses(mutation);

        assertEquals(1, violations.size(), violations.toString());
    }

    private static void assertPackagePrivate(int modifiers) {
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    private static List<String> unauthorizedSubmissionCalls(JavaClasses classes) {
        return classes.stream()
                .flatMap(owner -> owner.getAccessesFromSelf().stream())
                .filter(TestS3kSubmissionIsolationGuard::targetsUnboundSubmissionEntry)
                .filter(access -> !isApprovedProjectorAdapterCall(access))
                .map(JavaAccess::getDescription)
                .sorted()
                .distinct()
                .toList();
    }

    private static List<String> reflectiveSubmissionAccesses(JavaClasses classes) {
        return classes.stream()
                .flatMap(owner -> owner.getCodeUnits().stream())
                .filter(TestS3kSubmissionIsolationGuard::referencesUnboundClassObject)
                .filter(TestS3kSubmissionIsolationGuard::acquiresNonPublicAccess)
                .map(codeUnit -> codeUnit.getFullName()
                        + ":reflective-unbound-submission-access")
                .sorted()
                .toList();
    }

    private static boolean targetsUnboundSubmissionEntry(JavaAccess<?> access) {
        if (!(access instanceof JavaMethodCall)
                && !(access instanceof JavaMethodReference)) {
            return false;
        }
        String owner = access.getTargetOwner().getName();
        String method = access.getTarget().getName();
        return RAW_ADAPTER.equals(owner)
                        && "scanSubmissionV2PrefixForTesting".equals(method)
                || PROJECTOR.equals(owner)
                        && "projectSubmissionV2PrefixForTesting".equals(method);
    }

    private static boolean isApprovedProjectorAdapterCall(JavaAccess<?> access) {
        return exactCodeUnit(access.getOrigin(), PROJECTOR,
                        "projectSubmissionV2PrefixForTesting",
                        Path.class.getName(), Path.class.getName())
                && access.getTarget() instanceof CodeUnitAccessTarget target
                && exactCodeUnit(target, RAW_ADAPTER,
                        "scanSubmissionV2PrefixForTesting",
                        Path.class.getName(),
                        S3kCompleteRunReferenceRawAdapter.Sink.class.getName());
    }

    private static boolean referencesUnboundClassObject(JavaCodeUnit codeUnit) {
        return codeUnit.getReferencedClassObjects().stream()
                .map(reference -> reference.getValue().getName())
                .anyMatch(name -> RAW_ADAPTER.equals(name) || PROJECTOR.equals(name));
    }

    private static boolean acquiresNonPublicAccess(JavaCodeUnit codeUnit) {
        return codeUnit.getMethodCallsFromSelf().stream()
                .anyMatch(call -> {
                    String owner = call.getTargetOwner().getName();
                    String method = call.getName();
                    return Class.class.getName().equals(owner)
                                    && ("getDeclaredMethod".equals(method)
                                            || "getDeclaredMethods".equals(method))
                            || MethodHandles.class.getName().equals(owner)
                                    && "privateLookupIn".equals(method)
                            || MethodHandles.Lookup.class.getName().equals(owner)
                                    && ("findVirtual".equals(method)
                                            || "findStatic".equals(method)
                                            || "findSpecial".equals(method)
                                            || "unreflect".equals(method));
                });
    }

    private static boolean exactCodeUnit(JavaCodeUnit codeUnit, String owner,
            String name, String... parameters) {
        return owner.equals(codeUnit.getOwner().getName())
                && name.equals(codeUnit.getName())
                && codeUnit.getRawParameterTypes().stream()
                        .map(type -> type.getName())
                        .toList().equals(List.of(parameters));
    }

    private static boolean exactCodeUnit(CodeUnitAccessTarget codeUnit, String owner,
            String name, String... parameters) {
        return owner.equals(codeUnit.getOwner().getName())
                && name.equals(codeUnit.getName())
                && codeUnit.getRawParameterTypes().stream()
                        .map(type -> type.getName())
                        .toList().equals(List.of(parameters));
    }

    private JavaClasses compileApprovedOwnerMutation() throws IOException {
        Path source = temporaryDirectory.resolve(
                "com/openggf/tools/audio/completerun/s3k/"
                        + "S3kCompleteRunReferenceProjector.java");
        Path classes = temporaryDirectory.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, """
                package com.openggf.tools.audio.completerun.s3k;

                import java.nio.file.Path;

                public final class S3kCompleteRunReferenceProjector {
                    void projectSubmissionV2PrefixForTesting(Path raw, Path rom)
                            throws Exception {
                        S3kCompleteRunReferenceRawAdapter
                                .scanSubmissionV2PrefixForTesting(raw, null);
                    }

                    void otherEntry(Path raw, Path rom) throws Exception {
                        S3kCompleteRunReferenceRawAdapter
                                .scanSubmissionV2PrefixForTesting(raw, null);
                    }
                }
                """);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-classpath", Path.of("target/classes").toString(),
                "-d", classes.toString(), source.toString());
        assertEquals(0, result, "could not compile exact-owner bytecode mutation");
        return new ClassFileImporter().importPath(classes);
    }

    private static final class UnauthorizedBinding {
        Object acquire(S3kCompleteRunReferenceProjector projector, Path raw, Path rom)
                throws IOException {
            return projector.projectSubmissionV2PrefixForTesting(raw, rom);
        }
    }

    private static final class ReflectiveBinding {
        Object acquire(S3kCompleteRunReferenceProjector projector, Path raw, Path rom)
                throws Exception {
            Method method = S3kCompleteRunReferenceProjector.class.getDeclaredMethod(
                    "projectSubmissionV2PrefixForTesting", Path.class, Path.class);
            if (!method.trySetAccessible()) {
                method.setAccessible(true);
            }
            return method.invoke(projector, raw, rom);
        }
    }

    private static final class MethodHandleBinding {
        Object acquire(S3kCompleteRunReferenceProjector projector, Path raw, Path rom)
                throws Throwable {
            return MethodHandles.privateLookupIn(S3kCompleteRunReferenceProjector.class,
                            MethodHandles.lookup())
                    .findVirtual(S3kCompleteRunReferenceProjector.class,
                            "projectSubmissionV2PrefixForTesting",
                            MethodType.methodType(
                                    S3kCompleteRunReferenceProjector.Projection.class,
                                    Path.class, Path.class))
                    .invoke(projector, raw, rom);
        }
    }

    private static final class PackageLookupBinding {
        Object acquire(S3kCompleteRunReferenceProjector projector, Path raw, Path rom)
                throws Throwable {
            return MethodHandles.lookup()
                    .findVirtual(S3kCompleteRunReferenceProjector.class,
                            "projectSubmissionV2PrefixForTesting",
                            MethodType.methodType(
                                    S3kCompleteRunReferenceProjector.Projection.class,
                                    Path.class, Path.class))
                    .invoke(projector, raw, rom);
        }
    }
}
