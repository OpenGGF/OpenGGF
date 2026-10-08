package com.openggf.tools.challenge;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL11.*;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.TexturedQuadRenderer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Owns host-only GPU composition and title/transition presentation.
 *
 * <p>Text uses whole-number font scales so every glyph pixel stays square. Run
 * scenes share one pane grid, so loading, ready, countdown and play differ only
 * in what each pane shows. Before the first started tick a pane shows a standby
 * card rather than the worker's pre-start snapshot, which is not a frame the
 * console would present.
 */
final class ChallengePresentation implements AutoCloseable {
    static final int WIDTH = 1024, HEIGHT = 700;
    enum Scene { TITLE, LOADING, READY, COUNTDOWN, PLAY, PAUSE, FAULT }
    /**
     * One host frame. {@code elapsed} is time in the current scene and
     * {@code running} time since the three games started together (negative
     * before that). {@code pauseReason} is empty for a player's own pause.
     */
    record View(Scene scene, double elapsed, double running, int focus, String fault, String pauseReason,
            boolean slow) {}
    private static final String[] LABELS = {"SONIC 1", "SONIC 2", "SONIC 3 & KNUCKLES"};
    private static final String[] ZONES = {"GREEN HILL", "EMERALD HILL", "ANGEL ISLAND"};
    private static final DebugColor INK = new DebugColor(205, 221, 239),
                                    MUTED = new DebugColor(117, 145, 172),
                                    BACKGROUND = new DebugColor(6, 11, 19);
    private static final DebugColor[] ACCENTS = {
            new DebugColor(78, 223, 153), new DebugColor(245, 201, 73), new DebugColor(121, 171, 252)};
    private static final int PANE_W = 320, PANE_H = 224, LEFT = 22, STEP = 330;
    // The run grid and its message sit centred between the header and the control bar.
    private static final int LABEL_Y = 178, IMAGE_Y = LABEL_Y + 24, FOOT_Y = IMAGE_Y + PANE_H,
                             MESSAGE_Y = FOOT_Y + 54, BAR_Y = 632;
    private final long window;
    private final TexturedQuadRenderer quads = new TexturedQuadRenderer();
    private final PixelFontTextRenderer font = new PixelFontTextRenderer();
    private final int white;
    private final int[] textures = new int[3];
    private final ByteBuffer upload = MemoryUtil.memAlloc(ChallengeProtocol.RGBA_BYTES);
    private final float[] glow = new float[3];
    private long opened, lastDraw;
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
    /** Largest centred viewport with the layout's aspect: {x, y, width, height}. */
    static int[] letterbox(int framebufferWidth, int framebufferHeight) {
        double scale = Math.min(framebufferWidth / (double) WIDTH, framebufferHeight / (double) HEIGHT);
        int width = (int) Math.round(WIDTH * scale), height = (int) Math.round(HEIGHT * scale);
        return new int[] {(framebufferWidth - width) / 2, (framebufferHeight - height) / 2, width, height};
    }
    void draw(View view) {
        long now = System.nanoTime();
        if (opened == 0)
            opened = now;
        float dt = lastDraw == 0 ? 0 : (float) Math.min(.1, (now - lastDraw) / 1e9);
        lastDraw = now;
        int framebufferWidth = framebufferWidth(), framebufferHeight = framebufferHeight();
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glClearColor(BACKGROUND.getRed() / 255f, BACKGROUND.getGreen() / 255f, BACKGROUND.getBlue() / 255f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        if (framebufferWidth <= 0 || framebufferHeight <= 0)
            return;
        int[] viewport = letterbox(framebufferWidth, framebufferHeight);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glDisable(GL_DEPTH_TEST);
        Scene scene = view.scene;
        double elapsed = view.elapsed;
        for (int i = 0; i < 3; i++) {
            float target = scene == Scene.TITLE || scene == Scene.LOADING ? 0 : i == view.focus ? 1 : 0;
            glow[i] += (target - glow[i]) * Math.min(1, dt * 10);
        }
        header();
        switch (scene) {
            case TITLE -> title(elapsed);
            case LOADING, READY, COUNTDOWN, PLAY, PAUSE -> run(view);
            case FAULT -> fault(view);
        }
        // Content enters from the background; the header and the run grid stay put.
        double enter = switch (scene) {
            case TITLE, FAULT -> elapsed / .25;
            case LOADING, READY -> elapsed / .2;
            default -> 1;
        };
        if (enter < 1) {
            int top = scene == Scene.READY ? MESSAGE_Y - 10 : 100;
            rect(0, top, WIDTH, HEIGHT - top - 5, BACKGROUND, (float) (1 - easeOut(enter)));
        }
        double opening = (now - opened) / 1e9 / .35;
        if (opening < 1)
            rect(0, 0, WIDTH, HEIGHT, DebugColor.BLACK, (float) (1 - easeOut(opening)));
    }
    private void header() {
        for (int i = 0; i < 3; i++) {
            int x = LEFT + i * STEP;
            rect(x, 0, PANE_W, 5, ACCENTS[i], 1);
            rect(x, HEIGHT - 5, PANE_W, 5, ACCENTS[i], .35f);
        }
        text("OPENGGF  /  COMMON CONTROL LAB", LEFT, 24, MUTED, 1);
        text("THREE OPENINGS", LEFT, 42, INK, 3);
        text("ONE PAD. THREE WORLDS.", LEFT, 82, ACCENTS[2], 1);
    }
    private void title(double elapsed) {
        text("Three real games share every held button.", LEFT, 124, INK, 2);
        text("Find a rhythm that works for all three.", LEFT, 156, MUTED, 1);
        for (int i = 0; i < 3; i++) {
            int x = LEFT + i * STEP;
            int y = 190 + (int) Math.round((1 - easeOut((elapsed - i * .07) / .3)) * 16);
            rect(x, y, PANE_W, 128, ACCENTS[i], .09f);
            rect(x, y, 4, 128, ACCENTS[i], .6f);
            text("0" + (i + 1), x + 18, y + 16, ACCENTS[i], 3);
            text(LABELS[i], x + 18, y + 62, INK, 1);
            text(ZONES[i] + " ZONE  /  ACT 1", x + 18, y + 82, MUTED, 1);
            float travel = (float) ((elapsed * 55 + i * 60) % 266);
            rect(x + 18 + travel, y + 108, 18, 3, ACCENTS[i], 1);
        }
        text("PRESS ENTER OR PAD A TO PREPARE ALL THREE", LEFT, 354, pulse(INK, ACCENTS[0], elapsed), 2);
        text("IN EACH GAME", LEFT, 404, MUTED, 1);
        int row = 428;
        action(hints(LEFT, row, "ARROWS", "LEFT STICK"), row, "MOVE");
        action(hints(LEFT, row += 24, "Z X C", "PAD X B A"), row, "JUMP  (A B C)");
        action(hints(LEFT, row += 24, "ENTER", "START"), row, "EACH GAME\u2019S OWN START");
        int right = LEFT + 520;
        text("FOR ALL THREE", right, 404, MUTED, 1);
        row = 428;
        action(hints(right, row, "P", "ESC", "BACK"), row, "PAUSE");
        action(hints(right, row += 24, "R", "LB"), row, "RESTART");
        action(hints(right, row += 24, "TAB", "1 2 3", "RB"), row, "HEAR ONE GAME");
        text("HOW IT PLAYS", LEFT, 528, MUTED, 1);
        text("Each game keeps its own clock, physics and pauses. Every button you hold reaches all three.",
                LEFT, 552, INK, 1);
        text("Sound comes from one game at a time; the other two keep playing, muted.", LEFT, 574, INK, 1);
        rect(LEFT, BAR_Y, 980, 1, MUTED, .25f);
        text("Uses your own Sonic 1, Sonic 2 (World REV01) and Sonic 3 & Knuckles ROMs.", LEFT, 652,
                MUTED, 1);
        int quit = LEFT + 980 - keysWidth("ESC") - font.measureWidth("QUIT", 1);
        action(hints(quit, 652, "ESC"), 652, "QUIT");
    }
    private void run(View view) {
        Scene scene = view.scene;
        double elapsed = view.elapsed;
        // Preparation and START tuples (sequence 0) are pre-tick snapshots.
        boolean live = !frames.isEmpty() && frames.getFirst().sequence() > 0
                && (scene == Scene.PLAY || scene == Scene.PAUSE);
        for (int i = 0; i < 3; i++) {
            int x = LEFT + i * STEP;
            rect(x - 2, LABEL_Y - 2, PANE_W + 4, PANE_H + 52, ACCENTS[i], .08f + glow[i] * .32f);
            rect(x, LABEL_Y, PANE_W, 24, BACKGROUND, .7f);
            text(LABELS[i], x + 10, LABEL_Y + 7, ACCENTS[i], 1);
            if (live) {
                quads.drawTexture(textures[i], x, IMAGE_Y, PANE_W, PANE_H);
                // The standby card hands over to the first native frames.
                if (view.running >= 0 && view.running < .3)
                    standby(i, x, scene, elapsed, (float) (1 - view.running / .3));
            } else {
                standby(i, x, scene, elapsed, 1);
            }
            rect(x, FOOT_Y, PANE_W, 24, BACKGROUND, .7f);
            if (scene != Scene.LOADING) {
                boolean heard = i == view.focus;
                if (heard) {
                    speaker(x + PANE_W - 22, LABEL_Y + 7, ACCENTS[i]);
                    text("SOUND ON", x + 10, FOOT_Y + 7, ACCENTS[i], 1);
                } else {
                    text("MUTED", x + 10, FOOT_Y + 7, MUTED, 1);
                    String hint = "PRESS " + (i + 1) + " TO LISTEN";
                    text(hint, x + PANE_W - 10 - font.measureWidth(hint, 1), FOOT_Y + 7, MUTED, 1);
                }
            }
        }
        switch (scene) {
            case LOADING -> {
                text("PREPARING THREE WORLDS", LEFT, MESSAGE_Y, INK, 2);
                text("Checking ROMs, starting each game and its sound.", LEFT, MESSAGE_Y + 34, MUTED, 1);
                controlBar(false);
            }
            case READY -> {
                text("ALL THREE READY", LEFT, MESSAGE_Y, INK, 2);
                text("PRESS ENTER OR PAD A TO START TOGETHER", LEFT, MESSAGE_Y + 34,
                        pulse(INK, ACCENTS[view.focus], elapsed), 1);
                controlBar(true);
            }
            case COUNTDOWN -> {
                text("GET READY", LEFT, MESSAGE_Y, INK, 2);
                text("Every button you hold reaches all three games.", LEFT, MESSAGE_Y + 34, MUTED, 1);
                controlBar(true);
            }
            case PLAY -> {
                if (view.running >= 0 && view.running < .9) {
                    text("GO!", LEFT, MESSAGE_Y - 4, ACCENTS[view.focus], 4);
                    rect(LEFT, MESSAGE_Y - 8, 200, 48, BACKGROUND,
                            (float) Math.max(0, (view.running - .5) / .4));
                }
                if (view.slow)
                    text("Running below full speed: all three wait for the slowest game.", LEFT,
                            MESSAGE_Y + 54, ACCENTS[1], 1);
                controlBar(true);
            }
            case PAUSE -> {
                controlBar(true);
                pauseCard(view);
            }
            default -> {}
        }
    }
    private void standby(int i, int x, Scene scene, double elapsed, float alpha) {
        rect(x, IMAGE_Y, PANE_W, PANE_H, DebugColor.BLACK, alpha);
        rect(x, IMAGE_Y, PANE_W, PANE_H, ACCENTS[i], .05f * alpha);
        if (alpha < 1)
            return;
        int centre = x + PANE_W / 2;
        centred(ZONES[i], centre, IMAGE_Y + 54, INK, 2);
        centred("ZONE  /  ACT 1", centre, IMAGE_Y + 84, MUTED, 1);
        switch (scene) {
            case LOADING -> {
                centred("LOADING", centre, IMAGE_Y + 150, MUTED, 1);
                float travel = (float) ((elapsed * 125 + i * 70) % 200);
                rect(centre - 100, IMAGE_Y + 172, 200, 3, ACCENTS[i], .15f);
                rect(centre - 100 + travel, IMAGE_Y + 172, 20, 3, ACCENTS[i], 1);
            }
            case READY -> centred("READY", centre, IMAGE_Y + 142, pulse(MUTED, ACCENTS[i], elapsed), 2);
            case COUNTDOWN -> {
                int count = Math.max(1, 3 - (int) elapsed);
                double beat = elapsed - Math.floor(elapsed);
                // Whole-number steps keep the pop crisp in the pixel font. The
                // last beat holds while the three games admit their first tick.
                int scale = elapsed >= 3 || beat >= .12 ? 8 : beat < .06 ? 10 : 9;
                centred(Integer.toString(count), centre, IMAGE_Y + 150 - scale * 5, ACCENTS[i], scale);
            }
            default -> {}
        }
    }
    private void pauseCard(View view) {
        float fade = (float) easeOut(view.elapsed / .15);
        rect(0, LABEL_Y - 4, WIDTH, PANE_H + 56, DebugColor.BLACK, .55f * fade);
        int width = 600, x = (WIDTH - width) / 2, y = IMAGE_Y + (PANE_H - 150) / 2;
        card(x, y, width, 150, ACCENTS[view.focus]);
        centred("PAUSED", WIDTH / 2, y + 20, INK, 3);
        String reason = view.pauseReason.isEmpty() ? "All three games are holding still." : view.pauseReason;
        centred(reason, WIDTH / 2, y + 64, MUTED, 1);
        String[][] options = {{"RESUME", "P", "BACK"}, {"RESTART", "R", "LB"}, {"TITLE", "ESC", "B"}};
        centredOptions(options, y + 106);
    }
    private void fault(View view) {
        int width = 800, x = (WIDTH - width) / 2, y = IMAGE_Y + (PANE_H - 168) / 2;
        card(x, y, width, 168, ACCENTS[1]);
        centred("TRY AGAIN", WIDTH / 2, y + 22, ACCENTS[1], 3);
        centred(view.fault, WIDTH / 2, y + 72, INK, 1);
        String[][] options = {{"RETRY", "ENTER", "PAD A"}, {"TITLE", "ESC", "PAD B"}};
        centredOptions(options, y + 120);
    }
    private void controlBar(boolean running) {
        rect(LEFT, BAR_Y, 980, 1, MUTED, .25f);
        int row = 646;
        if (!running) {
            action(hints(LEFT, row, "ESC", "PAD B"), row, "CANCEL");
            return;
        }
        int x = action(hints(LEFT, row, "ARROWS"), row, "MOVE");
        x = action(hints(x + 14, row, "Z X C"), row, "JUMP");
        x = action(hints(x + 14, row, "ENTER"), row, "START");
        x = action(hints(x + 30, row, "P", "ESC"), row, "PAUSE");
        x = action(hints(x + 14, row, "R"), row, "RESTART");
        action(hints(x + 14, row, "TAB", "1 2 3"), row, "SOUND");
        text("CONTROLLER   D-PAD MOVE   X B A JUMP   START   BACK PAUSE   LB RESTART   RB SOUND", LEFT,
                row + 26, MUTED, 1);
    }
    private void centredOptions(String[][] options, int y) {
        int total = 0;
        for (String[] option : options)
            total += optionWidth(option) + 28;
        int x = (WIDTH - total + 28) / 2;
        for (String[] option : options) {
            String[] keys = Arrays.copyOfRange(option, 1, option.length);
            x = action(hints(x, y, keys), y, option[0]) + 28;
        }
    }
    private int optionWidth(String[] option) {
        return keysWidth(Arrays.copyOfRange(option, 1, option.length)) + font.measureWidth(option[0], 1);
    }
    /** The advance {@link #hints} uses for these keycaps. */
    private int keysWidth(String... keys) {
        int width = 0;
        for (String key : keys)
            width += font.measureWidth(key, 1) + 14;
        return width + 2;
    }
    /** Draws keycaps left to right and returns where the action label starts. */
    private int hints(int x, int y, String... keys) {
        for (String key : keys) {
            int width = font.measureWidth(key, 1) + 10;
            rect(x, y - 4, width, 18, INK, .12f);
            rect(x, y + 13, width, 1, INK, .22f);
            text(key, x + 5, y, INK, 1);
            x += width + 4;
        }
        return x + 2;
    }
    private int action(int x, int y, String label) {
        text(label, x, y, MUTED, 1);
        return x + font.measureWidth(label, 1);
    }
    private void card(int x, int y, int width, int height, DebugColor accent) {
        rect(x, y, width, height, BACKGROUND, .94f);
        rect(x, y, width, 2, accent, .8f);
        rect(x, y + height - 1, width, 1, accent, .35f);
        rect(x, y, 1, height, accent, .35f);
        rect(x + width - 1, y, 1, height, accent, .35f);
    }
    private void speaker(int x, int y, DebugColor color) {
        rect(x, y + 3, 3, 4, color, 1);
        rect(x + 3, y + 1, 2, 8, color, 1);
        rect(x + 7, y + 3, 1, 4, color, 1);
        rect(x + 9, y + 1, 1, 8, color, 1);
    }
    private static DebugColor pulse(DebugColor from, DebugColor to, double elapsed) {
        double t = .5 + .5 * Math.sin(elapsed * Math.PI * 1.6);
        return new DebugColor((int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }
    private static double easeOut(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - clamped, 3);
    }
    private void rect(float x, float y, float w, float h, DebugColor color, float alpha) {
        quads.drawTexture(white, x, y, w, h, color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }
    private void text(String text, int x, int y, DebugColor color, int scale) {
        font.drawShadowedText(text, x, y, color, scale);
    }
    private void centred(String text, int centre, int y, DebugColor color, int scale) {
        text(text, centre - font.measureWidth(text, scale) / 2, y, color, scale);
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
