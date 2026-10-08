package slaytherobotnik.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.AtlasFont;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's small all-caps pixel font, read from {@code art/font.txt} (see that file for the
 * format). Glyphs are packed into one white image, so text is tinted per draw call and
 * batches with everything else.
 *
 * <p>Besides plain text it understands the card markup produced by
 * {@link slaytherobotnik.core.Cards#describe}: {@code <g>..</g>} green, {@code <r>..</r>}
 * red and {@code <k>..</k>} gold keywords, and wraps such text to a width.
 */
public final class SmallFont {
    public static final int HEIGHT = 5;
    public static final int LINE = 7;

    private final AtlasFont font;

    public SmallFont(byte[] fontText) {
        font = AtlasFont.parse(fontText, HEIGHT);
    }

    /** Width in pixels of plain text (markup tags are ignored). */
    public int width(String text) {
        return font.width(strip(text));
    }

    /** Draws plain text. */
    public void draw(SceneCanvas canvas, String text, int x, int y, int argb) {
        draw(canvas, text, x, y, argb, 1);
    }

    /** Draws plain text at an integer scale. */
    public void draw(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        font.draw(canvas, text, x, y, argb, scale);
    }

    /** Text with a 1-pixel drop shadow, readable over any background. */
    public void drawShadowed(SceneCanvas canvas, String text, int x, int y, int argb) {
        drawShadowed(canvas, text, x, y, argb, 1);
    }

    public void drawShadowed(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        draw(canvas, text, x + scale, y + scale, 0xC0000000, scale);
        draw(canvas, text, x, y, argb, scale);
    }

    /** Text with a full 1-pixel outline (for numbers over sprites). */
    public void drawOutlined(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    draw(canvas, text, x + dx, y + dy, 0xFF000000, scale);
                }
            }
        }
        draw(canvas, text, x, y, argb, scale);
    }

    public void drawCentered(SceneCanvas canvas, String text, int centerX, int y, int argb) {
        drawShadowed(canvas, text, centerX - width(text) / 2, y, argb);
    }

    // ------------------------------------------------------------------ markup

    /** Removes {@code <g> <r> <k>} tags. */
    public static String strip(String markup) {
        return markup.replace("<g>", "").replace("</g>", "").replace("<r>", "").replace("</r>", "")
                .replace("<k>", "").replace("</k>", "");
    }

    /** Wraps markup text to {@code maxWidth}; newlines in the text force breaks. Tags stay balanced per line. */
    public List<String> wrap(String markup, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : markup.split("\n")) {
            StringBuilder line = new StringBuilder();
            String openTag = null;
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && width(candidate) > maxWidth) {
                    String finished = line.toString();
                    if (openTag != null) {
                        finished = finished + "</" + openTag.substring(1);
                    }
                    lines.add(finished);
                    line = new StringBuilder(openTag == null ? word : openTag + word);
                } else {
                    line = new StringBuilder(candidate);
                }
                openTag = unclosedTag(line.toString());
            }
            if (line.length() > 0 || paragraph.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private static String unclosedTag(String text) {
        String open = null;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("</", i)) {
                open = null;
                i = text.indexOf('>', i) + 1;
            } else if (text.charAt(i) == '<' && i + 2 < text.length() && text.charAt(i + 2) == '>') {
                open = text.substring(i, i + 3);
                i += 3;
            } else {
                i++;
            }
        }
        return open;
    }

    /** Draws one line of markup; {@code base} is the colour outside tags. */
    public void drawMarkup(SceneCanvas canvas, String markup, int x, int y, int base) {
        int cx = x;
        int color = base;
        int i = 0;
        StringBuilder run = new StringBuilder();
        while (i <= markup.length()) {
            boolean end = i == markup.length();
            boolean tag = !end && markup.charAt(i) == '<' && markup.indexOf('>', i) > i;
            if (end || tag) {
                if (run.length() > 0) {
                    drawShadowed(canvas, run.toString(), cx, y, color);
                    // width() already counts a trailing space; +1 is the usual letter gap.
                    cx += width(run.toString()) + 1;
                    run.setLength(0);
                }
                if (end) {
                    break;
                }
                int close = markup.indexOf('>', i);
                String name = markup.substring(i + 1, close);
                color = switch (name) {
                    case "g" -> Colors.TEXT_GOOD;
                    case "r" -> Colors.TEXT_BAD;
                    case "k" -> Colors.TEXT_KEYWORD;
                    default -> base;
                };
                i = close + 1;
                continue;
            }
            run.append(markup.charAt(i));
            i++;
        }
    }

    /** Draws wrapped markup lines centred on {@code centerX}. Returns the height used. */
    public int drawParagraphCentered(SceneCanvas canvas, List<String> lines, int centerX, int y, int base) {
        int cy = y;
        for (String line : lines) {
            drawMarkup(canvas, line, centerX - width(line) / 2, cy, base);
            cy += LINE;
        }
        return cy - y;
    }
}
