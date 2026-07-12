package com.openggf.mods.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.TypeReference;
import com.openggf.io.ModInputLimits;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class TestModValidator {
    private static final String ENTRY = "example/Entry";
    private static final String OBJECT = "example/Object";
    private static final String GGF_MOD = "com/openggf/mods/code/GgfMod";
    private static final String OBJECT_BASE = "com/openggf/level/objects/AbstractObjectInstance";
    private static final String RECREATABLE = "com/openggf/level/objects/RewindRecreatable";
    private static final String OBJECT_INSTANCE = "com/openggf/level/objects/ObjectInstance";
    private static final String OBJECT_REF_ID = "com/openggf/game/rewind/identity/ObjectRefId";
    private static final String RECREATE_CONTEXT = "com/openggf/level/objects/RewindRecreateContext";
    private static final String BADNIK_BASE = "com/openggf/level/objects/AbstractBadnikInstance";

    @TempDir Path temp;

    @Test
    void acceptsStructurallySafeEntrypointAndObjectWithoutLoadingClasses() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ENTRY, entrypoint(true, true));
        classes.put(OBJECT, objectClass(true, writer -> {
            writer.visitField(Opcodes.ACC_PRIVATE, "counter", "I", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE, "parent", "L" + OBJECT_INSTANCE + ";", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "parentId",
                    "L" + OBJECT_REF_ID + ";", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    "LIMIT", "I", null, 4).visitEnd();
        }, false, true, "parentId"));
        Path jar = jar(classes);

        ModValidationReport report = new ModValidator(Set.of()).validate(jar, "example.Entry");

        assertTrue(report.eligible(), report.findings().toString());
    }

    @Test
    void rejectsFieldlessClassInitializerWithoutExecutingIt() throws Exception {
        System.clearProperty("openggf.mod.validator.executed");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, ENTRY, null, "java/lang/Object",
                new String[] {GGF_MOD});
        constructor(writer, Opcodes.ACC_PUBLIC, false, ENTRY);
        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode(); clinit.visitLdcInsn("openggf.mod.validator.executed"); clinit.visitLdcInsn("true");
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        clinit.visitInsn(Opcodes.POP); clinit.visitInsn(Opcodes.RETURN); clinit.visitMaxs(2, 0); clinit.visitEnd();
        writer.visitEnd();

        ModValidationReport report = new ModValidator().validate(jar(Map.of(ENTRY, writer.toByteArray())),
                "example.Entry");
        assertCode(report, "STATIC_STATE_UNSUPPORTED", ModValidationFinding.Severity.ERROR);
        assertNull(System.getProperty("openggf.mod.validator.executed"));
    }

    @Test
    void rejectsMissingOrInvalidEntrypointAndWrongObjectBaseContract() throws Exception {
        ModValidationReport missing = new ModValidator(Set.of()).validate(
                jar(Map.of(ENTRY, entrypoint(false, true))), "missing.Entry");
        assertCode(missing, "ENTRYPOINT_MISSING", ModValidationFinding.Severity.ERROR);

        ModValidationReport invalid = new ModValidator(Set.of()).validate(jar(Map.of(
                ENTRY, entrypoint(false, false), OBJECT, objectClass(false, ignored -> {}, false))),
                "example.Entry");
        assertCode(invalid, "ENTRYPOINT_CONTRACT", ModValidationFinding.Severity.ERROR);
        assertCode(invalid, "OBJECT_RECREATE_PATH_MISSING", ModValidationFinding.Severity.ERROR);

        ModValidationReport privateConstructor = new ModValidator(Set.of()).validate(
                jar(Map.of(ENTRY, entrypoint(true, false))), "example.Entry");
        assertCode(privateConstructor, "ENTRYPOINT_CONSTRUCTOR", ModValidationFinding.Severity.ERROR);

        ModValidationReport fakeRecreate = new ModValidator(Set.of()).validate(jar(Map.of(
                ENTRY, entrypoint(true, true), OBJECT,
                objectClass(true, ignored -> {}, false, false))), "example.Entry");
        assertCode(fakeRecreate, "OBJECT_RECREATE_PATH_MISSING", ModValidationFinding.Severity.ERROR);

        ClassWriter unsupported = new ClassWriter(0);
        unsupported.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Unsupported", null,
                "java/lang/Object", new String[] {OBJECT_INSTANCE});
        constructor(unsupported, Opcodes.ACC_PUBLIC, false, "example/Unsupported");
        unsupported.visitEnd();
        ModValidationReport wrongBase = new ModValidator().validate(jar(Map.of(
                ENTRY, entrypoint(true, true), "example/Unsupported", unsupported.toByteArray())),
                "example.Entry");
        assertCode(wrongBase, "OBJECT_BASE_CONTRACT", ModValidationFinding.Severity.ERROR);
    }

    @Test
    void rejectsConstructorServicesUseFinalScalarsAndUncapturedObjectReferences() throws Exception {
        byte[] object = objectClass(true, writer -> {
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "routine", "I", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE, "parent", "L" + OBJECT_INSTANCE + ";", null, null).visitEnd();
        }, true);
        ModValidationReport report = new ModValidator(Set.of()).validate(
                jar(Map.of(ENTRY, entrypoint(true, true), OBJECT, object)), "example.Entry");

        assertCode(report, "CONSTRUCTOR_SERVICES_ACCESS", ModValidationFinding.Severity.ERROR);
        assertCode(report, "FINAL_SCALAR_REWIND_GAP", ModValidationFinding.Severity.ERROR);
        assertCode(report, "OBJECT_REFERENCE_REWIND_ID_MISSING", ModValidationFinding.Severity.ERROR);
    }

    @Test
    void rejectsEveryStaticExceptCompileTimePrimitiveOrStringConstants() throws Exception {
        byte[] object = objectClass(true, writer -> {
            writer.visitField(Opcodes.ACC_STATIC, "mutable", "I", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "array", "[I", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "object", "Ljava/lang/Object;", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "late", "I", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "written", "I", null, 1).visitEnd();
            writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "TEXT", "Ljava/lang/String;", null, "safe").visitEnd();
            MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode(); clinit.visitInsn(Opcodes.ICONST_2);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, OBJECT, "written", "I");
            clinit.visitInsn(Opcodes.RETURN); clinit.visitMaxs(1, 0); clinit.visitEnd();
        }, false);
        ModValidationReport report = new ModValidator(Set.of()).validate(
                jar(Map.of(ENTRY, entrypoint(true, true), OBJECT, object)), "example.Entry");

        assertEquals(6, report.findings().stream()
                .filter(f -> f.code().equals("STATIC_STATE_UNSUPPORTED")).count());
    }

    @Test
    void auditsExternalIntermediateBasesAndConcreteModObjectReferences() throws Exception {
        String child = "example/Child";
        String badnik = "example/Badnik";
        byte[] childBytes = objectClassNamed(child, OBJECT_BASE, true, ignored -> {}, false, true, null);
        byte[] parentBytes = objectClassNamed(OBJECT, OBJECT_BASE, true, writer ->
                writer.visitField(Opcodes.ACC_PRIVATE, "child", "L" + child + ";", null, null).visitEnd(),
                false, true, null);
        byte[] badnikBytes = objectClassNamed(badnik, BADNIK_BASE, false, writer ->
                writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "timer", "I", null, null).visitEnd(),
                false, false, null);

        ModValidationReport report = new ModValidator().validate(jar(Map.of(
                ENTRY, entrypoint(true, true), OBJECT, parentBytes, child, childBytes, badnik, badnikBytes)),
                "example.Entry");

        assertCode(report, "OBJECT_REFERENCE_REWIND_ID_MISSING", ModValidationFinding.Severity.ERROR);
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("FINAL_SCALAR_REWIND_GAP")
                && f.className().equals(badnik)));
    }

    @Test
    void reportsInternalEngineReferencesAsWarningsButTrustedApiReferencesAreClean() throws Exception {
        byte[] entry = entrypoint(true, true, writer -> {
            writer.visitField(Opcodes.ACC_PRIVATE, "stable", "Lcom/openggf/api/Stable;", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE, "internal", "Lcom/openggf/internal/Unstable;", null, null).visitEnd();
        });
        ModValidationReport report = new ModValidator(Set.of("com/openggf/api/Stable"))
                .validate(jar(Map.of(ENTRY, entry)), "example.Entry");

        assertTrue(report.eligible());
        assertCode(report, "NON_API_ENGINE_REFERENCE", ModValidationFinding.Severity.WARNING);
        assertTrue(report.findings().stream().noneMatch(f -> f.message().contains("Stable")));
    }

    @Test
    void scansInvokeDynamicBootstrapDescriptorsAndTypeAnnotations() throws Exception {
        byte[] entry = entrypoint(true, true, writer -> {
            writer.visitTypeAnnotation(TypeReference.newTypeParameterReference(
                    TypeReference.CLASS_TYPE_PARAMETER, 0).getValue(), null,
                    "Lcom/openggf/internal/TypeMarker;", true).visitEnd();
            MethodVisitor hidden = writer.visitMethod(Opcodes.ACC_PUBLIC, "hidden", "()V", null, null);
            hidden.visitCode();
            hidden.visitInvokeDynamicInsn("hidden", "()V", new Handle(Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/MethodHandles", "lookup",
                    "()Lcom/openggf/internal/Hidden;", false));
            hidden.visitInsn(Opcodes.RETURN); hidden.visitMaxs(0, 1); hidden.visitEnd();
        });

        ModValidationReport report = new ModValidator().validate(jar(Map.of(ENTRY, entry)), "example.Entry");

        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("TypeMarker")));
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("Hidden")));
    }

    @Test
    void boundsPathByteArrayAndNonClassEntryInflation() throws Exception {
        byte[] normal = jarBytes(Map.of(ENTRY, entrypoint(true, true)));
        ModInputLimits tinyJar = ModInputLimits.loweringBuilder().maxJarBytes(64).build();
        ModValidator jarValidator = new ModValidator(Set.of(), tinyJar);
        Path path = temp.resolve("oversized.jar");
        Files.write(path, normal);
        assertCode(jarValidator.validate(path, "example.Entry"), "JAR_SIZE_LIMIT",
                ModValidationFinding.Severity.ERROR);
        assertCode(jarValidator.validate(normal, "example.Entry"), "JAR_SIZE_LIMIT",
                ModValidationFinding.Severity.ERROR);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("asset.bin"));
            jar.write(new byte[32]); jar.closeEntry();
        }
        ModInputLimits tinyAsset = ModInputLimits.loweringBuilder().maxAssetBytes(8).build();
        assertCode(new ModValidator(Set.of(), tinyAsset).validate(bytes.toByteArray(), "example.Entry"),
                "MALFORMED_JAR", ModValidationFinding.Severity.ERROR);
    }

    @Test
    void malformedClassfileIsAStableValidationError() throws Exception {
        ModValidationReport report = new ModValidator(Set.of()).validate(
                jar(Map.of(ENTRY, new byte[] {0, 1, 2, 3})), "example.Entry");
        assertCode(report, "MALFORMED_CLASSFILE", ModValidationFinding.Severity.ERROR);
        assertFalse(report.eligible());
    }

    @Test
    void byteArrayAndPathValidationProduceTheSameReport() throws Exception {
        byte[] bytes = jarBytes(Map.of(ENTRY, entrypoint(true, true)));
        Path path = temp.resolve("mod.jar");
        Files.write(path, bytes);
        ModValidator validator = new ModValidator(Set.of());
        assertEquals(validator.validate(bytes, "example.Entry"), validator.validate(path, "example.Entry"));
    }

    @Test
    void rejectsClassEntryNameMismatchAndKeepsApiIndexEngineOwned() throws Exception {
        ModValidationReport mismatch = new ModValidator().validate(
                jar(Map.of("example/Wrong", entrypoint(true, true))), "example.Entry");

        assertCode(mismatch, "CLASS_ENTRY_NAME_MISMATCH", ModValidationFinding.Severity.ERROR);
        var injected = ModValidator.class.getDeclaredConstructor(Set.class);
        assertFalse(Modifier.isPublic(injected.getModifiers()));
    }

    private Path jar(Map<String, byte[]> classes) throws Exception {
        Path path = temp.resolve("fixture-" + System.nanoTime() + ".jar");
        Files.write(path, jarBytes(classes));
        return path;
    }

    private static byte[] jarBytes(Map<String, byte[]> classes) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (var entry : classes.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] entrypoint(boolean implementsContract, boolean publicNoArg) {
        return entrypoint(implementsContract, publicNoArg, ignored -> {});
    }

    private static byte[] entrypoint(boolean implementsContract, boolean publicNoArg,
                                     java.util.function.Consumer<ClassWriter> fields) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, ENTRY, null, "java/lang/Object",
                implementsContract ? new String[] {GGF_MOD} : null);
        fields.accept(writer);
        constructor(writer, publicNoArg ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE, false);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] objectClass(boolean recreatable,
                                      java.util.function.Consumer<ClassWriter> fields,
                                      boolean constructorServices) {
        return objectClass(recreatable, fields, constructorServices, recreatable, null);
    }

    private static byte[] objectClass(boolean recreatable,
                                      java.util.function.Consumer<ClassWriter> fields,
                                      boolean constructorServices, boolean emitRecreateMethod) {
        return objectClass(recreatable, fields, constructorServices, emitRecreateMethod, null);
    }

    private static byte[] objectClass(boolean recreatable,
                                      java.util.function.Consumer<ClassWriter> fields,
                                      boolean constructorServices, boolean emitRecreateMethod,
                                      String capturedIdField) {
        return objectClassNamed(OBJECT, OBJECT_BASE, recreatable, fields, constructorServices,
                emitRecreateMethod, capturedIdField);
    }

    private static byte[] objectClassNamed(String owner, String superName, boolean recreatable,
                                           java.util.function.Consumer<ClassWriter> fields,
                                           boolean constructorServices, boolean emitRecreateMethod,
                                           String capturedIdField) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, superName,
                recreatable ? new String[] {RECREATABLE} : null);
        fields.accept(writer);
        constructor(writer, Opcodes.ACC_PUBLIC, constructorServices, owner);
        if (emitRecreateMethod) {
            MethodVisitor recreate = writer.visitMethod(Opcodes.ACC_PUBLIC, "recreateForRewind",
                    "(L" + RECREATE_CONTEXT + ";)L" + OBJECT_BASE + ";", null, null);
            recreate.visitCode(); recreate.visitVarInsn(Opcodes.ALOAD, 0);
            recreate.visitInsn(Opcodes.ARETURN); recreate.visitMaxs(1, 2); recreate.visitEnd();
        }
        if (capturedIdField != null) {
            MethodVisitor capture = writer.visitMethod(Opcodes.ACC_PUBLIC, "captureRewindState",
                    "()Lcom/openggf/game/rewind/PerObjectRewindSnapshot;", null, null);
            capture.visitCode(); capture.visitVarInsn(Opcodes.ALOAD, 0);
            capture.visitFieldInsn(Opcodes.GETFIELD, owner, capturedIdField,
                    "L" + OBJECT_REF_ID + ";");
            capture.visitInsn(Opcodes.POP); capture.visitInsn(Opcodes.ACONST_NULL);
            capture.visitInsn(Opcodes.ARETURN); capture.visitMaxs(1, 1); capture.visitEnd();
            MethodVisitor restore = writer.visitMethod(Opcodes.ACC_PUBLIC, "restoreRewindState",
                    "(Lcom/openggf/game/rewind/PerObjectRewindSnapshot;)V", null, null);
            restore.visitCode(); restore.visitVarInsn(Opcodes.ALOAD, 0);
            restore.visitFieldInsn(Opcodes.GETFIELD, owner, capturedIdField,
                    "L" + OBJECT_REF_ID + ";");
            restore.visitInsn(Opcodes.POP); restore.visitInsn(Opcodes.RETURN);
            restore.visitMaxs(1, 2); restore.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, int access, boolean services) {
        constructor(writer, access, services, OBJECT);
    }

    private static void constructor(ClassWriter writer, int access, boolean services, String owner) {
        MethodVisitor method = writer.visitMethod(access, "<init>", "()V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        if (services) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "services",
                    "()Lcom/openggf/level/objects/ObjectServices;", false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 1); method.visitEnd();
    }

    private static void assertCode(ModValidationReport report, String code,
                                   ModValidationFinding.Severity severity) {
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals(code)
                && f.severity() == severity), () -> code + " absent from " + report.findings());
    }
}
