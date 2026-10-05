package paradise.ui;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;

import java.util.Map;
import java.util.Objects;

/** Small code-drawn 5x7 font and screen primitives. No bitmap/ROM payloads or camera mutation. */
public final class GolfText {
    public static final int CREAM = 0xFFF3CB;
    public static final int GOLD = 0xFFD45B;
    public static final int INK = 0x123C48;
    public static final int BLUE = 0x205B73;
    public static final int GREEN = 0x278B69;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String[] GLYPHS = {
            "0E11111F111111", "1E11111E11111E", "0F10101010100F", "1E11111111111E",
            "1F10101E10101F", "1F10101E101010", "0F10101311110F", "1111111F111111",
            "1F04040404041F", "0702020212120C", "11121418141211", "1010101010101F",
            "111B1515111111", "11191513111111", "0E11111111110E", "1E11111E101010",
            "0E11111115120D", "1E11111E141211", "0F10100E01011E", "1F040404040404",
            "1111111111110E", "11111111110A04", "11111115151B11", "11110A040A1111",
            "11110A04040404", "1F01020408101F", "0E11131519110E", "040C040404040E",
            "0E11010204081F", "1E01010E01011E", "02060A121F0202", "1F10101E01011E",
            "0E10101E11110E", "1F010204080808", "0E11110E11110E", "0E11110F01010E"
    };
    private static final Map<Character, String> PUNCTUATION = Map.ofEntries(
            Map.entry('.', "00000000000004"), Map.entry(':', "00040000040000"),
            Map.entry('-', "0000001F000000"), Map.entry('_', "0000000000001F"),
            Map.entry('/', "01010204081010"), Map.entry('+', "0004041F040400"),
            Map.entry('=', "00001F001F0000"), Map.entry('>', "10080402040810"),
            Map.entry('<', "01020408040201"), Map.entry('!', "04040404040004"),
            Map.entry('?', "0E110102040004"), Map.entry('%', "191A0204080B13"),
            Map.entry('[', "0E08080808080E"), Map.entry(']', "0E02020202020E"),
            Map.entry('(', "02040808080402"), Map.entry(')', "08040202020408"));

    private GolfText() { }

    /** Immutable queued screen rectangle; always executes with camera origin zero. */
    public record Rect(int x, int y, int width, int height, int rgb, float alpha) implements GLCommandable {
        public Rect {
            if (width < 1 || height < 1 || !Float.isFinite(alpha) || alpha < 0 || alpha > 1) {
                throw new IllegalArgumentException("Invalid screen rectangle");
            }
        }
        @Override public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha,
                    x, y, x + width, y + height).execute(0, 0, cameraWidth, cameraHeight);
        }
    }

    public static void panel(GraphicsManager graphics, int x, int y, int width, int height, int rgb, float alpha) {
        Objects.requireNonNull(graphics, "graphics");
        if (width > 0 && height > 0 && alpha > 0) graphics.registerCommand(new Rect(x, y, width, height, rgb, alpha));
    }

    public static void frame(GraphicsManager graphics, int x, int y, int width, int height, int rgb) {
        panel(graphics, x, y, width, 1, rgb, 1); panel(graphics, x, y + height - 1, width, 1, rgb, 1);
        panel(graphics, x, y, 1, height, rgb, 1); panel(graphics, x + width - 1, y, 1, height, rgb, 1);
    }

    public static void line(GraphicsManager graphics, int x1, int y1, int x2, int y2, int rgb) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1, error = dx + dy;
        while (true) {
            panel(graphics, x1, y1, 1, 1, rgb, 1);
            if (x1 == x2 && y1 == y2) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x1 += sx; }
            if (twice <= dx) { error += dx; y1 += sy; }
        }
    }

    public static int width(String text, int scale) { return text.isEmpty() ? 0 : text.length() * 6 * scale - scale; }

    public static String fit(String text, int pixels, int scale) {
        Objects.requireNonNull(text, "text");
        int count = Math.max(0, (pixels + scale) / (6 * scale));
        if (text.length() <= count) return text;
        return count <= 3 ? ".".repeat(count) : text.substring(0, count - 3) + "...";
    }

    public static void draw(GraphicsManager graphics, String text, int x, int y) { draw(graphics, text, x, y, 1, CREAM); }

    public static void draw(GraphicsManager graphics, String text, int x, int y, int scale, int rgb) {
        Objects.requireNonNull(text, "text");
        if (scale < 1 || scale > 4) throw new IllegalArgumentException("Font scale must be 1..4");
        for (int letter = 0; letter < text.length(); letter++) {
            char c = Character.toUpperCase(text.charAt(letter));
            if (c == ' ') continue;
            int index = ALPHABET.indexOf(c);
            String glyph = index >= 0 ? GLYPHS[index] : PUNCTUATION.getOrDefault(c, PUNCTUATION.get('?'));
            for (int row = 0; row < 7; row++) {
                int bits = Integer.parseInt(glyph.substring(row * 2, row * 2 + 2), 16);
                for (int column = 0; column < 5; column++) {
                    if ((bits & (16 >> column)) == 0) continue;
                    int start = column;
                    while (column + 1 < 5 && (bits & (16 >> (column + 1))) != 0) column++;
                    panel(graphics, x + letter * 6 * scale + start * scale, y + row * scale,
                            (column - start + 1) * scale, scale, rgb, 1);
                }
            }
        }
    }

    public static void centered(GraphicsManager graphics, String text, int viewportWidth, int y, int scale, int rgb) {
        draw(graphics, text, (viewportWidth - width(text, scale)) / 2, y, scale, rgb);
    }

    public static void meter(GraphicsManager graphics, int x, int y, int width, int value, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Positive meter maximum required");
        panel(graphics, x, y, width, 8, INK, 1);
        int fill = (width - 2) * Math.clamp(value, 0, maximum) / maximum;
        panel(graphics, x + 1, y + 1, fill, 6, GOLD, 1);
        frame(graphics, x, y, width, 8, CREAM);
    }
}
