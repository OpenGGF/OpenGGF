package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTilemapManager;
import com.openggf.level.Palette;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAct1RomRuntimeLifecycle {
    @Test
    void outdoorStartupPaletteAndRetainedRingSurviveRealFramePreparation() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        GraphicsManager.getInstance().initHeadless();
        Sonic sonic = new Sonic(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE), (short) 0x100, (short) 0x600);
        GameServices.sprites().addSprite(sonic);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);
        LevelManager levels = GameServices.level();
        levels.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0);
        GroundSensor.setLevelManager(levels);
        sonic.setCentreX((short) 0x100);
        camera.updatePosition(true);

        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kFBZEvents events = manager.getFbzEvents();
        events.initializeAct1Runtime();
        FbzZoneRuntimeState runtime = (FbzZoneRuntimeState) GameServices.zoneRuntimeRegistry().current();
        java.lang.reflect.Field tilemapsField = LevelManager.class.getDeclaredField("tilemapManager");
        tilemapsField.setAccessible(true);
        LevelTilemapManager tilemaps = (LevelTilemapManager) tilemapsField.get(levels);
        assertTrue(tilemaps.getBackgroundTilemapHeightTiles() > 32,
                "real FBZ1 world cache must be taller than the retained VDP ring");

        byte[] expectedPatch = GameServices.rom().getRom().readBytes(0x52DD0, 16);
        for (int i = 0; i < 8; i++) {
            Palette.Color expected = new Palette.Color();
            expected.fromSegaFormat(expectedPatch, i * 2);
            Palette.Color actual = levels.getCurrentLevel().getPalette(3).getColor(2 + i);
            assertEquals(expected.r, actual.r);
            assertEquals(expected.g, actual.g);
            assertEquals(expected.b, actual.b);
        }

        byte[] capturedRing = levels.captureBackgroundVdpPlane();
        assertEquals(64 * 32 * 4, capturedRing.length);
        byte[] state = runtime.captureBytes();
        levels.copyBackgroundTileRowFromWorldToVdpPlane(0, 0x330, 0xE780, 0x21);
        assertFalse(java.util.Arrays.equals(capturedRing, levels.captureBackgroundVdpPlane()));

        runtime.restoreBytes(state);
        events.reconcileAct1State();
        assertArrayEquals(capturedRing, levels.captureBackgroundVdpPlane());
        camera.setX((short) (camera.getX() + 0x20));
        assertArrayEquals(capturedRing, levels.captureBackgroundVdpPlane(),
                "next ensure preparation must retain the installed 64x32 VDP ring");
    }
}
