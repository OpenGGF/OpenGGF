package com.openggf.game.sonic2.competition.interlace;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceDecoder.*;
import static com.openggf.game.sonic2.competition.interlace.TestSonic2InterlaceDecoder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Real ROM art -> native field projection -> production atlas/SAT -> EGL pixels. */
@RequiresRom(SonicGame.SONIC_2)
@Isolated
class TestSonic2InterlacePixels {
    private static final int BANK = 0x1D8000;

    @Test void romPlanesAndWholeNativeSatPiecesRenderBothFieldsWithoutDeviceAudio() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires explicit surfaceless EGL");
        assertNull(System.getenv("DISPLAY"), "pixel qualification must not use the shared desktop");
        assertNull(System.getenv("WAYLAND_DISPLAY"), "pixel qualification must not use the shared desktop");
        var config = GameServices.configuration();
        var saved = new HashMap<SonicConfiguration, Object>();
        for (var key : new SonicConfiguration[]{SonicConfiguration.AUDIO_ENABLED,
                SonicConfiguration.TEST_MODE_ENABLED, SonicConfiguration.MAIN_CHARACTER_CODE,
                SonicConfiguration.SIDEKICK_CHARACTER_CODE, SonicConfiguration.SCREEN_WIDTH,
                SonicConfiguration.SCREEN_HEIGHT, SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT}) saved.put(key, config.getConfigValue(key));
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, 320);
        config.setConfigValue(SonicConfiguration.SCREEN_HEIGHT, 224);
        config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0);
            var graphics = GameServices.graphics();
            // Acquire the actual post-boot owner. Retire its scene and GL resources
            // while this EGL context remains current, before HeadlessGameBoot closes it.
            try (AutoCloseable cleanup = () -> {
                try { SessionManager.closeGameplaySession(); }
                finally { graphics.cleanup(); }
            }) {
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend(),
                    "HeadlessGameBoot must install a no-device owner before loading level music");
            graphics.getPatternAtlas().registerRange(BANK, 0x1000, "S2 private interlace test window");
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            for (int line = 0; line < 4; line++) {
                var palette = new Palette();
                for (int color = 0; color < 16; color++) palette.setColor(color,
                        new Palette.Color((byte) (color * 17), (byte) (line * 85), (byte) (255 - color * 17)));
                graphics.cachePaletteTexture(palette, line);
            }
            var profile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 0, 0);
            var image = Sonic2InterlaceVram.fromZoneArt(TestEnvironment.currentRom(), profile)
                    .withWrite(0, new byte[64]); // transparent control sprites still consume native admission
            byte[] table = new byte[32 * 32 * 2];
            for (int row = 0; row < 32; row++) for (int col = 0; col < 32; col++)
                word(table, (row * 32 + col) * 2, 8 + (row * 11 + col) % 64
                        | ((col % 4) << 13) | ((col & 1) == 0 ? 0x800 : 0) | ((row & 1) == 0 ? 0x9000 : 0));
            image = image.withWrite(0xE000, table);
            byte[] raw = image.bytes();
            var producer = new Sonic2InterlaceProducer(BANK, 0x1000);
            producer.uploadPatterns(graphics, image);
            for (boolean batched : new boolean[]{false, true}) {
                graphics.setBatchingEnabled(batched); graphics.setInstancedBatchingEnabled(batched);
                for (var field : Field.values()) {
                    var plane = new Plane(0xE000, 32, 32, -5, 7);
                    var preparedPlane = producer.preparePlane(image, plane, field,
                            new Sonic2InterlaceProducer.Viewport(0, 0, 320, 224));
                    ByteBuffer planePixels = draw(graphics, () -> {
                        producer.drawPlane(graphics, preparedPlane, false);
                        producer.drawPlane(graphics, preparedPlane, true);
                    });
                    int colored = 0;
                    for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
                        int sx = Math.floorMod(x - 5, 256), sy = (y * 2 + field.parity() + 7) % 512;
                        int name = ((table[(sy / 16 * 32 + sx / 8) * 2] & 255) << 8)
                                | (table[(sy / 16 * 32 + sx / 8) * 2 + 1] & 255);
                        int color = referencePixel(raw, name & 1023, (name & 0x800) == 0 ? sx % 8 : 7 - sx % 8,
                                (name & 0x1000) == 0 ? sy % 16 : 15 - sy % 16);
                        pixel(planePixels, x, y, color, name >>> 13 & 3);
                        if (color != 0) colored++;
                    }
                    assertTrue(colored > 1000, "the native ROM field must produce nonblank pixels");
                    var sprites = new ArrayList<Sprite>();
                    for (int flip = 0; flip < 4; flip++) {
                        int name = 9 | (flip << 13) | ((flip & 1) == 0 ? 0 : 0x800)
                                | ((flip & 2) == 0 ? 0 : 0x9000);
                        // Odd native origin and P2's +224 offset are intentionally distinct.
                        sprites.add(new Sprite(256 + 9, 9, 0, new NameWord(name), 128 + 16 + flip * 72));
                        sprites.add(new Sprite(256 + 233, 9, 0, new NameWord(name), 128 + 16 + flip * 72));
                    }
                    var prepared = producer.prepareSprites(new Input(image, field, sprites));
                    ByteBuffer spritePixels = draw(graphics, () -> producer.drawSprites(graphics, prepared,
                            SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224)));
                    for (var sprite : sprites) assertSprite(spritePixels, raw, sprite, field);
                    pixel(spritePixels, 0, 0, 0, 0); // reject stale plane pixels
                    var admission = new ArrayList<Sprite>();
                    for (int i = 0; i < 19; i++) admission.add(new Sprite(296, 0, 0, new NameWord(0), 136));
                    var twentieth = new Sprite(296, 9, 0, new NameWord(0xD009), 208);
                    admission.add(twentieth);
                    admission.add(new Sprite(296, 0, 0, new NameWord(9), 328));
                    var limited = producer.prepareSprites(new Input(image, field, admission));
                    ByteBuffer admitted = draw(graphics, () -> producer.drawSprites(graphics, limited,
                            SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224)));
                    assertSprite(admitted, raw, twentieth, field);
                    for (int y = 20; y < 28; y++) for (int x = 200; x < 208; x++) pixel(admitted, x, y, 0, 0);
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            }
            }
        } finally {
            saved.forEach(config::setConfigValue);
        }
    }

    @Test void twoNativePlayerDplcsChangeRealPixelsOnlyAtVblankAndRewindReplaysThePublication() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires explicit surfaceless EGL");
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        var config = GameServices.configuration();
        var savedConfig = new HashMap<SonicConfiguration, Object>();
        for (var key : new SonicConfiguration[]{SonicConfiguration.AUDIO_ENABLED,
                SonicConfiguration.TEST_MODE_ENABLED, SonicConfiguration.MAIN_CHARACTER_CODE,
                SonicConfiguration.SIDEKICK_CHARACTER_CODE, SonicConfiguration.SCREEN_WIDTH,
                SonicConfiguration.SCREEN_HEIGHT}) savedConfig.put(key, config.getConfigValue(key));
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, 320);
        config.setConfigValue(SonicConfiguration.SCREEN_HEIGHT, 224);
        try (var context = new PixelBoot(new HeadlessGameBoot(320, 224))) {
            var boot = context.boot();
            boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0);
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var rom = TestEnvironment.currentRom();
            var sprites = GameServices.sprites();
            var sonic = sprites.getMainPlayable();
            var tails = sprites.getSidekicks().getFirst();
            assertNotNull(sonic); assertNotNull(tails);
            sonic.setCpuControlled(false); tails.setCpuControlled(false);
            int sonicFrame = sonic.getMappingFrame(), tailsFrame = tails.getMappingFrame();
            var productionPlc = new Sonic2PlcService(rom);
            var service = new Sonic2InterlaceVramService(rom, productionPlc);
            assertSame(productionPlc, service.plcService());
            service.bindPlayers(sonic, tails); service.resetForNativeEntry();
            service.loadZoneArt(Sonic2CompetitionLevelProfile.load(rom, 0, 0));
            var graphics = GameServices.graphics();
            graphics.getPatternAtlas().registerRange(BANK, 0x1000, "S2 private interlace VBlank test");
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            var palette = new Palette();
            for (int color = 0; color < 16; color++) palette.setColor(color,
                    new Palette.Color((byte) (color * 17), (byte) 0, (byte) (255 - color * 17)));
            for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
            var geometry = List.of(new Sprite(296, 9, 0, new NameWord(0x3C0), 152),
                    new Sprite(296, 9, 0, new NameWord(0x3D0), 256),
                    new Sprite(360, 5, 0, new NameWord(0x3D8), 352));
            var producer = new Sonic2InterlaceProducer(BANK, 0x1000);
            var blank = drawNative(graphics, producer, service.current(), Field.EVEN, geometry);
            for (var sprite : geometry) for (int y = 0; y < sprite.heightCells() * 8; y++)
                for (int x = 0; x < sprite.widthPixels(); x++)
                    pixel(blank, sprite.x() + x, Field.EVEN.projectY(sprite.y()) + y, 0, 0);
            service.playerLoadPlc(sonic, 1, null); service.playerLoadPlc(tails, 1, null);
            service.tailsTailLoadPlc(9, null);
            var pending = service.capture();
            var waiting = drawNative(graphics, producer, service.current(), Field.EVEN, geometry);
            assertEquals(-1, blank.mismatch(waiting), "object-pass DPLC submissions must not publish pixels");
            claimVblank(service);
            byte[] oracle = pending.vram().bytes();
            TestSonic2InterlaceVramService.applyFrame(oracle, rom, 0x714E0, 0x50000, 0xF000, 1);
            TestSonic2InterlaceVramService.applyFrame(oracle, rom, 0x7446C, 0x64320, 0xF400, 1);
            TestSonic2InterlaceVramService.applyFrame(oracle, rom, 0x7446C, 0x64320, 0xF600, 9);
            assertArrayEquals(oracle, service.current().bytes());
            for (boolean batched : new boolean[]{false, true}) {
                graphics.setBatchingEnabled(batched); graphics.setInstancedBatchingEnabled(batched);
                for (var field : Field.values()) {
                    var pixels = drawNative(graphics, producer, service.current(), field, geometry);
                    for (var sprite : geometry) assertSprite(pixels, oracle, sprite, field);
                }
            }
            var expectedPixels = drawNative(graphics, producer, service.current(), Field.EVEN, geometry);
            assertNotEquals(-1, blank.mismatch(expectedPixels), "actual ROM DPLC must change rendered pixels");
            service.playerLoadPlc(sonic, 2, null); service.playerLoadPlc(tails, 2, null);
            service.tailsTailLoadPlc(0, null); claimVblank(service);
            service.resetForNativeEntry(); service.restore(pending);
            assertEquals(pending, service.capture());
            assertEquals(-1, blank.mismatch(drawNative(graphics, producer, service.current(), Field.EVEN, geometry)));
            claimVblank(service);
            assertEquals(-1, expectedPixels.mismatch(drawNative(graphics, producer, service.current(), Field.EVEN, geometry)));
            assertEquals(sonicFrame, sonic.getMappingFrame()); assertEquals(tailsFrame, tails.getMappingFrame());
            assertEquals(GL_NO_ERROR, glGetError());
        } finally { savedConfig.forEach(config::setConfigValue); }
    }

    private static void claimVblank(Sonic2InterlaceVramService service) {
        var frame = new PlcFrameLifecycleCoordinator(service.plcLifecycle()).latchBeforeFadeUpdate();
        assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL); frame.finish();
    }

    /** Static command VAOs belong to this context and must retire before its EGL teardown. */
    private record PixelBoot(HeadlessGameBoot boot) implements AutoCloseable {
        @Override public void close() {
            try {
                var graphics = GameServices.graphics();
                try { SessionManager.closeGameplaySession(); }
                finally { graphics.cleanup(); }
            } finally {
                try { RenderContext.reset(); }
                finally { boot.close(); }
            }
        }
    }

    private static ByteBuffer drawNative(GraphicsManager graphics, Sonic2InterlaceProducer producer,
            Sonic2InterlaceVram vram, Field field, List<Sprite> sprites) {
        var prepared = producer.prepareSprites(new Input(vram, field, sprites));
        return draw(graphics, () -> producer.drawSprites(graphics, prepared,
                SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224)));
    }

    private static void assertSprite(ByteBuffer pixels, byte[] raw, Sprite sprite, Field field) {
        int first = field.projectY(sprite.y());
        int nonzero = 0;
        for (int y = 0; y < sprite.heightCells() * 8; y++) for (int x = 0; x < sprite.widthPixels(); x++) {
            int nativeY = (first + y) * 2 + field.parity() - sprite.y();
            int sx = sprite.name().hFlip() ? sprite.widthPixels() - 1 - x : x;
            int sy = sprite.name().vFlip() ? sprite.heightPixels() - 1 - nativeY : nativeY;
            int pattern = (sprite.name().patternIndex() + sx / 8 * sprite.heightCells() + sy / 16) % 1024;
            int color = referencePixel(raw, pattern, sx % 8, sy % 16);
            pixel(pixels, sprite.x() + x, first + y, color, sprite.name().palette());
            if (color != 0) nonzero++;
        }
        assertTrue(nonzero > 8, "ROM sprite pixels must reach the framebuffer");
    }

    private static ByteBuffer draw(GraphicsManager graphics, Runnable producer) {
        glViewport(0, 0, 320, 224); glDisable(GL_DITHER);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(producer, 0, 0, 320, 224);
        glFinish(); var pixels = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void pixel(ByteBuffer pixels, int x, int y, int color, int palette) {
        int offset = ((223 - y) * 320 + x) * 4;
        int red = color == 0 ? 0 : color * 17;
        int green = color == 0 ? 0 : palette * 85;
        int blue = color == 0 ? 0 : 255 - color * 17;
        assertEquals(red, pixels.get(offset) & 255, "red at " + x + "," + y);
        assertEquals(green, pixels.get(offset + 1) & 255, "green at " + x + "," + y);
        assertEquals(blue, pixels.get(offset + 2) & 255, "blue at " + x + "," + y);
    }
}
