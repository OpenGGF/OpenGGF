package eggsky.world;

import eggsky.core.Rng;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The galaxy: a grid of {@link #CELL}-light-year cells, about half holding a star, around a core
 * at the origin. Stars grow rarer in colour toward the edge and more exotic toward the core.
 * Systems are built on demand from the galaxy seed and cached.
 */
public final class Galaxy {
    public static final float CELL = 64f;
    /** How far from the core a new expedition begins. */
    public static final float START_RADIUS = 2600f;
    public static final float CORE_RADIUS = 90f;

    public final long seed;
    public final int number;
    private final HashMap<Long, StarSystem> cache = new HashMap<>();
    private final HashMap<Long, Boolean> empty = new HashMap<>();

    public Galaxy(long seed, int number) {
        this.seed = seed;
        this.number = number;
    }

    /** The system in a cell, or null when the cell is empty space. */
    public StarSystem system(int cellX, int cellY) {
        long key = StarSystem.pack(cellX, cellY);
        StarSystem cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (empty.containsKey(key)) {
            return null;
        }
        Rng rng = new Rng(Rng.hash(seed, 0x43454C4CL, key));
        StarSystem made = null;
        if (cellX == 0 && cellY == 0) {
            made = new StarSystem(seed, 0, 0, 0, 0, StarSystem.CORE);
        } else if (rng.chance(0.55)) {
            float x = (cellX + 0.15f + rng.nextFloat() * 0.7f) * CELL;
            float y = (cellY + 0.15f + rng.nextFloat() * 0.7f) * CELL;
            float r = (float) Math.hypot(x, y);
            if (r < CORE_RADIUS) {
                empty.put(key, Boolean.TRUE);
                return null;
            }
            float coreFactor = Math.max(0, 1 - r / START_RADIUS);
            int starClass;
            double roll = rng.nextDouble();
            if (roll < 0.025) {
                starClass = StarSystem.BLACK_HOLE;
            } else if (roll < 0.12 + 0.15 * coreFactor) {
                starClass = StarSystem.BLUE;
            } else if (roll < 0.26 + 0.2 * coreFactor) {
                starClass = StarSystem.GREEN;
            } else if (roll < 0.45 + 0.2 * coreFactor) {
                starClass = StarSystem.RED;
            } else {
                starClass = StarSystem.YELLOW;
            }
            made = new StarSystem(seed, cellX, cellY, x, y, starClass);
        }
        if (made == null) {
            empty.put(key, Boolean.TRUE);
        } else {
            cache.put(key, made);
        }
        return made;
    }

    public StarSystem system(long id) {
        return system(StarSystem.unpackX(id), StarSystem.unpackY(id));
    }

    /** Systems within {@code radius} light years of (x, y). */
    public List<StarSystem> near(float x, float y, float radius) {
        List<StarSystem> out = new ArrayList<>();
        int c0 = (int) Math.floor((x - radius) / CELL);
        int c1 = (int) Math.floor((x + radius) / CELL);
        int r0 = (int) Math.floor((y - radius) / CELL);
        int r1 = (int) Math.floor((y + radius) / CELL);
        for (int cy = r0; cy <= r1; cy++) {
            for (int cx = c0; cx <= c1; cx++) {
                StarSystem s = system(cx, cy);
                if (s != null && Math.hypot(s.x - x, s.y - y) <= radius) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** A yellow-star system with planets near the starting radius, where an expedition begins. */
    public StarSystem start() {
        Rng rng = new Rng(Rng.hash(seed, 0x53544152L));
        double angle = rng.nextDouble() * Math.PI * 2;
        float x = (float) Math.cos(angle) * START_RADIUS;
        float y = (float) Math.sin(angle) * START_RADIUS;
        StarSystem best = null;
        float bestDistance = Float.MAX_VALUE;
        for (StarSystem s : near(x, y, CELL * 4)) {
            float d = (float) Math.hypot(s.x - x, s.y - y);
            if (s.starClass == StarSystem.YELLOW && s.planetCount >= 3 && d < bestDistance) {
                best = s;
                bestDistance = d;
            }
        }
        if (best == null) {
            for (StarSystem s : near(x, y, CELL * 8)) {
                if (s.starClass == StarSystem.YELLOW || best == null) {
                    best = s;
                    if (s.starClass == StarSystem.YELLOW) {
                        break;
                    }
                }
            }
        }
        return best;
    }

    /** The core's own system. */
    public StarSystem core() {
        return system(0, 0);
    }
}
