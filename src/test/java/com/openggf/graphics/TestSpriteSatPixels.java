package com.openggf.graphics;

import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Real production SAT replay/readback on an owned EGL context, with no desktop fallback. */
@Isolated
class TestSpriteSatPixels {
    @Test void nativeCountPixelMaskAndFlipBoundariesReachBothReplayPaths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            var graphics = new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
                var palette = new Palette();
                palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
                palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
                for (int id = 0; id < 108; id++) {
                    var pattern = new Pattern();
                    int color = id >= 100 ? 1 + (id - 100) % 3 : id == 2 ? 2 : 1;
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) color);
                    graphics.cachePatternTexture(pattern, id);
                }
                for (boolean batched : new boolean[]{false, true}) for (boolean markerRewrite : new boolean[]{false, true}) {
                    graphics.setBatchingEnabled(batched); graphics.setInstancedBatchingEnabled(batched);
                    ByteBuffer count = draw(graphics, () -> {
                        if (markerRewrite) graphics.requestSpriteMask();
                        for (int i = 0; i < 20; i++) submit(graphics, 8, 20, 1, 1, 1, false, false, false);
                        submit(graphics, 80, 20, 1, 1, 2, false, false, true);
                        for (int i = 0; i < 19; i++) submit(graphics, 8, 40, 1, 1, 1, false, false, false);
                        submit(graphics, 80, 40, 1, 1, 2, false, false, true);
                        submit(graphics, 8, 60, 1, 1, 1, false, false, false);
                        submit(graphics, 8, 60, 1, 1, 2, false, false, true);
                    });
                    pixel(count, 80, 20, 0); pixel(count, 80, 40, 2);
                    pixel(count, 8, 60, 1); // low tile-priority earlier SAT entry beats later high entry
                    for (boolean h : new boolean[]{false, true}) for (boolean v : new boolean[]{false, true}) {
                        ByteBuffer overflow = draw(graphics, () -> {
                            for (int i = 0; i < 9; i++) submit(graphics, -80, 70, 4, 1, 1, false, false, false);
                            submit(graphics, 8, 70, 3, 1, 1, false, false, false);
                            submit(graphics, 120, 70, 4, 2, 100, h, v, false);
                        });
                        pixel(overflow, 120, 70, 1 + ((h ? 6 : 0) + (v ? 1 : 0)) % 3);
                        pixel(overflow, 128, 70, 0);
                        pixel(overflow, 144, 80, 1 + ((h ? 0 : 6) + (v ? 0 : 1)) % 3);
                    }
                    ByteBuffer carry = draw(graphics, () -> {
                        graphics.submitSpriteSatControlEntry(-128, 108, 1, 1, 1);
                        for (int i = 0; i < 10; i++) submit(graphics, -80, 100, 4, 1, 1, false, false, false);
                        submit(graphics, 220, 108, 1, 1, 2, false, false, true);
                    });
                    pixel(carry, 220, 108, 0); pixel(carry, 220, 109, 2);
                    ByteBuffer masks = draw(graphics, () -> {
                        graphics.requestSpriteMask();
                        graphics.submitSpriteSatControlEntry(0, 132, 1, 1, 0x7C0);
                        graphics.submitSpriteSatControlEntry(0, 132, 1, 1, 0);
                        submit(graphics, 240, 128, 1, 2, 2, false, true, true);
                    });
                    pixel(masks, 240, 130, 1); pixel(masks, 240, 135, 0); pixel(masks, 240, 142, 2);
                    // Fragment group counts once, freezes its producer and clips partial admission.
                    ByteBuffer groups = draw(graphics, () -> {
                        for (int i = 0; i < 19; i++) submit(graphics, 8, 160, 1, 1, 1, false, false, false);
                        graphics.submitSpriteSatGroups(List.of(SpriteSatEntry.of(160, 160, 4, 1, 0, 0,
                                false, false, false, false)), () -> {
                            for (int i = 0; i < 4; i++) submit(graphics, 160 + i * 8, 160, 1, 1, 2, false, false, true);
                        });
                        submit(graphics, 220, 160, 1, 1, 2, false, false, false);
                    });
                    pixel(groups, 184, 160, 2); pixel(groups, 220, 160, 0);
                    SpriteSatInternalAccess.bindLineLimitSource(graphics, () -> false);
                    ByteBuffer unlimited = draw(graphics, () -> {
                        for (int i = 0; i < 80; i++) submit(graphics, 8, 180, 1, 1, 1, false, false, false);
                        submit(graphics, 240, 180, 1, 1, 2, false, false, false);
                    });
                    pixel(unlimited, 240, 180, 2);
                    for (boolean limits : new boolean[]{false, true}) {
                        SpriteSatInternalAccess.bindLineLimitSource(graphics, () -> limits);
                        ByteBuffer ordinaryOffscreen = draw(graphics, () -> {
                            submit(graphics, 8, 192, 1, 1, 1, false, false, false);
                            submit(graphics, -128, 192, 1, 1, 1, false, false, false);
                            submit(graphics, 384, 192, 1, 1, 1, false, false, false);
                            submit(graphics, 80, 192, 1, 1, 2, false, false, false);
                        });
                        pixel(ordinaryOffscreen, 80, 192, 2);
                    }
                    SpriteSatInternalAccess.bindLineLimitSource(graphics, () -> true);
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test void logical350ViewportKeepsAsymmetricEdgePixelsAndWiderTargetSentinelsInBothReplayPaths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var context = PresentationOffscreenTestContext.open(384, 224)) {
            var graphics = new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 350, 224);
                graphics.setProjectionWidth(350);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 350, 0, 224, -1, 1).get(new float[16]));
                graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
                var palette = new Palette();
                palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
                palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
                for (int id = 0; id < 4; id++) graphics.cachePatternTexture(new Pattern(), id);
                for (int id : new int[]{10, 100, 101, 102, 103, 104, 105, 106, 107}) {
                    var pattern = new Pattern();
                    int phase = id == 10 ? 0 : id - 100;
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                        pattern.setPixel(x, y, (byte) (1 + (phase + x + 2 * y) % 3));
                    }
                    graphics.cachePatternTexture(pattern, id);
                }
                for (boolean instanced : new boolean[]{false, true}) {
                    graphics.setBatchingEnabled(instanced); graphics.setInstancedBatchingEnabled(instanced);
                    if (instanced) assertNotNull(graphics.getInstancedShaderProgram(), "exercise the production instanced renderer");
                    for (boolean h : new boolean[]{false, true}) for (boolean v : new boolean[]{false, true}) {
                        ByteBuffer pixels = drawLogical350(graphics, () -> {
                            submit(graphics, 344, 20, 1, 1, 10, h, v, false);
                            submit(graphics, 350, 40, 1, 1, 10, h, v, false);
                            for (int i = 0; i < 9; i++) submit(graphics, i % 2 == 0 ? 350 : 8, 60, 4, 1, 0, false, false, false);
                            submit(graphics, 350, 60, 3, 1, 0, false, false, false); // 312 pixels, including on-screen transparent art.
                            submit(graphics, 328, 60, 4, 2, 100, h, v, false);
                            graphics.submitSpriteSatGroups(List.of(SpriteSatEntry.of(344, 100, 1, 1, 10, 0,
                                    false, false, false, false)), () -> {
                                graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(336, 100, 2, 2,
                                        100, 100, 0, h, v, false, false, SpriteMaskReplayRole.NORMAL,
                                        h ? 0 : 1, 1, v ? 1 : 0, 1, "partial edge fragment"));
                                ObjectVisualGraphicsAccess.clipLastSatTile(graphics, 2, 7);
                            });
                            for (int i = 0; i < 20; i++) submit(graphics, 350, 140, 1, 1, 0, false, false, false);
                            submit(graphics, 344, 140, 1, 1, 10, h, v, true); // Count overflow cannot borrow the wider presentation.
                            for (int i = 0; i < 10; i++) submit(graphics, 350, 152, 4, 1, 0, false, false, false);
                            graphics.submitSpriteSatControlEntry(-128, 160, 1, 1, 0);
                            submit(graphics, 344, 160, 1, 1, 10, h, v, false);
                            graphics.requestSpriteMask();
                            graphics.submitSpriteSatControlEntry(0, 180, 1, 1, 0x7C0);
                            graphics.submitSpriteSatControlEntry(0, 180, 1, 1, 0);
                            submit(graphics, 344, 180, 1, 1, 10, h, v, false);
                        });
                        for (int y = 20; y < 28; y++) {
                            logicalPixel(pixels, 343, y, 0);
                            for (int x = 344; x < 350; x++) {
                                int sx = h ? 7 - (x - 344) : x - 344, sy = v ? 7 - (y - 20) : y - 20;
                                logicalPixel(pixels, x, y, 1 + (sx + 2 * sy) % 3);
                            }
                        }
                        for (int y = 60; y < 76; y++) for (int x = 328; x < 350; x++) {
                            if (y < 68 && x >= 336) { logicalPixel(pixels, x, y, 0); continue; }
                            int screenCol = (x - 328) / 8, screenRow = (y - 60) / 8;
                            int sourceCol = h ? 3 - screenCol : screenCol, sourceRow = v ? 1 - screenRow : screenRow;
                            int sx = h ? 7 - (x - 328) % 8 : (x - 328) % 8;
                            int sy = v ? 7 - (y - 60) % 8 : (y - 60) % 8;
                            logicalPixel(pixels, x, y, 1 + (sourceCol * 2 + sourceRow + sx + 2 * sy) % 3);
                        }
                        for (int y = 100; y < 108; y++) for (int x = 344; x < 350; x++) {
                            if (y < 102 || y >= 107) { logicalPixel(pixels, x, y, 0); continue; }
                            int sx = h ? 7 - (x - 344) : x - 344, sy = v ? 7 - (y - 100) : y - 100;
                            int sourceCol = h ? 0 : 1, sourceRow = v ? 1 : 0;
                            logicalPixel(pixels, x, y, 1 + (sourceCol * 2 + sourceRow + sx + 2 * sy) % 3);
                        }
                        for (int x = 344; x < 350; x++) {
                            logicalPixel(pixels, x, 40, 0); logicalPixel(pixels, x, 140, 0);
                            logicalPixel(pixels, x, 160, 0); logicalPixel(pixels, x, 180, 0);
                            int sx = h ? 7 - (x - 344) : x - 344, sy = v ? 6 : 1;
                            logicalPixel(pixels, x, 161, 1 + (sx + 2 * sy) % 3);
                        }
                        for (int y = 0; y < 224; y++) for (int x = 350; x < 384; x++) logicalPixel(pixels, x, y, 4);
                        assertEquals(GL_NO_ERROR, glGetError());
                    }
                }
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    private static ByteBuffer drawLogical350(GraphicsManager graphics, Runnable producer) {
        glViewport(0, 0, 350, 224); glDisable(GL_DITHER); glDisable(GL_SCISSOR_TEST);
        glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT); // Sentinel covers the wider 384px backing target.
        glEnable(GL_SCISSOR_TEST); glScissor(0, 0, 350, 224);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
        graphics.executeCapturedCommands(() -> {
            graphics.beginLogicalSpriteSatCollection(SpriteSatMaskPostProcessor.LogicalViewport.h40(0, 0, 350, 224));
            producer.run(); graphics.endSpriteSatCollectionAndReplay();
        }, 0, 0, 350, 224);
        glFinish(); var pixels = BufferUtils.createByteBuffer(384 * 224 * 4);
        glReadPixels(0, 0, 384, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels); return pixels;
    }

    private static void logicalPixel(ByteBuffer pixels, int x, int y, int color) {
        int offset = ((223 - y) * 384 + x) * 4;
        for (int channel = 0; channel < 3; channel++) {
            int expected = color == 4 ? (channel == 1 ? 0 : 255) : color == channel + 1 ? 255 : 0;
            assertEquals(expected, pixels.get(offset + channel) & 255,
                    "350px viewport pixel " + x + "," + y + " color " + color + " channel " + channel);
        }
        assertEquals(255, pixels.get(offset + 3) & 255, "opaque target at " + x + "," + y);
    }

    private static ByteBuffer draw(GraphicsManager graphics, Runnable producer) {
        glViewport(0, 0, 320, 224); glDisable(GL_DITHER);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(() -> {
            graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
            producer.run(); graphics.endSpriteSatCollectionAndReplay();
        }, 0, 0, 320, 224);
        glFinish(); var pixels = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels); return pixels;
    }

    private static void submit(GraphicsManager graphics, int x, int y, int w, int h, int id,
                               boolean hf, boolean vf, boolean high) {
        graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(x, y, w, h, id, id, 0,
                hf, vf, high, false, SpriteMaskReplayRole.NORMAL, 0, w, 0, h, null));
    }

    private static void pixel(ByteBuffer pixels, int x, int y, int color) {
        int offset = ((223 - y) * 320 + x) * 4;
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(color == channel + 1 ? 255 : 0, pixels.get(offset + channel) & 255,
                    "pixel " + x + "," + y + " color " + color + " channel " + channel);
        }
    }
}
