package com.openggf.game.titlecard;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestTitleCardSpriteRenderer {
    private static final int HEIGHT = 224;

    @Test
    void asymmetricPiecesKeepColumnMajorSubmissionsAndDescriptorBitsForEveryFlip() {
        var graphics = new RecordingGraphics();
        graphics.initHeadless();
        for (boolean hFlip : new boolean[]{false, true}) {
            for (boolean vFlip : new boolean[]{false, true}) {
                graphics.submissions.clear();
                var piece = new TitleCardMappings.SpritePiece(-5, 7, 2, 3, 0x583,
                        hFlip, vFlip, 6, true);
                TitleCardSpriteRenderer.renderSpritePiece(graphics, piece, 100, 40, 0x580, 0x40800, 9);
                int[] x = hFlip ? new int[]{103, 103, 103, 95, 95, 95}
                        : new int[]{95, 95, 95, 103, 103, 103};
                int[] y = vFlip ? new int[]{63, 55, 47, 63, 55, 47}
                        : new int[]{47, 55, 63, 47, 55, 63};
                int flags = 0x4000 | (hFlip ? 0x800 : 0) | (vFlip ? 0x1000 : 0);
                assertEquals(List.of(
                        new Submission(0x40803, flags | 3, x[0], y[0]),
                        new Submission(0x40804, flags | 4, x[1], y[1]),
                        new Submission(0x40805, flags | 5, x[2], y[2]),
                        new Submission(0x40806, flags | 6, x[3], y[3]),
                        new Submission(0x40807, flags | 7, x[4], y[4]),
                        new Submission(0x40808, flags | 8, x[5], y[5])), graphics.submissions);
            }
        }
    }

    @Test
    void negativeBaseClampsBeforeExpansionAndBoundsOmitOnlyUnavailableTiles() {
        var graphics = new RecordingGraphics();
        graphics.initHeadless();
        var piece = new TitleCardMappings.SpritePiece(-4, -8, 2, 3, 0x4FF,
                true, true, 5, false);
        TitleCardSpriteRenderer.renderSpritePiece(graphics, piece, 20, 30, 0x500, 0x40000, 4);
        assertEquals(List.of(
                new Submission(0x40000, 0x3800, 24, 38),
                new Submission(0x40001, 0x3801, 24, 30),
                new Submission(0x40002, 0x3802, 24, 22),
                new Submission(0x40003, 0x3803, 16, 38)), graphics.submissions);
        graphics.submissions.clear();
        TitleCardSpriteRenderer.renderSpritePiece(graphics, piece, 20, 30, 0x500, 0x40000, 0);
        TitleCardSpriteRenderer.renderSpritePiece(graphics,
                new TitleCardMappings.SpritePiece(0, 0, 2, 3, 0x504, false, false, 0, false),
                20, 30, 0x500, 0x40000, 4);
        assertTrue(graphics.submissions.isEmpty(), "empty and wholly out-of-range banks submit nothing");
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 424})
    void productionRendererDrawsAsymmetricArtFlipsPaletteAndOverlappingPieces(int width) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = PresentationOffscreenTestContext.open(width, HEIGHT)) {
            var graphics = new GraphicsManager();
            try {
                glDisable(GL_DITHER);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, width, HEIGHT);
                graphics.setProjectionWidth(width);
                graphics.setProjectionMatrixBuffer(new Matrix4f()
                        .ortho(0, width, 0, HEIGHT, -1, 1).get(new float[16]));
                var palette = new Palette();
                for (int i = 1; i <= 7; i++) {
                    palette.setColor(i, new Palette.Color((byte) (i * 32), (byte) 0, (byte) 0));
                }
                graphics.cachePaletteTexture(palette, 2);
                for (int tile = 0; tile < 6; tile++) {
                    var art = new Pattern();
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            art.setPixel(x, y, (byte) (x < 4 && y < 4 ? 7 : tile + 1));
                        }
                    }
                    graphics.cachePatternTexture(art, 0x40800 + tile);
                }
                for (boolean batched : new boolean[]{false, true}) {
                    graphics.setBatchingEnabled(batched);
                    graphics.setInstancedBatchingEnabled(batched);
                    if (batched) assertNotNull(graphics.getInstancedShaderProgram());
                    for (boolean hFlip : new boolean[]{false, true}) {
                        for (boolean vFlip : new boolean[]{false, true}) {
                            byte[] pixels = render(graphics, width, () -> {
                                graphics.beginPatternBatch();
                                TitleCardSpriteRenderer.renderSpritePiece(graphics,
                                        new TitleCardMappings.SpritePiece(-4, 3, 2, 3, 0x500,
                                                hFlip, vFlip, 2, true),
                                        20, 13, 0x500, 0x40800, 6);
                                graphics.flushPatternBatch();
                            });
                            int[][] grid = {{1, 4}, {2, 5}, {3, 6}};
                            for (int row = 0; row < 3; row++) {
                                for (int col = 0; col < 2; col++) {
                                    int colour = grid[vFlip ? 2 - row : row][hFlip ? 1 - col : col];
                                    assertPixel(pixels, width, 16 + col * 8 + (hFlip ? 1 : 6),
                                            16 + row * 8 + (vFlip ? 1 : 6), colour);
                                    assertPixel(pixels, width, 16 + col * 8 + (hFlip ? 6 : 1),
                                            16 + row * 8 + (vFlip ? 6 : 1), 7);
                                }
                            }
                            assertPixel(pixels, width, 15, 20, 0);
                            assertPixel(pixels, width, 32, 20, 0);
                        }
                    }
                    byte[] overlap = render(graphics, width, () -> {
                        graphics.beginPatternBatch();
                        for (int tile : new int[]{0x500, 0x505}) {
                            TitleCardSpriteRenderer.renderSpritePiece(graphics,
                                    new TitleCardMappings.SpritePiece(0, 0, 1, 1, tile,
                                            false, false, 2, false),
                                    16, 16, 0x500, 0x40800, 6);
                        }
                        graphics.flushPatternBatch();
                    });
                    assertPixel(overlap, width, 22, 22, 6);
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.cleanup();
                RenderContext.reset();
            }
        }
    }

    private static byte[] render(GraphicsManager graphics, int width, Runnable submit) {
        glViewport(0, 0, width, HEIGHT);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(submit, 0, 0, width, HEIGHT);
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(width * HEIGHT * 4);
        try {
            glReadPixels(0, 0, width, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.remaining()];
            pixels.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void assertPixel(byte[] pixels, int width, int x, int y, int colour) {
        int offset = ((HEIGHT - 1 - y) * width + x) * 4;
        assertEquals(colour * 32, pixels[offset] & 255, "red at " + x + "," + y);
        assertEquals(0, pixels[offset + 1] & 255);
        assertEquals(0, pixels[offset + 2] & 255);
        assertEquals(255, pixels[offset + 3] & 255);
    }

    record Submission(int patternId, int descriptor, int x, int y) { }

    static final class RecordingGraphics extends GraphicsManager {
        final List<Submission> submissions = new ArrayList<>();

        @Override
        public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {
            submissions.add(new Submission(patternId, desc.get(), x, y));
            super.renderPatternWithId(patternId, desc, x, y);
        }
    }
}
