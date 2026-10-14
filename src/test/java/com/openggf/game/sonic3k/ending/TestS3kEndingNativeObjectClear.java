package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.InstaShieldObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Native post-Process_Sprites RAM clear must publish empty SSTs before the next object pass. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingNativeObjectClear {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 36})
    void nativeFadeClearRetiresShieldAndManagerIdentityBeforeAnotherObjectPass(int nativeRoutine) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().refreshViewportDimensions(config);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(13, 1)
                .withFreshLevelStartLifecycle().build();
        fixture.stepFrame(false, false, false, false, false);
        var manager = GameServices.level().getObjectManager();
        var shield = manager.activeObjectsOfType(InstaShieldObjectInstance.class).getFirst();
        assertEquals(100, shield.getSlotIndex(), "native Shield SST");
        assertSame(shield, fixture.sprite().getInstaShieldObject());
        assertFalse(shield.isDestroyed());
        var state = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
        state.screen().foregroundRoutine = nativeRoutine;
        state.screen().fadeTimer = 0; // next native subq expires the final Pal_ToBlack pass
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        var events = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

        events.update();
        assertTrue(shield.isDestroyed(), "native clear never revives the erased owner");
        assertNull(fixture.sprite().getInstaShieldObject());
        assertFalse(fixture.sprite().captureRewindState().playerExtra().instaShieldRegistered());
        assertTrue(manager.activeObjectsOfType(InstaShieldObjectInstance.class).isEmpty());
        var after = registry.capture();
        assertTrue(((ObjectManagerSnapshot) after.get("object-manager")).dynamicObjects().stream()
                .noneMatch(entry -> entry.slotIndex() == 100),
                "SST100 is empty before another Process_Sprites or deferred cleanup");
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(after);
            same(after, registry.capture(), "restore the already-cleared native boundary");
            registry.restore(before);
            same(before, registry.capture(), "restore the live pre-clear owner");
            events.update();
            same(after, registry.capture(), "replay native immediate clear");
            assertNull(GameServices.sprites().getMainPlayable().getInstaShieldObject());
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (var key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
