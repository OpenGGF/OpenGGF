package survivors;

import com.openggf.level.objects.ObjectServices;
import java.util.Arrays;

/** Rebuilt spatial scratch for reward consolidation; owns no gameplay or rewind state.
 * Origin: Survivors ring clutter reduction, 2026-10-07. */
final class RingClusters {
    static final int RADIUS = 48, PERIOD = 15;
    private final int[] heads = new int[1024];
    private Pickup[] rings = new Pickup[128];
    private int[] next = new int[128];

    void merge(ObjectServices services) {
        Arrays.fill(heads, -1);
        int count = 0;
        try {
            for (var object : services.objectManager().getActiveObjects()) {
                if (!(object instanceof Pickup ring) || !ring.mergeable()) continue;
                if (count == rings.length) {
                    rings = Arrays.copyOf(rings, count * 2);
                    next = Arrays.copyOf(next, count * 2);
                }
                int bucket = bucket(Math.floorDiv(ring.getX(), RADIUS), Math.floorDiv(ring.getY(), RADIUS));
                rings[count] = ring;
                next[count] = heads[bucket];
                heads[bucket] = count++;
            }
            for (int i = 0; i < count; i++) {
                Pickup anchor = rings[i];
                if (!anchor.mergeable()) continue;
                long total = anchor.value();
                int cx = Math.floorDiv(anchor.getX(), RADIUS), cy = Math.floorDiv(anchor.getY(), RADIUS);
                // Two passes: validate the complete sum before consuming this neighbourhood.
                // Colour is only a value display, never a merge eligibility rule.
                for (int pass = 0; pass < 2; pass++) {
                    for (int y = cy - 1; y <= cy + 1; y++) {
                        for (int x = cx - 1; x <= cx + 1; x++) {
                            for (int j = heads[bucket(x, y)]; j >= 0; j = next[j]) {
                                Pickup other = rings[j];
                                if (other == anchor || !other.mergeable()
                                        || Math.floorDiv(other.getX(), RADIUS) != x
                                        || Math.floorDiv(other.getY(), RADIUS) != y) continue;
                                long dx = other.getX() - anchor.getX(), dy = other.getY() - anchor.getY();
                                if (dx * dx + dy * dy > RADIUS * RADIUS) continue;
                                if (pass == 0) total += other.value();
                                else anchor.absorb(other);
                            }
                        }
                    }
                    // Never overflow the stored reward or delete value. Extremely large
                    // piles stay separate instead of saturating their total.
                    if (total > Integer.MAX_VALUE) break;
                }
            }
        } finally {
            Arrays.fill(rings, 0, count, null);
        }
    }

    private int bucket(int x, int y) { return (x * 73856093 ^ y * 19349663) & (heads.length - 1); }
}
