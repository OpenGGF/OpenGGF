package openggf.timeattack;

/** Display-only progress; finish placement must always use the reported frame count. */
public final class RaceProgressEstimate {
    private RaceProgressEstimate() { }

    public static int wrapDelta(int delta, int period) {
        return period > 0 ? Math.floorMod(delta + period / 2, period) - period / 2 : delta;
    }

    public static int nearestImage(int coordinate, int localCoordinate, int period) {
        return localCoordinate + wrapDelta(coordinate - localCoordinate, period);
    }

    public static double progress(int ordinal, int x, int levelWidth, int wrapWidth, int lapLineX) {
        double fraction = wrapWidth > 0 ? (double) Math.floorMod(x - lapLineX, wrapWidth) / wrapWidth
                : levelWidth > 0 ? Math.clamp((double) x / levelWidth, 0, Math.nextDown(1.0)) : 0;
        return Math.clamp(ordinal, 0, 15) + fraction;
    }
}
