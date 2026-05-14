package com.openggf.tests.trace.s2;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_2)
class TestS2ReplayBootstrapTailsFrame0 {

    @ParameterizedTest(name = "{0} frame-0 Tails starts active")
    @CsvSource({
            "ehz1_fullrun,0",
            "cpz,1",
            "arz,2"
    })
    void normalRouteFrameZeroStartsTailsNearSonicAndGrounded(String route, int zone) throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve(route);
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path;
        try (var files = Files.list(traceDir)) {
            bk2Path = files
                    .filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        TraceFrame expected = trace.getFrame(0);
        assertNotNull(expected.sidekick(), "Trace frame 0 must include recorded Tails state");

        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_2, zone, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition(meta.startX(), meta.startY())
                    .startPositionIsCentre()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
            assertEquals(0, boot.replayStart().startingTraceIndex(),
                    "S2 normal routes should compare from trace frame 0");

            AbstractPlayableSprite tailsBeforeFirstFrame = firstRegisteredSidekick();
            assertNotNull(tailsBeforeFirstFrame, "Tails should be registered after replay bootstrap");
            assertNotEquals(0x4000, tailsBeforeFirstFrame.getCentreX() & 0xFFFF,
                    "Replay bootstrap must not leave Tails at the S2 despawn marker");

            TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, null, expected);
            int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                    ? fixture.skipFrameFromRecording()
                    : fixture.stepFrameFromRecording();
            assertEquals(expected.input(), bk2Input,
                    "BK2 frame 0 input must stay aligned with the trace");

            AbstractPlayableSprite tails = firstRegisteredSidekick();
            assertNotNull(tails, "Tails should still be registered on trace frame 0");
            assertEquals(expected.sidekick().x(), tails.getCentreX(),
                    "Frame-0 Tails X should be produced natively near Sonic");
            assertEquals(expected.sidekick().y(), tails.getCentreY(),
                    "Frame-0 Tails Y should be produced natively near Sonic");
            assertEquals(expected.sidekick().air(), tails.getAir(),
                    "Frame-0 Tails grounded/air state should match ROM without trace hydration");
        } finally {
            sharedLevel.dispose();
        }
    }

    private static AbstractPlayableSprite firstRegisteredSidekick() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getRegisteredSidekicks().isEmpty()) {
            return null;
        }
        return spriteManager.getRegisteredSidekicks().getFirst();
    }
}
