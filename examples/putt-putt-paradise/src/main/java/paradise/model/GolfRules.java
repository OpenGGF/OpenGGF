package paradise.model;

/** Integer golf tuning; engine adapters supply the ROM-backed physics and native sounds. */
public final class GolfRules {
    public static final int MAX_ELEVATION_DEGREES = 90;
    public static final int VERTICAL_DRIFT_DIVISOR = 16;
    public static final int MIN_VERTICAL_DRIFT_FIXED = 0x40;
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
                + ":" + VERTICAL_DRIFT_DIVISOR + ":" + MIN_VERTICAL_DRIFT_FIXED
                + ":up-spring-side-entry:vertical-ascent-drive"
                + ":damage,death,finish,settled,lost,watchdog:gate32,-96,48:independent-worlds:ehz1,ehz2";
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rules.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private GolfRules() { }

    public record LaunchVelocity(int x, int y, int ground) { }

    public static int verticalDriftFixed(int speedFixed) {
        return Math.max(MIN_VERTICAL_DRIFT_FIXED, speedFixed / VERTICAL_DRIFT_DIVISOR);
    }

    /** The same surface-relative departure for the shot and its half-power guide. */
    public static LaunchVelocity launchVelocity(int direction, int elevationDegrees, int speedFixed, int surfaceAngle) {
        double elevation = Math.toRadians(elevationDegrees), angle = (surfaceAngle & 255) * Math.PI / 128;
        double tangent = direction * (elevationDegrees == MAX_ELEVATION_DEGREES
                ? verticalDriftFixed(speedFixed) : Math.cos(elevation) * speedFixed);
        double normal = Math.sin(elevation) * speedFixed;
        return new LaunchVelocity((int) Math.round(tangent * Math.cos(angle) + normal * Math.sin(angle)),
                (int) Math.round(tangent * Math.sin(angle) - normal * Math.cos(angle)), (int) Math.round(tangent));
    }

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
