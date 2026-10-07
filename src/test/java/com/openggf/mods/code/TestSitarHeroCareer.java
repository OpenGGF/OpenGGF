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

/** Exercises the real external career model with an empty engine/application classpath. */
class TestSitarHeroCareer {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path project = Path.of("examples/sitar-hero");
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", empty.toString(), "-d", output.toString()));
        for (String tree : List.of("src/main/java/sitarhero/model", "src/test/java/sitarhero/model")) {
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
        try { loader.loadClass("sitarhero.model.CareerChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError a) throw a;
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }
    @Test void everyStarThresholdIsAttainableAndFailedRunsNeverClear() throws Exception { check("attainableStarsAndGrades"); }
    @Test void invalidResultsAreRejectedAndOnlyFinishedSessionsProduceResults() throws Exception { check("resultValidationAndFinalSessionSnapshot"); }
    @Test void instrumentsAndDifficultiesHaveIndependentRecordsAndEligibleTotals() throws Exception { check("independentRecordsAndAttemptTotals"); }
    @Test void invalidInstrumentOrDifficultyNeverBecomesAnEmptyTour() throws Exception { check("querySelectionValidationAlsoAppliesToEmptyTours"); }
    @Test void aLowerScoreCanImproveStarsWithoutSynthesizingAnAttempt() throws Exception { check("lowerScoreCanImproveStarsWithoutInventingAnAttempt"); }
    @Test void savesRoundTripAndImportedLegacyScoresCannotInventClears() throws Exception { check("deterministicRoundTripAndLegacyScoreMigration"); }
    @Test void corruptionOrUnsupportedVersionsCannotPartiallyReplaceProgress() throws Exception { check("corruptionCannotPartiallyReplaceExistingProgress"); }
    @Test void recordCapsAndOverflowCannotCorruptStatistics() throws Exception { check("boundedRecordsAndSaturatingTotals"); }
    @Test void tiersUnlockInSequenceForTheSelectedInstrumentAndDifficulty() throws Exception { check("sequentialTiersAndWholeTourCompletion"); }
    @Test void everyNonemptyRomSubsetCanCompleteItsWholeTour() throws Exception { check("everyRomSubsetCanFinishWithoutMissingSongGates"); }
    @Test void duplicateMetadataCannotInventProgress() throws Exception { check("duplicateSongsDoNotAwardExtraClearsOrUnlocks"); }
    @Test void practiceSurvivesZeroRockWithoutChangingMissOrRecoveryRules() throws Exception { check("noFailResolvesAllMissesAndCanRecoverAudioAndStreak"); }
}
