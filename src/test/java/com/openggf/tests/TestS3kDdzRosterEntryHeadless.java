package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual loc_81554 Player_2 clear and its same-world rewind boundary. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzRosterEntryHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
    }
    @ParameterizedTest @ValueSource(ints = {320, 800})
    void nativeFlightEntryRetiresRegisteredFollowersAndReplays(int width) {
        var config = SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect(); config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle().build();
        assertEquals(width, f.camera().getWidth()); var sprites = GameServices.sprites();
        assertEquals(1, sprites.getRegisteredSidekicks().size(), "configured P2 exists until initial dispatch");
        var p2 = sprites.getRegisteredSidekicks().getFirst(); var registry = f.gameplayMode().getRewindRegistry();
        var before = registry.capture(); f.stepIdleFrames(1);
        assertTrue(sprites.getRegisteredSidekicks().isEmpty(), "loc_815DC clears actual P2");
        assertSame(sprites.getMainPlayable(), GameServices.camera().getFocusedSprite());
        assertTrue(sprites.getMainPlayable().isObjectControlled()); var entered = registry.capture();
        f.stepIdleFrames(30); var advanced = registry.capture();
        registry.restore(before); same(before, registry.capture(), "pre-entry restore");
        assertEquals(1, sprites.getRegisteredSidekicks().size());
        assertSame(p2, sprites.getRegisteredSidekicks().getFirst(), "native roster identity reattached");
        f.stepIdleFrames(1); same(entered, registry.capture(), "entry retirement replay");
        f.stepIdleFrames(30); same(advanced, registry.capture(), "flight replay");
        assertTrue(sprites.getRegisteredSidekicks().isEmpty());
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
