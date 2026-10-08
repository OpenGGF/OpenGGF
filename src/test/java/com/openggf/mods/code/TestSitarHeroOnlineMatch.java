package com.openggf.mods.code;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

/** Compiles and exercises the actual sample protocol/model with the production ScenePeer API. */
@Timeout(20)
class TestSitarHeroOnlineMatch {
    @TempDir static Path temp;
    private static URLClassLoader loader;

    @BeforeAll
    static void compile() throws Exception {
        Path project = Path.of("examples/sitar-hero");
        Path output = Files.createDirectory(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", System.getProperty("java.class.path"),
                "-d", output.toString()));
        for (String tree : List.of("src/main/java/sitarhero/model", "src/main/java/sitarhero/catalogue",
                "src/main/java/sitarhero/net", "src/test/java/sitarhero/net")) {
            try (var files = Files.walk(project.resolve(tree))) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors, args.toArray(String[]::new)),
                errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, TestSitarHeroOnlineMatch.class.getClassLoader());
    }

    @AfterAll
    static void close() throws Exception { if (loader != null) loader.close(); }

    private static void check(String name) throws Exception {
        try { loader.loadClass("sitarhero.net.OnlineMatchChecks").getMethod(name).invoke(null); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    @Test void pairedMatchingChartsAndIndependentJudgments() throws Exception { check("pairedJudgments"); }
    @Test void tenMinuteNaturalEndAndBoundedTail() throws Exception { check("longSong"); }
    @Test void rematchRejectsGuestAuthorityAndIgnoresOldRoundTraffic() throws Exception { check("rematch"); }
    @Test void rejectsUnsupportedRolesAndUnsharedSongs() throws Exception { check("offeredRoles"); }
    @Test void chartHashAndAudioRateMustMatch() throws Exception { check("mismatches"); }
    @Test void rejectsMalformedPacketsAndInvalidTelemetry() throws Exception { check("malformed"); }
    @Test void offsetUsesReceiveObservationsAndMinimumNetworkRtt() throws Exception { check("clockObservations"); }
    @Test void minimumRttWinsOverAsymmetricAndQueuedSamples() throws Exception { check("minimumRtt"); }
    @Test void completionReleasesPauseAndTelemetryRemainsMonotonic() throws Exception { check("completionAndTelemetry"); }
    @Test void clockUncertaintyDoesNotRejectValidEarlyPeerTraffic() throws Exception { check("timingUncertainty"); }
    @Test void signedAndZeroMonotonicClocksAreValid() throws Exception { check("signedClocks"); }
    @Test void startAcknowledgmentAndDuplicateMessagesAreIdempotent() throws Exception { check("startAcknowledgment"); }
    @Test void controlMayArriveBeforeLocalStartedCallback() throws Exception { check("callbackRace"); }
    @Test void crossedControlsRespectBothPauseOwners() throws Exception { check("pauseOwners"); }
    @Test void pauseThenResumeBeforeDeadlineRetainsLatestIntent() throws Exception { check("controlRace"); }
    @Test void burstsOfCrossedRequestsConvergeToFinalOwners() throws Exception { check("controlBursts"); }
    @Test void retriesControlsAndIgnoresObsoleteControlAcknowledgments() throws Exception { check("controlRetry"); }
    @Test void silentPeersHandshakeReadinessAndClockSyncHaveDeadlines() throws Exception { check("deadlines"); }
    @Test void disconnectAndSendBackpressureStopTheMatch() throws Exception { check("disconnect"); }
    @Test void realProductionTransportSessionAndRematch() throws Exception { check("loopback"); }
}
