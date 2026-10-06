package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The engine's outlined menu font ({@code pixel-font.png}, the same sheet {@code PixelFont}
 * uses) decoded once into a {@link SceneImage} so scene text batches with everything else.
 * Engine-internal.
 */
final class SceneFontAtlas {
    static final int CELL_W = 16;
    static final int CELL_H = 16;
    static final int GLYPH_W = 9;
    static final int GLYPH_H = 10;
    static final int ADVANCE = 10;
    private static final int OFFSET_X = 7;
    private static final int OFFSET_Y = 5;
    private static final String[] ROWS = {
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyz",
            "0123456789_-+×÷=*/\\%@©®°#¦",
            "?¿!¡()[]{}<>«»:;‘’~`¬,.|£€",
            "$“”^&☺½¾&Δ"
    };

    private final SceneImage image;
    private final Map<Character, int[]> glyphs = new HashMap<>();

    private SceneFontAtlas(SceneImage image) {
        this.image = image;
        for (int row = 0; row < ROWS.length; row++) {
            String chars = ROWS[row];
            for (int col = 0; col < chars.length(); col++) {
                glyphs.putIfAbsent(chars.charAt(col),
                        new int[] {col * CELL_W + OFFSET_X, row * CELL_H + OFFSET_Y});
            }
        }
        glyphs.putIfAbsent('\'', glyphs.get('’'));
        glyphs.putIfAbsent('"', glyphs.get('”'));
    }

    static SceneFontAtlas load() {
        try (InputStream in = SceneFontAtlas.class.getClassLoader().getResourceAsStream("pixel-font.png")) {
            if (in == null) {
                return null;
            }
            return new SceneFontAtlas(ScenePng.decode(in.readAllBytes()));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    SceneImage image() {
        return image;
    }

    /** Top-left of the glyph in the atlas, or null for characters the font lacks (drawn as gaps). */
    int[] glyph(char c) {
        return glyphs.get(c);
    }

    static int width(String text) {
        return text == null || text.isEmpty() ? 0 : text.length() * ADVANCE - 1;
    }
}
