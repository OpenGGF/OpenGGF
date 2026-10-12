package com.openggf.mods.scene.art;

/**
 * Pure positions for the four elements returned by {@code SceneRomArt.titleCard}.
 * Arrival moves 16 pixels per row; release moves 32, with element offsets 1, 3, 5 and 7.
 * Callers own their clocks, hold policy, optional pieces, replacement text and completion.
 * Coordinates use the ROM's 320-pixel layout; callers add their viewport offset.
 */
@com.openggf.game.ModApi
public final class TitleCardPresentation {
    private TitleCardPresentation() { }

    @com.openggf.game.ModApi
    public record Position(float x, float y) { }

    public static int arrivalFrames() { return 28; }
    public static int holdFrames() { return 90; }

    /** Default timed release. Nonpositive values apply no exit displacement. */
    public static long automaticExitElapsed(long elapsed) {
        if (elapsed < 0) throw new IllegalArgumentException("Arrival elapsed must be nonnegative");
        return elapsed - arrivalFrames() - holdFrames();
    }

    /** The width-dependent presentation duration used by a caller that waits for the last piece. */
    public static int durationFrames(int width) {
        if (width < 1) throw new IllegalArgumentException("Viewport width must be positive");
        return arrivalFrames() + holdFrames() + 7 + (int)((width + 240L) / 32);
    }

    /**
     * {@code element} is banner, name, ZONE or act (0..3). {@code arrivalElapsed} starts at zero;
     * {@code exitElapsed} stays zero during an unlimited hold and starts at one on release.
     * A timed caller can use {@link #automaticExitElapsed}. Absent provider frames are skipped by callers.
     */
    public static Position position(int element, long arrivalElapsed, long exitElapsed) {
        if (element < 0 || element > 3) throw new IllegalArgumentException("Title element must be 0..3");
        if (arrivalElapsed < 0) throw new IllegalArgumentException("Arrival elapsed must be nonnegative");
        int startX = switch (element) { case 0 -> 96; case 1 -> 480; case 2 -> 636; default -> 708; };
        int restX = switch (element) { case 0 -> 96; case 1 -> 160; case 2 -> 252; default -> 260; };
        int restY = switch (element) { case 0 -> 64; case 1 -> 96; case 2 -> 128; default -> 160; };
        // Once arrived, a loading caller may hold indefinitely; cap before multiplication.
        int travelled = (int)Math.min(arrivalElapsed, arrivalFrames()) * 16;
        float x = Math.max(restX, startX - travelled);
        float y = element == 0 ? Math.min(restY, -112 + travelled) : restY;
        int exitTick = element * 2 + 1;
        if (exitElapsed >= exitTick) {
            float away = (float)(exitElapsed - exitTick + 1) * 32;
            if (element == 0) y -= away; else x += away;
        }
        return new Position(x, y);
    }
}
