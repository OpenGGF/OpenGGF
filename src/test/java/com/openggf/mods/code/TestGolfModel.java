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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles the actual external model without engine classes on its compile classpath. */
class TestGolfModel {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compileExternalModel() throws Exception {
        Path project = Path.of("examples/putt-putt-paradise");
        Path source = project.resolve("src/main/java/paradise/model");
        assertTrue(Files.isDirectory(source), "External golf model has not been implemented");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        Path emptyClasspath = Files.createDirectory(temp.resolve("empty-classpath"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", emptyClasspath.toString(),
                "-d", classes.toString()));
        for (Path root : List.of(source, project.resolve("src/test/java/paradise/model"))) {
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output,
                args.toArray(String[]::new)), output.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    private void check(String method) throws Exception {
        try { loader.loadClass("paradise.model.GolfModelChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    @Test void puttUsesAForOnePowerSweepThenAutomaticallyReleases() throws Exception { check("puttTiming"); }
    @Test void chipTimesContactPointThenOneIndependentPowerSweep() throws Exception { check("chipTiming"); }
    @Test void missingTheOnlyPowerSweepSealsAVeryLightShotOnce() throws Exception { check("softExpiry"); }
    @Test void topAndBackContactPointsChangeDepartureAndLandingCarry() throws Exception { check("spinPhysics"); }
    @Test void heldAAndPostCommitCancelCannotDuplicateTheStrokeOrLaunch() throws Exception { check("buttonEdges"); }
    @Test void optionalCancelRestartsWithoutLeakingThePreviousSpin() throws Exception { check("freeCancel"); }
    @Test void restoredMidChargeProducesTheSameAutomaticLaunchAndEvents() throws Exception { check("meterReplay"); }
    @Test void higherPowerProducesMoreNativeChargeRequestsBeforeTheSameRelease() throws Exception { check("powerFeedback"); }
    @Test void aimingClampsElevationAndLocksItOnTheFirstA() throws Exception { check("aimLocks"); }
    @Test void allCharacterPairingsAlternateAndReverseTheSecondActStarter() throws Exception { check("pairings"); }
    @Test void compoundFailureBeatsFinishAndCannotAddAnotherPenaltyOnRetry() throws Exception { check("penaltiesAndDuplicates"); }
    @Test void completedGolferIsSkippedAndTwoActTotalsDetermineTheWinner() throws Exception { check("scoring"); }
    @Test void concessionLosesWithoutInventingStrokesAndAbandonmentHasNoWinner() throws Exception { check("concession"); }
    @Test void finishedGolferCanConcedeTheMatchWhileOpponentStillPlays() throws Exception { check("finishedConcession"); }
    @Test void lostBallAndWatchdogEachCostOnePenaltyAndEndTheTurn() throws Exception { check("otherPenalties"); }
    @Test void wrongTurnAndNonterminalObservationsCannotMutateTheLedger() throws Exception { check("invalidTurns"); }
    @Test void practiceActTwoEndsWithoutStartingAnotherHole() throws Exception { check("practice"); }
    @Test void ledgerSnapshotsRemainIndependentAndRestoreThePendingShot() throws Exception { check("ledgerReplay"); }
    @Test void rewindRefundsOnlyPendingShotAndRetiresItsIdentity() throws Exception { check("shotRewind"); }
    @Test void rewindLimitsAreIndependentPerGolferHoleAndTurn() throws Exception { check("rewindAllowances"); }
    @Test void longerShotsRewindFasterWithBoundedPlaybackTime() throws Exception { check("rewindSpeed"); }
    @Test void handoffNeedsAReleasedThenFreshPressAndRetriesKeepReadiness() throws Exception { check("turnReadiness"); }
}
