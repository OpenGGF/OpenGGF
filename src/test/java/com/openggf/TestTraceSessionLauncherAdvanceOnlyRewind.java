package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSeekAwareEngineStepper;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.replay.TraceReplayFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;

@RequiresRom(SonicGame.SONIC_3K)
class TestTraceSessionLauncherAdvanceOnlyRewind {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void visualRewindAdvanceOnlyLatchesInputWithoutExecutingGameplay() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(0, 0)
                    .build();
            TraceReplayFixture timingFixture = mock(TraceReplayFixture.class);
            GameLoop loop = new GameLoop(new InputHandler());
            Bk2Movie movie = heldActionMovie();
            RewindSeekAwareEngineStepper stepper =
                    visualStepper(loop, movie, advanceOnlyTrace(), timingFixture);
            int spriteFrame = GameServices.sprites().getFrameCounter();
            int levelFrame = GameServices.level().getFrameCounter();
            int vblank = GameServices.level().getObjectManager().getVblaCounter();

            stepper.step(movie.getFrame(0));

            assertEquals(spriteFrame, GameServices.sprites().getFrameCounter(),
                    "ADVANCE_ONLY must not dispatch playable gameplay");
            assertEquals(levelFrame, GameServices.level().getFrameCounter(),
                    "ADVANCE_ONLY must not run level/object updates");
            assertEquals(vblank, GameServices.level().getObjectManager().getVblaCounter(),
                    "ADVANCE_ONLY must not advance the VBlank/object clock");
            assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                    fixture.sprite().getForcedInputMask(),
                    "the input-only row must still latch its held controller state");
            assertTrue(fixture.sprite().isForcedJumpPress(),
                    "the input-only action edge must remain pending");
            assertFalse(fixture.sprite().getAir(),
                    "the input-only row must leave player gameplay untouched");

            stepper.step(movie.getFrame(1));

            assertEquals(spriteFrame, GameServices.sprites().getFrameCounter(),
                    "the structural level boundary remains a no-gameplay row");
            assertTrue(fixture.sprite().isForcedJumpPress(),
                    "the action edge must remain pending across later no-gameplay rows");

            int setupRetries = 0;
            do {
                stepper.step(movie.getFrame(2));
                setupRetries++;
            } while (GameServices.sprites().getFrameCounter() == spriteFrame
                    && setupRetries < 120);

            assertEquals(spriteFrame + 1, GameServices.sprites().getFrameCounter(),
                    "the following gameplay row must dispatch exactly one playable tick");
            assertTrue(fixture.sprite().getAir(),
                    "the following held gameplay row must consume the pending jump edge");
            assertTrue(fixture.sprite().getYSpeed() < 0);
            assertFalse(fixture.sprite().isForcedJumpPress(),
                    "the gameplay dispatch must consume the action edge once");

            clearInvocations(timingFixture);
            stepper.restoreToFrame(0, movie.getFrame(0));
            stepper.step(movie.getFrame(0));
            verify(timingFixture).beginTraceRow(1, 1);
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkipIntros != null ? oldSkipIntros : false);
        }
    }

    private static RewindSeekAwareEngineStepper visualStepper(
            GameLoop loop,
            Bk2Movie movie,
            TraceData trace,
            TraceReplayFixture fixture) throws Exception {
        Class<?> type = Class.forName(
                "com.openggf.TraceSessionLauncher$VisualTraceRewindStepper");
        Constructor<?> constructor = type.getDeclaredConstructor(
                GameLoop.class, Bk2Movie.class, TraceData.class,
                com.openggf.trace.replay.TraceReplayFixture.class,
                int.class, int.class);
        constructor.setAccessible(true);
        return (RewindSeekAwareEngineStepper) constructor.newInstance(
                loop, movie, trace, fixture, 0, 1);
    }

    private static Bk2Movie heldActionMovie() {
        int heldMask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
        return new Bk2Movie(
                Path.of("synthetic-rewind-advance-only-action.bk2"),
                "logkey",
                Map.of(),
                List.of(
                        new Bk2FrameInput(0, heldMask, 1, false, "press"),
                        new Bk2FrameInput(1, heldMask, 1, false, "hold"),
                        new Bk2FrameInput(2, heldMask, 1, false, "hold")),
                1);
    }

    private static TraceData advanceOnlyTrace() {
        TraceFrame beforeLatch = TraceFrame.of(0, 0,
                (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
        TraceFrame inputLatch = TraceFrame.of(1, AbstractPlayableSprite.INPUT_JUMP,
                (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
        TraceFrame gameplay = TraceFrame.of(2, AbstractPlayableSprite.INPUT_JUMP,
                (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
        TraceFrame firstGameplayDispatch = TraceFrame.executionTestFrame(3, 1, 1, 0);
        return TraceFixtures.trace(
                TraceFixtures.metadata("s3k", 0, 0),
                List.of(beforeLatch, inputLatch, gameplay, firstGameplayDispatch),
                Map.of(
                        0, List.of(new TraceEvent.ZoneActState(0, 0, 0, 0, 4)),
                        2, List.of(
                                new TraceEvent.ZoneActState(2, 0, 0, 0, 12),
                                new TraceEvent.Checkpoint(
                                        2, "gameplay_start", 0, 0, 0, 12, null))));
    }
}
