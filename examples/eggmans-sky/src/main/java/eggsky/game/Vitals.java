package eggsky.game;

/**
 * Refilling the Egg Mobile's systems from cargo, as No Man's Sky's quick menu does: life support
 * from Oxygen (or Life Support Gel), hazard protection from Sodium (or an Ion Battery), the
 * launch thrusters from Di-hydrogen, its Jelly or Egg Fuel, the hull from Ferrite or Metal
 * Plating, and the deflector from Sodium or a Shield Cell. Products fill completely; raw
 * resources are used one unit at a time only until the system is full.
 */
public final class Vitals {
    public static final int LIFE = 0;
    public static final int HAZARD = 1;
    public static final int LAUNCH = 2;
    public static final int HULL = 3;
    public static final int SHIP_SHIELD = 4;

    private Vitals() {
    }

    public static String name(int which) {
        return switch (which) {
            case LIFE -> "Life Support";
            case HAZARD -> "Hazard Protection";
            case LAUNCH -> "Launch Thrusters";
            case HULL -> "Hull";
            default -> "Deflector";
        };
    }

    /** {product, product amount, resource, amount per unit} for a system. */
    private static float[] sources(int which) {
        return switch (which) {
            case LIFE -> new float[] {Catalog.LIFE_GEL, 100, Catalog.OXYGEN, 3};
            case HAZARD -> new float[] {Catalog.ION_BATTERY, 100, Catalog.SODIUM, 3};
            case LAUNCH -> new float[] {Catalog.EGG_FUEL, 100, Catalog.DIHYDROGEN_JELLY, 25};
            case HULL -> new float[] {Catalog.METAL_PLATING, 60, Catalog.FERRITE, 0.5f};
            default -> new float[] {Catalog.SHIELD_CELL, 100, Catalog.SODIUM, 2};
        };
    }

    public static float current(Player p, int which) {
        return switch (which) {
            case LIFE -> p.life;
            case HAZARD -> p.hazard;
            case LAUNCH -> p.launchFuel;
            case HULL -> p.hull;
            default -> p.shipShield;
        };
    }

    public static float max(Player p, int which) {
        return switch (which) {
            case LIFE -> p.maxLife();
            case HAZARD -> p.maxHazard();
            case LAUNCH -> 100;
            case HULL -> p.maxHull();
            default -> p.maxShipShield();
        };
    }

    private static void set(Player p, int which, float v) {
        switch (which) {
            case LIFE -> p.life = v;
            case HAZARD -> p.hazard = v;
            case LAUNCH -> p.launchFuel = v;
            case HULL -> p.hull = v;
            default -> p.shipShield = v;
        }
    }

    /** Whether there is anything in cargo that would refill the system. */
    public static boolean canRecharge(Player p, int which) {
        float[] s = sources(which);
        boolean extra = which == LAUNCH && p.cargo.has(Catalog.DIHYDROGEN, 1)
                || which == HULL && p.cargo.has(Catalog.PURE_FERRITE, 1);
        return current(p, which) < max(p, which) - 0.5f
                && (p.cargo.has((int) s[0], 1) || p.cargo.has((int) s[2], 1) || extra);
    }

    /**
     * Refills a system. Prefers raw resources when they can top it up, otherwise uses a product.
     * Returns a message describing what was used, or null when nothing could be done.
     */
    public static String recharge(Player p, int which) {
        float need = max(p, which) - current(p, which);
        if (need < 0.5f) {
            return null;
        }
        float[] s = sources(which);
        int resource = (int) s[2];
        float per = s[3];
        int have = p.cargo.count(resource);
        int units = (int) Math.ceil(need / per);
        if (have > 0) {
            int use = Math.min(have, units);
            p.cargo.remove(resource, use);
            set(p, which, Math.min(max(p, which), current(p, which) + use * per));
            return "-" + use + " " + p.catalog().name(resource);
        }
        if (which == LAUNCH && p.cargo.has(Catalog.DIHYDROGEN, 1)) {
            int use = Math.min(p.cargo.count(Catalog.DIHYDROGEN), (int) Math.ceil(need / 0.5f));
            p.cargo.remove(Catalog.DIHYDROGEN, use);
            set(p, which, Math.min(100, current(p, which) + use * 0.5f));
            return "-" + use + " Di-hydrogen";
        }
        if (which == HULL && p.cargo.has(Catalog.PURE_FERRITE, 1)) {
            int use = Math.min(p.cargo.count(Catalog.PURE_FERRITE), (int) Math.ceil(need));
            p.cargo.remove(Catalog.PURE_FERRITE, use);
            set(p, which, Math.min(max(p, which), current(p, which) + use));
            return "-" + use + " Pure Ferrite";
        }
        int product = (int) s[0];
        if (p.cargo.has(product, 1)) {
            p.cargo.remove(product, 1);
            set(p, which, Math.min(max(p, which), current(p, which) + s[1]));
            return "-1 " + p.catalog().name(product);
        }
        return null;
    }
}
