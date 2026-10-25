package com.openggf.mods.code;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Runs Typing of the Hedgehog's engine-free rail format checks ({@code typinghog.rails.RailChecks}) outside the engine. */
class TestTypingHedgehogRailFormat {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/typing-of-the-hedgehog");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString()));
        // Rail.java is engine-free; the rest of typinghog.rails talks to the engine and stays out.
        for (String file : List.of("src/main/java/typinghog/rails/Rail.java", "src/test/java/typinghog/rails/RailChecks.java")) {
            Path source = project.resolve(file);
            assertTrue(Files.isRegularFile(source), "Missing rail source: " + source);
            args.add(source.toString());
        }
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)),
                errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @AfterAll static void close() throws Exception { if (loader != null) loader.close(); }

    private void check(String method) throws Exception {
        try { loader.loadClass("typinghog.rails.RailChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    @Test void theRetiredRecordingFormatIsRejected() throws Exception { check("retiredFormatRejected"); }
    @Test void forwardRailsCarryTheirCountdownStandsAndEndpoint() throws Exception { check("forwardRails"); }
    @Test void forwardRailsRejectBrokenSchedulesAndMissingIdentity() throws Exception { check("forwardRejects"); }
    @Test void onlyFourOrFiveFightsQualifyARegularCourse() throws Exception { check("qualification"); }
}
