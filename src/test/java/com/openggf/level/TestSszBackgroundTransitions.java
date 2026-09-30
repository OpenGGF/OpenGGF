package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlSsz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszBackgroundTransitions {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); }

    @ParameterizedTest
    @CsvSource({"320,false", "800,false", "320,true", "800,true"})
    void windowAndScrollSwitchTogetherWithoutAnEmptyBackgroundFrame(int width, boolean replay) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                com.openggf.configuration.WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();
        com.openggf.tests.TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var level = GameServices.level();
        var parallax = GameServices.parallax();
        var camera = GameServices.camera();
        assertEquals(width, camera.getWidth());
        var state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        state.setForegroundRoutine(0); state.setBackgroundRoutine(0);
        state.setCloudOscillator(0); state.markBackgroundInitApplied();
        camera.setX((short) 0x400);
        int[] positions = {0x7FF, 0x800, 0x800, 0x7FF, 0x7FF, 0xF00, 0xEFF, 0xEFF, 0xF00, 0xF00};
        for (int i = 0; i < positions.length; i++) {
            camera.setY((short) positions[i]);
            parallax.update(10, 0, camera, 1000 + i);
            level.ensureBackgroundTilemapData();
            var tilemap = level.getTilemapManager();
            assertTrue(visibleNontransparentSamples(level, width) > 0,
                    "background must contain ROM art on transition pass " + i
                            + ", routine=" + state.backgroundRoutine()
                            + ", sourceX=" + tilemap.getBackgroundTilemapSourceX()
                            + ", scrollY=" + parallax.getVscrollFactorBG());
            // The engine replaces the entire cache at once. Its source window must
            // follow the displayed deformation, including the saved plain frame.
            boolean displayedClouds = state.backgroundRoutine() == 8;
            assertEquals(displayedClouds, SwScrlSsz.cloudWindowActive());
            assertEquals(displayedClouds ? 0x1C00 : (state.backgroundCameraX() & 0xFFFF), parallax.getBgCameraX());
            if (!replay) continue;
            byte[] saved = state.captureBytes();
            int[] scroll = parallax.getHScroll().clone();
            int sourceX = parallax.getBgCameraX(), yScroll = parallax.getVscrollFactorBG();
            byte[] tiles = tilemap.getBackgroundTilemapData().clone();
            // Visit the other mode before re-rendering the saved frame. Handler
            // caches from that future must not select its mode or scroll table.
            camera.setX((short) 0xA00);
            camera.setY((short) 0x900);
            for (int pass = 0; pass < 54; pass++) parallax.update(10, 0, camera, 2000 + i * 100 + pass);
            if (displayedClouds) {
                camera.setY((short) 0x700);
                parallax.update(10, 0, camera, 2054 + i * 100);
                parallax.update(10, 0, camera, 2055 + i * 100);
            }
            state.restoreBytes(saved);
            camera.setX((short) 0x400);
            camera.setY((short) positions[i]);
            parallax.update(10, 0, camera, 1000 + i);
            level.ensureBackgroundTilemapData();
            assertArrayEquals(saved, state.captureBytes(), "same-frame render cannot advance persistent state");
            assertArrayEquals(scroll, parallax.getHScroll(), "restored frame keeps its cloud band words");
            assertEquals(sourceX, parallax.getBgCameraX()); assertEquals(yScroll, parallax.getVscrollFactorBG());
            assertArrayEquals(tiles, tilemap.getBackgroundTilemapData(), "restored frame selects the same ROM tile window");
        }
    }

    private static int visibleNontransparentSamples(LevelManager manager, int width) {
        var tilemap = manager.getTilemapManager();
        var level = manager.getCurrentLevel();
        byte[] data = tilemap.getBackgroundTilemapData();
        int columns = tilemap.getBackgroundTilemapWidthTiles();
        int rows = tilemap.getBackgroundTilemapHeightTiles();
        int sourceX = tilemap.getBackgroundTilemapSourceX();
        int sourceY = tilemap.getBackgroundTilemapSourceY();
        int bgY = GameServices.parallax().getVscrollFactorBG();
        int[] scroll = GameServices.parallax().getHScroll();
        int nonzero = 0;
        // Sample the real CPU-built descriptor cache with the shader's wrapped
        // world coordinates, then inspect the corresponding ROM pattern pixel.
        for (int y = 0; y < 224; y += 8) for (int x = 0; x < width; x += 8) {
            int worldX = x - (short) scroll[y] - sourceX;
            int worldY = bgY + y - sourceY;
            int tileX = Math.floorMod(Math.floorDiv(worldX, 8), columns);
            int tileY = Math.floorMod(Math.floorDiv(worldY, 8), rows);
            int offset = (tileY * columns + tileX) * 4;
            int attributes = data[offset + 1] & 255;
            int patternId = (data[offset] & 255) | ((attributes & 7) << 8);
            var pattern = level.getPattern(patternId);
            if (pattern == null) continue;
            int px = Math.floorMod(worldX, 8), py = Math.floorMod(worldY, 8);
            if ((attributes & 0x20) != 0) px = 7 - px;
            if ((attributes & 0x40) != 0) py = 7 - py;
            if (pattern.getPixel(px, py) != 0) nonzero++;
        }
        return nonzero;
    }
}
