package com.openggf.game.sonic1;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Obj3F uses shared backwards mapping references but distinct fiery frames1/2. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1BossExplosionRomArt {
    @Test
    void productionBossSheetUsesTheSignedTableSelectedByExplMain() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        try {
            RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
            assertEquals(0x217C, rom.readU16BE(0x948A)); // move.l #Map_ExplodeBomb,obMap
            assertEquals(0x958A, rom.readU32BE(0x948C));
            assertEquals(-60, rom.readS16BE(0x958A));
            Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
            provider.loadArtForZone(Sonic1Constants.ZONE_GHZ);
            var item = provider.getSheet(ObjectArtKeys.EXPLOSION);
            var boss = provider.getSheet(ObjectArtKeys.BOSS_EXPLOSION);
            assertNotSame(item, boss);
            assertSame(item.getPatterns(), boss.getPatterns(), "same ROM Nem_Explode bank, different mappings");
            assertEquals(5, boss.getFrameCount());
            for (int sharedFrame : new int[]{0, 3, 4}) {
                assertEquals(item.getFrame(sharedFrame), boss.getFrame(sharedFrame));
            }
            assertEquals(0x40, boss.getFrame(1).pieces().getFirst().tileIndex());
            assertEquals(0x50, boss.getFrame(2).pieces().getFirst().tileIndex());
            assertEquals(6, item.getFrame(1).pieces().getFirst().tileIndex());
            assertEquals(0x16, item.getFrame(2).pieces().getFirst().tileIndex());
            assertTrue(boss.getPatterns().length >= 0x60);
        } finally {
            GraphicsManager.getInstance().resetState();
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void productionRendererDrawsBothFieryFramesFromTheirNativeRomTilesOnEgl() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            boot.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 0, 0);
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var renderManager = GameServices.level().getObjectRenderManager();
            var renderer = renderManager.getBossExplosionRenderer();
            assertNotNull(renderer);
            assertTrue(renderer.isReady());
            GraphicsManager graphics = GameServices.graphics();
            graphics.discardQueuedCommands();
            PaletteUploadPresentation.release(graphics);
            graphics.clearPaletteFadePresentation();
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            Palette palette = new Palette();
            for (int index = 1; index < 16; index++) {
                palette.colors[index] = new Palette.Color((byte) (index * 17), (byte) (255 - index * 17), (byte) 0);
            }
            graphics.cachePaletteTexture(palette, 0);
            glDisable(GL_DITHER);
            Pattern[] patterns = new Sonic1ObjectArt(TestEnvironment.currentRom(),
                    RomByteReader.fromRom(TestEnvironment.currentRom()))
                    .loadNemesisPatterns(Sonic1Constants.ART_NEM_EXPLOSION_ADDR);
            RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
            int table = rom.readU32BE(0x948C);
            for (int frame = 1; frame <= 2; frame++) {
                int frameAddress = table + rom.readS16BE(table + frame * 2);
                assertEquals(1, rom.readU8(frameAddress));
                assertEquals(0x0F, rom.readU8(frameAddress + 2)); // native single 4x4 piece
                int firstTile = rom.readU16BE(frameAddress + 3) & 0x7FF;
                RgbaImage fiery = draw(renderer, graphics, frame);
                RgbaImage item = draw(renderManager.getExplosionRenderer(), graphics, frame);
                int opaquePixels = 0;
                int differencesFromItem = 0;
                // Independent native column-major tile oracle, no renderer/map-loader reuse.
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                    int color = patterns[firstTile + (x / 8) * 4 + y / 8].getPixel(x % 8, y % 8) & 0xF;
                    int expected = color == 0 ? 0xFF000000
                            : 0xFF000000 | (color * 17 << 16) | (255 - color * 17 << 8);
                    assertEquals(expected, fiery.argb(144 + x, 96 + y),
                            "native fiery frame " + frame + " pixel " + x + "," + y);
                    if (color != 0) opaquePixels++;
                    if (fiery.argb(144 + x, 96 + y) != item.argb(144 + x, 96 + y)) differencesFromItem++;
                }
                assertTrue(opaquePixels > 0, "real native art reached GPU readback");
                assertTrue(differencesFromItem > 0, "fiery frames must discriminate against the old item mapping alias");
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    private static RgbaImage draw(PatternSpriteRenderer renderer, GraphicsManager graphics, int frame) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        graphics.beginPatternBatch();
        renderer.drawFrameIndex(frame, 160, 112, false, false);
        graphics.flushPatternBatch();
        graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
