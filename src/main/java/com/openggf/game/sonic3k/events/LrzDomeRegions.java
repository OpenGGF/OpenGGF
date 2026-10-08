package com.openggf.game.sonic3k.events;

/**
 * {@code sub_56DCA} and its table {@code word_56F88} (sonic3k.asm:115457-115497, :115645-115649):
 * the three Lava Reef act 1 boxes inside which the background stops scrolling with the camera and
 * locks to the dome.
 *
 * <p>The table is three five-word rows -- X min, X max, Y min, Y max, threshold -- and
 * {@code loc_56DDC} walks them with {@code cmp.w (a5)+,d0} four times:
 * {@code blo} out when {@code X < min}, {@code bhi} out when {@code X > max} (so <b>X max is
 * inclusive</b>), {@code blo} out when {@code Y < min}, and {@code blo} <i>in</i> when
 * {@code Y < max} (so <b>Y max is exclusive</b>). All four are unsigned.
 *
 * <p>Being inside the box is only the gate. Each row then tests one threshold against one
 * coordinate, and the test's sense inverts with {@code Events_bg+$00}, the "already locked" word:
 * <ul>
 *   <li>Row 0 ({@code loc_56E0A}): lock when {@code X >= $1B00}, release when {@code X < $1B00}.</li>
 *   <li>Row 1 ({@code loc_56E1C}): lock when {@code X < $22C0}, release when {@code X >= $22C0}.</li>
 *   <li>Row 2 ({@code loc_56E2E}): lock when {@code Y >= $7A0}, release when {@code Y < $7A0}.</li>
 * </ul>
 * So each region is entered from its own ROM side and left again across the same line, and outside
 * every box the routine simply returns: a locked background is never released by walking out of
 * the box, only by crossing that box's own threshold.
 *
 * <p>Only Player 1 is read ({@code Player_1+x_pos} / {@code +y_pos}); a sidekick's position never
 * enters or leaves a region.
 */
public final class LrzDomeRegions {

    /** The coordinate a row's threshold is compared against. */
    public enum Axis { X, Y }

    /**
     * One {@code word_56F88} row. {@code lockWhenBelowThreshold} carries the branch sense:
     * row 1 is the only one that locks on the low side.
     */
    public record Region(int xMin, int xMax, int yMin, int yMax, int threshold, Axis axis,
            boolean lockWhenBelowThreshold) {

        /** {@code loc_56DDC}: X max inclusive, Y max exclusive, all four unsigned. */
        public boolean containsPlayer(int x, int y) {
            int px = x & 0xFFFF;
            int py = y & 0xFFFF;
            return Integer.compareUnsigned(px, xMin) >= 0
                    && Integer.compareUnsigned(px, xMax) <= 0
                    && Integer.compareUnsigned(py, yMin) >= 0
                    && Integer.compareUnsigned(py, yMax) < 0;
        }

        private int coordinate(int x, int y) {
            return (axis == Axis.X ? x : y) & 0xFFFF;
        }

        /** The row's own lock test, {@code Events_bg+$00} clear. */
        public boolean locksAt(int x, int y) {
            int value = coordinate(x, y);
            return lockWhenBelowThreshold
                    ? Integer.compareUnsigned(value, threshold) < 0
                    : Integer.compareUnsigned(value, threshold) >= 0;
        }

        /** The same test with the branch inverted, {@code Events_bg+$00} set. */
        public boolean releasesAt(int x, int y) {
            return !locksAt(x, y);
        }
    }

    /** {@code word_56F88} (sonic3k.asm:115645-115649), in table order. */
    private static final Region[] REGIONS = {
            new Region(0x1AC0, 0x1B40, 0x840, 0x8C0, 0x1B00, Axis.X, false),
            new Region(0x2240, 0x2340, 0x840, 0x880, 0x22C0, Axis.X, true),
            new Region(0x20C0, 0x2180, 0x740, 0x800, 0x07A0, Axis.Y, false),
    };

    private LrzDomeRegions() {
    }

    public static int count() {
        return REGIONS.length;
    }

    public static Region region(int index) {
        return REGIONS[index];
    }

    /**
     * {@code loc_56DDC}'s walk: the first row whose box holds Player 1, or {@code -1}. The ROM
     * stops at the first match ({@code bra} into {@code loc_56DFA}), so overlapping boxes would
     * resolve in table order; these three do not overlap.
     */
    public static int regionIndexAt(int playerX, int playerY) {
        for (int index = 0; index < REGIONS.length; index++) {
            if (REGIONS[index].containsPlayer(playerX, playerY)) {
                return index;
            }
        }
        return -1;
    }

    /** What {@code sub_56DCA} does this frame, given {@code Events_bg+$00}. */
    public enum Transition { NONE, LOCK, RELEASE }

    /**
     * {@code sub_56DCA} in one call. Outside every box the routine returns without touching
     * {@code Events_bg+$00}, which is why a locked background survives leaving the box.
     */
    public static Transition evaluate(int playerX, int playerY, boolean alreadyLocked) {
        int index = regionIndexAt(playerX, playerY);
        if (index < 0) {
            return Transition.NONE;
        }
        Region region = REGIONS[index];
        if (!alreadyLocked) {
            return region.locksAt(playerX, playerY) ? Transition.LOCK : Transition.NONE;
        }
        return region.releasesAt(playerX, playerY) ? Transition.RELEASE : Transition.NONE;
    }

    /**
     * {@code sub_56DAC} (sonic3k.asm:115442-115452), the locked background's own camera copies:
     * {@code Camera_Y_pos_BG_copy = Camera_Y_pos_copy - $788 + _unkEE9C} and
     * {@code Camera_X_pos_BG_copy = Camera_X_pos_copy - $1500}. The Y term is what ties the
     * background to the dome platform's own rise and fall.
     */
    public static int lockedBackgroundY(int cameraYCopy, int platformPhase) {
        return (cameraYCopy - 0x788 + platformPhase) & 0xFFFF;
    }

    /** {@code sub_56DAC}'s X half. */
    public static int lockedBackgroundX(int cameraXCopy) {
        return (cameraXCopy - 0x1500) & 0xFFFF;
    }
}
