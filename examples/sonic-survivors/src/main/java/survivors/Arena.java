package survivors;

/** The current stage's walled arena, in level pixels: the walls and the floor surface's height range. */
record Arena(int stage, int act, int left, int right, int floorTop, int floorBottom) {
    static Arena of(int stage, int act) {
        int[] a = Stages.arena(stage, act);
        return new Arena(stage, act, a[0], a[1], a[2], a[3]);
    }

    int centreX() { return (left + right) / 2; }
    int width() { return right - left; }

    /** Clamps an x into the arena, keeping {@code margin} from each wall. */
    int clampX(int x, int margin) { return Math.max(left + margin, Math.min(right - margin, x)); }
}
