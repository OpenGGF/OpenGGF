package com.openggf.mods.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCreatorTestLauncher {
    @TempDir Path work;

    @Test void runsJupiterLifecycleParameterizedDynamicAndExtensionTests() throws Exception {
        Path classes = compile("CreatorChecks", """
                import org.junit.jupiter.api.*;
                import org.junit.jupiter.api.extension.*;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.ValueSource;
                @org.junit.jupiter.api.extension.ExtendWith(CreatorChecks.Ext.class)
                public class CreatorChecks {
                    public static int beforeAll, beforeEach, afterEach, afterAll, extensions, bodies;
                    @BeforeAll static void start() { beforeAll++; }
                    @BeforeEach void before() { beforeEach++; }
                    @AfterEach void after() { afterEach++; }
                    @AfterAll static void end() { afterAll++; }
                    @Test void ordinary() { bodies++; }
                    @ParameterizedTest @ValueSource(ints={1,2}) void parameter(int value) {
                        Assertions.assertTrue(value > 0); bodies++;
                    }
                    @TestFactory java.util.List<DynamicTest> dynamic() {
                        return java.util.List.of(DynamicTest.dynamicTest("one", () -> bodies++),
                                DynamicTest.dynamicTest("two", () -> bodies++));
                    }
                    public static class Ext implements BeforeEachCallback {
                        public void beforeEach(ExtensionContext ctx) { extensions++; }
                    }
                }
                """);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
                getClass().getClassLoader())) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            StringWriter output = new StringWriter();
            assertEquals(0, CreatorTestLauncher.scan(loader, classes, new PrintWriter(output), new PrintWriter(output)),
                    output::toString);
            assertEquals(previous, Thread.currentThread().getContextClassLoader());
            Class<?> checks = loader.loadClass("CreatorChecks");
            assertEquals(1, checks.getField("beforeAll").getInt(null));
            assertEquals(1, checks.getField("afterAll").getInt(null));
            assertEquals(4, checks.getField("beforeEach").getInt(null));
            assertEquals(4, checks.getField("afterEach").getInt(null));
            assertEquals(4, checks.getField("extensions").getInt(null));
            assertEquals(5, checks.getField("bodies").getInt(null));
        }
    }

    @Test void failedAndEmptyDiscoveryReturnFailure() throws Exception {
        Path classes = compile("FailingChecks", """
                public class FailingChecks {
                    @org.junit.jupiter.api.Test void failure() { org.junit.jupiter.api.Assertions.fail("expected"); }
                }
                """);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
                getClass().getClassLoader())) {
            StringWriter output = new StringWriter();
            assertNotEquals(0, CreatorTestLauncher.scan(loader, classes, new PrintWriter(output), new PrintWriter(output)));
            Path empty = Files.createDirectory(work.resolve("empty"));
            assertNotEquals(0, CreatorTestLauncher.scan(loader, empty, new PrintWriter(output), new PrintWriter(output)));
        }
    }

    private Path compile(String name, String source) throws Exception {
        Path java = work.resolve(name + ".java");
        Files.writeString(java, source);
        Path classes = Files.createDirectories(work.resolve(name));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), java.toString()));
        return classes;
    }
}
