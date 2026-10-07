package eggsky.world;

import eggsky.core.Rng;
import java.util.ArrayList;
import java.util.List;

/**
 * A star system: one cell of the galaxy grid that holds a star. Position, star class, planets,
 * trade economy and pirate activity all derive from the galaxy seed and the cell.
 */
public final class StarSystem {
    public static final int YELLOW = 0;
    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int BLUE = 3;
    public static final int BLACK_HOLE = 4;
    public static final int CORE = 5;

    public final long id;
    public final int cellX;
    public final int cellY;
    /** Position in light years; the galactic core is at the origin. */
    public final float x;
    public final float y;
    public final long seed;
    public final String name;
    public final int starClass;
    public final int planetCount;
    /** 0 poor, 1 average, 2 wealthy. */
    public final int wealth;
    /** 0 peaceful, 1 some pirates, 2 dangerous. */
    public final int conflict;
    /** The trade economy's speciality: an item that sells high here. */
    public final int demand;
    private List<PlanetSpec> planets;

    StarSystem(long galaxySeed, int cellX, int cellY, float x, float y, int starClass) {
        this.id = pack(cellX, cellY);
        this.cellX = cellX;
        this.cellY = cellY;
        this.x = x;
        this.y = y;
        this.seed = Rng.hash(galaxySeed, 0x5359534CL, id);
        Rng rng = new Rng(seed);
        this.starClass = starClass;
        this.name = starClass == CORE ? "Galactic Core" : Names.system(Rng.hash(seed, 7));
        this.planetCount = starClass == BLACK_HOLE ? 0 : starClass == CORE ? 1 : rng.range(2, 5);
        this.wealth = rng.nextInt(3);
        this.conflict = rng.chance(0.5) ? 0 : rng.chance(0.6) ? 1 : 2;
        int[] demands = {eggsky.game.Catalog.GOLD, eggsky.game.Catalog.CHROMATIC_METAL, eggsky.game.Catalog.SODIUM,
                eggsky.game.Catalog.OXYGEN, eggsky.game.Catalog.COBALT, eggsky.game.Catalog.BADNIK_SCRAP,
                eggsky.game.Catalog.DIHYDROGEN, eggsky.game.Catalog.PURE_FERRITE};
        this.demand = demands[rng.nextInt(demands.length)];
    }

    public static long pack(int cellX, int cellY) {
        return ((long) cellX << 32) ^ (cellY & 0xFFFFFFFFL);
    }

    public static int unpackX(long id) {
        return (int) (id >> 32);
    }

    public static int unpackY(long id) {
        return (int) id;
    }

    public List<PlanetSpec> planets(List<Biome> biomes) {
        if (planets == null) {
            planets = new ArrayList<>();
            for (int i = 0; i < planetCount; i++) {
                planets.add(new PlanetSpec(seed, i, starClass, biomes));
            }
        }
        return planets;
    }

    public float distanceTo(StarSystem other) {
        return (float) Math.hypot(other.x - x, other.y - y);
    }

    public float distanceToCore() {
        return (float) Math.hypot(x, y);
    }

    public int colour() {
        return switch (starClass) {
            case RED -> 0xFFFF6048;
            case GREEN -> 0xFF60FF80;
            case BLUE -> 0xFF60A8FF;
            case BLACK_HOLE -> 0xFF8040C0;
            case CORE -> 0xFFFFFFFF;
            default -> 0xFFFFE070;
        };
    }

    public String className() {
        return switch (starClass) {
            case RED -> "Red dwarf";
            case GREEN -> "Green star";
            case BLUE -> "Blue giant";
            case BLACK_HOLE -> "Black hole";
            case CORE -> "Galactic core";
            default -> "Yellow star";
        };
    }

    public String wealthName() {
        return switch (wealth) {
            case 0 -> "Struggling";
            case 1 -> "Developing";
            default -> "Wealthy";
        };
    }

    public String conflictName() {
        return switch (conflict) {
            case 0 -> "Peaceful";
            case 1 -> "Pirates sighted";
            default -> "Pirate stronghold";
        };
    }
}
