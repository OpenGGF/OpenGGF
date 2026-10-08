package com.openggf.level.render;

import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.level.LevelData;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class TestDetachedStockScenePictures {
    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void greenHillPicturesAndCharacterDecodeWithoutAmbientServices() throws Exception {
        check(new Sonic1GameModule(), 0);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void chemicalPlantUsesPublicZoneOneAndPicturesDecodeWithoutAmbientServices() throws Exception {
        check(new Sonic2GameModule(), 1);
    }

    private static void check(GameModule module, int zone) throws Exception {
        Rom rom = TestEnvironment.currentRom();
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                try (var services = Mockito.mockStatic(GameServices.class, invocation -> {
                    throw new AssertionError("Detached scene accessed GameServices." + invocation.getMethod().getName());
                }); var sessions = Mockito.mockStatic(SessionManager.class, invocation -> {
                    throw new AssertionError("Detached scene accessed SessionManager." + invocation.getMethod().getName());
                })) {
                    SceneRomArt art = SceneRomArtFactory.forModule(module, rom);
                    if (module.getGameId().code().equals("s2")) {
                        var detached = new Sonic2(rom).buildDetachedLevel(LevelData.CHEMICAL_PLANT_1.levelIndex());
                        // Animated_CPZ at $401B2 copies its first two raw art tiles ($4FAFE)
                        // into $370/$371 before the static scene can draw its background.
                        byte[] firstFrame = rom.readBytes(0x4FAFE, 64);
                        for (int tile = 0; tile < 2; tile++) {
                            assertNotNull(detached.getPattern(0x370 + tile));
                            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                                int packed = firstFrame[tile * 32 + y * 4 + x / 2] & 255;
                                int expected = (x & 1) == 0 ? packed >>> 4 : packed & 15;
                                assertEquals(expected, detached.getPattern(0x370 + tile).getPixel(x, y),
                                        "detached CPZ animated tile is genuine first-frame ROM art");
                            }
                        }
                    }
                    assertTrue(art.hasZonePictures(zone, 0));
                    assertFalse(art.hasZonePictures(zone, 1));
                    assertNull(art.zoneBackdrop(zone, 1));
                    assertTrue(art.character("sonic").frameCount() > 0);
                    assertTrue(Arrays.stream(art.characterPalette("sonic")).distinct().count() > 4,
                            "stock character palette is loaded rather than the provider's empty default");
                    var character = art.character("sonic");
                    assertTrue(java.util.stream.IntStream.range(0, character.frameCount()).anyMatch(frame ->
                            Arrays.stream(character.frame(frame).image().pixels()).distinct().count() > 4));
                    var backdrop = art.zoneBackdrop(zone, 0);
                    assertNotNull(backdrop);
                    assertTrue(Arrays.stream(backdrop.image().pixels()).distinct().count() > 16);
                    assertEquals(1, backdrop.bands().size());
                    assertEquals(0, backdrop.bands().get(0).speed());
                    List<com.openggf.mods.scene.SceneLevelStage> stages = art.levelStages(zone, 0, 320, 80, 24);
                    assertFalse(stages.isEmpty(), "ROM collision contains a usable performance stage");
                    var stage = stages.getFirst();
                    var foreground = art.levelForeground(zone, 0, stage.x(), stage.floorY() - 134, 320, 224);
                    assertNotNull(foreground);
                    assertTrue(Arrays.stream(foreground.pixels()).anyMatch(pixel -> pixel != 0));
                    var overview = art.levelOverview(zone, 0, 128);
                    assertNotNull(overview);
                    assertTrue(overview.height() <= 128);
                    assertTrue(overview.width() <= 4096);
                    assertThrows(IllegalArgumentException.class, () -> art.levelOverview(zone, 0, 0));
                    return null;
                }
            }).get(120, TimeUnit.SECONDS);
        }
    }
}
