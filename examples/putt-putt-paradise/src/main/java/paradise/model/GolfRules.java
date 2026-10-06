package paradise.model;

/** Integer golf tuning; engine adapters supply the ROM-backed physics and native sounds. */
public final class GolfRules {
    public static final int MAX_ELEVATION_DEGREES = 90;
    public static final int VERTICAL_DRIFT_DIVISOR = 16;
    public static final int MIN_VERTICAL_DRIFT_FIXED = 0x40;
    public static final int ELEVATION_STEP_DEGREES = 1;
    public static final int MAX_POWER = 1000;
    public static final int POWER_SWEEP_TICKS = 120;
    public static final int POWER_QUANTUM = 2;
    public static final int SPIN_PERIOD_TICKS = 120;
    public static final int MAX_SPIN = 100;
    public static final int NEUTRAL_SPIN_WINDOW = 8;
    public static final int SPIN_STEP = 5;
    public static final int SPIN_FLIGHT_DIVISOR = 2;
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
        String rules = "golf-v2:kdc-spin-then-power:" + MAX_ELEVATION_DEGREES + ":" + ELEVATION_STEP_DEGREES + ":"
                + MAX_POWER + ":" + POWER_SWEEP_TICKS + ":" + POWER_QUANTUM + ":" + SPIN_PERIOD_TICKS + ":"
                + MAX_SPIN + ":" + NEUTRAL_SPIN_WINDOW + ":" + SPIN_STEP + ":" + SPIN_FLIGHT_DIVISOR
                + ":spin-flight-and-first-ground-impulse:"
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
    public static String fingerprint(RewindAllowance.Rules rewinds) {
        try {
            String rules = fingerprint() + ":rewind-v1:review:refund:90ticks:" + rewinds.perHole() + ":" + rewinds.perTurn();
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rules.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record LaunchVelocity(int x, int y, int ground) { }

    public static int verticalDriftFixed(int speedFixed) {
        return Math.max(MIN_VERTICAL_DRIFT_FIXED, speedFixed / VERTICAL_DRIFT_DIVISOR);
    }

    /** The same surface-relative departure for the shot and its power/spin guide. */
    public static LaunchVelocity launchVelocity(int direction, int elevationDegrees, int speedFixed, int surfaceAngle) {
        return launchVelocity(direction, elevationDegrees, speedFixed, surfaceAngle, 0);
    }

    public static LaunchVelocity launchVelocity(int direction, int elevationDegrees, int speedFixed, int surfaceAngle, int spin) {
        double elevation = Math.toRadians(elevationDegrees), angle = (surfaceAngle & 255) * Math.PI / 128;
        double tangent = direction * (elevationDegrees == MAX_ELEVATION_DEGREES
                ? verticalDriftFixed(speedFixed) : Math.cos(elevation) * speedFixed);
        double normal = Math.sin(elevation) * speedFixed;
        if (elevationDegrees > 0) tangent = Math.clamp(tangent * (1 + spin / (double) (MAX_SPIN * SPIN_FLIGHT_DIVISOR)),
                -MAX_SPEED_FIXED, MAX_SPEED_FIXED);
        return new LaunchVelocity((int) Math.round(tangent * Math.cos(angle) + normal * Math.sin(angle)),
                (int) Math.round(tangent * Math.sin(angle) - normal * Math.cos(angle)), (int) Math.round(tangent));
    }

    public static int speedFixed(int normalizedPower) {
        if (normalizedPower < 0 || normalizedPower > MAX_POWER) {
            throw new IllegalArgumentException("Power must be in 0.." + MAX_POWER);
        }
        return MIN_SPEED_FIXED + (MAX_SPEED_FIXED - MIN_SPEED_FIXED) * normalizedPower / MAX_POWER;
    }

    /** Dream Course-style power: one rise and fall, with the full-power frame hittable. */
    public static int powerAtPhase(int phase) {
        if (phase < 0 || phase > POWER_SWEEP_TICKS) throw new IllegalArgumentException("Invalid power clock");
        int half = POWER_SWEEP_TICKS / 2;
        // Retain the original shot-speed granularity when replacing the two
        // equal charge samples with one sweep, especially for very light putts.
        return (MAX_POWER / POWER_QUANTUM) * (phase <= half ? phase : POWER_SWEEP_TICKS - phase)
                / half * POWER_QUANTUM;
    }

    /** Repeating top/backspin marker, starting at centre. */
    public static int spinAtPhase(int phase) {
        int position = Math.floorMod(phase, SPIN_PERIOD_TICKS), quarter = SPIN_PERIOD_TICKS / 4;
        int value = position <= quarter ? MAX_SPIN * position / quarter
                : position <= 3 * quarter ? MAX_SPIN * (2 * quarter - position) / quarter
                : MAX_SPIN * (position - SPIN_PERIOD_TICKS) / quarter;
        return value;
    }

    public static int stoppedSpin(int phase, int target) {
        int marker = spinAtPhase(phase);
        return Math.abs(marker - target) <= NEUTRAL_SPIN_WINDOW ? target : marker;
    }

    /** Sonic adaptation: one tangent impulse at a chip's first unassisted landing, then native rolling. */
    public static int landingSpeed(int groundSpeed, GolfShot shot) {
        return Math.clamp(groundSpeed + shot.direction() * shot.speedFixed() * shot.spin() / MAX_SPIN,
                -MAX_SPEED_FIXED, MAX_SPEED_FIXED);
    }
}
