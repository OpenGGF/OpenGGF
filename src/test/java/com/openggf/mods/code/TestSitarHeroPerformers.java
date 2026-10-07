package com.openggf.mods.code;

import java.net.URLClassLoader;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compiles and packages the real external performer consumer before its cosmetic checks. */
class TestSitarHeroPerformers {
    @TempDir Path temp;

    @Test void nativeCutoutsAndHitDrivenGestures() throws Exception {
        try (var harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), temp.resolve("package"))) {
            Path tests = java.nio.file.Files.createDirectories(temp.resolve("checks"));
            String cp = temp.resolve("package/classes") + java.io.File.pathSeparator + System.getProperty("java.class.path");
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-cp", cp,
                    "-d", tests.toString(), "examples/sitar-hero/src/test/java/sitarhero/PerformerChecks.java"));
            // A single loader defines the example and its package-private tests together.
            try (var loader = new URLClassLoader(new java.net.URL[] {tests.toUri().toURL(),
                    temp.resolve("package/classes").toUri().toURL()}, getClass().getClassLoader())) {
                loader.loadClass("sitarhero.PerformerChecks").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        }
    }
}
