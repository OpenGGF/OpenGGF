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
    public static final int EXTRA_CHARGE_INTERVAL_TICKS = 3;
    public static final int EXTRA_CHARGE_POWER_STEP = 250;
    // ROM velocity units (256 = one pixel/step), verified on complete EHZ routes.
    public static final int MIN_SPEED_FIXED = 0x80;
    public static final int MAX_SPEED_FIXED = 0xC00;

    public static final int SETTLE_DWELL_TICKS = 20;
    public static final int LOST_BALL_MARGIN = 128;
    public static final int WATCHDOG_TICKS = 3600;

    /** Includes the deterministic shot and resolution rules in the direct-connect handshake. */
    public static String fingerprint() {
        String rules = "golf-v1:" + MAX_ELEVATION_DEGREES + ":" + ELEVATION_STEP_DEGREES + ":"
                + MAX_CHARGE + ":" + MAX_POWER + ":" + METER_PERIOD_TICKS + ":"
                + CHARGE_FEEDBACK_TICKS + ":" + PRE_RELEASE_TICKS + ":" + MIN_SPEED_FIXED + ":"
                + MAX_SPEED_FIXED + ":" + SETTLE_DWELL_TICKS + ":" + LOST_BALL_MARGIN + ":" + WATCHDOG_TICKS
                + ":" + EXTRA_CHARGE_INTERVAL_TICKS + ":" + EXTRA_CHARGE_POWER_STEP
                + ":damage,death,finish,settled,lost,watchdog:gate32,-96,48:independent-worlds:ehz1,ehz2";
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rules.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

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
