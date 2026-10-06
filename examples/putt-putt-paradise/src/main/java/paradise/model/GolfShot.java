package paradise.model;

/** Locked power and signed backspin (-100) / neutral (0) / topspin (+100). */
public record GolfShot(int direction, int elevationDegrees, int normalizedPower, int spin) {
    public GolfShot {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        if (elevationDegrees < 0 || elevationDegrees > GolfRules.MAX_ELEVATION_DEGREES)
            throw new IllegalArgumentException("Unsupported elevation");
        if (normalizedPower < 0 || normalizedPower > GolfRules.MAX_POWER
                || spin < -GolfRules.MAX_SPIN || spin > GolfRules.MAX_SPIN || (elevationDegrees == 0 && spin != 0))
            throw new IllegalArgumentException("Unsupported power/spin");
    }
    public int speedFixed() { return GolfRules.speedFixed(normalizedPower); }
    public boolean isPutt() { return elevationDegrees == 0; }
}
