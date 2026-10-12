package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import com.openggf.mods.testing.CreatorTestLauncher;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Packages the external creator sources through the SDK boundary and runs their
 * Jupiter model and production scene tests. Origin: Starpost Valley (2026-10-09), after Starfall Frontier's bridge.
 */
class TestStarpostValleyExample {
    private static final Path PROJECT = Path.of("examples/starpost-valley");

    private URLClassLoader loader;

    @org.junit.jupiter.api.AfterEach
    void closeLoader() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @TempDir
    Path work;

    @Test
    void packagesAndPassesValidation() throws Exception {
        // ExampleModHarness.build compiles, packages with GgfModCli (which validates) and registers.
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work)) {
            assertEquals("WIDE_16_9", harness.plan().requiredDisplayAspect(), "the mod asks for the 400-pixel display");
        }
    }

    @TestFactory
    Stream<DynamicTest> exampleRulesTests() throws Exception {
        Path main = Files.createDirectories(work.resolve("main"));
        Path tests = Files.createDirectories(work.resolve("tests"));
        compile(PROJECT.resolve("src/main/java"), System.getProperty("java.class.path"), main);
        copyResources(PROJECT.resolve("src/main/resources"), main);
        compile(PROJECT.resolve("src/test/java"), main + java.io.File.pathSeparator
                + System.getProperty("java.class.path"), tests);
        loader = new URLClassLoader(new java.net.URL[] {tests.toUri().toURL(), main.toUri().toURL()},
                getClass().getClassLoader());
        return Stream.of(DynamicTest.dynamicTest("creator Jupiter suite", () -> assertEquals(0,
                CreatorTestLauncher.scan(loader, tests, new PrintWriter(System.out, true),
                        new PrintWriter(System.err, true)), "the example Jupiter suite passed")));
    }

    private static void compile(Path sources, String classpath, Path out) throws Exception {
        List<String> args = new ArrayList<>(List.of("--release", "21", "-nowarn", "-cp", classpath, "-d", out.toString()));
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)),
                "compiling " + sources);
    }

    private static void copyResources(Path from, Path to) throws Exception {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                Files.createDirectories(target.getParent());
                Files.copy(p, target);
            }
        }
    }
}
