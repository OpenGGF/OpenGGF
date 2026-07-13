package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestModJarValidator {
    private static final String MANIFEST = "META-INF/openggf-mod.yaml";
    @TempDir Path temp;

    @Test
    void knownGoodCodeAndDataJarPassesWithoutLoadingAuthorClasses() throws Exception {
        ModJarValidator.Report report = new ModJarValidator().validate(jar(validEntries()));
        assertTrue(report.valid(), report.findings().toString());

        Map<String, byte[]> dataOnly = validEntries();
        dataOnly.remove("example/Entry.class");
        dataOnly.put(MANIFEST, validManifest().replace("entrypoint: example.Entry\n", "")
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(new ModJarValidator().validate(jar(dataOnly)).valid(), "known-good data-only jar");
    }

    @Test
    void wrapperRejectsFieldlessClassInitializerWithoutExecutingIt() throws Exception {
        System.clearProperty("ggfmod.fixture.loaded");
        Map<String, byte[]> entries = validEntries();
        entries.put("example/Entry.class", entrypointWithInitializer());
        assertCode(new ModJarValidator().validate(jar(entries)), "STATIC_STATE_UNSUPPORTED");
        assertNull(System.getProperty("ggfmod.fixture.loaded"));
    }

    @Test
    void reportsManifestApiRangeAndPerJarPatternBudgetRules() throws Exception {
        assertCode(validateWithManifest(validManifest().replace("formatVersion: 1", "formatVersion: 2")),
                "MANIFEST_INVALID");
        assertCode(validateWithManifest(validManifest().replace(">=1.0.0 <3.0.0", ">=9.0.0")),
                "ENGINE_API_INCOMPATIBLE");
        assertCode(validateWithManifest(validManifest().replace("patternWindows: 1", "patternWindows: 17")),
                "MANIFEST_INVALID");
    }

    @Test
    void reportsDeclaredAssetPresenceAndBakedFormatRules() throws Exception {
        Map<String, byte[]> missing = validEntries();
        missing.remove("art/object.ggfsheet");
        assertCode(new ModJarValidator().validate(jar(missing)), "ASSET_MISSING");

        Map<String, byte[]> malformed = validEntries();
        malformed.put("art/object.ggfsheet", new byte[] {0, 1, 2});
        assertCode(new ModJarValidator().validate(jar(malformed)), "ASSET_FORMAT_INVALID");
    }

    @Test
    void reportsAudioManifestAssetDecodeAndLoopRules() throws Exception {
        Map<String, byte[]> malformedManifest = validEntries();
        malformedManifest.put("audio/audio-manifest.yaml", "formatVersion: nope\n".getBytes(StandardCharsets.UTF_8));
        assertCode(new ModJarValidator().validate(jar(malformedManifest)), "AUDIO_MANIFEST_INVALID");

        Map<String, byte[]> malformedAudio = validEntries();
        malformedAudio.put("audio/audio-manifest.yaml", audioManifest(0, null));
        malformedAudio.put("audio/test.wav", new byte[] {1, 2, 3});
        assertCode(new ModJarValidator().validate(jar(malformedAudio)), "AUDIO_ASSET_INVALID");

        Map<String, byte[]> badLoop = validEntries();
        badLoop.put("audio/audio-manifest.yaml", audioManifest(2, 8L));
        badLoop.put("audio/test.wav", tinyWav());
        assertCode(new ModJarValidator().validate(jar(badLoop)), "AUDIO_LOOP_INVALID");

        Map<String, byte[]> combined = validEntries();
        combined.put(MANIFEST, validManifest().replace("audioOverrides: {}", "audioOverrides: {999: test}")
                .getBytes(StandardCharsets.UTF_8));
        combined.put("audio/audio-manifest.yaml", audioManifest(0, null));
        combined.put("audio/test.wav", new byte[] {1, 2, 3});
        ModJarValidator.Report combinedReport = new ModJarValidator().validate(jar(combined));
        assertCode(combinedReport, "AUDIO_OVERRIDE_ID_INVALID");
        assertCode(combinedReport, "AUDIO_ASSET_INVALID");

        Map<String, byte[]> malformedSfx = validEntries();
        malformedSfx.put("audio/audio-manifest.yaml", audioManifestWithSfx());
        malformedSfx.put("audio/test.wav", tinyWav());
        malformedSfx.put("audio/hit.wav", new byte[] {1, 2, 3});
        ModJarValidator.Report malformedSfxReport = new ModJarValidator().validate(jar(malformedSfx));
        assertCode(malformedSfxReport, "AUDIO_ASSET_INVALID");
        assertTrue(malformedSfxReport.findings().stream().anyMatch(finding ->
                finding.member().equals(new com.openggf.mods.SfxKey("example", "hit").toString())
                        && finding.location().equals("audio/hit.wav")));
    }

    @Test
    void reportsInventoriedLevelFormatAndNamespacedObjectKeyRules() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.putAll(levelWithObjectKey("Bad Key"));
        ModJarValidator.Report report = new ModJarValidator().validate(jar(entries));
        assertCode(report, "LEVEL_FORMAT_INVALID");
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("LEVEL_FORMAT_INVALID")
                && f.message().contains("objectKey")), report.findings().toString());
    }

    @Test
    void rejectsCrossOwnerLevelKeysAndUnknownStockProgressionAnchor() throws Exception {
        Map<String, byte[]> entries = validEntries();
        Map<String, byte[]> level = levelWithObjectKey("other:object");
        String jsonPath = "levels/example/level.json";
        String json = new String(level.get(jsonPath), StandardCharsets.UTF_8)
                .replace("\"music\":{\"stockId\":0}",
                        "\"music\":{\"trackKey\":{\"modId\":\"other\",\"name\":\"track\"}}");
        level.put(jsonPath, json.getBytes(StandardCharsets.UTF_8));
        entries.putAll(level);
        ModJarValidator.Report ownerReport = new ModJarValidator().validate(jar(entries));
        assertCode(ownerReport, "LEVEL_OWNER_MISMATCH");
        assertEquals(2, ownerReport.findings().stream()
                .filter(f -> f.code().equals("LEVEL_OWNER_MISMATCH")).count());

        String badAnchor = validManifest().replace("patternWindows: 1", "insertAfter: nowhere\npatternWindows: 1");
        assertCode(validateWithManifest(badAnchor), "INSERT_AFTER_STOCK_ANCHOR_INVALID");
        String standaloneAnchor = badAnchor.replace("type: patch\nbaseGame: s2", "type: standalone");
        assertCode(validateWithManifest(standaloneAnchor), "INSERT_AFTER_STOCK_ANCHOR_INVALID");
    }

    @Test
    void everyPassUsesTheRetainedImmutableSnapshot() throws Exception {
        Path source = jar(validEntries());
        ModJarValidator validator = new ModJarValidator(com.openggf.io.ModInputLimits.production(),
                snapshot -> Files.write(source, new byte[] {1, 2, 3}));
        assertTrue(validator.validate(source).valid(), "source replacement after snapshot must be irrelevant");
    }

    @Test
    void preservesStructuralStaticCodeAndHasNoNumericModObjectIdRule() throws Exception {
        Map<String, byte[]> invalid = validEntries();
        invalid.put("example/Entry.class", entrypoint(true, false));
        ModJarValidator.Report report = new ModJarValidator().validate(jar(invalid));
        assertCode(report, "STATIC_STATE_UNSUPPORTED");
        assertTrue(report.findings().stream().noneMatch(f -> f.code().contains("OBJECT_ID")));

        Map<String, byte[]> numericConstant = validEntries();
        numericConstant.put("example/Entry.class", entrypoint(false, true));
        assertTrue(new ModJarValidator().validate(jar(numericConstant)).valid(),
                "numeric object constants have no reserved-range rule");
    }

    @Test
    void rejectsObjectInstanceThatDoesNotUseSupportedAbstractObjectBase() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("example/Unsupported.class", unsupportedObject());
        assertCode(new ModJarValidator().validate(jar(entries)), "OBJECT_BASE_CONTRACT");
    }

    @Test
    void wrapperPreservesStructuralEntrypointObjectAndClassfileFindingCodes() throws Exception {
        assertCode(validateWithManifest(validManifest().replace("example.Entry", "missing.Entry")),
                "ENTRYPOINT_MISSING");

        Map<String, byte[]> contract = validEntries();
        contract.put("example/Entry.class", entrypoint(false, false, false, true));
        assertCode(new ModJarValidator().validate(jar(contract)), "ENTRYPOINT_CONTRACT");

        Map<String, byte[]> constructor = validEntries();
        constructor.put("example/Entry.class", entrypoint(false, false, true, false));
        assertCode(new ModJarValidator().validate(jar(constructor)), "ENTRYPOINT_CONSTRUCTOR");

        Map<String, byte[]> recreate = validEntries();
        recreate.put("example/Object.class", objectClass(false, false, null));
        assertCode(new ModJarValidator().validate(jar(recreate)), "OBJECT_RECREATE_PATH_MISSING");

        Map<String, byte[]> finalScalar = validEntries();
        finalScalar.put("example/Object.class", objectClass(true, false,
                writer -> writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                        "routine", "I", null, null).visitEnd()));
        assertCode(new ModJarValidator().validate(jar(finalScalar)), "FINAL_SCALAR_REWIND_GAP");

        Map<String, byte[]> objectRef = validEntries();
        objectRef.put("example/Object.class", objectClass(true, false,
                writer -> writer.visitField(Opcodes.ACC_PRIVATE, "parent",
                        "Lcom/openggf/level/objects/ObjectInstance;", null, null).visitEnd()));
        assertCode(new ModJarValidator().validate(jar(objectRef)), "OBJECT_REFERENCE_REWIND_ID_MISSING");

        Map<String, byte[]> services = validEntries();
        services.put("example/Object.class", objectClass(true, true, null));
        assertCode(new ModJarValidator().validate(jar(services)), "CONSTRUCTOR_SERVICES_ACCESS");

        Map<String, byte[]> malformed = validEntries();
        malformed.put("example/Entry.class", new byte[] {0, 1, 2});
        assertCode(new ModJarValidator().validate(jar(malformed)), "MALFORMED_CLASSFILE");

        Map<String, byte[]> mismatch = validEntries();
        mismatch.remove("example/Entry.class");
        mismatch.put("example/Alias.class", entrypoint(false, false));
        assertCode(new ModJarValidator().validate(jar(mismatch)), "CLASS_ENTRY_NAME_MISMATCH");

        Map<String, byte[]> reserved = validEntries();
        reserved.remove("example/Entry.class");
        reserved.put(MANIFEST, validManifest().replace("example.Entry", "com.openggf.Bad")
                .getBytes(StandardCharsets.UTF_8));
        reserved.put("com/openggf/Bad.class", namedEntrypoint("com/openggf/Bad", true, true));
        assertCode(new ModJarValidator().validate(jar(reserved)), "RESERVED_ENGINE_PACKAGE");

        assertCode(new ModJarValidator().validate(duplicateClassJar()), "DUPLICATE_CLASS");
    }

    @Test
    void cliNumbersDeterministicFindingsAndWarningsExitZero() throws Exception {
        Map<String, byte[]> warning = validEntries();
        warning.put("example/Entry.class", warningEntrypoint());
        Path warningJar = jar(warning);
        ByteArrayOutputStream warningOut = new ByteArrayOutputStream();
        assertEquals(0, GgfModCli.run(new String[] {"validate", warningJar.toString()},
                new PrintStream(warningOut, true, StandardCharsets.UTF_8)));
        String warningText = warningOut.toString(StandardCharsets.UTF_8);
        assertTrue(warningText.matches("(?s)1\\. WARNING NON_API_ENGINE_REFERENCE .*"), warningText);

        Map<String, byte[]> errors = validEntries();
        errors.put("art/object.ggfsheet", new byte[] {1});
        ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
        assertEquals(1, GgfModCli.run(new String[] {"validate", jar(errors).toString()},
                new PrintStream(errorOut, true, StandardCharsets.UTF_8)));
        assertTrue(errorOut.toString(StandardCharsets.UTF_8).startsWith("1. ERROR "));
        assertEquals(1, GgfModCli.run(new String[] {"validate"}, System.out));
    }

    @Test
    void cliTurnsInvalidPathsAndUnexpectedValidationFailuresIntoDeterministicErrors() {
        ByteArrayOutputStream invalidPath = new ByteArrayOutputStream();
        assertEquals(1, GgfModCli.run(new String[] {"validate", "bad\0path"},
                new PrintStream(invalidPath, true, StandardCharsets.UTF_8)));
        assertTrue(invalidPath.toString(StandardCharsets.UTF_8).startsWith("1. ERROR CLI_INPUT_INVALID "));

        ByteArrayOutputStream unexpected = new ByteArrayOutputStream();
        assertEquals(1, GgfModCli.run(new String[] {"validate", "mod.jar"},
                new PrintStream(unexpected, true, StandardCharsets.UTF_8),
                ignored -> { throw new IllegalStateException("seeded failure"); }));
        assertEquals("1. ERROR VALIDATION_FAILED seeded failure\n",
                unexpected.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private ModJarValidator.Report validateWithManifest(String manifest) throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put(MANIFEST, manifest.getBytes(StandardCharsets.UTF_8));
        return new ModJarValidator().validate(jar(entries));
    }

    private static void assertCode(ModJarValidator.Report report, String code) {
        assertTrue(report.findings().stream().anyMatch(f -> f.code().equals(code)),
                () -> code + " absent from " + report.findings());
    }

    private Map<String, byte[]> validEntries() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(MANIFEST, validManifest().getBytes(StandardCharsets.UTF_8));
        entries.put("example/Entry.class", entrypoint(false, false));
        entries.put("art/object.ggfsheet", validSheet());
        return entries;
    }

    private Path jar(Map<String, byte[]> entries) throws Exception {
        Path path = temp.resolve("fixture-" + System.nanoTime() + ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return path;
    }

    private Path duplicateClassJar() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove("example/Entry.class");
        entries.put(MANIFEST, validManifest().replace("example.Entry", "example.Duplicate")
                .getBytes(StandardCharsets.UTF_8));
        byte[] duplicate = namedEntrypoint("example/Duplicate", true, true);
        entries.put("example/DuplicatA.class", duplicate);
        entries.put("example/DuplicatB.class", duplicate);
        Path path = jar(entries);
        byte[] bytes = Files.readAllBytes(path);
        replaceAscii(bytes, "example/DuplicatA.class", "example/Duplicate.class");
        replaceAscii(bytes, "example/DuplicatB.class", "example/Duplicate.class");
        Files.write(path, bytes);
        return path;
    }

    private static void replaceAscii(byte[] bytes, String from, String to) {
        byte[] source = from.getBytes(StandardCharsets.US_ASCII);
        byte[] replacement = to.getBytes(StandardCharsets.US_ASCII);
        assertEquals(source.length, replacement.length);
        for (int offset = 0; offset <= bytes.length - source.length; offset++) {
            boolean match = true;
            for (int index = 0; index < source.length; index++) {
                if (bytes[offset + index] != source[index]) { match = false; break; }
            }
            if (match) System.arraycopy(replacement, 0, bytes, offset, replacement.length);
        }
    }

    private static String validManifest() {
        return """
                formatVersion: 1
                id: example
                name: Example
                version: 1.0.0
                authors: [Author]
                description: Fixture
                engineApiRange: ">=1.0.0 <3.0.0"
                type: patch
                baseGame: s2
                entrypoint: example.Entry
                dependencies: []
                audioOverrides: {}
                artOverrides:
                  object: art/object.ggfsheet
                patternWindows: 1
                """;
    }

    private static byte[] validSheet() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFS"); out.writeShort(1); out.writeInt(1);
            out.write(new byte[32]); out.writeShort(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] audioManifest(long loopStart, Long loopEnd) {
        String end = loopEnd == null ? "" : "    loopEndFrame: " + loopEnd + "\n";
        return ("formatVersion: 1\ntracks:\n  - id: test\n    assetPath: audio/test.wav\n"
                + "    loop: " + (loopEnd != null) + "\n    loopStartFrame: " + loopStart + "\n"
                + end + "    gain: 1.0\n    tempoEffects: false\nsfx: []\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] audioManifestWithSfx() {
        return new String(audioManifest(0, null), StandardCharsets.UTF_8)
                .replace("sfx: []", "sfx:\n  - id: hit\n    assetPath: audio/hit.wav\n    gain: 1.0")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tinyWav() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("RIFF"); writeLe32(out, 40); out.writeBytes("WAVEfmt ");
            writeLe32(out, 16); writeLe16(out, 1); writeLe16(out, 1); writeLe32(out, 8000);
            writeLe32(out, 16000); writeLe16(out, 2); writeLe16(out, 16);
            out.writeBytes("data"); writeLe32(out, 4); out.write(new byte[4]);
        }
        return bytes.toByteArray();
    }

    private static Map<String, byte[]> levelWithObjectKey(String objectKey) throws Exception {
        String root = "levels/example/";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(root + "patterns.bin", fixed("GPTN", 32, 1, new byte[32]));
        entries.put(root + "chunks.bin", fixed("GCHK", 8, 1, new byte[8]));
        entries.put(root + "blocks.bin", blockFile());
        entries.put(root + "foreground.map", mapFile());
        entries.put(root + "solid-heights.bin", fixed("GSHG", 16, 1, new byte[16]));
        entries.put(root + "solid-widths.bin", fixed("GSWD", 16, 1, new byte[16]));
        entries.put(root + "solid-angles.bin", fixed("GSAN", 1, 1, new byte[1]));
        entries.put(root + "collision-primary.bin", collisionFile(0));
        entries.put(root + "collision-secondary.bin", collisionFile(1));
        entries.put(root + "palettes.bin", paletteFile());
        entries.put(root + "level.json", ("""
                {"formatVersion":1,"zoneName":"Test","zoneIndex":64,"levelIndex":1024,
                 "blockGridSide":8,"width":1,"height":1,
                 "bounds":{"minX":0,"maxX":256,"minY":0,"maxY":256},
                 "start":{"x":0,"y":0},"music":{"stockId":0},
                 "assets":{"patterns":"patterns.bin","chunks":"chunks.bin","blocks":"blocks.bin",
                  "foregroundMap":"foreground.map","solidHeights":"solid-heights.bin",
                  "solidWidths":"solid-widths.bin","solidAngles":"solid-angles.bin",
                  "collisionPrimary":"collision-primary.bin","collisionSecondary":"collision-secondary.bin",
                  "palettes":"palettes.bin"},
                 "objects":[{"placementId":1,"x":0,"y":0,"objectKey":"%s","subtype":0,
                  "renderFlags":0,"respawnTracked":false,"rawYWord":0}],"rings":[]}
                """).formatted(objectKey).getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private static byte[] fixed(String magic, int recordSize, int count, byte[] payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes(magic); out.writeShort(1); out.writeShort(recordSize); out.writeInt(count); out.write(payload);
        }
        return bytes.toByteArray();
    }

    private static byte[] blockFile() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GBLK"); out.writeShort(1); out.writeByte(8); out.writeByte(0); out.writeInt(1);
            out.write(new byte[128]);
        }
        return bytes.toByteArray();
    }

    private static byte[] mapFile() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GMAP"); out.writeShort(1); out.writeShort(1); out.writeShort(1); out.writeShort(1);
            out.writeInt(1); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] collisionFile(int path) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GCOL"); out.writeShort(1); out.writeByte(path); out.writeByte(2); out.writeInt(1);
            out.writeShort(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] paletteFile() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GPAL"); out.writeShort(1); out.writeShort(1); out.writeShort(16); out.writeShort(0);
            out.write(new byte[32]);
        }
        return bytes.toByteArray();
    }

    private static void writeLe16(DataOutputStream out, int value) throws Exception {
        out.writeByte(value); out.writeByte(value >>> 8);
    }
    private static void writeLe32(DataOutputStream out, int value) throws Exception {
        out.writeByte(value); out.writeByte(value >>> 8); out.writeByte(value >>> 16); out.writeByte(value >>> 24);
    }

    private static byte[] entrypoint(boolean mutableStatic, boolean numericConstant) {
        return entrypoint(mutableStatic, numericConstant, true, true);
    }

    private static byte[] entrypoint(boolean mutableStatic, boolean numericConstant,
                                     boolean implementsContract, boolean publicConstructor) {
        return namedEntrypoint("example/Entry", implementsContract, publicConstructor,
                mutableStatic, numericConstant);
    }

    private static byte[] namedEntrypoint(String name, boolean implementsContract,
                                          boolean publicConstructor) {
        return namedEntrypoint(name, implementsContract, publicConstructor, false, false);
    }

    private static byte[] namedEntrypoint(String name, boolean implementsContract,
                                          boolean publicConstructor, boolean mutableStatic,
                                          boolean numericConstant) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object",
                implementsContract ? new String[] {"com/openggf/mods/code/GgfMod"} : null);
        if (mutableStatic) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "state", "I", null, null).visitEnd();
        if (numericConstant) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "OBJECT_ID", "I", null, 999).visitEnd();
        MethodVisitor constructor = writer.visitMethod(publicConstructor ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE,
                "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        MethodVisitor register = writer.visitMethod(Opcodes.ACC_PUBLIC, "register",
                "(Lcom/openggf/mods/code/ModContext;)V", null, null);
        register.visitCode(); register.visitInsn(Opcodes.RETURN); register.visitMaxs(0, 2); register.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] entrypointWithInitializer() {
        byte[] plain = entrypoint(false, false);
        ClassWriter writer = new ClassWriter(0);
        new org.objectweb.asm.ClassReader(plain).accept(writer, 0);
        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode(); clinit.visitLdcInsn("ggfmod.fixture.loaded"); clinit.visitLdcInsn("true");
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        clinit.visitInsn(Opcodes.POP); clinit.visitInsn(Opcodes.RETURN); clinit.visitMaxs(2, 0); clinit.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] objectClass(boolean recreate, boolean services,
                                      java.util.function.Consumer<ClassWriter> fields) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Object", null,
                "com/openggf/level/objects/AbstractObjectInstance",
                recreate ? new String[] {"com/openggf/level/objects/RewindRecreatable"} : null);
        if (fields != null) fields.accept(writer);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        if (services) {
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "example/Object", "services",
                    "()Lcom/openggf/level/objects/ObjectServices;", false);
            constructor.visitInsn(Opcodes.POP);
        }
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        if (recreate) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "recreateForRewind",
                    "(Lcom/openggf/level/objects/RewindRecreateContext;)Lcom/openggf/level/objects/AbstractObjectInstance;",
                    null, null);
            method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0); method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 2); method.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] warningEntrypoint() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Entry", null, "java/lang/Object",
                new String[] {"com/openggf/mods/code/GgfMod"});
        writer.visitField(Opcodes.ACC_PRIVATE, "engine", "Lcom/openggf/internal/Unstable;", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        MethodVisitor register = writer.visitMethod(Opcodes.ACC_PUBLIC, "register",
                "(Lcom/openggf/mods/code/ModContext;)V", null, null);
        register.visitCode(); register.visitInsn(Opcodes.RETURN); register.visitMaxs(0, 2); register.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] unsupportedObject() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Unsupported", null, "java/lang/Object",
                new String[] {"com/openggf/level/objects/ObjectInstance"});
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
}
