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

/** Executes the real mod's rhythm rules outside the engine class loader. */
class TestSitarHeroModel {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/sitar-hero");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString()));
        for (String tree : List.of("src/main/java/sitarhero/model", "src/test/java/sitarhero/model")) {
            Path root = project.resolve(tree);
            assertTrue(Files.isDirectory(root), "Missing external rhythm model: " + root);
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)),
                errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    @AfterAll static void close() throws Exception { if (loader != null) loader.close(); }
    private void check(String method) throws Exception {
        try { loader.loadClass("sitarhero.model.RhythmChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }
    @Test void windowsAndSingleFretAnchoring() throws Exception { check("windowAndAnchoring"); }
    @Test void exactChordsAndOverstrums() throws Exception { check("chordsAndOverstrum"); }
    @Test void earlyHopoChainAndStrumRecovery() throws Exception { check("hopoChainAndRecovery"); }
    @Test void directPadEdgesAndSeparateKick() throws Exception { check("directPadsAndKick"); }
    @Test void sustainReleaseAndCadenceIndependentScoring() throws Exception { check("sustainReleaseAndCadence"); }
    @Test void completePhrasesPowerAndWhammy() throws Exception { check("phrasesPowerAndWhammy"); }
    @Test void powerExpiryInsideTailIsCadenceIndependent() throws Exception { check("powerExpiryCadence"); }
    @Test void failureFiniteEndAndFreshRetry() throws Exception { check("failuresAndFiniteResults"); }
    @Test void inputOrderAndChartOwnership() throws Exception { check("monotonicAndImmutableChart"); }
    @Test void finalGemKeepsItsLateWindowAndEveryNoteIsResolved() throws Exception { check("finalJudgmentWindow"); }
}
