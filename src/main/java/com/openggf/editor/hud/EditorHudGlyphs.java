package com.openggf.editor.hud;

/**
 * Pixel-art masks for controller button prompts, drawn in code so the HUD ships no image
 * assets. Each mask is white with coverage in alpha and is tinted when drawn: Xbox-style face
 * buttons are coloured discs with a letter, PlayStation-style ones a dark disc with a coloured
 * shape. Rows are stored top-first.
 */
final class EditorHudGlyphs {
    record Mask(int x, int y, int w, int h) { }

    static final int ATLAS_WIDTH = 128;
    static final int ATLAS_HEIGHT = 32;
    static final Mask DISC = new Mask(0, 0, 15, 15);
    static final Mask RING = new Mask(16, 0, 15, 15);
    static final Mask PILL = new Mask(32, 0, 23, 13);
    static final Mask CROSS = new Mask(56, 0, 9, 9);
    static final Mask CIRCLE = new Mask(66, 0, 9, 9);
    static final Mask TRIANGLE = new Mask(76, 0, 9, 9);
    static final Mask SQUARE = new Mask(86, 0, 9, 9);
    static final Mask PLUS = new Mask(96, 0, 15, 15);
    static final Mask MENU = new Mask(112, 0, 9, 7);
    static final Mask VIEW = new Mask(112, 8, 9, 9);
    static final Mask TRIGGER = new Mask(0, 16, 19, 13);
    static final Mask BUMPER = new Mask(20, 16, 23, 11);
    static final Mask ARROW_LEFT = new Mask(44, 16, 5, 9);
    static final Mask ARROW_RIGHT = new Mask(50, 16, 5, 9);

    private EditorHudGlyphs() { }

    /** RGBA bytes, rows top-first. */
    static byte[] atlasRgba() {
        byte[] rgba = new byte[ATLAS_WIDTH * ATLAS_HEIGHT * 4];
        disc(rgba, DISC, 0);
        disc(rgba, RING, 2);
        roundRect(rgba, PILL, 4, 0);
        for (int i = 0; i < 9; i++) {
            set(rgba, CROSS, i, i);
            set(rgba, CROSS, 8 - i, i);
            if (i > 0) { set(rgba, CROSS, i - 1, i); set(rgba, CROSS, 9 - i, i); }
        }
        disc(rgba, CIRCLE, 2);
        for (int y = 0; y < 9; y++) {
            int half = y / 2;
            for (int x = 4 - half; x <= 4 + half; x++) {
                boolean edge = y >= 7 || x <= 4 - half + 1 || x >= 4 + half - 1;
                if (edge) set(rgba, TRIANGLE, x, y + 0);
            }
        }
        for (int i = 0; i < 9; i++) {
            for (int t = 0; t < 2; t++) {
                set(rgba, SQUARE, i, t); set(rgba, SQUARE, i, 8 - t);
                set(rgba, SQUARE, t, i); set(rgba, SQUARE, 8 - t, i);
            }
        }
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x >= 5 && x <= 9) || (y >= 5 && y <= 9)) set(rgba, PLUS, x, y);
            }
        }
        for (int x = 0; x < 9; x++) { set(rgba, MENU, x, 0); set(rgba, MENU, x, 3); set(rgba, MENU, x, 6); }
        for (int i = 0; i < 6; i++) {
            set(rgba, VIEW, i, 0); set(rgba, VIEW, i, 5); set(rgba, VIEW, 0, i); set(rgba, VIEW, 5, i);
            set(rgba, VIEW, i + 3, 3); set(rgba, VIEW, i + 3, 8); set(rgba, VIEW, 3, i + 3); set(rgba, VIEW, 8, i + 3);
        }
        roundRect(rgba, TRIGGER, 5, 1);
        roundRect(rgba, BUMPER, 3, 0);
        for (int y = 0; y < 9; y++) {
            int span = 4 - Math.abs(4 - y);
            for (int x = 0; x <= span; x++) {
                set(rgba, ARROW_LEFT, 4 - x, y);
                set(rgba, ARROW_RIGHT, x, y);
            }
        }
        return rgba;
    }

    private static void disc(byte[] rgba, Mask mask, int ringWidth) {
        float c = (mask.w() - 1) / 2f;
        float outer = mask.w() / 2f;
        for (int y = 0; y < mask.h(); y++) {
            for (int x = 0; x < mask.w(); x++) {
                float d = (float) Math.hypot(x - c, y - c);
                if (d <= outer - 0.25f && (ringWidth == 0 || d >= outer - ringWidth - 0.75f)) set(rgba, mask, x, y);
            }
        }
    }

    /** Rounded rectangle; {@code taper} narrows the bottom edge for trigger shapes. */
    private static void roundRect(byte[] rgba, Mask mask, int radius, int taper) {
        for (int y = 0; y < mask.h(); y++) {
            int inset = taper * y / Math.max(1, mask.h() / 2) / 2;
            for (int x = inset; x < mask.w() - inset; x++) {
                int dx = Math.max(0, Math.max(radius - x, x - (mask.w() - 1 - radius)));
                int dy = Math.max(0, Math.max(radius - y, y - (mask.h() - 1 - radius)));
                if (dx * dx + dy * dy <= radius * radius) set(rgba, mask, x, y);
            }
        }
    }

    private static void set(byte[] rgba, Mask mask, int x, int y) {
        if (x < 0 || y < 0 || x >= mask.w() || y >= mask.h()) return;
        int offset = ((mask.y() + y) * ATLAS_WIDTH + mask.x() + x) * 4;
        rgba[offset] = rgba[offset + 1] = rgba[offset + 2] = rgba[offset + 3] = (byte) 255;
    }
}
