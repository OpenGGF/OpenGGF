package com.openggf.mods.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link SceneCanvas} handed to scenes: it records each call as a {@link SceneDrawOp}
 * in order. The GL renderer submits the recorded list after {@link ModScene#draw} returns;
 * headless runs simply discard it (tests can inspect it). Engine-internal.
 */
final class RecordingCanvas implements SceneCanvas {
    private final int width;
    private final int height;
    private final SceneFontAtlas font;
    private final List<SceneDrawOp> ops = new ArrayList<>();
    private int[] clip;

    RecordingCanvas(int width, int height, SceneFontAtlas font) {
        this.width = width;
        this.height = height;
        this.font = font;
    }

    List<SceneDrawOp> ops() {
        return ops;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void clear(int rgb) {
        int[] saved = clip;
        clip = null;
        fill(0, 0, width, height, 0xFF000000 | (rgb & 0xFFFFFF));
        clip = saved;
    }

    @Override
    public void fill(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
            return;
        }
        ops.add(new SceneDrawOp(null, 0, 0, 0, 0, x, y, x + w, y + h, argb, 0, clip));
    }

    @Override
    public void draw(SceneImage image, float x, float y) {
        draw(image, x, y, SceneDraw.plain());
    }

    @Override
    public void draw(SceneImage image, float x, float y, SceneDraw style) {
        if (image == null) {
            return;
        }
        drawRegion(image, 0, 0, image.width(), image.height(), x, y,
                image.width() * style.scaleX(), image.height() * style.scaleY(), style);
    }

    @Override
    public void draw(SceneSprite sprite, float x, float y, SceneDraw style) {
        if (sprite == null) {
            return;
        }
        SceneImage image = sprite.image();
        float sx = style.scaleX();
        float sy = style.scaleY();
        // Mirroring and scaling happen around the origin, as a Mega Drive sprite flips about x_pos.
        float left = style.flipX() ? x - (image.width() - sprite.originX()) * sx : x - sprite.originX() * sx;
        float top = style.flipY() ? y - (image.height() - sprite.originY()) * sy : y - sprite.originY() * sy;
        drawRegion(image, 0, 0, image.width(), image.height(), left, top, image.width() * sx,
                image.height() * sy, style);
    }

    @Override
    public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float dx, float dy, float dw, float dh,
            SceneDraw style) {
        if (image == null || sw <= 0 || sh <= 0 || dw == 0 || dh == 0 || (style.tint() >>> 24) == 0) {
            return;
        }
        float u0 = sx;
        float u1 = sx + sw;
        float v0 = sy;
        float v1 = sy + sh;
        if (style.flipX()) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        if (style.flipY()) {
            float t = v0;
            v0 = v1;
            v1 = t;
        }
        ops.add(new SceneDrawOp(image, u0, v0, u1, v1, dx, dy, dx + dw, dy + dh, style.tint(), style.flash(), clip));
    }

    @Override
    public void text(String text, int x, int y, int argb) {
        if (text == null || font == null) {
            return;
        }
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = font.glyph(text.charAt(i));
            if (glyph != null) {
                ops.add(new SceneDrawOp(font.image(), glyph[0], glyph[1], glyph[0] + SceneFontAtlas.GLYPH_W,
                        glyph[1] + SceneFontAtlas.GLYPH_H, cx, y, cx + SceneFontAtlas.GLYPH_W,
                        y + SceneFontAtlas.GLYPH_H, argb, 0, clip));
            }
            cx += SceneFontAtlas.ADVANCE;
        }
    }

    @Override
    public int textWidth(String text) {
        return SceneFontAtlas.width(text);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        clip = new int[] {x, y, Math.max(0, w), Math.max(0, h)};
    }

    @Override
    public void unclip() {
        clip = null;
    }
}
