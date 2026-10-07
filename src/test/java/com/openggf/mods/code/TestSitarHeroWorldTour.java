package com.openggf.mods.code;

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
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs the real authored-tour model without an engine classpath; scene packaging is an integration check. */
class TestSitarHeroWorldTour {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compileChecks() throws Exception {
        Path checks = Files.createDirectory(temp.resolve("checks"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", checks.toString()));
        for (String tree : List.of("src/main/java/sitarhero/model", "src/main/java/sitarhero/catalogue", "src/test/java/sitarhero/model")) {
            try (var files = Files.walk(Path.of("examples/sitar-hero").resolve(tree))) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)),
                errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{checks.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    @AfterAll static void close() throws Exception {
        if (loader != null) loader.close();
    }
    private void check(String method) throws Exception {
        try { loader.loadClass("sitarhero.model.WorldTourChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }
    @Test void routeMetadataIsImmutableAndCannotDuplicateAssignments() throws Exception { check("immutableMetadata"); }
    @Test void allSeventyNineSongsBelongOnceToTheNativeWorldOrder() throws Exception { check("completePartitionAndNativeOrder"); }
    @Test void installedRomSubsetsKeepCompleteRequiredRoutes() throws Exception { check("completeInstalledTours"); }
    @Test void everyMainActMustClearAndOptionalSongsNeverGate() throws Exception { check("everyMainActGatesAndSideGigsNeverGate"); }
    @Test void onlyCompletedEarnedCareerResultsEnterTheJournal() throws Exception { check("completedEligibleResultsOnly"); }
    @Test void journeyProgressIsSharedWhileHighScoresRemainIndependent() throws Exception { check("sharedProgressAndSeparateHighScores"); }
    @Test void foreignOrForgedMetadataCannotBypassAWorldGate() throws Exception { check("canonicalMembership"); }
    @Test void knownSeenScenesAreBoundedAndIdempotent() throws Exception { check("boundedSeenScenes"); }
    @Test void corruptSavesAreRejectedAtomicallyAndFullJournalsRoundTrip() throws Exception { check("atomicPersistence"); }
}
