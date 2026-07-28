package com.openggf.tests.trace.s3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.timing.HardwareCompletionEdge;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHardwareTimingReplay {
    private static final Path AIZ_STANDARD = Path.of(
            "src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    private static final Path AIZ_COMPLETE = Path.of(
            "src/test/resources/traces/s3k/aiz_completerun");
    private static final Path HCZ_COMPLETE = Path.of(
            "src/test/resources/traces/s3k/hcz_completerun");

    @Test
    void aizStandardFirstEdgeMatchesNativeIntroSubmission() throws Exception {
        assertFirstEdgeMatchesNativeSubmission(
                AIZ_STANDARD,
                Sonic3kConstants.ART_KOSM_AIZ_INTRO_PLANE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_INTRO_PLANE);
    }

    @Test
    void standaloneAizCompleteRunSeedsAndMatchesIntroSubmission()
            throws Exception {
        assertFirstEdgeMatchesNativeSubmission(
                AIZ_COMPLETE,
                Sonic3kConstants.ART_KOSM_AIZ_INTRO_PLANE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_INTRO_PLANE);
    }

    @Test
    void standaloneHczCompleteRunSeedsAndMatchesEnemySubmission()
            throws Exception {
        assertFirstEdgeMatchesNativeSubmission(
                HCZ_COMPLETE,
                Sonic3kConstants.ART_KOSM_HCZ_BLASTOID_ADDR,
                Sonic3kConstants.ARTTILE_HCZ_BLASTOID_JAWZ);
    }

    @Test
    void standaloneAizCompleteRunConsumesFirstEdgeThroughProductionFrameDriver()
            throws Exception {
        TraceData trace = TraceData.load(AIZ_COMPLETE);
        HardwareCompletionEdge first =
                trace.hardwareTimingSchedule().edges().getFirst();
        Path bk2 = AIZ_COMPLETE.getParent()
                .resolve("_movies")
                .resolve(trace.metadata().sourceBk2());
        TraceReplaySessionBootstrap.prepareConfiguration(
                trace, trace.metadata());
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2)
                .withRecordingStartFrame(
                        TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                .withHardwareReadinessAdmissionPolicy(
                        com.openggf.game.timing.HardwareReadinessAdmissionPolicy.RECORDED)
                .withZoneAndAct(0, 0)
                .startPosition(trace.metadata().startX(), trace.metadata().startY())
                .startPositionIsCentre()
                .build();
        try {
            var boot = TraceReplaySessionBootstrap.applyBootstrap(
                    trace, fixture, -1);
            int index = boot.replayStart().startingTraceIndex();
            TraceFrame previous = index > 0 ? trace.getFrame(index - 1) : null;
            while (index <= first.rawFrame()) {
                TraceFrame current = trace.getFrame(index);
                fixture.beginTraceRow(index, current.frame());
                TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, current);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
                previous = current;
                index++;
            }
            IllegalStateException remaining = org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    fixture::verifyHardwareTimingSegmentEdges);
            assertTrue(remaining.getMessage().contains("raw_frame=73"),
                    remaining::getMessage);
        } finally {
            fixture.abortHardwareTimingReplayRun();
            TestEnvironment.resetAll();
        }
    }

    private static void assertFirstEdgeMatchesNativeSubmission(
            Path traceDirectory,
            int romSource,
            int destinationPattern) throws Exception {
        TraceData trace = TraceData.load(traceDirectory);
        HardwareCompletionEdge edge =
                trace.hardwareTimingSchedule().edges().getFirst();
        HardwareTimingService timing = new HardwareTimingService();
        HardwareTimingReplayPort replay = new HardwareTimingReplayPort(
                timing.beginRecordedAdmission());
        replay.install(trace.hardwareTimingSchedule());
        S3kKosDecompressionQueue direct =
                new S3kKosDecompressionQueue(timing);
        S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);

        HardwareWorkHandle handle = queue.queue(
                TestEnvironment.currentRom(), romSource, destinationPattern);

        assertEquals(edge.kind(), handle.kind());
        assertEquals(edge.ordinal(), handle.ordinal());
        assertEquals(edge.submissionFingerprint(),
                handle.submissionFingerprint(),
                "recorder and engine must independently derive the same ROM-work identity");

        replay.beginRawFrame(edge.rawFrame());
        for (int servicePass = 0;
                servicePass < 4096 && !isPrepared(timing);
                servicePass++) {
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
            queue.afterTimingService(HardwareServiceBoundary.POST_OBJECTS);
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            direct.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
        }
        timing.service(edge.boundary());
        if (edge.boundary() == HardwareServiceBoundary.POST_OBJECTS) {
            queue.afterTimingService(edge.boundary());
        } else {
            direct.afterTimingService(edge.boundary());
        }
        assertTrue(isPrepared(timing),
                "the production decoder must prepare the archive independently");
        assertFalse(queue.isReady(handle),
                "recorded admission must hold prepared work until its edge");

        replay.apply(edge.boundary());

        assertTrue(queue.isReady(handle));
    }

    private static boolean isPrepared(HardwareTimingService timing) {
        return timing.capture().jobs().getFirst().preparedPayload() != null;
    }
}
