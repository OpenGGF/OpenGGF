package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.TexturedQuadRenderer;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;

/**
 * Boot screen shown on a native build when enabled code-bearing mods cannot load.
 * The notice fades in, dismisses on input, and fades back to black before boot
 * continues.
 */
public final class NativeModNoticeScreen {
    private static final Logger LOGGER = Logger.getLogger(NativeModNoticeScreen.class.getName());
    public static final int MAX_VISIBLE_MOD_LINES = 12;
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;
    private static final float BODY_SCALE = 1f;

    private final FadeManager fadeManager;
    private final List<String> noticeLines;
    private boolean dismissed;
    private boolean fadingOut;

    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int solidWhiteTextureId;

    public NativeModNoticeScreen(FadeManager fadeManager, List<String> noticeLines) {
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.noticeLines = List.copyOf(noticeLines);
    }

    public void initialize() {
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font = new PixelFont();
            font.init("pixel-font.png", renderer);
            solidWhiteTextureId = createSolidWhiteTexture();
            fadeManager.startFadeFromBlack(null);
            LOGGER.info("Native mod notice screen initialized");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize native mod notice screen", e);
        }
    }

    public void update(InputHandler inputHandler) {
        if (dismissed) {
            return;
        }
        if (!fadingOut && inputHandler.isAnyKeyJustPressed()) {
            fadingOut = true;
            fadeManager.startFadeToBlack(() -> dismissed = true);
        }
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void draw() {
        if (renderer == null) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderer.drawTexture(solidWhiteTextureId, 0, 0, SCREEN_W, SCREEN_H,
                0f, 0f, 0f, 1f);
        font.beginMegaBatch();
        int y = 40;
        for (String line : noticeLines) {
            int x = (SCREEN_W - font.measureWidth(line, BODY_SCALE)) / 2;
            font.drawText(line, x, y, BODY_SCALE, 0.95f, 0.95f, 0.95f, 1f);
            y += 12;
        }
        font.drawTextCentered("Press any key to continue", SCREEN_W, SCREEN_H - 20,
                0.8f, 0.8f, 0.8f, 1f);
        font.endMegaBatch();
    }

    public void setProjectionMatrix(float[] projectionMatrix) {
        if (renderer != null && projectionMatrix != null) {
            renderer.setProjectionMatrix(projectionMatrix);
        }
    }

    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        if (renderer != null) {
            renderer.cleanup();
        }
    }

    private static int createSolidWhiteTexture() {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        MemoryUtil.memFree(pixel);
        return texId;
    }
}
