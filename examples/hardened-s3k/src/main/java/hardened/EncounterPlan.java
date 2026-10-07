package hardened;

import com.openggf.level.rings.RingSpawn;
import java.util.List;

/**
 * Frozen Post Two authoring data, surveyed against the locked-on ROM. It is a
 * local MHZ1 shelf encounter, not a replacement arena or a whole-act route.
 * Coordinates are native centres. The shelf ends before the stock spring and
 * mushroom platform; those mechanics and the physical starpost stay untouched.
 */
public final class EncounterPlan {
    public static final String ID = "post-two-ambush";
    public static final String ROM_SHA1 = "CFBF98C36C776677290A872547AC47C53D2761D6";
    public static final int LEVEL_INDEX = 0xce;
    public static final int START_X = 0x1d30, START_Y = 0x1a8;
    public static final int POST_X = 0x1d60, POST_Y = 0x1a8, POST_INDEX = 2;
    public static final int ROOM_LEFT = 0x1d10, ROOM_RIGHT = 0x1db8;
    public static final int ROOM_TOP = 0x100, ROOM_BOTTOM = 0x1d0;
    public static final int MIN_X = ROOM_LEFT, MAX_X = ROOM_RIGHT;
    public static final int MIN_Y = ROOM_TOP, MAX_Y = ROOM_BOTTOM;
    public static final int ATTACK_LEFT = 0x1d78, TRIGGER_X = 0x1d80;
    public static final int SENTRY_X = 0x1da0, SENTRY_Y = 0x1ad;
    public static final int EXIT_X = 0x1db0, EXIT_Y = 0x1ac;
    public static final int SAFE_RING_X = 0x1d70, SAFE_RING_Y = 0x1a8;

    public static final int TELL_TICKS = 36, LOCK_TICKS = 12;
    public static final int VOLLEY_COUNT = 2, VOLLEY_GAP_TICKS = 24, RECOVERY_TICKS = 90;
    public static final int PROJECTILE_CAP = 4, PROJECTILE_SPEED = 0x200, PROJECTILE_LIFE_TICKS = 180;

    private EncounterPlan() { }

    /**
     * The surveyed upper shelf has no stock rings. Retention is deliberately
     * empty here; the registered bounded placement plan authors one recovery
     * ring through the native ring owner, at SAFE_RING_X/Y.
     */
    public static List<RingSpawn> retainedRings() { return List.of(); }

    /** Conservative authoring guard; changing a pattern still requires route/replay evidence. */
    public static void validatePattern(int tellTicks, int projectileCap, int lifeTicks) {
        if (tellTicks < 24 || tellTicks > 120) throw new IllegalArgumentException("Tell must be 24..120 gameplay ticks");
        if (projectileCap < 2 || projectileCap > 4) throw new IllegalArgumentException("Projectile cap must be 2..4");
        if (lifeTicks < 1 || lifeTicks > 180) throw new IllegalArgumentException("Projectile life must be 1..180 ticks");
    }
}
