package paradise.model;

/** Locked meter result. Direction is -1/left or +1/right; zero elevation is a putt. */
public record GolfShot(int direction, int elevationDegrees, int firstCharge, int secondCharge) {
    public GolfShot {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        if (elevationDegrees < 0 || elevationDegrees > GolfRules.MAX_ELEVATION_DEGREES) {
            throw new IllegalArgumentException("Unsupported elevation");
        }
        if (firstCharge < 0 || firstCharge > GolfRules.MAX_CHARGE
                || secondCharge < 0 || secondCharge > GolfRules.MAX_CHARGE) {
            throw new IllegalArgumentException("Unsupported charge value");
        }
    }

    public int normalizedPower() { return firstCharge + secondCharge; }
    public int speedFixed() { return GolfRules.speedFixed(normalizedPower()); }
    public boolean isPutt() { return elevationDegrees == 0; }
}
