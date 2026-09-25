package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.LrzTurbineSpritesObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Declared positioned entry at the real $F80,$448 turbine; not a cold Act 2 route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzTurbineHeadless {
    @AfterEach
    void reset() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "{0}px {1} {2}")
    @CsvSource({"320,off,sonic", "400,off,sonic", "352,off,sonic", "528,off,sonic",
            "800,off,sonic", "320,s1,sonic", "400,s1,sonic", "352,s1,sonic", "528,s1,sonic",
            "800,s1,sonic", "320,s2,sonic", "400,s2,sonic", "352,s2,sonic", "528,s2,sonic",
            "800,s2,sonic", "320,off,tails", "320,off,knuckles"})
    void turbineCapturesReleasesAndRewindsOnRealTerrain(int width, String donor, String character) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(a -> a.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short) 0xF80, (short) 0x3F8).startPositionIsCentre()
                .withCrossGameDonation(donor.equals("off") ? null : donor).build();
        assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
        assertEquals(character, fixture.sprite().getCode());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
        var renderers = GameServices.level().getObjectRenderManager();
        for (String key : new String[]{Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES,
                Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES_THIN}) {
            assertNotNull(renderers.getRenderer(key), key);
            assertTrue(renderers.getRenderer(key).isReady(), key);
        }
        for (int i = 0; i < 10 && !turbine().isCaptured(0); i++) step(fixture, false);
        assertTrue(turbine().isCaptured(0), "production placement must capture the player");
        for (int i = 0; i < 8; i++) step(fixture, false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        int angle = turbine().rideAngle(0);
        step(fixture, false);
        CompositeSnapshot after = registry.capture();
        assertNotEquals(angle, turbine().rideAngle(0), "the replay must exercise changing state");
        registry.restore(before);
        assertSameState(before, registry.capture(), "restore mid-ride");
        step(fixture, false);
        assertSameState(after, registry.capture(), "replay mid-ride");
        step(fixture, true);
        assertFalse(turbine().isCaptured(0), "logical press releases the production player");
        assertFalse(fixture.sprite().isObjectControlled());
        assertEquals(20, turbine().cooldown(0));
        step(fixture, false);
        CompositeSnapshot released = registry.capture();
        step(fixture, false);
        CompositeSnapshot releasedNext = registry.capture();
        registry.restore(released);
        assertSameState(released, registry.capture(), "restore cooldown");
        step(fixture, false);
        assertSameState(releasedNext, registry.capture(), "replay cooldown");
    }

    private static LrzTurbineSpritesObjectInstance turbine() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(LrzTurbineSpritesObjectInstance.class::isInstance)
                .map(LrzTurbineSpritesObjectInstance.class::cast)
                .filter(t -> t.getCentreX() == 0xF80).findFirst().orElseThrow();
    }

    private static void step(HeadlessTestFixture fixture, boolean jump) {
        fixture.stepFrame(false, false, false, false, jump);
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
