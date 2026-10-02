package infinite;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectServices;
import java.util.Locale;

/** Tiny code-drawn HUD glyphs; no external art or GPU-owned state to restore. */
public final class CourseHud {
    private CourseHud() { }
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ.-:";
    private static final String GLYPHS =
            "111101101101111"
            + "010110010010111"
            + "111001111100111"
            + "111001111001111"
            + "101101111001001"
            + "111100111001111"
            + "111100111101111"
            + "111001010010010"
            + "111101111101111"
            + "111101111001111"
            + "010101111101101"
            + "110101110101110"
            + "111100100100111"
            + "110101101101110"
            + "111100110100111"
            + "111100110100100"
            + "111100101101111"
            + "101101111101101"
            + "111010010010111"
            + "001001001101111"
            + "101101110101101"
            + "100100100100111"
            + "101111111101101"
            + "101111111111101"
            + "111101101101111"
            + "111101111100100"
            + "111101101111001"
            + "110101110101101"
            + "111100111001111"
            + "111010010010010"
            + "101101101101111"
            + "101101101101010"
            + "101101111111101"
            + "101101010101101"
            + "101101010010010"
            + "111001010100111"
            + "000000000000010"
            + "000000111000000"
            + "000010000010000";
    public static String speedText(CourseController course) {
        return String.format(Locale.ROOT, "SPEED %.2fX", course.speedMultiplier());
    }
    public static String countdownText(CourseController course) {
        return course.speedMultiplier() >= 32 ? "MAX SPEED" : "NEXT " + course.secondsRemaining();
    }
    public static String warningText(CourseController course) {
        return !course.gameOver() && course.speedMultiplier() < 32 && course.secondsRemaining() <= 5
                ? "SPEED UP IN " + course.secondsRemaining() : "";
    }
    public static void draw(ObjectServices services, CourseController course) {
        text(services, "SCORE " + services.gameState().getScore(), 16, 8, true);
        text(services, speedText(course) + "  " + countdownText(course), 16, 24, true);
        text(services, "RINGS " + services.levelGamestate().getRings(), 16, 40, true);
        String warning = warningText(course);
        if (!warning.isEmpty()) {
            text(services, warning, (services.camera().getWidth() - warning.length() * 12) / 2,
                    64, true, 3);
        }
        if (course.gameOver()) {
            String message = "GAME OVER";
            text(services, message, (services.camera().getWidth() - message.length() * 8) / 2, 96, true);
            String prompt = "PRESS SPACE TO RESTART";
            if (course.restartReady()) {
                text(services, prompt, (services.camera().getWidth() - prompt.length() * 8) / 2, 120, true);
            }
        }
    }
    private static void text(ObjectServices services, String text, int x, int y, boolean shadow) {
        text(services, text, x, y, shadow, 2);
    }
    private static void text(ObjectServices services, String text, int x, int y, boolean shadow, int scale) {
        if (shadow) text(services, text, x + 1, y + 1, false, scale);
        int cameraX = services.camera().getX();
        int cameraY = services.camera().getY();
        for (int letter = 0; letter < text.length(); letter++) {
            int index = ALPHABET.indexOf(text.charAt(letter));
            if (index < 0) continue;
            String glyph = GLYPHS.substring(index * 15, index * 15 + 15);
            for (int pixel = 0; pixel < 15; pixel++) {
                if (glyph.charAt(pixel) != '1') continue;
                int px = cameraX + x + letter * 4 * scale + pixel % 3 * scale;
                int py = cameraY + y + pixel / 3 * scale;
                services.graphicsManager().registerCommand(new GLCommand(GLCommand.CommandType.RECTI,
                        0, shadow ? 1f : 0f, shadow ? 1f : 0f, shadow ? 0.5f : 0f,
                        px, py, px + scale, py + scale));
            }
        }
    }
}
