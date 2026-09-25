package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestLrzDeathEggBackground {
    @Test void knucklesInitializerRetiresTheNativeSlot() {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        com.openggf.game.session.SessionManager.clear();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        for (int i = 0; i < 3; i++) fixture.stepFrame(false, false, false, false, false);
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(LrzDeathEggBackgroundInstance.class).isEmpty());
    }

    @Test void nativeGateAndUnwrappedWideEntryMatchObservedCoordinates() {
        assertFalse(LrzDeathEggBackgroundInstance.active(3671, 320));
        assertTrue(LrzDeathEggBackgroundInstance.active(3672, 320));
        assertEquals(416, LrzDeathEggBackgroundInstance.screenX(3672));
        assertEquals(415, LrzDeathEggBackgroundInstance.screenX(3673));
        assertEquals(5, LrzDeathEggBackgroundInstance.screenX(4083));
        for (int width : new int[] {320, 352, 400, 528, 800}) {
            int gate = 3672 - (width - 320);
            assertFalse(LrzDeathEggBackgroundInstance.active(gate - 1, width));
            assertTrue(LrzDeathEggBackgroundInstance.active(gate, width));
            assertEquals(width + 96, LrzDeathEggBackgroundInstance.screenX(gate));
        }
    }

    @org.junit.jupiter.api.AfterEach void resetConfiguration() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.openggf.configuration.WidescreenAspect.class)
    void directLoadOwnsOneSpriteAndRecreatesPendingArtWithFullRegistryReplay(
            com.openggf.configuration.WidescreenAspect aspect) throws Exception {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short) 13218, (short) 850).startPositionIsCentre().build();
        fixture.sprite().setRingCount(99);
        // Warm the production managers before capturing; the positioned player sets the
        // real camera/scroll pass. No object state or art readiness is injected.
        for (int i = 0; i < 2; i++) fixture.stepFrame(false, false, false, false, false);
        var manager = GameServices.level().getObjectManager();
        var objects = manager.activeObjectsOfType(LrzDeathEggBackgroundInstance.class);
        assertEquals(1, objects.size());
        var object = objects.getFirst();
        assertEquals(7, object.getPriorityBucket());
        assertFalse(object.isHighPriority());
        var runtime = (LrzZoneRuntimeState) GameServices.zoneRuntimeState();
        assertTrue(LrzDeathEggBackgroundInstance.active(runtime.deathEggScrollWord(), aspect.pixelWidth()));
        var queued = LrzDeathEggBackgroundInstance.class.getDeclaredField("artQueued");
        queued.setAccessible(true);
        assertTrue(queued.getBoolean(object));
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 45; i++) fixture.stepFrame(false, false, false, false, false);
        var after = registry.capture();
        manager.activeObjectsOfType(LrzDeathEggBackgroundInstance.class).forEach(o -> o.setDestroyed(true));
        fixture.stepFrame(false, false, false, false, false);
        registry.restore(before);
        assertEquals(1, manager.activeObjectsOfType(LrzDeathEggBackgroundInstance.class).size());
        var restored = registry.capture();
        for (String key : before.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, before.get(key), restored.get(key)).isEmpty(), key);
        }
        for (int i = 0; i < 45; i++) fixture.stepFrame(false, false, false, false, false);
        var replay = registry.capture();
        for (String key : after.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key)).isEmpty(), key);
        }
    }
}
