package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.graphics.*;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.level.render.BackgroundRendererInternalAccess;
import com.openggf.util.IntIndexedView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Real tile/compositor/mask/sprite shader pixels in an owned display-free EGL context. */
class TestBackgroundPlaneSpriteOcclusion {
    @Test
    @EnabledOnOs(OS.LINUX)
    void eitherRearPlaneMasksOnlyItsOpaqueHighTilesWithMatchingScroll() throws Exception {
        var config = GameServices.configuration();
        Object oldWidth = config.getConfigValue(SonicConfiguration.SCREEN_WIDTH);
        config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, 320);
        try (var context = SurfacelessEglContext.create(320, 224, true)) {
            GL.createCapabilities();
            glDisable(GL_DITHER);
            glDisable(GL_BLEND);
            glViewport(0, 0, 320, 224);
            GraphicsManager graphics = mock(GraphicsManager.class);
            when(graphics.getViewportWidth()).thenReturn(320);
            when(graphics.getViewportHeight()).thenReturn(224);
            var background = new BackgroundRenderer(graphics);
            var tiles = new TilemapGpuRenderer();
            var mask = new TilePriorityFBO();
            var sprite = new SpritePriorityShaderProgram("shaders/shader_sprite_priority.glsl");
            int atlas = 0, palette = 0, spriteTexture = 0, vao = 0, vbo = 0;
            try {
                background.init("shaders/shader_parallax_bg.glsl");
                background.ensureCapacity(512, 272);
                tiles.init("shaders/shader_tilemap.glsl");
                mask.init(320, 224);
                sprite.cacheUniformLocations();
                byte[] atlasBytes = new byte[24 * 8 * 4];
                for (int y = 0; y < 8; y++) for (int x = 0; x < 24; x++) {
                    atlasBytes[(y * 24 + x) * 4] = (byte) (x < 8 ? 0 : x < 16 ? (x % 2 == 0 ? 1 : 0) : 2);
                }
                atlas = texture(atlasBytes, 24, 8);
                byte[] colors = new byte[16 * 4 * 4];
                for (int line = 0; line < 4; line++) {
                    colors[(line * 16 + 1) * 4 + 1] = -1;
                    colors[(line * 16 + 2) * 4] = -1;
                    colors[(line * 16 + 3) * 4 + 2] = -1;
                    for (int i = 0; i < 16; i++) colors[(line * 16 + i) * 4 + 3] = -1;
                }
                palette = texture(colors, 16, 4);
                spriteTexture = texture(new byte[]{3, 0, 0, -1}, 1, 1);
                tiles.setPatternLookupData(new byte[]{0, 0, 0, -1, 1, 0, 0, -1, 2, 0, 0, -1}, 3);
                vao = glGenVertexArrays(); vbo = glGenBuffers();
                glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glBufferData(GL_ARRAY_BUFFER, new float[]{-1,-1,0,0,0, 1,-1,0,0,0,
                        -1,1,0,0,0, 1,1,0,0,0}, GL_STATIC_DRAW);
                glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 20, 0);
                glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 8);
                glEnableVertexAttribArray(2); glVertexAttribPointer(2, 1, GL_FLOAT, false, 20, 16);
                glBindVertexArray(0);
                for (var source : TilemapGpuRenderer.Layer.values()) {
                    int[] descriptors = new int[64 * 34];
                    for (int row = 0; row < 34; row++) for (int x = 0; x < 64; x++) {
                        descriptors[row * 64 + x] = ((x + row) % 3 == 0) ? 0xE001 : 2;
                    }
                    tiles.setTilemapData(source, packDescriptors(descriptors), 64, 34);
                    for (boolean perLineTilePass : new boolean[]{false, true}) {
                        for (boolean initialBlending : new boolean[]{false, true}) {
                            int[] scroll = new int[224];
                            for (int y = 0; y < 224; y++) scroll[y] = y < 112 ? 7 : 21;
                            IntIndexedView view = new IntIndexedView() {
                                @Override public int size() { return scroll.length; }
                                @Override public int get(int index) { return scroll[index]; }
                            };
                            if (initialBlending) glEnable(GL_BLEND); else glDisable(GL_BLEND);
                            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                            background.beginTilePass(512, 272, true);
                            assertFalse(glIsEnabled(GL_BLEND), "descriptor alpha must be stored without blending");
                            if (perLineTilePass) {
                                background.uploadHScroll(view);
                                tiles.enablePerLineScroll(background.getHScrollTextureId(), 224, 0, 0, 5);
                            }
                            tiles.render(source, 512, 272, 0, 0, 512, 272, 0, 0,
                                    24, 8, atlas, palette, palette, -2, true, false, false, 224);
                            background.endTilePass();
                            assertEquals(initialBlending, glIsEnabled(GL_BLEND), "the tile pass restores its caller's blend state");
                            glDisable(GL_BLEND);
                            glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
                            BackgroundRendererInternalAccess.renderWithPriorityMetadata(background,
                                    view, null, null, 0, 0, 5, perLineTilePass);
                            ByteBuffer visible = pixels();
                            Arrays.fill(scroll, 99); // delayed mask must retain the color pass's captured scroll
                            mask.begin();
                            when(graphics.getViewportWidth()).thenReturn(960);
                            when(graphics.getViewportHeight()).thenReturn(672);
                            BackgroundRendererInternalAccess.renderPriorityMask(background);
                            ByteBuffer priority = pixels();
                            mask.end();
                            when(graphics.getViewportWidth()).thenReturn(320);
                            when(graphics.getViewportHeight()).thenReturn(224);
                            int highPixels = 0, holes = 0;
                            for (int i = 0; i < 320 * 224; i++) {
                                boolean highTile = (visible.get(i * 4 + 1) & 255) == 255;
                                assertEquals(highTile ? 255 : 0, priority.get(i * 4) & 255,
                                        "mask must match sampled high tile color for " + source + "/" + perLineTilePass);
                                if (highTile) {
                                    assertEquals(255, priority.get(i * 4 + 1) & 255, "palette line 3 must survive replay");
                                    highPixels++;
                                } else if ((visible.get(i * 4) & 255) == 0) holes++;
                                assertEquals(255, visible.get(i * 4 + 3) & 255, "metadata must not become visible translucency");
                            }
                            assertTrue(highPixels > 1000); assertTrue(holes > 1000);
                            drawSprite(sprite, palette, spriteTexture, mask.getTextureId(), vao, false);
                            ByteBuffer lowSprite = pixels();
                            for (int i = 0; i < 320 * 224; i++) {
                                boolean highTile = (visible.get(i * 4 + 1) & 255) == 255;
                                assertEquals(highTile ? 0 : 255, lowSprite.get(i * 4 + 2) & 255,
                                        "low sprite covers only low/transparent background pixels");
                            }
                            drawSprite(sprite, palette, spriteTexture, mask.getTextureId(), vao, true);
                            ByteBuffer highSprite = pixels();
                            for (int i = 0; i < 320 * 224; i++) assertEquals(255, highSprite.get(i * 4 + 2) & 255);
                            assertEquals(GL_NO_ERROR, glGetError());
                        }
                    }
                }
            } finally {
                background.cleanup(); tiles.cleanup(); mask.cleanup(); sprite.cleanup();
                if (atlas != 0) glDeleteTextures(atlas);
                if (palette != 0) glDeleteTextures(palette);
                if (spriteTexture != 0) glDeleteTextures(spriteTexture);
                if (vbo != 0) glDeleteBuffers(vbo);
                if (vao != 0) glDeleteVertexArrays(vao);
            }
        } finally {
            GL.setCapabilities(null);
            config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, oldWidth);
        }
    }

    private static void drawSprite(SpritePriorityShaderProgram shader, int palette, int image, int mask,
            int vao, boolean high) {
        shader.use();
        int program = shader.getProgramId();
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjectionMatrix"), false,
                new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1});
        glUniform1i(glGetUniformLocation(program, "Palette"), 0);
        glUniform1i(glGetUniformLocation(program, "IndexedColorTexture"), 1);
        glUniform1i(glGetUniformLocation(program, "UnderwaterPalette"), 0);
        shader.setTilePriorityTexture(2); shader.setSpriteHighPriority(high);
        shader.setScreenSize(320, 224); shader.setPaletteLine(0); shader.setTotalPaletteLines(4);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, palette);
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, image);
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, mask);
        glBindVertexArray(vao); glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); glBindVertexArray(0);
        shader.stop(); glActiveTexture(GL_TEXTURE0);
    }

    private static ByteBuffer pixels() {
        glFinish(); ByteBuffer result = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, result); return result;
    }

    private static byte[] packDescriptors(int[] descriptors) throws Exception {
        Method method = TilemapGpuRenderer.class.getDeclaredMethod("packWindowDescriptors", int[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(null, (Object) descriptors);
    }

    private static int texture(byte[] bytes, int width, int height) {
        int texture = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length); buffer.put(bytes).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }
}
