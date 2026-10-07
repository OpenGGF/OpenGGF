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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compiles the shipped scripts without host classes, then exercises the actual story API. */
class TestSitarHeroStory {
    @TempDir static Path temp;
    private static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/sitar-hero");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString(),
                project.resolve("src/main/java/sitarhero/model/Roster.java").toString()));
        for (String tree : List.of("src/main/java/sitarhero/story", "src/test/java/sitarhero/story")) {
            try (var files = Files.walk(project.resolve(tree))) {
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
        try { loader.loadClass("sitarhero.story.StoryChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    @Test void allThirtyEightScriptsAreStableAndReadableForEveryAvailableCast() throws Exception { check("allScriptsForEveryRomSubset"); }
    @Test void everyPerformerHasContextualStagingWithoutRequiringMissingPortraits() throws Exception { check("performerVariationAndGuestDonors"); }
    @Test void unknownOrUnavailableToursCannotFallThroughToGenericDialogue() throws Exception { check("unknownAndUnavailableScenes"); }
    @Test void immutableScenesAndLinesRejectInvalidDisplayContent() throws Exception { check("immutableValidatedContent"); }
    @Test void playingRobotnikHasARespectedConcertAndAnUnsuccessfulOffstageScheme() throws Exception { check("robotnikFinalesAndRelationshipPayoffs"); }
    @Test void retryAndSuccessResponsesAreDistinctBoundedAndEncouraging() throws Exception { check("resultQuips"); }
}
