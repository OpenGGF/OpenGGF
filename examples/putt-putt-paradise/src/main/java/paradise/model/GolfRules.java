package paradise.model;

/** Integer golf tuning; engine adapters supply the ROM-backed physics and native sounds. */
public final class GolfRules {
    public static final int MAX_ELEVATION_DEGREES = 75;
    public static final int ELEVATION_STEP_DEGREES = 1;
    public static final int MAX_CHARGE = 500;
    public static final int MAX_POWER = 1000;
    public static final int METER_PERIOD_TICKS = 120;
    public static final int CHARGE_FEEDBACK_TICKS = 12;
    public static final int PRE_RELEASE_TICKS = 18;
    // ROM velocity units (256 = one pixel/step). Provisional range for route tuning.
    public static final int MIN_SPEED_FIXED = 0x80;
    public static final int MAX_SPEED_FIXED = 0xC00;

    private GolfRules() { }

    public static int speedFixed(int normalizedPower) {
        if (normalizedPower < 0 || normalizedPower > MAX_POWER) {
            throw new IllegalArgumentException("Power must be in 0.." + MAX_POWER);
        }
        return MIN_SPEED_FIXED + (MAX_SPEED_FIXED - MIN_SPEED_FIXED) * normalizedPower / MAX_POWER;
    }

    /** Triangle-wave meter: zero, peak, zero, with no accuracy or random angle stage. */
    public static int chargeAtPhase(int phase) {
        int position = Math.floorMod(phase, METER_PERIOD_TICKS);
        int half = METER_PERIOD_TICKS / 2;
        return MAX_CHARGE * (position <= half ? position : METER_PERIOD_TICKS - position) / half;
    }
}
