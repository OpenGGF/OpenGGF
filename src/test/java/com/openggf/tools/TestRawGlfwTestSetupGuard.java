package com.openggf.tools;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** No new test context owners: inventory bytecode without initializing any native code. */
class TestRawGlfwTestSetupGuard {
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String CONTEXT_OWNER = "com/openggf/tools/PresentationOffscreenTestContext";
    private static final String FIX = "Use PresentationOffscreenTestContext.openConfigured(width, height, "
            + "LegacyProfile.DEFAULT or CORE_41) instead of raw GLFW test setup. "
            + "Explicit EGL must execute or fail without a display fallback. "
            + "Delete migrated legacy entries; do not add exceptions.";
    private static final String RESOURCES = "/architecture/raw-glfw/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void onlyTheSupportedContextOwnerMayAddRawGlfwSetup() throws Exception {
        Path classes = TestSessionOutputPaths.compiledTestClasses();
        assertTrue(Files.isRegularFile(classes.resolve(CONTEXT_OWNER + ".class")),
                "the complete compiled test tree, including the supported context owner, is required");
        assertInventory(scan(classes), baseline());
    }

    @Test
    void rejectsOriginalReadPixelsFixtureFrom659b02c98() throws Exception {
        assertHistoricalFixtureRejected("GlReadPixelsGrabberTest",
                "60b74d5c0ce09581a7d72ce01eb06e507f00f0b2",
                "com.openggf.capture.GlReadPixelsGrabberTest#createOffscreenContextOrAbort()J");
    }

    @Test
    void rejectsOriginalCnzViewportFixtureFrom659b02c98() throws Exception {
        assertHistoricalFixtureRejected("TestCNZSlotMachineViewport",
                "39752b078688f071e06e9593932428a81cf1fc14",
                "com.openggf.game.sonic2.slotmachine.TestCNZSlotMachineViewport"
                        + "#reelsStayAtGameCoordinatesAcrossViewportChanges()V");
    }

    @Test
    void ratchetRejectsMoreCallsInAnExistingCallerAndCallsMovedToANewCaller() {
        String caller = "legacy.TestPixels#open()V->glfwInit()Z";
        Map<String, Integer> approved = Map.of(caller, 1);
        assertThrows(AssertionError.class, () -> assertInventory(Map.of(caller, 2), approved));
        assertThrows(AssertionError.class, () -> assertInventory(
                Map.of("legacy.TestPixels#anotherOpen()V->glfwInit()Z", 1), approved));
        assertThrows(AssertionError.class, () -> assertInventory(Map.of(), approved),
                "retired debt must be removed from the baseline rather than becoming spare allowance");
    }

    @Test
    void scannerRecognizesStaticImportsQualifiedCallsAndMethodReferences() throws Exception {
        Path classes = compile("RawSetup", """
                import java.util.function.BooleanSupplier;
                import static org.lwjgl.glfw.GLFW.glfwInit;

                class RawSetup {
                    boolean imported() { return glfwInit(); }
                    boolean qualified() { return org.lwjgl.glfw.GLFW.glfwInit(); }
                    BooleanSupplier reference() { return org.lwjgl.glfw.GLFW::glfwInit; }
                }
                """);
        assertEquals(Map.of(
                "RawSetup#imported()Z->glfwInit()Z", 1,
                "RawSetup#qualified()Z->glfwInit()Z", 1,
                "RawSetup#reference()Ljava/util/function/BooleanSupplier;->glfwInit()Z", 1), scan(classes));
    }

    private void assertHistoricalFixtureRejected(String name, String blob, String caller) throws Exception {
        byte[] source = resource(name + ".java.txt");
        // These are the complete, unmodified historical sources, not synthetic lookalikes.
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(("blob " + source.length + "\0").getBytes(StandardCharsets.UTF_8));
        assertEquals(blob, HexFormat.of().formatHex(sha1.digest(source)), "historical Git blob identity");
        Map<String, Integer> calls = scan(compile(name, new String(source, StandardCharsets.UTF_8)));
        assertEquals(2, calls.size(), "both original raw context operations must be observed");
        assertEquals(1, calls.get(caller + "->glfwInit()Z"));
        assertEquals(1, calls.get(caller
                + "->glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"));
        Map<String, Integer> restored = new TreeMap<>(scan(TestSessionOutputPaths.compiledTestClasses()));
        restored.putAll(calls);
        var failure = assertThrows(AssertionError.class, () -> assertInventory(restored, baseline()));
        assertTrue(failure.getMessage().contains(caller));
        assertTrue(failure.getMessage().contains(caller + "->glfwInit()Z observed=1 baseline=0"));
        assertTrue(failure.getMessage().contains(caller
                + "->glfwCreateWindow(IILjava/lang/CharSequence;JJ)J observed=1 baseline=0"));
        assertTrue(failure.getMessage().contains("PresentationOffscreenTestContext.openConfigured"));
        assertTrue(calls.keySet().stream().noneMatch(baseline()::containsKey),
                "the repaired fixtures must not gain legacy allowances");
    }

    private Path compile(String name, String source) throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve(name));
        Path java = root.resolve(name + ".java");
        Path classes = Files.createDirectory(root.resolve("classes"));
        Files.writeString(java, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "a JDK is required to compile the real historical negative controls");
        var output = new ByteArrayOutputStream();
        int result = compiler.run(null, output, output, "--release", "25", "-proc:none",
                "-implicit:none", "--source-path", "",
                "-classpath", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "-d", classes.toString(), java.toString());
        assertEquals(0, result, () -> "negative control failed to compile: " + output.toString(StandardCharsets.UTF_8));
        return classes;
    }

    private static Map<String, Integer> scan(Path classes) throws IOException {
        assertTrue(Files.isDirectory(classes), "compiled test classes are required: " + classes);
        Map<String, Integer> counts = new TreeMap<>();
        try (var files = Files.walk(classes)) {
            List<Path> bytecode = files.filter(path -> path.toString().endsWith(".class")).sorted().toList();
            assertFalse(bytecode.isEmpty(), "no test classes were scanned");
            for (Path file : bytecode) {
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    private String callerClass;

                    @Override
                    public void visit(int version, int access, String name, String signature,
                                      String superName, String[] interfaces) {
                        callerClass = name;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (CONTEXT_OWNER.equals(callerClass)) return null;
                        String caller = callerClass.replace('/', '.') + "#" + name + descriptor;
                        return new MethodVisitor(Opcodes.ASM9) {
                            private void record(String owner, String method, String desc) {
                                if (GLFW.equals(owner)
                                        && (method.equals("glfwInit") || method.equals("glfwCreateWindow"))) {
                                    counts.merge(caller + "->" + method + desc, 1, Integer::sum);
                                }
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String method,
                                                        String desc, boolean isInterface) {
                                record(owner, method, desc);
                            }

                            @Override
                            public void visitInvokeDynamicInsn(String name, String descriptor,
                                                               Handle bootstrap, Object... arguments) {
                                for (Object argument : arguments) {
                                    if (argument instanceof Handle handle) {
                                        record(handle.getOwner(), handle.getName(), handle.getDesc());
                                    }
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return counts;
    }

    private static void assertInventory(Map<String, Integer> actual, Map<String, Integer> approved) {
        var callers = new TreeSet<>(actual.keySet());
        callers.addAll(approved.keySet());
        var differences = callers.stream()
                .filter(caller -> !actual.getOrDefault(caller, 0).equals(approved.getOrDefault(caller, 0)))
                .map(caller -> caller + " observed=" + actual.getOrDefault(caller, 0)
                        + " baseline=" + approved.getOrDefault(caller, 0)).toList();
        assertEquals(List.of(), differences, FIX);
    }

    private static Map<String, Integer> baseline() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        for (String line : new String(resource("legacy-callers.txt"), StandardCharsets.UTF_8).lines().toList()) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split(" ", 2);
            assertEquals(2, columns.length, "malformed GLFW baseline: " + line);
            int count = Integer.parseInt(columns[0]);
            assertTrue(count > 0, "legacy allowance must be positive: " + line);
            assertNull(counts.put(columns[1], count), "duplicate GLFW baseline caller: " + line);
        }
        return counts;
    }

    private static byte[] resource(String name) throws IOException {
        try (var input = TestRawGlfwTestSetupGuard.class.getResourceAsStream(RESOURCES + name)) {
            assertNotNull(input, "required GLFW guard resource: " + name);
            return input.readAllBytes();
        }
    }
}
