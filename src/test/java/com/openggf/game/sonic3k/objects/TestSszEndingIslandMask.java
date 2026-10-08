package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszEndingIslandMask {
    @Test void retainsScreenPositionBeforeBackgroundTransitionAndDeletesAtStage18() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var mask = spawn();
        mask.update(0, null);
        assertEquals(0xF0, mask.screenYForTest());
        assertEquals(7, mask.getPriorityBucket()); assertFalse(mask.isHighPriority());
        state().setForegroundRoutine(0x14); mask.update(1, null); assertFalse(mask.isDestroyed());
        state().setForegroundRoutine(0x18); mask.update(2, null); assertTrue(mask.isDestroyed());
    }

    @Test void fractionalBackgroundRiseReplaysAndStopsOnInteger80() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var mask = spawn();
        state().setBackgroundRoutine(4); state().setBackgroundCameraY(0x82);
        mask.update(0, null);
        assertEquals(0x81, state().backgroundCameraY());
        assertEquals(0x23F, mask.screenYForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(saved);
                mask = GameServices.level().getObjectManager().activeObjectsOfType(SszEndingIslandMask.class).getFirst();
            }
            mask.update(1, null); assertEquals(0x81, state().backgroundCameraY());
            mask.update(2, null); assertEquals(0x80, state().backgroundCameraY());
            for (int i = 0; i < 16; i++) mask.update(i, null);
            assertEquals(0x80, state().backgroundCameraY());
            assertEquals(0x240, mask.screenYForTest());
        }
    }

    @Test void runtimeFillRefreshesMappedSheetAndRestoresOriginalTiles() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        var level = GameServices.level().getCurrentLevel();
        int originalCount = level.getPatternCount();
        byte[] original = new byte[originalCount * 64];
        for (int i = 0; i < originalCount; i++) level.getPattern(i).copyInto(original, i * 64);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        SszEndingIslandMask.fillTiles(TestEnvironment.objectServices());
        var provider = (com.openggf.game.sonic3k.Sonic3kObjectArtProvider)
                GameServices.level().getGameModule().getObjectArtProvider();
        var sheet = provider.getSheet(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SSZ_ENDING_ISLAND_MASK);
        assertNotNull(sheet); assertEquals(16, sheet.getPatterns().length);
        assertEquals(4, sheet.getFrame(0).pieces().size());
        for (int i = 0; i < 16; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(6, level.getPattern(0x7F0 + i).getPixel(x, y));
            assertEquals(6, sheet.getPatterns()[i].getPixel(x, y));
        }
        registry.restore(saved);
        byte[] restored = new byte[original.length];
        for (int i = 0; i < originalCount; i++) level.getPattern(i).copyInto(restored, i * 64);
        assertArrayEquals(original, restored);
        SszEndingIslandMask.fillTiles(TestEnvironment.objectServices());
        assertEquals(6, level.getPattern(0x7FF).getPixel(7, 7));
    }

    private static SszEndingIslandMask spawn() {
        var mask = new SszEndingIslandMask(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        mask.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(mask);
        return mask;
    }
    private static SszZoneRuntimeState state() { return (SszZoneRuntimeState) GameServices.zoneRuntimeState(); }
}
