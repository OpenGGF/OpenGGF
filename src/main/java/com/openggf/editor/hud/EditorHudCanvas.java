package com.openggf.editor.hud;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.text.Graphemes;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.PixelFontVariant;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.SolidColorTexture;
import com.openggf.graphics.TexturedQuadRenderer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;

/**
 * Immediate-mode drawing for the editor HUD at window resolution. Coordinates are top-left UI
 * units (see {@link EditorHudLayout}). Text uses the engine's own menu faces and every quad goes
 * through the existing textured-quad shader, so the HUD adds no dependency. Quads that share a
 * texture are submitted together until the texture, clip or text changes.
 */
final class EditorHudCanvas implements AutoCloseable {
    enum Size {
        /** Compact 6x8 lettering. */
        SMALL(.5f),
        /** The 9x10 menu font. */
        BODY(1f),
        /** The 9x10 menu font at twice the size. */
        LARGE(2f);

        final float scale;
        Size(float scale) { this.scale = scale; }
    }

    private static final int FLOATS_PER_QUAD = 48;
    // The menu fonts place text with glY = 224 - y; the projection below keeps that origin.
    private static final float FONT_ORIGIN = 224f;

    private TexturedQuadRenderer renderer;
    private MenuPixelFont font;
    private int white;
    private int glyphs;
    private float[] vertices = new float[FLOATS_PER_QUAD * 512];
    private int quads;
    private int batchTexture;
    private int scale = 1;
    private int framebufferHeight;
    private final float[] projection = new float[16];
    private final int[] savedViewport = new int[4];
    private final int[] savedScissorBox = new int[4];
    private boolean savedBlend;
    private int savedBlendSrcRgb;
    private int savedBlendDstRgb;
    private int savedBlendSrcAlpha;
    private int savedBlendDstAlpha;
    private boolean savedScissor;
    private boolean savedDepth;

    void init() throws IOException {
        renderer = new TexturedQuadRenderer();
        renderer.init();
        font = new MenuPixelFont();
        font.init(PixelFontVariant.PIXEL_FONT_NO_SHADOW.resourcePath(), renderer);
        white = SolidColorTexture.createWhite();
        glyphs = upload(EditorHudGlyphs.ATLAS_WIDTH, EditorHudGlyphs.ATLAS_HEIGHT, EditorHudGlyphs.atlasRgba());
    }

    boolean isInitialized() { return renderer != null; }

    void begin(int framebufferWidth, int framebufferHeight, int uiScale) {
        scale = uiScale;
        this.framebufferHeight = framebufferHeight;
        float w = framebufferWidth / (float) uiScale;
        float h = framebufferHeight / (float) uiScale;
        new Matrix4f().ortho2D(0, w, FONT_ORIGIN - h, FONT_ORIGIN).get(projection);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            viewport.get(savedViewport);
            IntBuffer scissor = stack.mallocInt(4);
            glGetIntegerv(GL_SCISSOR_BOX, scissor);
            scissor.get(savedScissorBox);
        }
        savedBlend = glIsEnabled(GL_BLEND);
        savedBlendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        savedBlendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        savedBlendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        savedBlendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        savedScissor = glIsEnabled(GL_SCISSOR_TEST);
        savedDepth = glIsEnabled(GL_DEPTH_TEST);
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderer.setProjectionMatrix(projection);
    }

    void end() {
        flush();
        glDisable(GL_SCISSOR_TEST);
        glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
        glBlendFuncSeparate(savedBlendSrcRgb, savedBlendDstRgb, savedBlendSrcAlpha, savedBlendDstAlpha);
        glScissor(savedScissorBox[0], savedScissorBox[1], savedScissorBox[2], savedScissorBox[3]);
        if (!savedBlend) glDisable(GL_BLEND);
        if (savedScissor) glEnable(GL_SCISSOR_TEST);
        if (savedDepth) glEnable(GL_DEPTH_TEST);
        // Text and HUD quads change programs and texture bindings; the next frame's tiles rebind.
        PatternRenderCommand.resetFrameState();
    }

    void rect(float x, float y, float w, float h, int rgb, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        quad(white, x, y, w, h, 0, 0, 1, 1, rgb, alpha);
    }

    /** Screen-space stroke through the HUD's batched quad renderer, including diagonal routes. */
    void line(float x1, float y1, float x2, float y2, int rgb, float width) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < .001) return;
        float nx = (float) ((y1 - y2) / length * width / 2);
        float ny = (float) ((x2 - x1) / length * width / 2);
        if (white != batchTexture) flush();
        batchTexture = white;
        int needed = (quads + 1) * FLOATS_PER_QUAD;
        if (needed > vertices.length) vertices = Arrays.copyOf(vertices, Math.max(needed, vertices.length * 2));
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        int i = quads * FLOATS_PER_QUAD;
        i = vertex(i, x1 + nx, FONT_ORIGIN - y1 - ny, 0, 0, r, g, b, 1);
        i = vertex(i, x1 - nx, FONT_ORIGIN - y1 + ny, 0, 0, r, g, b, 1);
        i = vertex(i, x2 - nx, FONT_ORIGIN - y2 + ny, 0, 0, r, g, b, 1);
        i = vertex(i, x1 + nx, FONT_ORIGIN - y1 - ny, 0, 0, r, g, b, 1);
        i = vertex(i, x2 - nx, FONT_ORIGIN - y2 + ny, 0, 0, r, g, b, 1);
        vertex(i, x2 + nx, FONT_ORIGIN - y2 - ny, 0, 0, r, g, b, 1);
        quads++;
    }

    /** One-unit outline drawn inside the rectangle. */
    void frame(float x, float y, float w, float h, float thickness, int rgb, float alpha) {
        rect(x, y, w, thickness, rgb, alpha);
        rect(x, y + h - thickness, w, thickness, rgb, alpha);
        rect(x, y + thickness, thickness, h - 2 * thickness, rgb, alpha);
        rect(x + w - thickness, y + thickness, thickness, h - 2 * thickness, rgb, alpha);
    }

    /** Raised panel: drop shadow, body, light top edge and dark bottom edge, with clipped corners. */
    void panel(float x, float y, float w, float h, int body, float alpha) {
        rect(x + 2, y + 2, w, h, EditorHudTheme.SHADOW, .35f * alpha);
        rect(x + 1, y, w - 2, h, body, alpha);
        rect(x, y + 1, 1, h - 2, body, alpha);
        rect(x + w - 1, y + 1, 1, h - 2, body, alpha);
        rect(x + 1, y, w - 2, 1, EditorHudTheme.PANEL_HIGHLIGHT, alpha);
        rect(x + 1, y + h - 1, w - 2, 1, EditorHudTheme.SHADOW, .5f * alpha);
    }

    /** Rounded pill, as used by chips and tabs. */
    void pill(float x, float y, float w, float h, int rgb, float alpha) {
        rect(x + 1, y, w - 2, h, rgb, alpha);
        rect(x, y + 1, 1, h - 2, rgb, alpha);
        rect(x + w - 1, y + 1, 1, h - 2, rgb, alpha);
    }

    /** Draws a sub-image; {@code vTop}/{@code vBottom} are texture rows with row 0 at the top. */
    void image(int texture, float x, float y, float w, float h, float u0, float vTop, float u1, float vBottom, float alpha) {
        if (texture == 0) return;
        quad(texture, x, y, w, h, u0, vTop, u1, vBottom, 0xFFFFFF, alpha);
    }

    void mask(EditorHudGlyphs.Mask mask, float x, float y, int rgb, float alpha) {
        float u0 = mask.x() / (float) EditorHudGlyphs.ATLAS_WIDTH;
        float u1 = (mask.x() + mask.w()) / (float) EditorHudGlyphs.ATLAS_WIDTH;
        float v0 = mask.y() / (float) EditorHudGlyphs.ATLAS_HEIGHT;
        float v1 = (mask.y() + mask.h()) / (float) EditorHudGlyphs.ATLAS_HEIGHT;
        quad(glyphs, x, y, mask.w(), mask.h(), u0, v0, u1, v1, rgb, alpha);
    }

    /**
     * Draws text. Translated labels retain their authored case at every size.
     * Both native menu-font weights support lowercase descenders.
     */
    void text(String text, float x, float y, Size size, int rgb, float alpha) {
        if (text == null || text.isEmpty() || alpha <= 0) return;
        text = display(text, size);
        flush();
        font.drawText(text, x, y + font.lineMetrics(size.scale).above(), size.scale, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f,
                (rgb & 255) / 255f, alpha);
    }

    /** Text truncated with "..." to fit {@code maxWidth} units. */
    void fittedText(String text, float x, float y, float maxWidth, Size size, int rgb, float alpha) {
        text(fit(text, maxWidth, size), x, y, size, rgb, alpha);
    }

    /** Headings retain authored case; measurement and drawing agree. */
    static String display(String text, Size size) {
        return text;
    }

    static String fit(String text, float maxWidth, Size size) {
        if (text == null) return "";
        String shown = display(text, size);
        TextFace face = MenuPixelFont.face(size.scale);
        TextLanguage language = HostTextContext.current();
        HostText host = HostText.shared();
        int max = Math.max(0, (int) maxWidth / MenuPixelFont.pixelScale(size.scale));
        if (host.width(shown, face, language) <= max) return shown;
        if (max >= host.width("...", face, language) + host.width("M", face, language)) {
            return host.fit(shown, max, face, language);
        }
        // Too narrow for an ellipsis: keep the clusters that fit.
        int end = 0;
        for (int next = Graphemes.next(shown, 0); next <= shown.length()
                && host.width(shown.substring(0, next), face, language) <= max; next = Graphemes.next(shown, next)) {
            end = next;
            if (next == shown.length()) break;
        }
        return shown.substring(0, end);
    }

    static int textWidth(String text, Size size) {
        return text == null ? 0 : HostText.shared().width(display(text, size), MenuPixelFont.face(size.scale),
                HostTextContext.current()) * MenuPixelFont.pixelScale(size.scale);
    }

    /** Line pitch in the active language; never smaller than the native cell. */
    static int lineHeight(Size size) {
        return HostText.shared().lineMetrics(MenuPixelFont.face(size.scale), HostTextContext.current())
                .lineHeight() * MenuPixelFont.pixelScale(size.scale);
    }

    /** Restricts drawing to a rectangle until {@link #unclip()}. */
    void clip(EditorHudLayout.Rect rect) {
        flush();
        glEnable(GL_SCISSOR_TEST);
        int x = Math.round(rect.x() * scale);
        int w = Math.round(rect.w() * scale);
        int h = Math.round(rect.h() * scale);
        int y = framebufferHeight - Math.round(rect.y() * scale) - h;
        glScissor(x, y, Math.max(0, w), Math.max(0, h));
    }

    void unclip() {
        flush();
        glDisable(GL_SCISSOR_TEST);
    }

    private void quad(int texture, float x, float y, float w, float h, float u0, float vTop, float u1, float vBottom,
                      int rgb, float alpha) {
        if (texture != batchTexture) flush();
        batchTexture = texture;
        int needed = (quads + 1) * FLOATS_PER_QUAD;
        if (needed > vertices.length) vertices = Arrays.copyOf(vertices, Math.max(needed, vertices.length * 2));
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float left = x, right = x + w, top = FONT_ORIGIN - y, bottom = FONT_ORIGIN - y - h;
        int i = quads * FLOATS_PER_QUAD;
        i = vertex(i, left, top, u0, vTop, r, g, b, alpha);
        i = vertex(i, left, bottom, u0, vBottom, r, g, b, alpha);
        i = vertex(i, right, bottom, u1, vBottom, r, g, b, alpha);
        i = vertex(i, left, top, u0, vTop, r, g, b, alpha);
        i = vertex(i, right, bottom, u1, vBottom, r, g, b, alpha);
        vertex(i, right, top, u1, vTop, r, g, b, alpha);
        quads++;
    }

    private int vertex(int i, float x, float y, float u, float v, float r, float g, float b, float a) {
        vertices[i] = x; vertices[i + 1] = y; vertices[i + 2] = u; vertices[i + 3] = v;
        vertices[i + 4] = r; vertices[i + 5] = g; vertices[i + 6] = b; vertices[i + 7] = a;
        return i + 8;
    }

    private void flush() {
        if (quads > 0) renderer.drawColoredTextureBatch(batchTexture, vertices, quads);
        quads = 0;
    }

    static int upload(int width, int height, byte[] rgba) {
        ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
        try {
            pixels.put(rgba).flip();
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, 0);
            return texture;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    @Override
    public void close() {
        if (font != null) font.cleanup();
        if (renderer != null) renderer.cleanup();
        PngTextureLoader.deleteTexture(white);
        PngTextureLoader.deleteTexture(glyphs);
        white = glyphs = 0;
        font = null;
        renderer = null;
    }
}
