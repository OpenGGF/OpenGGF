package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.events.Sonic3kSOZEvents;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** sub_55E96 admits the arena at the same centered native viewport in widescreen. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozAct1ArenaAdmission {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 512, 640, 800})
    void centeredNativeViewportCrossesTheArenaThresholdWithoutPassingTheWall(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 0)
                .startPosition((short) 0x43B0, (short) 0x9D4).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        assertEquals(width, fixture.camera().getWidth());
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var events = new Sonic3kSOZEvents();
        fixture.camera().setY((short) 0x960);
        if (width == 320) {
            fixture.camera().setX((short) 0x430F);
            events.update(0, 1);
            assertEquals(0, state.sandCorkBackgroundFlag(), "one pixel before the native gate");
            fixture.camera().setX((short) 0x4310);
            events.update(0, 2);
            assertEquals(0xFFFF, state.sandCorkBackgroundFlag(), "admit at the native gate");
            assertEquals(0x4180, fixture.camera().getMinX());
        } else {
            // Wide views end at the native arena view's right edge ($4450), which
            // always sits left of the camera gate; the focused player's $4310+$A0
            // position admits instead.
            int wideRight = 0x4450 - width;
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x43AF);
            fixture.camera().setX((short) 0x4400);
            events.update(0, 1);
            assertEquals(wideRight, fixture.camera().getX() & 0xFFFF, "view right edge locks at $4450");
            assertEquals(0, state.sandCorkBackgroundFlag(), "one pixel before the player gate");
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x43B0);
            events.update(0, 2);
            assertEquals(0xFFFF, state.sandCorkBackgroundFlag(), "admit at the player gate");
            assertEquals(Math.min(0x4180, wideRight), fixture.camera().getMinX() & 0xFFFF,
                    "arena minimum yields when the view is wider than the native arena span");
        }
        assertTrue((fixture.sprite().getCentreX() & 0xFFFF) < 0x4438,
                "admission must not require walking through the right arena wall");
    }
}
