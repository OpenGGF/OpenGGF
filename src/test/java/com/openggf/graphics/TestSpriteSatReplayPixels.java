package com.openggf.graphics;

import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.game.GameId;
import com.openggf.level.render.SpritePieceRenderer.PreparedPiece;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Pixels from the actual legacy submission -> SAT processing -> graphics replay boundary. */
@Isolated
class TestSpriteSatReplayPixels {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;
    private static final int FIRST_PATTERN = 40;
    private static final int[][] COLOURS = {
            {0, 0, 0}, {255, 0, 0}, {0, 255, 0}, {0, 0, 255},
            {255, 255, 0}, {255, 0, 255}, {0, 255, 255}
    };
    // ROM mapping pieces number art down columns, not across rows.
    private static final int[][] SOURCE_GRID = {{1, 4}, {2, 5}, {3, 6}};

    @Test
    void directTypedIngressRendersCachedPaletteSevenFlipsFractionalScaleAndStrips() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = PresentationOffscreenTestContext.open(WIDTH, HEIGHT)) {
            var graphics = new GraphicsManager();
            try {
                RenderContext.reset(); RenderContext.getOrCreateDonor(GameId.S1);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, WIDTH, HEIGHT); graphics.setProjectionWidth(WIDTH);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, WIDTH, 0, HEIGHT, -1, 1).get(new float[16]));
                graphics.setBatchingEnabled(false);
                var palette = new Palette();
                for (int colour = 1; colour <= 4; colour++) {
                    int[] rgb = COLOURS[colour];
                    palette.setColor(colour, new Palette.Color((byte) rgb[0], (byte) rgb[1], (byte) rgb[2]));
                }
                graphics.cachePaletteTexture(new Palette(), 3); // Raw-word palette would render black.
                graphics.cachePaletteTexture(palette, 7);
                var pattern = new Pattern();
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                    pattern.setPixel(x, y, (byte) (1 + (x / 4) + 2 * (y / 4)));
                int explicitId = PatternAtlasRange.OBJECTS.base();
                graphics.cachePatternTexture(pattern, explicitId);
                for (int flags = 0; flags < 8; flags++) {
                    boolean h = (flags & 1) != 0, v = (flags & 2) != 0;
                    var desc = new PatternDesc(0x6000); // The raw word disagrees with every cached attribute.
                    desc.setPatternIndex(99); desc.setPaletteIndex(7);
                    desc.setHFlip(h); desc.setVFlip(v); desc.setPriority((flags & 4) != 0);
                    byte[] pixels = draw(graphics, () -> {
                        graphics.renderPatternWithId(explicitId, desc, 16, 16);
                        graphics.renderPatternWithIdScaled(explicitId, desc, 40.25f, 16.25f, 15.5f, 11.5f);
                        graphics.renderStripPatternWithId(explicitId, desc, 64, 16, 0);
                    });
                    for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                        int expected = 1 + (h ? 1 - col : col) + 2 * (v ? 1 - row : row);
                        assertPixel(pixels, 18 + col * 4, 18 + row * 4, expected);
                        assertPixel(pixels, 43 + col * 8, 18 + row * 6, expected);
                    }
                    assertPixel(pixels, 15, 18, 0); assertPixel(pixels, 56, 20, 0);
                    // Strip zero samples the bottom source band, in either vertical orientation.
                    assertPixel(pixels, 66, 16, h ? 4 : 3);
                    assertPixel(pixels, 64, 18, 0);
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test
    void directAndInstancedSatReplayPreserveFlippedClippedGeometryAndNativeScanlineMasks() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = PresentationOffscreenTestContext.open(WIDTH, HEIGHT)) {
            var graphics = new GraphicsManager();
            try {
                glDisable(GL_DITHER);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, WIDTH, HEIGHT);
                graphics.setProjectionWidth(WIDTH);
                graphics.setProjectionMatrixBuffer(new Matrix4f()
                        .ortho(0, WIDTH, 0, HEIGHT, -1, 1).get(new float[16]));
                var palette = new Palette();
                for (int colour = 1; colour < COLOURS.length; colour++) {
                    int[] rgb = COLOURS[colour];
                    palette.setColor(colour, new Palette.Color((byte) rgb[0], (byte) rgb[1], (byte) rgb[2]));
                    var pattern = new Pattern();
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            pattern.setPixel(x, y, (byte) colour);
                        }
                    }
                    graphics.cachePatternTexture(pattern, FIRST_PATTERN + colour - 1);
                }
                graphics.cachePaletteTexture(palette, 0);
                for (boolean instanced : new boolean[]{false, true}) {
                    graphics.setBatchingEnabled(instanced);
                    graphics.setInstancedBatchingEnabled(instanced);
                    if (instanced) {
                        assertNotNull(graphics.getInstancedShaderProgram(), "Exercise a real instanced renderer");
                    }
                    for (boolean hFlip : new boolean[]{false, true}) {
                        for (boolean vFlip : new boolean[]{false, true}) {
                            for (boolean clipped : new boolean[]{false, true}) {
                                PreparedPiece piece = new PreparedPiece(16, 16, 2, 3,
                                        FIRST_PATTERN, 0x123, 0, hFlip, vFlip, false, false,
                                        SpriteMaskReplayRole.NORMAL,
                                        clipped ? 1 : 0, clipped ? 1 : 2,
                                        clipped ? 1 : 0, clipped ? 2 : 3, "pixel control");
                                byte[] pixels = render(graphics, () -> graphics.submitSpriteSatPiece(piece));
                                for (int row = 0; row < 3; row++) {
                                    for (int col = 0; col < 2; col++) {
                                        int sourceRow = vFlip ? 2 - row : row;
                                        int sourceCol = hFlip ? 1 - col : col;
                                        int expected = clipped && (sourceCol == 0 || sourceRow == 0)
                                                ? 0 : SOURCE_GRID[sourceRow][sourceCol];
                                        assertPixel(pixels, 20 + col * 8, 20 + row * 8, expected);
                                    }
                                }
                                assertPixel(pixels, 15, 20, 0);
                                assertPixel(pixels, 32, 20, 0);
                            }
                        }
                    }
                    byte[] masked = render(graphics, () -> {
                        graphics.requestSpriteMask();
                        graphics.submitSpriteSatControlEntry(108, 20, 1, 1, 0x7C0);
                        graphics.submitSpriteSatControlEntry(100, 20, 1, 1, 0);
                        graphics.submitSpriteSatPiece(new PreparedPiece(100, 16, 1, 2,
                                FIRST_PATTERN, 0x123, 0, false, false, false, false,
                                SpriteMaskReplayRole.NORMAL, 0, 1, 0, 2, "masked pixel control"));
                    });
                    assertPixel(masked, 104, 19, 1);
                    assertPixel(masked, 104, 20, 0);
                    assertPixel(masked, 104, 27, 0);
                    assertPixel(masked, 104, 28, 2);
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.cleanup();
                RenderContext.reset();
            }
        }
    }

    private static byte[] render(GraphicsManager graphics, Runnable submit) {
        return draw(graphics, () -> {
            graphics.beginSpriteSatCollection();
            submit.run();
            graphics.endSpriteSatCollectionAndReplay();
        });
    }

    private static byte[] draw(GraphicsManager graphics, Runnable submit) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(submit, 0, 0, WIDTH, HEIGHT);
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        try {
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.remaining()];
            pixels.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void assertPixel(byte[] pixels, int x, int y, int colour) {
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(COLOURS[colour][channel], pixels[((HEIGHT - 1 - y) * WIDTH + x) * 4 + channel] & 255,
                    "pixel " + x + "," + y + " channel " + channel);
        }
    }
}
