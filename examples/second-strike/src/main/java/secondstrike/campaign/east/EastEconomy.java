package secondstrike.campaign.east;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Original-record economy selection shared by the Carnival Night, IceCap and
 * Launch Base recipes. Everything returned is an original ROM record: rings keep
 * their placement identity and order, monitors keep their placement identity.
 */
public final class EastEconomy {
    /** Scope budget: one to five original world-ring placements per ordinary act. */
    public static final int MAX_RINGS_PER_ACT = 5;
    /** Native ObjectId of a monitor, and subtype nibble of the speed-shoes box. */
    private static final int MONITOR = 0x01;
    private static final int SPEED_SHOES = 4;

    private EastEconomy() { }

    /** True when the original object inventory contains the exact identifying record. */
    public static boolean contains(List<ObjectSpawn> objects, int id, int x, int y) {
        Objects.requireNonNull(objects);
        return objects.stream().anyMatch(s -> s.objectId() == id && s.x() == x && s.y() == y);
    }

    /**
     * Ordered original subset keeping exactly the named ring identities. A
     * missing identity keeps nothing, so a stale list can only reduce the
     * budget, never invent a ring.
     */
    public static List<RingSpawn> keepRings(List<RingSpawn> source, Set<Integer> placementIds) {
        Objects.requireNonNull(source);
        if (placementIds.isEmpty() || placementIds.size() > MAX_RINGS_PER_ACT) {
            throw new IllegalArgumentException("Ring budget is 1.." + MAX_RINGS_PER_ACT + " per act");
        }
        return source.stream().filter(r -> placementIds.contains(r.placementId())).toList();
    }

    /**
     * An ordinary, non-speed-shoes native monitor close to an authored threat:
     * a box beside a recipe is a visible temptation that costs the collector.
     * Hidden/boss reward boxes use a different object id and are never selected.
     * {@code threats} are original placement coordinates {x, y}, ROM-verified by
     * the campaign tests.
     */
    public static boolean monitorBeside(ObjectSpawn spawn, int[][] threats, int dx, int dy) {
        if (spawn.ownerModId() != null || spawn.objectId() != MONITOR
                || (spawn.subtype() & 0x0F) == SPEED_SHOES) return false;
        for (int[] threat : threats) {
            if (Math.abs(threat[0] - spawn.x()) <= dx && Math.abs(threat[1] - spawn.y()) <= dy) return true;
        }
        return false;
    }
}
