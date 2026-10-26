package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PhysicsProfile;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native reversion's prev_anim write against the real ROM animation scripts. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SuperReversionAnimation {
    private static final int RUN = 1;
    private static final int ROLL = 2;

    @AfterEach
    void clearConfiguration() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private static HeadlessTestFixture activeSuperSonic() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withFreshLevelStartLifecycle().build();
        var controller = assertInstanceOf(Sonic2SuperStateController.class,
                fixture.sprite().getSuperStateController());
        controller.debugActivate();
        for (int frame = 0; frame < 80 && controller.getState() != SuperState.SUPER; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals(SuperState.SUPER, controller.getState(), "ordinary transformation completed");
        return fixture;
    }

    private static void selectScript(AbstractPlayableSprite player, int animationId) {
        player.setForcedAnimationId(animationId);
        player.setAnimationId(animationId);
        player.getAnimationManager().update(0);
    }

    private static void revertAtPausedTimer(AbstractPlayableSprite player) {
        int rings = player.getRingCount();
        assertTrue(rings > 0, "timer pause, rather than depleted rings, owns this reversion");
        player.currentLevelState().pauseTimer();
        player.getSuperStateController().update();
        assertEquals(SuperState.NORMAL, player.getSuperStateController().getState());
        assertEquals(rings, player.getRingCount(), "the timer gate runs before ring drain");
        assertEquals(RUN, player.getAnimationManager().captureRewindState().lastAnimationId(),
                "Sonic_RevertToNormal publishes the exact Run sentinel");
        // Isolate Sonic_Animate's anim/prev_anim test from movement's selection:
        // reversion has already restored the real normal scripts and profile.
        player.setAnimationProfile(null);
    }

    @Test
    void realPlayableTickRevertsBeforeAnimationAfterMovingWithSuperSpeedsAndReplays() throws Exception {
        var fixture = activeSuperSonic();
        var player = fixture.sprite();
        player.setAir(true);
        player.setRolling(true);
        player.setRollingJump(false);
        player.setJumping(true);
        NativePositionOps.writeXPosResetSubpixel(player, 0x300);
        NativePositionOps.writeYPosResetSubpixel(player, 0x100);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0x100);
        player.setGSpeed((short) 0x388);
        selectScript(player, ROLL);
        player.setForcedAnimationId(-1);
        player.setAnimationFrameIndex(5);
        player.setAnimationTick(0);
        player.setRingCount(47);
        player.currentLevelState().pauseTimer();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            if (cycle != 0) {
                registry.restore(before);
            }
            assertEquals(SuperState.SUPER, player.getSuperStateController().getState());
            assertEquals(5, player.getAnimationFrameIndex(), "restore retains the interrupted Roll phase");

            fixture.stepFrame(false, false, false, true, false);

            assertEquals(0x160, player.getXSpeed(),
                    "movement precedes Sonic_Super and still uses doubled $30 Super air acceleration");
            assertEquals(SuperState.NORMAL, player.getSuperStateController().getState());
            assertEquals(PhysicsProfile.SONIC_2_SONIC, player.getPhysicsProfile());
            assertEquals(47, player.getRingCount(), "the paused HUD timer reverts before ring drain");
            assertEquals(1, player.getInvincibleFrames(),
                    "Sonic_Display already ran before reversion writes its one-frame grace");
            assertEquals(ROLL, player.getAnimationId());
            assertEquals(0x3D, player.getMappingFrame(), "the same playable tick restarts normal Roll");
            assertEquals(1, player.getAnimationFrameIndex());

            fixture.stepFrame(false, false, false, true, false);
            assertEquals(0, player.getInvincibleFrames(), "the following Sonic_Display expires the grace");
            assertEquals(0x41, player.getMappingFrame(), "next frame continues the restarted script");
            assertEquals(2, player.getAnimationFrameIndex());
        }
    }

    @Test
    void pausedTimerReversionRestartsSelectedRollAtItsFirstRomFrame() throws Exception {
        var player = activeSuperSonic().sprite();
        player.setGSpeed((short) 0x388);
        selectScript(player, ROLL);
        player.setAnimationFrameIndex(5);
        player.setAnimationTick(0);

        revertAtPausedTimer(player);
        assertEquals(ROLL, player.getAnimationId(), "reversion retains the selected anim byte");
        player.getAnimationManager().update(1);
        assertEquals(0x3D, player.getMappingFrame(), "SonAni_Roll restarts at script entry zero");
        assertEquals(1, player.getAnimationFrameIndex());
        player.getAnimationManager().update(2);
        assertEquals(0x41, player.getMappingFrame(), "the next native Roll entry follows normally");
        assertEquals(2, player.getAnimationFrameIndex());
    }

    @Test
    void pausedTimerReversionKeepsAlreadySelectedRunPhase() throws Exception {
        var player = activeSuperSonic().sprite();
        selectScript(player, RUN);
        player.setAnimationFrameIndex(2);
        player.setAnimationTick(3);
        int mappingBefore = player.getMappingFrame();

        revertAtPausedTimer(player);
        assertEquals(RUN, player.getAnimationId());
        player.getAnimationManager().update(1);
        assertEquals(2, player.getAnimationFrameIndex(), "anim=prev_anim preserves the script phase");
        assertEquals(2, player.getAnimationTick(), "the existing delay decrements instead of resetting");
        assertEquals(mappingBefore, player.getMappingFrame());
    }
}
