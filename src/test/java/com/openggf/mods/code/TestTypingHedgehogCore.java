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

/** Runs Typing of the Hedgehog's engine-free typing rules ({@code typinghog.core}) outside the engine. */
class TestTypingHedgehogCore {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/typing-of-the-hedgehog");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString()));
        for (String tree : List.of("src/main/java/typinghog/core", "src/test/java/typinghog/core")) {
            Path root = project.resolve(tree);
            assertTrue(Files.isDirectory(root), "Missing typing core: " + root);
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
        try { loader.loadClass("typinghog.core.CoreChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    @Test void promptFormsDropPunctuationAndCaseUnlessEnabled() throws Exception { check("promptForms"); }
    @Test void aLeadLocksTheMostUrgentMatchingTarget() throws Exception { check("lockingPrefersTheMostUrgentMatch"); }
    @Test void errorsNeitherAdvanceNorStealTheLock() throws Exception { check("errorsDoNotAdvanceOrSteal"); }
    @Test void releaseRestartsAPromptAndHiddenPromptsCannotLock() throws Exception { check("releaseAndHiddenTargets"); }
    @Test void phrasesNeedTheirSpaces() throws Exception { check("phrasesNeedSpaces"); }
    @Test void chainsMultiplyAndPickTheRomScoreSprite() throws Exception { check("scoringChainsAndPopups"); }
    @Test void wordsPerMinuteCountsOnlyActiveRows() throws Exception { check("speedCountsActiveRowsOnly"); }
    @Test void tallyBonusesAndGrades() throws Exception { check("tallyAndGrades"); }
    @Test void anAssistNeverLeavesAPhraseOnASpace() throws Exception { check("assistNeverStopsOnASpace"); }
}
