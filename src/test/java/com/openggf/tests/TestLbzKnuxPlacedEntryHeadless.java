package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** Short actual ROM $CD admission, native gate and whole-world replay, independent of the fight. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLbzKnuxPlacedEntryHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void placedEntryComesFromRomAndReplaysNativeGate(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, aspect.pixelWidth());
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        // Declared approach into word_72FEA; no boss, camera or gameplay seeding after boot.
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(6, 1)
                .withFreshLevelStartLifecycle()
                .startPosition((short) 0x43A0, (short) 0x03E0).startPositionIsCentre().build();
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        var placement = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(s -> s.objectId() == Sonic3kObjectIds.LBZ_FINAL_BOSS_KNUX)
                .findFirst().orElseThrow();
        assertEquals(0x4410, placement.x());
        assertEquals(0x0440, placement.y());
        LbzFinalBoss1Instance owner = null;
        for (int frame = 0; frame < 32; frame++) {
            fixture.stepIdleFrames(1);
            var admitted = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getSpawn() != null && o.getSpawn().objectId() == Sonic3kObjectIds.LBZ_FINAL_BOSS_KNUX)
                    .findFirst().orElse(null);
            if (admitted == null) continue;
            owner = assertInstanceOf(LbzFinalBoss1Instance.class, admitted,
                    "the actual placed $CD must own the native prelude rather than a placeholder");
            break;
        }
        assertNotNull(owner, "the native approach must admit the ROM-placed $CD");
        assertEquals(0, owner.getCollisionFlags(), "CD is non-collidable before same-slot CA init");
        assertTrue(GameServices.gameState().getCurrentBossId() != 0);
        int slot = owner.getSlotIndex();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(45);
        var expected = registry.capture();
        registry.restore(saved); same(saved, registry.capture(), "gate restore");
        fixture.stepIdleFrames(45); same(expected, registry.capture(), "gate replay");
        owner = GameServices.level().getObjectManager().activeObjectsOfType(LbzFinalBoss1Instance.class)
                .stream().findFirst().orElseThrow();
        for (int frame = 0; frame < 160 && owner.getCollisionFlags() == 0; frame++) fixture.stepIdleFrames(1);
        assertEquals(slot, owner.getSlotIndex(), "loc_7304C changes code in the same native slot");
        assertEquals(9, owner.getCollisionProperty());
        assertEquals(0x0F, owner.getCollisionFlags());
        assertEquals(0x4300, fixture.camera().getMinX() & 0xFFFF);
        assertEquals(0x4300, fixture.camera().getMaxX() & 0xFFFF);
        assertEquals(0x328, fixture.camera().getMinY() & 0xFFFF);
        assertEquals(0x328, fixture.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(0x4300, com.openggf.camera.NativeViewportFraming.nativeLeft(
                fixture.camera().getX(), fixture.camera().getWidth()) & 0xFFFF,
                "the same native320 arena is centred in the actual supported viewport");
        int initialY = owner.getCentreY();
        fixture.stepIdleFrames(3);
        assertTrue(owner.getCentreY() < initialY, "native CA Obj_Wait must start Knuckles' upward shuttle");
        var combat = registry.capture();
        fixture.stepIdleFrames(30); var combatForward = registry.capture();
        registry.restore(combat); same(combat, registry.capture(), "CA graph restore");
        fixture.stepIdleFrames(30); same(combatForward, registry.capture(), "CA graph replay");
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
