package com.openggf.tools.challenge;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL11.*;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.TexturedQuadRenderer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/** Owns host-only GPU composition and title/transition presentation. */
final class ChallengePresentation implements AutoCloseable {
    static final int WIDTH = 1024, HEIGHT = 700;
    enum Scene { TITLE, LOADING, READY, COUNTDOWN, PLAY, PAUSE, FAULT }
    record View(Scene scene, double elapsed, int focus, String fault, double lastStepMs, long tick) {}
    private static final String[] LABELS = {"SONIC 1", "SONIC 2", "SONIC 3 & KNUCKLES"};
    private static final String[] ROUTES = {"GREEN HILL", "EMERALD HILL", "ANGEL ISLAND"};
    private static final DebugColor INK = new DebugColor(205, 221, 239),
                                    MUTED = new DebugColor(117, 145, 172);
    private static final DebugColor[] ACCENTS = {
            new DebugColor(78, 223, 153), new DebugColor(245, 201, 73), new DebugColor(121, 171, 252)};
    private final long window;
    private final TexturedQuadRenderer quads = new TexturedQuadRenderer();
    private final PixelFontTextRenderer font = new PixelFontTextRenderer();
    private final int white;
    private final int[] textures = new int[3];
    private final ByteBuffer upload = MemoryUtil.memAlloc(ChallengeProtocol.RGBA_BYTES);
    private List<ChallengeProtocol.Frame> frames = List.of();
    private long generation;
    ChallengePresentation(long window) throws IOException {
        this.window = window;
        quads.init();
        float[] projection = new Matrix4f().ortho2D(0, WIDTH, HEIGHT, 0).get(new float[16]);
        quads.setProjectionMatrix(projection);
        // PixelFont emits bottom-up geometry with a fixed224-pixel origin.
        // Give that existing owner its native convention without changing the
        // host's top-left pane geometry or the public font API.
        font.setProjectionMatrix(new Matrix4f().ortho2D(0, WIDTH, 224 - HEIGHT, 224).get(new float[16]));
        white = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, white);
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        pixel.putInt(-1).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        MemoryUtil.memFree(pixel);
        for (int i = 0; i < 3; i++) {
            textures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(
                    GL_TEXTURE_2D, 0, GL_RGBA8, 320, 224, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    void reset(long newGeneration) {
        generation = newGeneration;
        frames = List.of();
    }
    void upload(List<ChallengeProtocol.Frame> tuple) throws IOException {
        for (var frame : tuple)
            if (frame.generation() != generation)
                throw new IOException("Stale generation at GPU publication");
        for (int i = 0; i < tuple.size(); i++) {
            upload.clear();
            upload.put(tuple.get(i).rgba()).flip();
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, upload);
        }
        frames = tuple;
    }
    int framebufferWidth() {
        int[] w = {0}, h = {0};
        glfwGetFramebufferSize(window, w, h);
        return w[0];
    }
    int framebufferHeight() {
        int[] w = {0}, h = {0};
        glfwGetFramebufferSize(window, w, h);
        return h[0];
    }
    void draw(View view) {
        Scene scene = view.scene;
        double elapsed = view.elapsed;
        int focus = view.focus;
        String fault = view.fault;
        double lastStepMs = view.lastStepMs;
        long tick = view.tick;
        try {
            glViewport(0, 0, framebufferWidth(), framebufferHeight());
            glClearColor(.025f, .044f, .075f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glDisable(GL_DEPTH_TEST);
            for (int i = 0; i < 3; i++) {
                float x = 31 + i * 330;
                rect(x, 0, 320, 5, ACCENTS[i], 1);
                rect(x, HEIGHT - 5, 320, 5, ACCENTS[i], .35f);
            }
            text("OPEN GGF  /  COMMON CONTROL LAB", 31, 31, MUTED, 1);
            text("THREE OPENINGS", 31, 62, INK, 3);
            text("ONE PAD. THREE WORLDS.", 33, 106, ACCENTS[2], 1.2f);
            if (scene == Scene.TITLE) {
                text("Three real games share every held button.", 33, 169, INK, 1.4f);
                text("Find a rhythm that works for all three.", 33, 198, MUTED, 1.2f);
                for (int i = 0; i < 3; i++) {
                    int x = 31 + i * 330;
                    rect(x, 258, 320, 140, ACCENTS[i], .09f);
                    text("0" + (i + 1), x + 18, 275, ACCENTS[i], 2.5f);
                    text(LABELS[i], x + 18, 325, INK, 1.2f);
                    text(ROUTES[i] + " / ACT 1", x + 18, 360, MUTED, 1);
                    float travel = (float) ((elapsed * 55 + i * 60) % 275);
                    rect(x + 18 + travel, 388, 22, 3, ACCENTS[i], 1);
                }
                text("ENTER / PAD A   PREPARE ALL THREE", 33, 449, INK, 1.6f);
                text("Arrows / left stick   MOVE", 33, 501, MUTED, 1.2f);
                text("Z X C / pad X B A     A B C", 33, 527, MUTED, 1.2f);
                text("Enter / pad Start     GAME START", 33, 553, MUTED, 1.2f);
                text("P / Back  PAUSE   R / LB  RESTART   Tab / RB  SOUND", 33, 599, INK, 1);
                text("ESC  EXIT     WORLD REV01 ROMs REQUIRED   /   OPENING PLAY PROTOTYPE", 33, 647, MUTED,
                        1);
                return;
            }
            if (scene == Scene.LOADING) {
                text("PREPARING THREE WORLDS", 33, 205, INK, 1.6f);
                text("Checking ROMs, opening games and preparing sound.", 33, 253, MUTED, 1.1f);
                for (int i = 0; i < 3; i++) {
                    int x = 31 + i * 330;
                    rect(x, 329, 320, 96, ACCENTS[i], .08f);
                    text(LABELS[i], x + 14, 349, ACCENTS[i], 1);
                    float travel = (float) ((elapsed * 125 + i * 90) % 280);
                    rect(x + 14 + travel, 404, 22, 4, ACCENTS[i], 1);
                }
                text("All three will start together. ESC returns to the title.", 33, 480, INK, 1);
                return;
            }
            for (int i = 0; i < 3; i++) {
                int x = 31 + i * 330;
                rect(x - 2, 183, 324, 286, ACCENTS[i], i == focus ? .4f : .1f);
                text(LABELS[i], x + 10, 196, ACCENTS[i], 1);
                if (!frames.isEmpty())
                    quads.drawTexture(textures[i], x, 224, 320, 224);
                else
                    rect(x, 224, 320, 224, MUTED, .08f);
                if (!frames.isEmpty()) {
                    var f = frames.get(i);
                    text("RINGS " + f.rings() + "  /  " + f.mode().replace('_', ' '), x + 8, 453, INK, .8f);
                }
                text(i == focus ? "SOUND FOCUS" : "PLAYING TOGETHER", x + 8, 489,
                        i == focus ? ACCENTS[i] : MUTED, 1);
            }
            if (scene == Scene.READY) {
                text("ALL THREE READY", 33, 542, INK, 1.8f);
                text("ENTER / PAD A  TO BEGIN", 33, 582, ACCENTS[focus], 1.2f);
            } else if (scene == Scene.COUNTDOWN) {
                text("GET READY  " + Math.max(1, 3 - (int) elapsed), 33, 542, INK, 2);
                text("Your buttons will reach all three games.", 33, 589, MUTED, 1.2f);
            } else if (scene == Scene.PAUSE) {
                rect(0, 170, WIDTH, 350, DebugColor.BLACK, .45f);
                text("ALL WORLDS PAUSED", 243, 322, INK, 2);
                text("P / BACK  RESUME     R / LB  RESTART", 243, 368, ACCENTS[focus], 1.1f);
                text("Native Start remains each game's own button.", 33, 583, MUTED, 1);
            } else if (scene == Scene.FAULT) {
                rect(0, 170, WIDTH, 350, DebugColor.BLACK, .8f);
                text("LET'S TRY AGAIN", 33, 295, ACCENTS[1], 2);
                text(fault, 33, 349, INK, 1);
                text("ENTER / PAD A  RETRY     ESC  TITLE", 33, 399, MUTED, 1.2f);
            } else {
                text("ONE PAD / THREE WORLDS", 33, 552, MUTED, 1);
                text("P / BACK  PAUSE   R / LB  RESTART   TAB / RB  SOUND", 33, 585, INK, 1.1f);
                if (lastStepMs > 16.67)
                    text("Taking it together: presentation is running slower.", 33, 622, ACCENTS[1], 1);
            }
            text("ESC  TITLE   /   ARROWS + Z X C   /   ENTER = GAME START", 33, 660, MUTED, 1);
        } finally {
            float cover = (float) Math.max(0, 1 - elapsed / .22);
            if (cover > 0)
                rect(0, 0, WIDTH, HEIGHT, DebugColor.BLACK, cover);
        }
    }
    private void rect(float x, float y, float w, float h, DebugColor color, float alpha) {
        quads.drawTexture(white, x, y, w, h, color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }
    private void text(String text, int x, int y, DebugColor color, float scale) {
        font.drawShadowedText(text, x, y, color, scale);
    }
    @Override
    public void close() {
        font.cleanup();
        quads.cleanup();
        for (int texture : textures)
            if (texture != 0)
                glDeleteTextures(texture);
        glDeleteTextures(white);
        MemoryUtil.memFree(upload);
    }
}
