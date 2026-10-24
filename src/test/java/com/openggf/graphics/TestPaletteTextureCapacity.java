package com.openggf.graphics;

import com.openggf.game.GameId;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30.GL_R8;

/** Real palette uploads and production draws across ordinary bank reclamation. */
@Isolated
@EnabledOnOs(OS.LINUX)
class TestPaletteTextureCapacity {
    private static final int PATTERN = PatternAtlasRange.OBJECTS.base();
    private static final int[] NORMAL = {0xEE2222, 0x22EE22, 0x2222EE, 0xEEEE22,
            0xEE22EE, 0x22EEEE, 0x884422, 0x4488CC};
    private static final int[] WATER = {0xCC4422, 0x22CC44, 0x4422CC, 0xCCCC44,
            0xCC44CC, 0x44CCCC, 0x664422, 0x2266AA};

    enum Path {
        DIRECT, BATCHED, INSTANCED, WATER, INSTANCED_WATER, PRIORITY, INSTANCED_PRIORITY, TILEMAP, CNZ_SLOTS, S3K_SLOTS
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void reclaimedSidekickBanksKeepNormalAndUnderwaterRowsAtTheirLiteralColors(Path path) throws Exception {
        RenderContext.reset();
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            GraphicsManager graphics = openGraphics();
            try {
                allocateSidekicks(6);
                upload(graphics);
                assertEquals(28, textureHeight(graphics.getCombinedPaletteTextureId()));
                assertEquals(28, textureHeight(graphics.getUnderwaterPaletteTextureId()));
                assertRows(graphics, path, "before reclamation");

                RenderContext.clearSidekickContexts();
                assertEquals(4, RenderContext.getTotalPaletteLines());
                allocateSidekicks(1);
                assertEquals(8, RenderContext.getTotalPaletteLines());
                upload(graphics);
                assertRows(graphics, path, "after rebuilding fewer sidekicks");
                graphics.clearPaletteTextures();
                assertNull(graphics.getCombinedPaletteTextureId());
                assertNull(graphics.getUnderwaterPaletteTextureId());
                upload(graphics);
                assertEquals(8, textureHeight(graphics.getCombinedPaletteTextureId()));
                assertEquals(8, textureHeight(graphics.getUnderwaterPaletteTextureId()));
                assertRows(graphics, path, "after texture lifetime reset");
            } finally {
                graphics.cleanup();
            }
        } finally {
            RenderContext.reset();
        }
    }

    @Test
    void independentHostsKeepTheirOwnRowsWhenTheSharedRegistryGrowsAndShrinks() throws Exception {
        RenderContext.reset();
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            GraphicsManager first = openGraphics();
            GraphicsManager second = null;
            try {
                allocateSidekicks(1);
                upload(first);
                assertRows(first, Path.PRIORITY, "first host at eight rows");
                second = openGraphics();
                allocateSidekicks(5);
                upload(second);
                assertEquals(8, textureHeight(first.getCombinedPaletteTextureId()));
                assertEquals(28, textureHeight(second.getCombinedPaletteTextureId()));
                assertRows(first, Path.PRIORITY, "first host after another host grows the registry");
                assertRows(second, Path.PRIORITY, "second host before reclamation");
                RenderContext.clearSidekickContexts();
                allocateSidekicks(1);
                upload(second);
                assertRows(first, Path.PRIORITY, "first host after reclamation");
                assertRows(second, Path.PRIORITY, "second host after reclamation");
            } finally {
                if (second != null) second.cleanup();
                first.cleanup();
            }
        } finally {
            RenderContext.reset();
        }
    }

    private static GraphicsManager openGraphics() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        try {
            graphics.init("shaders/shader_the_hedgehog.glsl");
            graphics.setBatchingEnabled(false);
            graphics.setViewport(0, 0, 320, 224);
            graphics.setProjectionWidth(320);
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
            graphics.setWindowHeight(224);
            graphics.setScreenHeight(224);
            graphics.setWaterlineScreenY(100);
            graphics.setCurrentSpriteHighPriority(true);
            // Both water programs keep their production split and disable only ripple displacement.
            for (WaterShaderProgram water : new WaterShaderProgram[]{graphics.getWaterShaderProgram(),
                    graphics.getInstancedWaterShaderProgram()}) {
                water.use();
                water.setWaterlineScreenY(100);
                water.setScreenDimensions(320, 224);
                water.setWindowHeight(224);
                water.setIndexedTextureWidth(graphics.getPatternAtlasWidth());
                water.setDistortionAmplitude(0);
                water.stop();
            }
            glDisable(GL_DITHER);
            Pattern pattern = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) 1);
            graphics.cachePatternTexture(pattern, PATTERN);
            return graphics;
        } catch (Exception | Error failure) {
            graphics.cleanup();
            throw failure;
        }
    }

    private static void allocateSidekicks(int count) {
        for (int i = 0; i < count; i++) {
            RenderContext sidekick = RenderContext.createSidekickContext(GameId.S2);
            for (int line = 0; line < 4; line++) {
                sidekick.setPalette(line, palette(NORMAL[4 + line]));
                sidekick.setUnderwaterPalette(line, palette(WATER[4 + line]));
            }
        }
    }

    private static void upload(GraphicsManager graphics) {
        for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette(NORMAL[line]), line);
        RenderContext.uploadDonorPalettes(graphics);
        Palette[] water = new Palette[4];
        for (int line = 0; line < 4; line++) water[line] = palette(WATER[line]);
        graphics.cacheUnderwaterPaletteTexture(water, palette(NORMAL[0]));
    }

    private static Palette palette(int rgb) {
        Palette palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) (rgb >>> 16), (byte) (rgb >>> 8), (byte) rgb));
        return palette;
    }

    private static int textureHeight(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        return glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
    }

    private static void assertRows(GraphicsManager graphics, Path path, String phase) throws Exception {
        if (path == Path.TILEMAP) {
            assertTileRows(graphics);
            return;
        }
        if (path == Path.CNZ_SLOTS || path == Path.S3K_SLOTS) {
            assertSlotRows(graphics, path);
            return;
        }
        boolean priority = path == Path.PRIORITY || path == Path.INSTANCED_PRIORITY;
        boolean water = path == Path.WATER || path == Path.INSTANCED_WATER;
        boolean batched = path != Path.DIRECT && path != Path.WATER && path != Path.PRIORITY;
        graphics.setUseSpritePriorityShader(priority);
        graphics.setUseWaterShader(water);
        graphics.setBatchingEnabled(batched);
        graphics.setInstancedBatchingEnabled(path != Path.BATCHED);
        graphics.setWaterEnabled(true);
        glViewport(0, 0, 320, 224);
        glClearColor(3 / 255f, 5 / 255f, 7 / 255f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        for (int y : new int[]{40, 140}) {
            // The legacy batcher reads this flag at execution, so execute each binding separately.
            graphics.setUseUnderwaterPaletteForBackground(!priority && !water && y == 140);
            graphics.executeCapturedCommands(() -> {
                graphics.beginPatternBatch();
                for (int line = 0; line < 8; line++) {
                    PatternDesc desc = new PatternDesc();
                    desc.setPaletteIndex(line);
                    desc.setPriority(true);
                    graphics.renderPatternWithId(PATTERN, desc, 20 + line * 16, y);
                }
                graphics.flushPatternBatch();
            }, 0, 0, 320, 224);
        }
        graphics.setUseUnderwaterPaletteForBackground(false);
        glFinish();
        assertAll(phase,
                () -> assertPixels(40, NORMAL),
                () -> assertPixels(140, WATER));
        assertEquals(GL_NO_ERROR, glGetError(), phase);
    }

    private static void assertPixels(int y, int[] expected) {
        for (int line = 0; line < 8; line++) {
            assertPixel(22 + line * 16, y + 3, expected[line], "row " + line);
        }
    }

    private static void assertPixel(int x, int y, int expected, String message) {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        glReadPixels(x, 223 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        int rgb = (Byte.toUnsignedInt(pixel.get(0)) << 16) | (Byte.toUnsignedInt(pixel.get(1)) << 8)
                | Byte.toUnsignedInt(pixel.get(2));
        assertEquals(String.format("%06X", expected), String.format("%06X", rgb), message + " at y=" + y);
    }

    private static int indexedTexture(int width, int height) {
        glActiveTexture(GL_TEXTURE0);
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height);
        while (pixels.hasRemaining()) pixels.put((byte) 1);
        pixels.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static void assertSlotRows(GraphicsManager graphics, Path path) throws Exception {
        int faces = indexedTexture(32, 256);
        ShaderProgram shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER,
                path == Path.CNZ_SLOTS ? "shaders/shader_cnz_slots.glsl" : "shaders/shader_s3k_slots.glsl");
        SlotWindowGpuPass pass = new SlotWindowGpuPass();
        try {
            pass.setShader(shader);
            pass.init();
            glViewport(0, 0, 320, 224);
            glDisable(GL_BLEND);
            glEnable(GL_DEPTH_TEST);
            for (boolean water : new boolean[]{false, true}) for (int row = 0; row < 8; row++) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                pass.draw(faces, water ? graphics.getUnderwaterPaletteTextureId() : graphics.getCombinedPaletteTextureId(),
                        20, 40, row, 0, 1, 2, 3, 4, 5, 0, 0, 0);
                assertFalse(glIsEnabled(GL_BLEND));
                assertTrue(glIsEnabled(GL_DEPTH_TEST));
                assertEquals(GL_TEXTURE0, glGetInteger(GL_ACTIVE_TEXTURE));
                assertEquals(0, glGetInteger(GL_CURRENT_PROGRAM));
                assertPixel(22, 43, (water ? WATER : NORMAL)[row], path + " row " + row);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glDisable(GL_DEPTH_TEST);
            pass.cleanup();
            shader.cleanup();
            glDeleteTextures(faces);
        }
    }

    private static void assertTileRows(GraphicsManager graphics) {
        int atlas = indexedTexture(8, 8);
        try {
            TilemapGpuRenderer tiles = graphics.getTilemapGpuRenderer();
            tiles.setPatternLookupData(new byte[]{0, 0, 0, -1}, 1);
            glViewport(0, 0, 320, 224);
            glDisable(GL_BLEND);
            for (int row = 0; row < 4; row++) {
                tiles.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, new byte[]{0, (byte) (row << 3), 0, -1}, 1, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                tiles.render(TilemapGpuRenderer.Layer.FOREGROUND, 320, 224, 0, 0, 320, 224,
                        0, 0, 8, 8, atlas, graphics.getCombinedPaletteTextureId(), graphics.getUnderwaterPaletteTextureId(),
                        -1, true, false, true, 100);
                final int line = row;
                assertAll(() -> assertPixel(2, 3, NORMAL[line], "normal tile row " + line),
                        () -> assertPixel(2, 143, WATER[line], "underwater tile row " + line));
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glActiveTexture(GL_TEXTURE0);
            glDeleteTextures(atlas);
        }
    }
}
