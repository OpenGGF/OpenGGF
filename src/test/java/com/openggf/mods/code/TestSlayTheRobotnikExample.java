package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds the Slay the Robotnik example the way a creator would (compile, package, validate)
 * and runs the example's own rules tests (examples/slay-the-robotnik/src/test/java) as
 * dynamic tests, so the engine suite catches API changes that break the example. No ROM is
 * needed: the rules core and its tests are plain Java.
 */
class TestSlayTheRobotnikExample {
    private static final Path PROJECT = Path.of("examples/slay-the-robotnik");

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
        copyResources(PROJECT.resolve("src/main/resources"), tests);
        compile(PROJECT.resolve("src/test/java"), main + java.io.File.pathSeparator
                + System.getProperty("java.class.path"), tests);
        URLClassLoader loader = new URLClassLoader(new java.net.URL[] {tests.toUri().toURL(), main.toUri().toURL()},
                getClass().getClassLoader());
        List<DynamicTest> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(tests)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class") && !f.toString().contains("$")).sorted()
                    .toList()) {
                String name = tests.relativize(p).toString().replace('/', '.').replace('\\', '.')
                        .replaceAll("\\.class$", "");
                Class<?> type = loader.loadClass(name);
                for (Method m : type.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Test.class) && !Modifier.isStatic(m.getModifiers())) {
                        found.add(DynamicTest.dynamicTest(type.getSimpleName() + "." + m.getName(), () -> run(type, m)));
                    }
                }
            }
        }
        assertFalse(found.isEmpty(), "the example has tests");
        return found.stream();
    }

    private static void run(Class<?> type, Method method) throws Throwable {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        method.setAccessible(true);
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
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
