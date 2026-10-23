package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.internal.HudSatLayout;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.*;

/**
 * ROM-free real-EGL composition controls for upstream 78b44b26e's optional TIME format
 * with PR223's native HUD SAT grouping (merge-tree ae2e1c83a).
 * Synthetic colored digit art makes every pixel expectation independent of the formatter.
 * No engine boot, audio playback, gameplay stepping, or desktop graphics connection.
 */
@Isolated
class TestHudHundredthsPixels {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;
    private static final int DIGITS = 200;

    @Test
    void defaultFourCharactersStayExactInGroupedAndCounterPublications() throws Exception {
        withFixture(f -> {
            f.state.setTimerFrames(119);
            assertTime(f.grouped(0), "0:01");
            assertTime(f.counters(false), "0:01");
            assertEquals(119, f.state.getTimerFrames());
        });
    }

    @Test
    void configuredHundredthsIncludeTheDotAndBothDigitsInBothPublications(@TempDir Path directory)
            throws Exception {
        withFixture(f -> {
            var configuration = SonicConfigurationService.createStandalone(directory);
            configuration.setConfigValue(SonicConfiguration.DISPLAY_HUD_TIME_HUNDREDTHS, true);
            HudProfileAccess.applyDisplaySettings(f.hud, configuration);
            assertTrue(HudProfileAccess.timeHundredths(f.hud));
            f.state.setTimerFrames(119);
            // The receiving pre-fix grouping drops all three characters at x88 and beyond.
            assertTime(f.grouped(0), "0:01.98");
            assertTime(f.counters(false), "0:01.98");
            assertEquals(119, f.state.getTimerFrames());
        });
    }

    @Test
    void hostedClockOverridesProjectedActTimeAndClearsThroughItsOwner() throws Exception {
        withFixture(f -> {
            f.state.setTimerFrames(59);
            HudProfileAccess.setHostTime(f.hud, 119, true);
            assertTime(f.grouped(0), "0:01.98");
            assertTime(f.counters(true), "0:01.98");
            assertEquals(59, f.state.getTimerFrames(), "presentation must not advance the act timer");
            assertEquals(119, HudProfileAccess.hostTimeFrames(f.hud));

            HudProfileAccess.setHostTime(f.hud, -1, false);
            assertFalse(HudProfileAccess.timeHundredths(f.hud));
            assertTime(f.grouped(0), "0:00");
            assertTime(f.counters(true), "0:01");
            assertTime(f.grouped(0), "0:00");
            assertEquals(59, f.state.getTimerFrames());
        });
    }

    @Test
    void projectedRolloverPauseAndRestoredDisplayPreferenceLeaveGameplayTimeAlone(@TempDir Path directory)
            throws Exception {
        withFixture(f -> {
            var configuration = SonicConfigurationService.createStandalone(directory);
            boolean previous = configuration.getBoolean(SonicConfiguration.DISPLAY_HUD_TIME_HUNDREDTHS);
            configuration.setConfigValue(SonicConfiguration.DISPLAY_HUD_TIME_HUNDREDTHS, true);
            HudProfileAccess.applyDisplaySettings(f.hud, configuration);
            f.state.setTimerFrames(59);
            assertTime(f.counters(true), "0:01.00");
            assertEquals(59, f.state.getTimerFrames());
            assertTime(f.counters(false), "0:00.98");
            f.state.pauseTimer();
            assertTime(f.counters(true), "0:00.98");
            assertTrue(f.state.isTimerPaused());
            assertEquals(59, f.state.getTimerFrames());
            // Reapply the saved preference through the configuration/HUD bridge, not a private field.
            configuration.setConfigValue(SonicConfiguration.DISPLAY_HUD_TIME_HUNDREDTHS, previous);
            HudProfileAccess.applyDisplaySettings(f.hud, configuration);
            assertFalse(HudProfileAccess.timeHundredths(f.hud));
            assertTime(f.grouped(0), "0:00");
            assertTime(f.counters(true), "0:00");
            f.state.resumeTimer();
            assertTime(f.counters(true), "0:01");
            assertTime(f.grouped(0), "0:00");
            assertEquals(59, f.state.getTimerFrames());
        });
    }

    @Test
    void optionalCharactersCannotEscapeAnExhaustedNativeScanline() throws Exception {
        withFixture(f -> {
            f.state.setTimerFrames(119);
            HudProfileAccess.setHostTime(f.hud, 119, true);
            assertBlankTime(f.grouped(20));
            assertEquals(119, f.state.getTimerFrames());
        });
    }

    @Test
    void optionalCharactersRespectPartialColumnAdmission() throws Exception {
        withFixture(f -> {
            HudProfileAccess.setHostTime(f.hud, 119, true);
            var widths = new java.util.ArrayList<>(Collections.nCopies(7, 4));
            widths.add(2); // 30 columns, then the native label/time use eight.
            assertTime(f.groupedColumns(widths), "0:01.9");
            assertEquals(0, f.state.getTimerFrames());
        });
    }

    private static void withFixture(CheckedFixture test) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var context = PresentationOffscreenTestContext.open(WIDTH, HEIGHT)) {
            var graphics = new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, WIDTH, HEIGHT);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, WIDTH, 0, HEIGHT, -1, 1)
                        .get(new float[16]));
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                installArt(graphics);
                // Both production replay batching implementations must consume the same art/geometry.
                for (boolean batching : new boolean[]{false, true}) {
                    graphics.setBatchingEnabled(batching);
                    graphics.setInstancedBatchingEnabled(batching);
                    test.run(new Fixture(graphics));
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            } finally {
                graphics.cleanup();
                RenderContext.reset();
            }
        }
    }

    @FunctionalInterface
    private interface CheckedFixture { void run(Fixture fixture) throws Exception; }

    private static final class Fixture {
        final GraphicsManager graphics;
        final LevelGamestate state = new LevelGamestate();
        final HudRenderManager hud;

        Fixture(GraphicsManager graphics) {
            this.graphics = graphics;
            Camera camera = mock(Camera.class);
            GameStateManager gameState = mock(GameStateManager.class);
            hud = new HudRenderManager(graphics, camera, gameState);
            hud.setDigitPatternIndex(DIGITS);
            hud.setLivesNumbersPatternIndex(300);
            // Same native S3K ten-piece normal footprint as TestHudSatProjection.
            var stock = new SpriteMappingFrame(List.of(
                    piece(0, -128, 4, 0), piece(32, -128, 4, 0x18), piece(64, -128, 4, 0x20),
                    piece(0, -112, 4, 0x10), piece(40, -112, 4, 0x28),
                    piece(0, -96, 4, 8), piece(32, -96, 1, 0), piece(48, -96, 3, 0x30),
                    piece(0, 64, 2, 0x10A), piece(16, 64, 4, 0x10E)));
            hud.setSatLayout(new HudSatLayout(Collections.nCopies(4, stock)));
        }

        ByteBuffer grouped(int precedingPieces) {
            return groupedColumns(Collections.nCopies(precedingPieces, 1));
        }

        ByteBuffer groupedColumns(List<Integer> precedingWidths) {
            var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                SpriteSatInternalAccess.beginCollection(graphics,
                        SpriteSatMaskPostProcessor.Raster.h40(0, 0, HEIGHT));
                for (int width : precedingWidths) {
                    graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                            160, 24, width, 2, DIGITS, DIGITS, 0, false, false, false, false,
                            SpriteMaskReplayRole.NORMAL, 0, width, 0, 2, null));
                }
                hud.draw(state, null);
                graphics.endSpriteSatCollectionAndReplay();
            });
            return render(frame);
        }

        ByteBuffer counters(boolean advanceTimer) {
            // LevelRenderer.publishHudCounters prepares this same bridge without SAT collection.
            var frame = SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> HudProfileAccess.drawVBlankCounters(hud, state, null, advanceTimer));
            return render(frame);
        }

        private ByteBuffer render(SpritePresentation.Frame frame) {
            glViewport(0, 0, WIDTH, HEIGHT);
            glDisable(GL_DITHER);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            graphics.executeCapturedCommands(() -> SpritePresentationRenderer.drawNative(
                    graphics, frame, 0, 0, layer -> layer == SpritePresentation.Layer.HUD_COUNTERS),
                    0, 0, WIDTH, HEIGHT);
            glFinish();
            ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return pixels;
        }
    }

    private static SpriteMappingPiece piece(int x, int y, int width, int tile) {
        return new SpriteMappingPiece(x, y, width, 2, tile, false, false, 1);
    }

    private static void installArt(GraphicsManager graphics) {
        var palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
        palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
        palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
        palette.setColor(4, new Palette.Color((byte) 255, (byte) 255, (byte) 0));
        palette.setColor(5, new Palette.Color((byte) 255, (byte) 255, (byte) 255));
        for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
        for (int digit = 0; digit < 10; digit++) for (int half = 0; half < 2; half++) {
            var pattern = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                pattern.setPixel(x, y, (byte) colorIndex((char) ('0' + digit)));
            }
            graphics.cachePatternTexture(pattern, DIGITS + digit * 2 + half);
        }
        var colonTop = new Pattern();
        var colonBottom = new Pattern();
        for (int x : new int[]{3, 4}) {
            colonTop.setPixel(x, 2, (byte) 5);
            colonTop.setPixel(x, 3, (byte) 5);
            colonTop.setPixel(x, 7, (byte) 5);
            colonBottom.setPixel(x, 0, (byte) 5);
            colonBottom.setPixel(x, 1, (byte) 5);
        }
        graphics.cachePatternTexture(colonTop, DIGITS + 20);
        graphics.cachePatternTexture(colonBottom, DIGITS + 21);
        for (int id = 300; id < 310; id++) graphics.cachePatternTexture(new Pattern(), id);
    }

    private static int colorIndex(char glyph) {
        return switch (glyph) { case '0' -> 1; case '1' -> 2; case '8' -> 3; case '9' -> 4; default -> 5; };
    }

    private static void assertTime(ByteBuffer pixels, String text) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 56; x++) {
            int character = x / 8;
            int color = 0;
            if (character < text.length()) {
                char glyph = text.charAt(character);
                if (Character.isDigit(glyph)) color = colorIndex(glyph);
                else if ((x % 8 == 3 || x % 8 == 4)
                        && (glyph == ':' && (y == 2 || y == 3) || y >= 7 && y <= 9)) color = 5;
            }
            assertPixel(pixels, 56 + x, 24 + y, color);
        }
    }

    private static void assertBlankTime(ByteBuffer pixels) {
        for (int y = 24; y < 40; y++) for (int x = 56; x < 112; x++) assertPixel(pixels, x, y, 0);
    }

    private static void assertPixel(ByteBuffer pixels, int x, int y, int color) {
        int[] rgb = switch (color) {
            case 1 -> new int[]{255, 0, 0}; case 2 -> new int[]{0, 255, 0};
            case 3 -> new int[]{0, 0, 255}; case 4 -> new int[]{255, 255, 0};
            case 5 -> new int[]{255, 255, 255}; default -> new int[]{0, 0, 0};
        };
        int offset = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
        for (int channel = 0; channel < 3; channel++) assertEquals(rgb[channel],
                pixels.get(offset + channel) & 255, "pixel " + x + "," + y + " channel " + channel);
    }
}
