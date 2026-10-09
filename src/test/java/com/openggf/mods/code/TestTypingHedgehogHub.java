package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs Typing of the Hedgehog's engine-free hub rules ({@code typinghog.hub.HubChecks}: the
 * profile codec, story progression, typed menus, Typing School drills and grades, result
 * merging and hand-off records) compiled from the example's source with no engine classes on
 * the class path, so the rules cannot lean on the engine by accident.
 */
class TestTypingHedgehogHub {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/typing-of-the-hedgehog");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString()));
        for (String tree : List.of("src/main/java/typinghog/core", "src/main/java/typinghog/save",
                "src/main/java/typinghog/hub/model", "src/test/java/typinghog/hub")) {
            Path root = project.resolve(tree);
            assertTrue(Files.isDirectory(root), "Missing engine-free hub source: " + root);
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)),
                errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @AfterAll static void close() throws Exception {
        if (loader != null) loader.close();
    }

    private void check(String method) throws Exception {
        try {
            loader.loadClass("typinghog.hub.HubChecks").getMethod(method).invoke(null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    @Test void profileRoundTripsAndSurvivesDamage() throws Exception { check("profileRoundTripsAndSurvivesDamage"); }
    @Test void recordsKeepTheBestTen() throws Exception { check("recordsKeepTheBestTen"); }
    @Test void storyOpensStopsInOrderAndSkipsUnbuiltActs() throws Exception { check("storyOpensStopsInOrderAndSkipsUnbuiltActs"); }
    @Test void storyFileParses() throws Exception { check("storyFileParses"); }
    @Test void typedMenusFollowAndSelect() throws Exception { check("typedMenusFollowAndSelect"); }
    @Test void resultsMergeIntoTheProfile() throws Exception { check("resultsMergeIntoTheProfile"); }
    @Test void bossRushRaisesStarsAndEndless() throws Exception { check("bossRushRaisesStarsAndEndless"); }
    @Test void handoffsRoundTrip() throws Exception { check("handoffsRoundTrip"); }
    @Test void attractDemosPlayThemselves() throws Exception { check("attractDemosPlayThemselves"); }
    @Test void suddenDeathEndsOnTheFirstError() throws Exception { check("suddenDeathEndsOnTheFirstError"); }
    @Test void ringRushRunsSixtySeconds() throws Exception { check("ringRushRunsSixtySeconds"); }
    @Test void reflexTimesLettersAndMisses() throws Exception { check("reflexTimesLettersAndMisses"); }
    @Test void bossTrainingRoundsAndQuiz() throws Exception { check("bossTrainingRoundsAndQuiz"); }
    @Test void gradesAndFinalExamUnlock() throws Exception { check("gradesAndFinalExamUnlock"); }
    @Test void weakKeysFollowLifetimeMisses() throws Exception { check("weakKeysFollowLifetimeMisses"); }
    @Test void lessonsUseTheirRows() throws Exception { check("lessonsUseTheirRows"); }
    @Test void textArtParses() throws Exception { check("textArtParses"); }
}
