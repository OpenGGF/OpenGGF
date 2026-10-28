package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_1)
class TestRetailDynamicWaterMotion {
    @Test void actualMeanStepHoldsForRevisionOneDrowningButStillPublishesWaterPresentation() throws Exception {
        Rom later = TestEnvironment.currentRom();
        assertEquals(RetailRomRevision.S1_REV01, RetailRomRevision.detect(later));
        check(later, true, false, 100);
        check(later, false, true, 100);
        check(later, false, false, 101);
        try (var unknown = Rom.fromReader(RomByteReader.fromBytes(new byte[512]), "unknown water image")) {
            check(unknown, true, false, 101);
            check(unknown, false, true, 101);
        }
    }

    @Test void revisionZeroContinuesWhileDrowningAndBothRetailRevisionsHoldAfterDeath() throws Exception {
        String path = System.getProperty("sonic1.rev00.rom.path");
        assumeTrue(path != null && !path.isBlank(), "Requires sonic1.rev00.rom.path");
        try (var original = new Rom()) {
            assertTrue(original.open(path));
            assertEquals(RetailRomRevision.S1_REV00, RetailRomRevision.detect(original));
            check(original, true, false, 101);
            check(original, false, true, 100);
        }
    }

    private static void check(Rom source, boolean drowning, boolean dead, int expected) throws Exception {
        var provider = new Sonic1ZoneFeatureProvider();
        provider.initZoneFeatures(source, 0, 0, 0); // Bind identity without loading water art.
        var manager = mock(LevelManager.class);
        manager.level = mock(Level.class);
        manager.camera = new Camera();
        manager.camera.setY((short) 200);
        manager.zoneFeatureProvider = provider;
        manager.spriteManager = mock(SpriteManager.class);
        var player = mock(AbstractPlayableSprite.class);
        when(player.isDrowningPreDeath()).thenReturn(drowning);
        when(player.getDead()).thenReturn(dead);
        when(manager.resolveMainCharacterCode()).thenReturn("sonic");
        when(manager.spriteManager.getSprite("sonic")).thenReturn(player);
        when(manager.getFeatureZoneId()).thenReturn(1);
        when(manager.getFeatureActId()).thenReturn(0);
        manager.waterSystem = new WaterSystem();
        manager.waterSystem.loadForLevelFromProvider(new WaterDataProvider() {
            public boolean hasWater(int zone, int act, PlayerCharacter character) { return true; }
            public int getStartingWaterLevel(int zone, int act) { return 100; }
            public Palette[] getUnderwaterPalette(Rom rom, int zone, int act, PlayerCharacter character) { return null; }
            public DynamicWaterHandler getDynamicHandler(int zone, int act, PlayerCharacter character) { return null; }
        }, null, 1, 0, PlayerCharacter.SONIC_ALONE);
        manager.waterSystem.setWaterLevelTarget(1, 0, 105);
        var water = new LevelWaterCoordinator(manager);
        water.advanceDynamicWaterLevel();
        assertEquals(expected, manager.waterSystem.getWaterLevelY(1, 0));
        assertEquals(105, manager.waterSystem.getWaterLevelTarget(1, 0));
        assertTrue(manager.waterSystem.captureFullScreenFlag(1, 0, 200));
        var saved = manager.waterSystem.capture();
        water.advanceDynamicWaterLevel();
        var forward = manager.waterSystem.capture();
        manager.waterSystem.restore(saved);
        water.advanceDynamicWaterLevel();
        assertEquals(forward, manager.waterSystem.capture());
    }
}
