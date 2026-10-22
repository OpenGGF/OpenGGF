package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusPlayer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real title-card handoffs must seed rewind only from the assembled destination. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameLoopDeferredSetupRewind {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void slotTitleCardEntryRewindsItsFirstNonKeyframeAfterRuntimeAssembly() {
        Fixture f = fixture();
        f.input.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);
        f.input.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);
        f.loop.step();
        f.input.handleKeyEvent(GLFW_KEY_B, GLFW_RELEASE);
        f.input.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_RELEASE);
        awaitTitleCardAndRelease(f, GameMode.BONUS_STAGE);

        var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class,
                f.owner.getActiveBonusStageProvider());
        assertNotNull(provider.activeSlotRuntime());
        assertTrue(provider.activeSlotRuntime().isInitialized());
        assertInstanceOf(S3kSlotBonusPlayer.class, f.owner.getCamera().getFocusedSprite());
        assertEquals(0, f.owner.getLevelManager().getFrameCounter(),
                "the title release retains its native setup-only cadence");
        assertFirstNonKeyframeRoundTrip(f);
        assertTrue(provider.activeSlotRuntime().isInitialized());
        assertInstanceOf(S3kSlotBonusPlayer.class, f.owner.getCamera().getFocusedSprite());
    }

    @Test
    void freshLevelTitleCardEntryDoesNotRestoreItsDeferredPlayerAssembly() {
        Fixture f = fixture();
        f.owner.getLevelManager().requestZoneAndAct(1, 0, true);
        awaitTitleCardAndRelease(f, GameMode.LEVEL);

        RewindController controller = null;
        for (int n = 0; n < 10; n++) {
            f.loop.step();
            controller = f.owner.getRewindController();
            if (controller != null && controller.currentFrame() > 0) break;
        }
        assertNotNull(controller);
        assertEquals(1, controller.currentFrame(), "setup rows do not consume rewind input time");
        assertFalse(f.owner.getLevelManager().hasPendingFreshLevelTransitionBoundary());
        Observation first = observe(f.owner);
        f.loop.step();
        assertEquals(2, controller.currentFrame());
        assertTrue(controller.stepBackward());
        assertEquals(1, controller.currentFrame());
        assertFalse(f.owner.getLevelManager().hasPendingFreshLevelTransitionBoundary(),
                "rewinding gameplay must not resurrect the pre-assembly title boundary");
        assertEquals(first, observe(f.owner));
    }

    private static void assertFirstNonKeyframeRoundTrip(Fixture f) {
        RewindController controller = assertNotNullController(f.owner);
        assertEquals(0, controller.currentFrame());
        f.loop.step();
        assertEquals(1, controller.currentFrame());
        Observation first = observe(f.owner);
        f.loop.step();
        assertEquals(2, controller.currentFrame());
        Observation second = observe(f.owner);
        assertTrue(assertDoesNotThrow(controller::stepBackward),
                "cold expansion of frame one must restore an assembled frame-zero snapshot");
        assertEquals(1, controller.currentFrame());
        assertEquals(first, observe(f.owner));
        controller.step();
        assertEquals(2, controller.currentFrame());
        assertEquals(second, observe(f.owner));
    }

    private static void awaitTitleCardAndRelease(Fixture f, GameMode destination) {
        for (int n = 0; n < 600 && f.loop.getCurrentGameMode() != GameMode.TITLE_CARD; n++) f.loop.step();
        assertEquals(GameMode.TITLE_CARD, f.loop.getCurrentGameMode(), "real entry presents the card");
        for (int n = 0; n < 600 && f.loop.getCurrentGameMode() == GameMode.TITLE_CARD; n++) f.loop.step();
        assertEquals(destination, f.loop.getCurrentGameMode());
    }

    private static RewindController assertNotNullController(GameplayModeContext owner) {
        RewindController controller = owner.getRewindController();
        assertNotNull(controller);
        return controller;
    }

    private static Observation observe(GameplayModeContext owner) {
        AbstractPlayableSprite player = owner.getCamera().getFocusedSprite();
        return new Observation(owner.getLevelManager().getFrameCounter(),
                player.getCentreX(), player.getCentreY(), player.getXSpeed(), player.getYSpeed(), player.getAir());
    }

    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        InputHandler input = new InputHandler();
        GameLoop loop = new GameLoop(input);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        // Drain the source fixture's real initial admission before sending an
        // entry shortcut; a SETUP_ONLY row intentionally consumes host edges.
        for (int n = 0; n < 5; n++) loop.step();
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        return new Fixture(loop, input, fixture.gameplayMode());
    }

    private record Fixture(GameLoop loop, InputHandler input, GameplayModeContext owner) { }
    private record Observation(int frame, int x, int y, short xSpeed, short ySpeed, boolean air) { }
}
