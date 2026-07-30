package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestPlaybackAdvanceOnlyInputBridge {

    private final PlaybackDebugManager playback = PlaybackDebugManager.getInstance();

    @AfterEach
    void tearDown() {
        playback.endSession();
        SessionManager.clear();
    }

    @Test
    void advanceOnlyActionEdgeRemainsPendingUntilOneGameplayDispatch() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(0, 0)
                    .build();
            GameLoop loop = new GameLoop(new InputHandler());
            loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
            playback.startSession(heldActionMovie(), 0);
            playback.setFrameObserver(new FirstRowAdvanceOnlyObserver());

            invokeSyncPlaybackInputBridge(loop);
            assertTrue(playback.isCurrentForcedJumpPress(),
                    "the ADVANCE_ONLY row introduces the action press edge");
            invokeUpdateLevelMode(loop);

            assertTrue(playback.isCurrentForcedJumpPress(),
                    "skipping gameplay must leave the action edge pending");
            assertFalse(fixture.sprite().getAir(),
                    "ADVANCE_ONLY must not execute player gameplay");

            invokeSyncPlaybackInputBridge(loop);
            assertTrue(playback.isCurrentForcedJumpPress(),
                    "publishing the following held row must not erase the pending edge");
            assertTrue(fixture.sprite().isForcedJumpPress(),
                    "the first gameplay row must observe the pending action edge");
            invokeUpdateLevelMode(loop);

            assertTrue(playback.isCurrentForcedJumpPress(),
                    "the initial Process_Sprites setup pass must not consume the pending gameplay edge");
            assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass(),
                    "the setup-only retry should consume the fresh-level setup authority");
            invokeUpdateLevelMode(loop);

            assertFalse(playback.isCurrentForcedJumpPress(),
                    "the gameplay dispatch must consume the pending edge exactly once");
            invokeSyncPlaybackInputBridge(loop);
            assertFalse(playback.isCurrentForcedJumpPress(),
                    "a later held row must not repeat the consumed action edge");
            assertFalse(fixture.sprite().isForcedJumpPress(),
                    "the consumed edge must not be republished to gameplay");
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkipIntros != null ? oldSkipIntros : false);
        }
    }

    private static Bk2Movie heldActionMovie() {
        int heldMask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
        return new Bk2Movie(
                Path.of("synthetic-advance-only-action.bk2"),
                "logkey",
                Map.of(),
                List.of(
                        new Bk2FrameInput(0, heldMask, 1, false, "press"),
                        new Bk2FrameInput(1, heldMask, 1, false, "hold"),
                        new Bk2FrameInput(2, heldMask, 1, false, "hold")),
                1);
    }

    private static void invokeSyncPlaybackInputBridge(GameLoop loop) throws Exception {
        Method method = GameLoop.class.getDeclaredMethod("syncPlaybackInputBridge");
        method.setAccessible(true);
        method.invoke(loop);
    }

    private static void invokeUpdateLevelMode(GameLoop loop) throws Exception {
        GameLoopTestStep.invoke(loop, "updateLevelMode", new Class<?>[] { boolean.class }, false);
    }

    private static final class FirstRowAdvanceOnlyObserver
            implements PlaybackDebugManager.PlaybackFrameObserver {
        @Override
        public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
            return frame.frameIndex() == 0;
        }

        @Override
        public int vblankAdvanceCountOnSkippedTick(Bk2FrameInput frame) {
            return 0;
        }

        @Override
        public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
        }
    }
}
