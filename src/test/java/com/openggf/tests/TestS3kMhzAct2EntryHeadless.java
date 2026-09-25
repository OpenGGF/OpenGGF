package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary MHZ2 entry must retain the placed Knuckles actor until its camera gate opens. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzAct2EntryHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void coldEntryPressesLiftsAndReleasesSonicWithReplay(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, aspect.pixelWidth());
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 1)
                .withFreshLevelStartLifecycle().build();
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        boolean press = false, lift = false, release = false, retired = false;
        var registry = fixture.gameplayMode().getRewindRegistry();
        for (int frame = 0; frame < 1400; frame++) {
            right(fixture);
            var player = fixture.sprite();
            assertFalse(player.getDead(), "entry death at " + frame);
            boolean replay = false;
            if (!press && player.isObjectControlled()) { press = true; replay = true; }
            if (!lift && press && player.getCentreY() < 1700 && player.isObjectControlled()) {
                assertTrue(player.isHighPriority(), "loc_6339C puts the lifted player in front of terrain");
                lift = true; replay = true;
            }
            if (lift && player.isObjectControlled()) {
                assertEquals(0, player.getYSpeed(), "carrier motion must not overwrite player y_vel");
                assertEquals(com.openggf.camera.NativeViewportFraming.visibleLeft(0x3D0, aspect.pixelWidth()),
                        fixture.camera().getMinX(), "level event must preserve the cutscene-owned minimum");
                assertTrue(player.getCentreY() >= fixture.camera().getY()
                                && player.getCentreY() < fixture.camera().getY() + 224,
                        "camera must follow the carrier above the lower-route Y limit at frame " + frame);
            }
            boolean controllerAlive = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .anyMatch(o -> o instanceof com.openggf.game.sonic3k.objects.CutsceneKnucklesMhz2Instance
                            && !o.isDestroyed());
            if (lift && !controllerAlive && !retired && player.isObjectControlled()) {
                retired = true;
                replay = true; // restore the independent carrier after Knuckles has gone
            }
            if (replay) {
                var saved = registry.capture(); String before = observed(fixture);
                for (int n = 0; n < 30; n++) right(fixture);
                String expected = observed(fixture);
                registry.restore(saved); assertEquals(before, observed(fixture), "restore at " + frame);
                for (int n = 0; n < 30; n++) right(fixture);
                assertEquals(expected, observed(fixture), "forward replay at " + frame);
                registry.restore(saved);
                // Input is held Right throughout, so the runner's input history is unchanged.
            }
            if (lift && !player.isObjectControlled()) { release = true; break; }
        }
        assertTrue(press, "placed controller must survive pre-activation loading");
        assertTrue(lift, "leaf blower must carry Sonic above the blocked lower passage");
        assertTrue(retired, "offscreen Knuckles must retire while the independent lift continues");
        assertTrue(release, "the lift must return player control");
        assertFalse(fixture.sprite().isHighPriority(), "loc_633D6 clears lift priority on release");
        assertEquals(7, GameServices.level().getCheckpointState().getLastCheckpointIndex());
    }

    private static void right(HeadlessTestFixture fixture) {
        fixture.stepFrame(false, false, false, true, false);
    }

    private static String observed(HeadlessTestFixture fixture) {
        var p = fixture.sprite(); var c = fixture.camera();
        var objects = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed())
                .map(o -> o.getClass().getName() + (o.getSpawn() == null ? ":fixed" : ":" + o.getX() + ":" + o.getY()))
                .sorted().toList();
        return p.getCentreX() + ":" + p.getCentreY() + ":" + p.getXSpeed() + ":" + p.getYSpeed()
                + ":" + p.isObjectControlled() + ":" + p.isHighPriority() + ":" + p.getRenderVFlip()
                + ":" + p.getAnimationId() + ":" + p.getMappingFrame()
                + ":" + c.getX() + ":" + c.getY() + ":" + c.getMinX() + ":" + objects;
    }
}
