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
import java.util.List;
import javax.tools.ToolProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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
    private static final String MOD_RECREATABLE = "com/openggf/level/objects/ModRewindRecreatable";
    private static final String MOD_RECREATE_CONTEXT = "com/openggf/level/objects/ObjectReconstructionContext";
    private static final String BADNIK_BASE = "com/openggf/level/objects/AbstractBadnikInstance";
    private static final String DELEGATING_GAME_MODULE =
            "com/openggf/game/patch/DelegatingGameModule";
    private static final String GROUND_SENSOR = "com/openggf/physics/GroundSensor";
    private static final String ABSTRACT_LEVEL_INIT_PROFILE =
            "com/openggf/game/AbstractLevelInitProfile";

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
    void acceptsProjectedReconstructionDirectlyAndThroughAnAuthoredSuperclass() throws Exception {
        byte[] projected = projectedObject(OBJECT, MOD_RECREATABLE, Opcodes.ACC_PUBLIC);
        var direct = new ModValidator().validate(jar(Map.of(
                ENTRY, entrypoint(true, true), OBJECT, projected)), "example.Entry");
        assertTrue(direct.eligible(), direct.findings().toString());

        String parent = "example/ProjectedParent";
        var inherited = new ModValidator().validate(jar(Map.of(
                ENTRY, entrypoint(true, true), parent, projectedObject(parent, MOD_RECREATABLE, Opcodes.ACC_PUBLIC),
                OBJECT, objectClassNamed(OBJECT, parent, false, ignored -> {}, false, false, null))),
                "example.Entry");
        assertTrue(inherited.eligible(), inherited.findings().toString());
    }

    @Test
    void rejectsMissingProjectedBridgeAndNonPublicStaticOrAbstractTargets() throws Exception {
        for (int access : new int[]{Opcodes.ACC_PRIVATE, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT}) {
            var report = new ModValidator().validate(jar(Map.of(
                    ENTRY, entrypoint(true, true), OBJECT, projectedObject(OBJECT, MOD_RECREATABLE, access))),
                    "example.Entry");
            assertCode(report, "OBJECT_RECREATE_PATH_MISSING", ModValidationFinding.Severity.ERROR);
        }
        // A similarly named overload does not implement the native-context contract
        // unless the supported interface actually supplies its default bridge.
        var noBridge = new ModValidator().validate(jar(Map.of(
                ENTRY, entrypoint(true, true), OBJECT, projectedObject(OBJECT, RECREATABLE, Opcodes.ACC_PUBLIC))),
                "example.Entry");
        assertCode(noBridge, "OBJECT_RECREATE_PATH_MISSING", ModValidationFinding.Severity.ERROR);
    }

    private static byte[] projectedObject(String owner, String contract, int access) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, OBJECT_BASE, new String[]{contract});
        constructor(writer, Opcodes.ACC_PUBLIC, false, owner);
        MethodVisitor method = writer.visitMethod(access, "recreateForRewind",
                "(L" + MOD_RECREATE_CONTEXT + ";)L" + OBJECT_BASE + ";", null, null);
        if ((access & Opcodes.ACC_ABSTRACT) == 0) {
            method.visitCode(); method.visitInsn(Opcodes.ACONST_NULL); method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, (access & Opcodes.ACC_STATIC) == 0 ? 2 : 1);
        }
        method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
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
    void javacEnumSwitchAndAssertArtefactsAreNotModState() throws Exception {
        // A switch over an enum makes javac emit a synthetic Entry$1 holding a $SwitchMap$ table
        // and a class initializer; an assert adds a synthetic $assertionsDisabled flag. Neither
        // holds mod state, so the validator accepts them.
        Map<String, byte[]> classes = compile("""
                package example;
                public final class Entry implements com.openggf.mods.code.GgfMod {
                    @Override public void register(com.openggf.mods.code.ModContext context) { }
                    int code(java.time.DayOfWeek day) {
                        switch (day) { case MONDAY: return 1; case FRIDAY: return 5; default: return 0; }
                    }
                    int positive(int x) { assert x > 0 : "positive"; return x; }
                }
                """);
        assertTrue(classes.containsKey("example/Entry$1"), "javac's switch-map class: " + classes.keySet());
        ModValidationReport report = new ModValidator(Set.of()).validate(jar(classes), "example.Entry");
        assertTrue(report.findings().stream().noneMatch(f -> f.code().equals("STATIC_STATE_UNSUPPORTED")),
                report.findings().toString());
    }

    @Test
    void initializersDoingMoreThanJavacsArtefactsAreStillRejected() throws Exception {
        Map<String, byte[]> classes = compile("""
                package example;
                public final class Entry implements com.openggf.mods.code.GgfMod {
                    static { System.setProperty("openggf.mod.validator.probe", "ran"); }
                    static final int[] $SwitchMap$fake = {1};
                    @Override public void register(com.openggf.mods.code.ModContext context) { }
                    int positive(int x) { assert x > 0; return x; }
                }
                """);
        ModValidationReport report = new ModValidator(Set.of()).validate(jar(classes), "example.Entry");
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("STATIC_STATE_UNSUPPORTED")
                && f.member().equals("<clinit>")), "a real static block: " + report.findings());
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("STATIC_STATE_UNSUPPORTED")
                && f.member().equals("$SwitchMap$fake")), "a hand-written table is not synthetic: " + report.findings());
    }

    /** Compiles one source file with javac and returns its classes by internal name. */
    private Map<String, byte[]> compile(String source) throws Exception {
        Path src = Files.createDirectories(temp.resolve("src-" + System.nanoTime()).resolve("example"));
        Files.writeString(src.resolve("Entry.java"), source);
        Path out = Files.createDirectories(temp.resolve("out-" + System.nanoTime()));
        // The engine's own classes (GgfMod) wherever this runner put them, plus the test classpath.
        String engine = Path.of(com.openggf.mods.code.GgfMod.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI()).toString();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, errors, "--release", "21",
                "-cp", engine + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                "-d", out.toString(), src.resolve("Entry.java").toString());
        assertEquals(0, result, () -> "javac: " + errors);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (var files = Files.walk(out)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = out.relativize(p).toString().replace('\\', '/').replaceAll("\\.class$", "");
                classes.put(name, Files.readAllBytes(p));
            }
        }
        return classes;
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
    void delegatingGameModuleIsATrustedCreatorApiReference() throws Exception {
        byte[] entry = entrypoint(true, true, writer -> writer.visitField(
                Opcodes.ACC_PRIVATE, "module", "L" + DELEGATING_GAME_MODULE + ";", null, null).visitEnd());

        ModValidationReport report = new ModValidator().validate(
                jar(Map.of(ENTRY, entry)), "example.Entry");

        assertTrue(report.findings().stream().noneMatch(f -> f.code().equals("NON_API_ENGINE_REFERENCE")
                        && f.message().contains(DELEGATING_GAME_MODULE)),
                report.findings()::toString);
    }

    @Test
    void prescribedStandaloneSupportTypesAreTrustedCreatorApiReferences() throws Exception {
        byte[] entry = entrypoint(true, true, writer -> {
            writer.visitField(Opcodes.ACC_PRIVATE, "groundSensor",
                    "L" + GROUND_SENSOR + ";", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PRIVATE, "levelInitProfile",
                    "L" + ABSTRACT_LEVEL_INIT_PROFILE + ";", null, null).visitEnd();
        });

        ModValidationReport report = new ModValidator().validate(
                jar(Map.of(ENTRY, entry)), "example.Entry");

        assertTrue(report.findings().stream().noneMatch(f -> f.code().equals(
                        "NON_API_ENGINE_REFERENCE") && (f.message().contains(GROUND_SENSOR)
                        || f.message().contains(ABSTRACT_LEVEL_INIT_PROFILE))),
                report.findings()::toString);
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

    @Test
    void acceptsJavaCompilerImmutableEnumsAndGeneratedSwitchTables() throws Exception {
        var classes = compileConstants("""
                enum Course {
                    EHZ1(320, "one"), EHZ2(800, "two");
                    final int width; final String label;
                    Course(int width, String label) { this.width = width; this.label = label; }
                }
                enum Result { SETTLED, FINISH, DAMAGE }
                class Choice {
                    int choose(Course course, Result result) {
                        return switch (course) {
                            case EHZ1 -> switch (result) { case FINISH -> 1; default -> 2; };
                            case EHZ2 -> 3;
                        };
                    }
                }
                """);
        var report = new ModValidator().validate(jar(classes), "example.Entry");
        assertTrue(report.eligible(), report.findings().toString());
        assertTrue(classes.keySet().stream().anyMatch(name -> name.endsWith("$1")), "real javac switch helper fixture");
    }

    @Test
    void acceptsAssertionsAlongsideOwnPlatformAndApiEnumSwitches() throws Exception {
        var classes = compile("""
                package example;
                public final class Entry implements com.openggf.mods.code.GgfMod {
                    @Override public void register(com.openggf.mods.code.ModContext context) { }
                    enum Course {
                        ONE, TWO;
                        int displayOrdinal() { assert ordinal() >= 0; return ordinal(); }
                    }
                    static final class Choice {
                        int positive(int value) { assert value > 0; return value; }
                    }
                    int code(Course course, java.time.DayOfWeek day, com.openggf.game.GameId game) {
                        assert course != null;
                        return switch (course) { case ONE -> 1; case TWO -> 2; }
                                + switch (day) { case MONDAY -> 1; default -> 0; }
                                + switch (game) { case S1 -> 1; default -> 0; };
                    }
                }
                """);
        var report = new ModValidator().validate(jar(classes), "example.Entry");
        assertTrue(report.eligible(), report.findings().toString());
        assertTrue(classes.containsKey("example/Entry$1"), "one compiler helper covers all three enum owners");
    }

    @Test
    void rejectsMutatedCompilerArtifactsWhenAssertionsAndOwnEnumSwitchOverlap() throws Exception {
        var clean = compile("""
                package example;
                enum Course { ONE, TWO }
                public final class Entry implements com.openggf.mods.code.GgfMod {
                    @Override public void register(com.openggf.mods.code.ModContext context) { }
                    int code(Course course) {
                        assert course != null;
                        return switch (course) { case ONE -> 1; case TWO -> 2; };
                    }
                }
                """);
        String helper = "example/Entry$1";
        assertTrue(clean.containsKey(helper), "top-level enum produces a real javac switch-map helper");
        for (String mutation : List.of("assertFlags", "switchFlags", "assertCallback", "switchCallback",
                "extraRead", "assertBranch", "assertHost")) {
            String owner = mutation.startsWith("switch") ? helper : ENTRY;
            var modified = new LinkedHashMap<>(clean);
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(clean.get(owner)).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                        String signature, Object value) {
                    if (mutation.endsWith("Flags") && (name.equals("$assertionsDisabled") || name.startsWith("$SwitchMap$"))) {
                        access &= ~Opcodes.ACC_SYNTHETIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!name.equals("<clinit>")) return delegate;
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN && mutation.endsWith("Callback")) {
                                super.visitLdcInsn("openggf.mod.validator.enumExecuted"); super.visitLdcInsn("true");
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                                super.visitInsn(Opcodes.POP);
                            } else if (opcode == Opcodes.RETURN && mutation.equals("extraRead")) {
                                super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                super.visitInsn(Opcodes.POP);
                            }
                            super.visitInsn(opcode);
                        }
                        @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                            super.visitJumpInsn(mutation.equals("assertBranch") && opcode == Opcodes.IFNE
                                    ? Opcodes.IFEQ : opcode, label);
                        }
                        @Override public void visitLdcInsn(Object value) {
                            super.visitLdcInsn(mutation.equals("assertHost") && value instanceof org.objectweb.asm.Type
                                    ? org.objectweb.asm.Type.getObjectType("java/lang/Object") : value);
                        }
                    };
                }
            }, 0);
            modified.put(owner, writer.toByteArray());
            System.clearProperty("openggf.mod.validator.enumExecuted");
            var report = new ModValidator().validate(jar(modified), "example.Entry");
            assertFalse(report.eligible(), mutation + ": " + report.findings());
            assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("STATIC_STATE_UNSUPPORTED")
                    && f.className().equals(owner)), mutation + ": " + report.findings());
            assertNull(System.getProperty("openggf.mod.validator.enumExecuted"), "validator never executes " + mutation);
        }
    }

    @Test
    void engineEnumSwitchRecognitionRequiresTheApiAllowlist() throws Exception {
        var classes = compile("""
                package example;
                public final class Entry implements com.openggf.mods.code.GgfMod {
                    @Override public void register(com.openggf.mods.code.ModContext context) { }
                    int code(com.openggf.game.LevelAssemblyKind kind) {
                        return switch (kind) { case DECODE_ONLY -> 1; default -> 0; };
                    }
                }
                """);
        var rejected = new ModValidator().validate(jar(classes), "example.Entry");
        assertCode(rejected, "STATIC_STATE_UNSUPPORTED", ModValidationFinding.Severity.ERROR);
        assertCode(rejected, "NON_API_ENGINE_REFERENCE", ModValidationFinding.Severity.WARNING);
        var allowed = new ModValidator(Set.of("com/openggf/game/LevelAssemblyKind")).validate(jar(classes), "example.Entry");
        assertTrue(allowed.eligible(), allowed.findings().toString());
    }

    @Test
    void rejectsMutableEnumsAndEnumConstructorCallbacksWithoutExecutingThem() throws Exception {
        for (String declaration : List.of(
                "enum Course { ONE; int mutable; }",
                "enum Course { ONE; final int[] state = new int[1]; }",
                "enum Course { ONE; Course() { System.setProperty(\"openggf.mod.validator.enumExecuted\", \"true\"); } }",
                "enum Course { ONE; static { System.setProperty(\"openggf.mod.validator.enumExecuted\", \"true\"); } }",
                "enum Course { ONE; final int value = Integer.parseInt(\"1\"); }")) {
            System.clearProperty("openggf.mod.validator.enumExecuted");
            var report = new ModValidator().validate(jar(compileConstants(declaration)), "example.Entry");
            assertFalse(report.eligible(), declaration);
            assertCode(report, "STATIC_STATE_UNSUPPORTED", ModValidationFinding.Severity.ERROR);
            assertNull(System.getProperty("openggf.mod.validator.enumExecuted"), "validator never loads creator enum");
        }
    }

    @Test
    void rejectsSyntheticSwitchHelperWithCallbackOrRuntimeTableRewrites() throws Exception {
        var clean = compileConstants("enum Course { ONE, TWO } class Choice { int choose(Course c) { return switch(c) { case ONE -> 1; case TWO -> 2; }; } }");
        String helper = clean.keySet().stream().filter(name -> name.endsWith("$1")).findFirst().orElseThrow();
        for (boolean callback : List.of(true, false)) {
            var modified = new LinkedHashMap<>(clean);
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(clean.get(helper)).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!name.equals("<clinit>") || !callback) return delegate;
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitLdcInsn("openggf.mod.validator.enumExecuted"); super.visitLdcInsn("true");
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                                super.visitInsn(Opcodes.POP);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                @Override public void visitEnd() {
                    if (!callback) {
                        MethodVisitor reset = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "reset", "()V", null, null);
                        reset.visitCode(); reset.visitInsn(Opcodes.ICONST_0); reset.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
                        reset.visitFieldInsn(Opcodes.PUTSTATIC, helper, "$SwitchMap$example$Course", "[I");
                        reset.visitInsn(Opcodes.RETURN); reset.visitMaxs(1, 0); reset.visitEnd();
                    }
                    super.visitEnd();
                }
            }, 0);
            modified.put(helper, writer.toByteArray());
            System.clearProperty("openggf.mod.validator.enumExecuted");
            var report = new ModValidator().validate(jar(modified), "example.Entry");
            assertFalse(report.eligible(), "synthetic metadata cannot whitelist callbacks or table mutation");
            assertCode(report, "STATIC_STATE_UNSUPPORTED", ModValidationFinding.Severity.ERROR);
            assertNull(System.getProperty("openggf.mod.validator.enumExecuted"));
        }
    }

    @Test
    void rejectsForgedEnumBackingArrayAccessAndNonCompilerExceptionGuards() throws Exception {
        var clean = compileConstants("enum Course { ONE, TWO } class Choice { int choose(Course c) { return switch(c) { case ONE -> 1; case TWO -> 2; }; } }");
        var leaked = new LinkedHashMap<>(clean);
        ClassWriter enumWriter = new ClassWriter(0);
        new ClassReader(clean.get("example/Course")).accept(new ClassVisitor(Opcodes.ASM9, enumWriter) {
            @Override public void visitEnd() {
                MethodVisitor accessor = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "(I)[Lexample/Course;", null, null);
                accessor.visitCode(); accessor.visitFieldInsn(Opcodes.GETSTATIC, "example/Course", "$VALUES", "[Lexample/Course;");
                accessor.visitInsn(Opcodes.ARETURN); accessor.visitMaxs(1, 1); accessor.visitEnd();
                super.visitEnd();
            }
        }, 0);
        leaked.put("example/Course", enumWriter.toByteArray());
        assertFalse(new ModValidator().validate(jar(leaked), "example.Entry").eligible(), "an overloaded values method cannot leak static enum array");
        String helper = clean.keySet().stream().filter(name -> name.endsWith("$1")).findFirst().orElseThrow();
        var forged = new LinkedHashMap<>(clean);
        ClassWriter helperWriter = new ClassWriter(0);
        new ClassReader(clean.get(helper)).accept(new ClassVisitor(Opcodes.ASM9, helperWriter) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                            org.objectweb.asm.Label handler, String type) {
                        super.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
                    }
                };
            }
        }, 0);
        forged.put(helper, helperWriter.toByteArray());
        assertFalse(new ModValidator().validate(jar(forged), "example.Entry").eligible(), "synthetic switch exception guards must match javac shape");
    }

    private Map<String, byte[]> compileConstants(String declarations) throws Exception {
        Path directory = Files.createTempDirectory(temp, "java-constants-");
        Path source = directory.resolve("Entry.java");
        Files.writeString(source, "package example; public class Entry implements com.openggf.mods.code.GgfMod { public void register(com.openggf.mods.code.ModContext context) { } } " + declarations);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", directory.toString(), source.toString()));
        var result = new LinkedHashMap<String, byte[]>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                result.put(directory.relativize(file).toString().replace('\\', '/').replaceFirst("\\.class$", ""), Files.readAllBytes(file));
            }
        }
        return result;
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
