package infinite;

import com.openggf.mods.ui.BitmapFont;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.level.objects.ObjectServices;
import java.util.Locale;

/** Tiny code-drawn HUD glyphs; no external art or GPU-owned state to restore. */
public final class CourseHud {
    CourseHud() { }
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ.-:>!";
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
            + "000010000010000"
            + "100010001010100"
            + "010010010000010";
    private final BitmapFont font = BitmapFont.binary(ALPHABET, GLYPHS, 3, 5, 4);
    public static String speedText(CourseController course) {
        return String.format(Locale.ROOT, "SPEED %.2fX", course.speedMultiplier());
    }
    public static String countdownText(CourseController course) {
        return course.speedMultiplier() >= 32 ? "MAX SPEED" : "NEXT " + course.secondsRemaining();
    }
    public static String warningText(CourseController course) {
        return !course.gameOver() && course.resumePhase() == CourseController.RESUME_NONE && course.speedMultiplier() < 32 && course.secondsRemaining() <= 5
                ? "SPEED UP IN " + course.secondsRemaining() : "";
    }
    /** Shown over the run while it overtakes the zone's top score, and on the death screen for one. */
    public static final String TOP_SCORE = "NEW TOP SCORE!";
    /** The death screen's congratulation: a new top score, or the run's place in the zone's top 10. */
    public static String rankText(CourseController course) {
        int rank = course.rank();
        return rank == 1 ? TOP_SCORE : rank > 1 ? "RANK " + rank + " OF " + Leaderboard.SIZE : "";
    }
    /** One line of the top-10 board: place, score and the speed the run reached. */
    public static String boardLine(int place, Leaderboard.Entry entry) {
        return entry == null ? String.format(Locale.ROOT, "%2d %7s", place, "-")
                : String.format(Locale.ROOT, "%2d %7d %5.2fX", place, entry.score(), entry.speed());
    }
    public void draw(ObjectServices services, CourseController course) {
        float board = course.openingBoardAlpha();
        if (board > 0) drawBoard(services, course, board);
        text(services, topText(course), 16, 8, true);
        text(services, "SCORE " + services.gameState().getScore(), 16, 24, true);
        text(services, speedText(course) + "  " + countdownText(course), 16, 40, true);
        String rings = "RINGS " + services.levelGamestate().getRings();
        if (ringsFlashing(course)) {
            // In danger the ring count flashes red, as the stock counter does at zero.
            glyphs(services, rings, 17, 57, 2, 0f, 0f, 0f, 1f);
            glyphs(services, rings, 16, 56, 2, 1f, 0.15f, 0.1f, 1f);
        } else {
            text(services, rings, 16, 56, true);
        }
        text(services, "  LIVES " + course.displayLives(), 16 + rings.length() * 8, 56, true);
        String warning = warningText(course);
        if (!warning.isEmpty()) {
            text(services, warning, (services.camera().getWidth() - warning.length() * 12) / 2,
                    72, true, 3);
        }
        int speedUp = course.speedUpFrames();
        if (speedUp > 0) {
            // The new speed takes the countdown's place, fading over its last quarter second.
            String announced = speedText(course);
            glyphs(services, announced, (services.camera().getWidth() - announced.length() * 12) / 2 + 1, 73, 3,
                    0f, 0f, 0f, Math.min(1f, speedUp / 15f));
            glyphs(services, announced, (services.camera().getWidth() - announced.length() * 12) / 2, 72, 3,
                    1f, 0.82f, 0.19f, Math.min(1f, speedUp / 15f));
        }
        if (course.celebrating() && course.celebrateFrames() / 6 % 2 == 0) {
            glyphs(services, TOP_SCORE, (services.camera().getWidth() - TOP_SCORE.length() * 12) / 2, 88, 3,
                    1f, 0.82f, 0.19f, 1f);
        }
        if (course.resumePhase() == CourseController.RESUME_READY) {
            String ready = "READY";
            glyphs(services, ready, (services.camera().getWidth() - ready.length() * 12) / 2, 88, 3, 1f, 0.82f, 0.19f, 1f);
            centred(services, "RIGHT OR JUMP TO GO", 116);
        } else if (course.goFrames() > 0) {
            String go = "GO!";
            glyphs(services, go, (services.camera().getWidth() - go.length() * 16) / 2, 84, 4, 1f, 0.82f, 0.19f,
                    Math.min(1f, course.goFrames() / 10f));
        }
        // CONTINUE's rewind passes back through the death; its menu stays down.
        if (!course.gameOver() || course.rewinding()) return;
        String rank = rankText(course);
        if (!rank.isEmpty()) {
            int scale = course.rank() == 1 ? 3 : 2;
            glyphs(services, rank, (services.camera().getWidth() - rank.length() * 4 * scale) / 2, 64, scale,
                    1f, 0.82f, 0.19f, 1f);
        }
        centred(services, course.canContinue() ? "LIVES LEFT " + course.displayLives() : "GAME OVER", 88);
        int top = 112;
        if (!course.restartReady()) return;
        var lines = menuLines(course);
        // Every option shares a left edge so the cursor column lines up.
        int width = lines.stream().mapToInt(String::length).max().orElse(0);
        int x = (services.camera().getWidth() - width * 8) / 2;
        for (int i = 0; i < lines.size(); i++) text(services, lines.get(i), x, top + i * 16, true);
        centred(services, "PRESS SPACE", top + lines.size() * 16 + 8);
    }
    /** The zone's top score, above the run's own score. */
    public static String topText(CourseController course) {
        return "TOP " + course.topScore();
    }
    /** The ring count flashes red (eight updates on, eight off) while Sonic is in danger. */
    public static boolean ringsFlashing(CourseController course) {
        return course.inDanger() && course.dangerFrames() / 8 % 2 == 0;
    }
    public static String livesText(ObjectServices services, CourseController course) {
        return "RINGS " + services.levelGamestate().getRings() + "  LIVES " + course.displayLives();
    }
    /** CONTINUE (with a spare life), RESTART and EXIT; the cursor marks the choice SPACE (button A) confirms. */
    public static java.util.List<String> menuLines(CourseController course) {
        String selected = course.menuSelection();
        return course.menuOptions().stream().map(option -> (option.equals(selected) ? "> " : "  ") + option).toList();
    }
    private void centred(ObjectServices services, String text, int y) {
        text(services, text, (services.camera().getWidth() - text.length() * 8) / 2, y, true);
    }
    private void text(ObjectServices services, String text, int x, int y, boolean shadow) {
        text(services, text, x, y, shadow, 2);
    }
    /** The zone's top 10, top right, over a translucent panel that fades as the run gets going. */
    private void drawBoard(ObjectServices services, CourseController course, float alpha) {
        var top = services.gameService(Leaderboard.class).top(course.zone());
        int width = 168, left = services.camera().getWidth() - width - 8, y = 56;
        int height = 26 + Leaderboard.SIZE * 12;
        canvas(services).fill(left, y, width, height, colour(0.04f, 0.06f, 0.2f, 0.7f * alpha));
        glyphs(services, "TOP 10 SCORES", left + 8, y + 6, 2, 1f, 0.82f, 0.19f, alpha);
        for (int i = 0; i < Leaderboard.SIZE; i++) {
            String line = boardLine(i + 1, i < top.size() ? top.get(i) : null);
            glyphs(services, line, left + 8, y + 22 + i * 12, 2, 1f, 1f, 1f, alpha);
        }
    }

    private void text(ObjectServices services, String text, int x, int y, boolean shadow, int scale) {
        if (shadow) text(services, text, x + 1, y + 1, false, scale);
        if (shadow) glyphs(services, text, x, y, scale, 1f, 1f, 0.5f, 1f);
        else glyphs(services, text, x, y, scale, 0f, 0f, 0f, 1f);
    }

    /** Draws {@code text} in one colour; below full opacity it blends over the scene. */
    private void glyphs(ObjectServices services, String text, int x, int y, int scale,
                               float r, float g, float b, float alpha) {
        font.draw(canvas(services), text, x, y, scale, colour(r, g, b, alpha));
    }

    private LevelOverlayCanvas canvas(ObjectServices services) {
        return new LevelOverlayCanvas(services.graphicsManager(), services.camera().getWidth(), services.camera().getHeight());
    }
    private static int colour(float r, float g, float b, float alpha) {
        return Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24
                | Math.round(Math.clamp(r, 0f, 1f) * 255) << 16
                | Math.round(Math.clamp(g, 0f, 1f) * 255) << 8
                | Math.round(Math.clamp(b, 0f, 1f) * 255);
    }
}
