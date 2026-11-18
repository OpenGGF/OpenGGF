package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneKey;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdSpring;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.ModLevel;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

class TestSonicCdAuthoredSprings {
    private static final ZoneKey.Mod ZONE = new ZoneKey.Mod("cd-springs", "test-act");
    private static final int UP_X = 96;
    private static final int SIDE_X = 160;
    private static final int SPRING_Y = 410;

    @Test
    void coldAuthoredActDrawsBothSpringSheetsAndLaunchesSonicTwice() throws Exception {
        var module = authoredAct(List.of(spawn(1, UP_X, 0x0A, 2), spawn(2, SIDE_X, 0x0A, 6)));
        int zone = module.getZoneRegistry().resolveZoneKey(ZONE).orElseThrow();
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = (CdDiscDataSource) HostedGameMedia.of(module).openMedia(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, module, zone, 0);
            var levels = GameServices.level();
            assertInstanceOf(ModLevel.class, levels.getCurrentLevel());
            var art = module.getGameService(CdObjectArtProvider.class);
            assertNull(art.payload(), "common spring art does not publish a native stage");
            assertNull(art.image());
            assertNull(art.getSheet(CdObjectArtProvider.MONITOR));
            loop.step();
            var springs = levels.getObjectManager().getActiveObjects().stream()
                    .filter(CdSpring.class::isInstance).toList();
            assertEquals(2, springs.size());
            var vertical = springs.stream().filter(spring -> spring.getX() == UP_X).findFirst().orElseThrow();
            RgbaImage gameplay = draw(LevelManager.LevelRenderOptions.gameplay());
            RgbaImage tiles = draw(LevelManager.LevelRenderOptions.tilesOnly());
            assertSpringPixels(gameplay, tiles, UP_X);
            assertSpringPixels(gameplay, tiles, SIDE_X);

            var player = GameServices.camera().getFocusedSprite();
            int launches = 0;
            for (int frame = 0; frame < 240 && launches < 2; frame++) {
                int before = player.getYSpeed();
                loop.step();
                if (before >= 0 && player.getYSpeed() == -0x0A00) launches++;
            }
            assertEquals(2, launches, "the native animation must re-arm the spring for a second real landing");
            assertSame(vertical, levels.getObjectManager().getActiveObjects().stream()
                    .filter(spring -> spring instanceof CdSpring && spring.getX() == UP_X).findFirst().orElseThrow());
            assertFalse(player.getDead());
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio);
        }
    }

    @Test
    void diagonalAndOtherStockIdsStillRequireNativeStageContext() {
        for (var placement : List.of(spawn(1, UP_X, 0x0A, 8), spawn(1, UP_X, 0x0A, 0x0C),
                spawn(1, UP_X, 0x0F, 0), spawn(1, UP_X, 0x7F, 0))) {
            assertThrows(ModZoneRegistrationException.class, () -> authoredAct(List.of(placement)), placement.toString());
        }
    }

    private static ModLevelDefinition.StockObjectSpawn spawn(int id, int x, int objectId, int subtype) {
        return new ModLevelDefinition.StockObjectSpawn(id, x, SPRING_Y, objectId, subtype, 0, true, SPRING_Y);
    }

    private static GameModule authoredAct(List<ModLevelDefinition.ObjectEntry> objects) {
        var declared = ModZoneContribution.singleAct(ZONE.localName(), new BakedLevelRef("level.json"), null, null, false);
        byte[][] palettes = {new byte[32], new byte[32], new byte[32], new byte[32]};
        for (int color = 2; color < 32; color += 2) {
            palettes[1][color] = 0x0E;
            palettes[1][color + 1] = (byte) 0xE0;
        }
        var data = new ModLevelDefinition(1, "Springs", 0x40, 0x400, 16, 2, 3,
                new ModLevelDefinition.Bounds(0, 511, 0, 767), new ModLevelDefinition.Start(UP_X, 346),
                new ModLevelDefinition.StockMusic(1), objects, List.of(),
                new byte[32], new byte[8], new byte[1024], new byte[6], null,
                new byte[16], new byte[16], new byte[1], new int[]{0}, new int[]{0}, palettes, 1, 1, 1, 1);
        var prepared = PreparedModZone.prepared(ZONE.ownerModId(), declared, data);
        var plan = new ModRegistrationPlan(ZONE.ownerModId(), "scd", Map.of(), Map.of(), Map.of(),
                List.of(), List.of(declared), List.of(prepared));
        return new ModBackedGamePatch(plan).apply(new SonicCdGameModule(),
                new PatchContext(ignored -> null, GameServices.configuration()));
    }

    private static RgbaImage draw(LevelManager.LevelRenderOptions options) {
        var levels = GameServices.level();
        var graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        levels.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        levels.drawWithRenderOptions(GameServices.sprites(), options);
        graphics.flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void assertSpringPixels(RgbaImage gameplay, RgbaImage tiles, int worldX) {
        var camera = GameServices.camera();
        int x0 = worldX - camera.getX();
        int y0 = SPRING_Y - camera.getY();
        int pixels = 0;
        for (int y = Math.max(0, y0 - 16); y < Math.min(224, y0 + 16); y++) {
            for (int x = Math.max(0, x0 - 16); x < Math.min(320, x0 + 16); x++) {
                int color = gameplay.argb(x, y);
                if (color == tiles.argb(x, y)) continue;
                assertEquals(0x00FFFF, color & 0xFFFFFF, "spring uses the authored palette line 1");
                pixels++;
            }
        }
        assertTrue(pixels > 32, "the native spring art must occupy visible pixels at X " + worldX);
        System.out.println("CD authored spring at X " + worldX + " uses " + pixels + " authored-palette pixels");
    }
}
