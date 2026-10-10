package starpost.ui;

import com.openggf.mods.scene.SceneCanvas;

/** Text and panel drawing in the engine's menu font (upper case, 9x10 glyphs). */
public final class Text {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int YELLOW = 0xFFFFDB00;
    public static final int GREY = 0xFFB6B6B6;
    public static final int RED = 0xFFFF4949;
    public static final int GREEN = 0xFF92FF49;
    public static final int BLUE = 0xFF6DB6FF;
    /** The panels' fill and edge: Green Hill's deep night blue and its checker brown. */
    public static final int PANEL = 0xE0101848;
    public static final int EDGE = 0xFFB66D24;
    public static final int EDGE_DARK = 0xFF492400;

    private Text() {
    }

    /** Text with a one-pixel drop shadow, readable over any background. */
    public static void shadow(SceneCanvas canvas, String text, int x, int y, int argb) {
        canvas.text(text, x + 1, y + 1, 0xC0000000);
        canvas.text(text, x, y, argb);
    }

    public static void centred(SceneCanvas canvas, String text, int y, int argb) {
        shadow(canvas, text, (canvas.width() - canvas.textWidth(text)) / 2, y, argb);
    }

    public static void right(SceneCanvas canvas, String text, int right, int y, int argb) {
        shadow(canvas, text, right - canvas.textWidth(text), y, argb);
    }

    /** A framed panel: checker-brown edge, dark fill. */
    public static void panel(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, PANEL);
        canvas.fill(x, y, w, 2, EDGE);
        canvas.fill(x, y + h - 2, w, 2, EDGE_DARK);
        canvas.fill(x, y, 2, h, EDGE);
        canvas.fill(x + w - 2, y, 2, h, EDGE_DARK);
    }

    /**
     * Shortens a name to fit a width: drops a trailing " SEEDS" first (the packet icon says it),
     * then whole words, then letters.
     */
    public static String fit(SceneCanvas canvas, String text, int width) {
        if (canvas.textWidth(text) <= width) {
            return text;
        }
        String s = text.endsWith(" SEEDS") ? text.substring(0, text.length() - 6) : text;
        while (canvas.textWidth(s) > width && s.contains(" ")) {
            s = s.substring(0, s.lastIndexOf(' '));
        }
        while (canvas.textWidth(s) > width && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** One line of description: the menu font when it fits, the compact font when it does not. */
    public static void note(SceneCanvas canvas, String text, int x, int y, int width, int argb) {
        if (canvas.textWidth(text) <= width) {
            shadow(canvas, text, x, y, argb);
        } else {
            com.openggf.mods.ui.CompactFont.shadowed(canvas, com.openggf.mods.ui.CompactFont.fit(text, width, 1),
                    x, y + 2, 1, argb, 0xFF000000);
        }
    }

    /** Splits text into lines no wider than {@code width} pixels. */
    public static java.util.List<String> wrap(SceneCanvas canvas, String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (canvas.textWidth(next) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
