package com.openggf.graphics;

import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Real descriptor textures and both production shaders, without a display connection. */
@EnabledOnOs(OS.LINUX)
class TestCinematicBackgroundPixels {
    @Test void cinematicDescriptorTextureNeverOverwritesNativePhysicalImageEvenOnFailure() throws Exception {
        try (var context = PresentationOffscreenTestContext.open(20, 16);
             var privateResidency = new CinematicBackgroundResidency()) {
            var renderer = new TilemapGpuRenderer();
            var textureField = TilemapGpuRenderer.class.getDeclaredField("backgroundTexture");
            textureField.setAccessible(true);
            var nativeTexture = (TilemapTexture) textureField.get(renderer);
            var foregroundField = TilemapGpuRenderer.class.getDeclaredField("foregroundTexture");
            foregroundField.setAccessible(true);
            var nativeForeground = (TilemapTexture) foregroundField.get(renderer);
            byte[] nativeBytes = new byte[2 * 2 * 4];
            for (int i = 0; i < nativeBytes.length; i++) nativeBytes[i] = (byte) (i * 13);
            nativeTexture.upload(nativeBytes, 2, 2);
            nativeForeground.upload(nativeBytes, 2, 2);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, nativeBytes, 2, 2);
            renderer.setBackgroundTilemapDataPhysical(nativeBytes, 2, 2, 1, 1);
            var nativeState = renderer.captureBackgroundRenderState();
            try {
                assertThrows(IllegalStateException.class, () -> renderer.withCinematicBackground(privateResidency, () -> {
                    byte[] directed = new byte[4 * 4 * 4];
                    java.util.Arrays.fill(directed, (byte) 0x7a);
                    privateResidency.texture.upload(directed, 4, 4);
                    privateResidency.foregroundTexture.upload(directed, 4, 4);
                    renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, directed, 4, 4);
                    assertNotEquals(nativeForeground.getTextureId(), privateResidency.foregroundTexture.getTextureId());
                    assertArrayEquals(nativeBytes, texturePixels(nativeForeground.getTextureId(), nativeBytes.length));
                    renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, directed, 4, 4);
                    assertNotEquals(nativeTexture.getTextureId(), privateResidency.texture.getTextureId());
                    assertArrayEquals(nativeBytes, texturePixels(nativeTexture.getTextureId(), nativeBytes.length));
                    throw new IllegalStateException("abort directed tile");
                }));
                assertSame(nativeTexture, textureField.get(renderer));
                assertSame(nativeForeground, foregroundField.get(renderer));
                assertArrayEquals(nativeBytes, texturePixels(nativeForeground.getTextureId(), nativeBytes.length));
                assertEquals(nativeState, renderer.captureBackgroundRenderState());
                assertArrayEquals(nativeBytes, texturePixels(nativeTexture.getTextureId(), nativeBytes.length));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { renderer.cleanup(); }
        }
    }

    @Test void nonAlignedRasterColumnPhaseMatchesAcrossForegroundAndBackgroundShaders() throws Exception {
        try (var context = PresentationOffscreenTestContext.open(20, 224)) {
            var target = FboHelper.createColorOnly(20, 224, GL_CLAMP_TO_EDGE);
            assertNotNull(target);
            var tilemap = new TilemapGpuRenderer(20);
            int atlas = 0, palette = 0, background = 0, columns = 0;
            ShaderProgram parallax = null;
            QuadRenderer quad = null;
            try {
                glBindFramebuffer(GL_FRAMEBUFFER, target.fboId()); glViewport(0, 0, 20, 224);
                glDisable(GL_BLEND); glDisable(GL_DITHER);
                tilemap.init("shaders/shader_tilemap.glsl");
                int[] descriptors = new int[4 * 3];
                for (int i = 0; i < descriptors.length; i++) descriptors[i] = i / 4;
                tilemap.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                        TilemapGpuRenderer.packWindowDescriptors(descriptors), 4, 3);
                tilemap.setPatternLookupData(new byte[]{0, 0, 0, -1, 0, 1, 0, -1, 0, 2, 0, -1}, 3);
                byte[] atlasBytes = new byte[8 * 24 * 4];
                for (int y = 0; y < 24; y++) for (int x = 0; x < 8; x++) {
                    atlasBytes[(y * 8 + x) * 4] = (byte) (1 + y / 8);
                    atlasBytes[(y * 8 + x) * 4 + 3] = -1;
                }
                atlas = texture(atlasBytes, 8, 24);
                byte[] paletteBytes = new byte[16 * 4 * 4];
                for (int i = 0; i < 64; i++) {
                    paletteBytes[i * 4] = (byte) ((i & 15) * 16);
                    paletteBytes[i * 4 + 3] = -1;
                }
                palette = texture(paletteBytes, 16, 4);
                tilemap.setCinematicColumnPhase(15);
                tilemap.enablePerColumnVScroll(new short[]{0, 8, 16});
                tilemap.render(TilemapGpuRenderer.Layer.FOREGROUND, 20, 224, 0, 0, 20, 224,
                        0, 0, 8, 24, atlas, palette, 0, -1, true, false, false, 224);
                assertColumnPixels("foreground");
                tilemap.setCinematicColumnPhase(0);

                byte[] backgroundBytes = new byte[20 * 256 * 4];
                for (int y = 0; y < 256; y++) for (int x = 0; x < 20; x++) {
                    backgroundBytes[(y * 20 + x) * 4] = (byte) ((1 + (255 - y) / 8) * 16);
                    backgroundBytes[(y * 20 + x) * 4 + 3] = -1;
                }
                background = texture(backgroundBytes, 20, 256);
                columns = glGenTextures();
                glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_1D, columns);
                FloatBuffer values = BufferUtils.createFloatBuffer(3).put(new float[]{0, 8 / 32767f, 16 / 32767f});
                values.flip();
                glTexImage1D(GL_TEXTURE_1D, 0, GL_R32F, 3, 0, GL_RED, GL_FLOAT, values);
                glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                parallax = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, "shaders/shader_parallax_bg.glsl");
                parallax.use();
                int program = parallax.getProgramId();
                for (String sampler : new String[]{"HScrollTexture", "VScrollTexture", "VScrollColumnTexture", "ColumnRemapX", "ColumnRemapY"})
                    glUniform1i(glGetUniformLocation(program, sampler), 1);
                glUniform1i(glGetUniformLocation(program, "BackgroundTexture"), 0);
                glUniform1i(glGetUniformLocation(program, "NoHScroll"), 1);
                glUniform1i(glGetUniformLocation(program, "UsePerColumnVScroll"), 1);
                uniform(program, "ScreenWidth", 20); uniform(program, "ScreenHeight", 224);
                uniform(program, "ActiveDisplayWidth", 20); uniform(program, "ColumnPhaseX", 15);
                uniform(program, "BGTextureWidth", 20); uniform(program, "BGTextureHeight", 256);
                glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, background);
                glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_1D, columns);
                quad = new QuadRenderer(); quad.draw(0, 0, 20, 224);
                assertColumnPixels("background");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                tilemap.cleanup();
                if (parallax != null) parallax.cleanup();
                if (quad != null) quad.cleanup();
                for (int id : new int[]{atlas, palette, background, columns}) if (id != 0) glDeleteTextures(id);
                glBindFramebuffer(GL_FRAMEBUFFER, 0); FboHelper.destroy(target);
            }
        }
    }

    private static void assertColumnPixels(String layer) {
        var pixel = BufferUtils.createByteBuffer(4);
        for (int x = 0; x < 20; x++) {
            pixel.clear(); glReadPixels(x, 223, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertEquals((1 + (x + 15) / 16) * 16, pixel.get(0) & 255, layer + " x=" + x);
        }
    }
    private static void uniform(int program, String name, float value) { glUniform1f(glGetUniformLocation(program, name), value); }
    private static byte[] texturePixels(int id, int count) {
        var bytes = BufferUtils.createByteBuffer(count);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, id);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        byte[] result = new byte[count]; bytes.get(result); return result;
    }
    private static int texture(byte[] bytes, int width, int height) {
        int id = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, id);
        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length).put(bytes); data.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return id;
    }
}
