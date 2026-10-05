package com.openggf.mods.scene;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import com.openggf.graphics.ShaderProgram;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Submits a scene's recorded {@link SceneDrawOp}s with one small shader, batching
 * consecutive quads that share a texture and clip. Images become GL textures on first use
 * and are deleted by {@link #release()} when the scene closes. Engine-internal.
 */
final class SceneRenderer {
    private static final int FLOATS_PER_VERTEX = 12;
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * 6;
    private static final int MAX_QUADS = 2048;

    private ShaderProgram shader;
    private int projectionLocation;
    private int textureLocation;
    private int vao;
    private int vbo;
    private FloatBuffer vertices;
    private int whiteTexture;
    private final Map<SceneImage, Integer> textures = new IdentityHashMap<>();

    void init() throws IOException {
        shader = new ShaderProgram("shaders/shader_scene.vert", "shaders/shader_scene.frag");
        projectionLocation = glGetUniformLocation(shader.getProgramId(), "ProjectionMatrix");
        textureLocation = glGetUniformLocation(shader.getProgramId(), "Texture");
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_QUADS * FLOATS_PER_QUAD * Float.BYTES, GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        vertices = MemoryUtil.memAllocFloat(MAX_QUADS * FLOATS_PER_QUAD);
        whiteTexture = upload(1, 1, new int[] {0xFFFFFFFF});
    }

    boolean initialized() {
        return shader != null;
    }

    /**
     * Draws {@code ops}. {@code logicalWidth/Height} is the canvas size; {@code viewport}
     * is the framebuffer rectangle {x, y, w, h} the logical screen is scaled into.
     */
    void render(List<SceneDrawOp> ops, float[] projection, int logicalWidth, int logicalHeight, int[] viewport) {
        if (ops.isEmpty()) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
        glUniformMatrix4fv(projectionLocation, false, projection);
        glUniform1i(textureLocation, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        int batchTexture = -1;
        int[] batchClip = null;
        int quads = 0;
        vertices.clear();
        for (SceneDrawOp op : ops) {
            int texture = op.image() == null ? whiteTexture : texture(op.image());
            if (quads > 0 && (texture != batchTexture || op.clip() != batchClip || quads == MAX_QUADS)) {
                flush(quads);
                quads = 0;
                vertices.clear();
            }
            if (quads == 0) {
                batchTexture = texture;
                batchClip = op.clip();
                glBindTexture(GL_TEXTURE_2D, texture);
                applyClip(batchClip, logicalWidth, logicalHeight, viewport);
            }
            putQuad(op, logicalHeight);
            quads++;
        }
        if (quads > 0) {
            flush(quads);
        }
        glDisable(GL_SCISSOR_TEST);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        shader.stop();
    }

    private void flush(int quads) {
        vertices.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, quads * 6);
    }

    private static void applyClip(int[] clip, int logicalWidth, int logicalHeight, int[] viewport) {
        if (clip == null) {
            glDisable(GL_SCISSOR_TEST);
            return;
        }
        float sx = viewport[2] / (float) logicalWidth;
        float sy = viewport[3] / (float) logicalHeight;
        int x = viewport[0] + Math.round(clip[0] * sx);
        int y = viewport[1] + Math.round((logicalHeight - clip[1] - clip[3]) * sy);
        glEnable(GL_SCISSOR_TEST);
        glScissor(x, y, Math.max(0, Math.round(clip[2] * sx)), Math.max(0, Math.round(clip[3] * sy)));
    }

    private void putQuad(SceneDrawOp op, int logicalHeight) {
        float u0;
        float v0;
        float u1;
        float v1;
        if (op.image() == null) {
            u0 = 0;
            v0 = 0;
            u1 = 1;
            v1 = 1;
        } else {
            float w = op.image().width();
            float h = op.image().height();
            u0 = op.u0() / w;
            u1 = op.u1() / w;
            v0 = op.v0() / h;
            v1 = op.v1() / h;
        }
        // Logical y grows downwards; the projection's y grows upwards.
        float left = op.x0();
        float right = op.x1();
        float top = logicalHeight - op.y0();
        float bottom = logicalHeight - op.y1();
        int t = op.tint();
        float r = ((t >> 16) & 0xFF) / 255f;
        float g = ((t >> 8) & 0xFF) / 255f;
        float b = (t & 0xFF) / 255f;
        float a = ((t >>> 24) & 0xFF) / 255f;
        int f = op.flash();
        float fr = ((f >> 16) & 0xFF) / 255f;
        float fg = ((f >> 8) & 0xFF) / 255f;
        float fb = (f & 0xFF) / 255f;
        float fa = ((f >>> 24) & 0xFF) / 255f;
        vertex(left, bottom, u0, v1, r, g, b, a, fr, fg, fb, fa);
        vertex(left, top, u0, v0, r, g, b, a, fr, fg, fb, fa);
        vertex(right, top, u1, v0, r, g, b, a, fr, fg, fb, fa);
        vertex(left, bottom, u0, v1, r, g, b, a, fr, fg, fb, fa);
        vertex(right, top, u1, v0, r, g, b, a, fr, fg, fb, fa);
        vertex(right, bottom, u1, v1, r, g, b, a, fr, fg, fb, fa);
    }

    private void vertex(float x, float y, float u, float v, float r, float g, float b, float a,
            float fr, float fg, float fb, float fa) {
        vertices.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a).put(fr).put(fg).put(fb).put(fa);
    }

    private int texture(SceneImage image) {
        Integer id = textures.get(image);
        if (id == null) {
            id = upload(image.width(), image.height(), image.rawPixels());
            textures.put(image, id);
        }
        return id;
    }

    private static int upload(int width, int height, int[] argb) {
        ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int p : argb) {
                rgba.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            }
            rgba.flip();
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glBindTexture(GL_TEXTURE_2D, 0);
            return id;
        } finally {
            MemoryUtil.memFree(rgba);
        }
    }

    /** Deletes the scene's textures (the shader and buffers are kept for the next visit). */
    void releaseTextures() {
        for (int id : textures.values()) {
            glDeleteTextures(id);
        }
        textures.clear();
    }

    void cleanup() {
        releaseTextures();
        if (shader != null) {
            glDeleteTextures(whiteTexture);
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            shader.cleanup();
            shader = null;
        }
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            vertices = null;
        }
    }
}
