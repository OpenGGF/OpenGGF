package survivors;

/**
 * The level-up catalogue. Ids index {@link RunState#levels}, so only append new upgrades.
 * Bounce weapons fire whenever Sonic rebounds off an enemy; auto weapons run on cooldowns;
 * moves add new ways to keep a bounce chain alive; passives scale everything else.
 * (Mod classes may not hold static state, so this is ids and switches rather than an enum.)
 */
final class Upgrades {
    private Upgrades() { }

    // Bounce weapons.
    static final int SHOCKWAVE = 0, SPARKS = 1, CHAIN_ZAP = 2, HOMING_RINGS = 3;
    // Auto weapons.
    static final int ORBIT_RINGS = 4, SONIC_BOOM = 5, FLICKIES = 6;
    // Moves.
    static final int HOMING_DASH = 7, AIR_JUMP = 8, GROUND_POUND = 9;
    // Passives.
    static final int POWER = 10, MAGNET = 11, ARMOR = 12, HASTE = 13, GREED = 14, SPRING_HEELS = 15,
            COMBO_KEEPER = 16, BARRIER = 17;
    static final int COUNT = 18;

    static final int KIND_BOUNCE = 0, KIND_AUTO = 1, KIND_MOVE = 2, KIND_PASSIVE = 3;

    static String name(int id) {
        return switch (id) {
            case SHOCKWAVE -> "SHOCKWAVE";
            case SPARKS -> "SPARK BURST";
            case CHAIN_ZAP -> "CHAIN ZAP";
            case HOMING_RINGS -> "HOMING RINGS";
            case ORBIT_RINGS -> "ORBIT RINGS";
            case SONIC_BOOM -> "SONIC BOOM";
            case FLICKIES -> "FLICKY SQUAD";
            case HOMING_DASH -> "HOMING DASH";
            case AIR_JUMP -> "AIR JUMP";
            case GROUND_POUND -> "GROUND POUND";
            case POWER -> "POWER";
            case MAGNET -> "MAGNET";
            case ARMOR -> "ARMOR";
            case HASTE -> "HASTE";
            case GREED -> "GREED";
            case SPRING_HEELS -> "SPRING HEELS";
            case COMBO_KEEPER -> "COMBO KEEPER";
            default -> "BARRIER";
        };
    }

    static int kind(int id) {
        if (id <= HOMING_RINGS) return KIND_BOUNCE;
        if (id <= FLICKIES) return KIND_AUTO;
        if (id <= GROUND_POUND) return KIND_MOVE;
        return KIND_PASSIVE;
    }

    static String kindName(int id) {
        return switch (kind(id)) {
            case KIND_BOUNCE -> "ON BOUNCE";
            case KIND_AUTO -> "AUTO";
            case KIND_MOVE -> "MOVE";
            default -> "PASSIVE";
        };
    }

    static int kindColour(int id) {
        return switch (kind(id)) {
            case KIND_BOUNCE -> Draw.ORANGE;
            case KIND_AUTO -> Draw.CYAN;
            case KIND_MOVE -> Draw.GREEN;
            default -> Draw.PURPLE;
        };
    }

    static int maxLevel(int id) {
        return switch (id) {
            case HOMING_DASH, AIR_JUMP, GROUND_POUND, COMBO_KEEPER, BARRIER -> 3;
            default -> 5;
        };
    }

    /** Two short description lines for the card offered at {@code nextLevel} (1-based). */
    static String[] describe(int id, int nextLevel) {
        int l = nextLevel;
        return switch (id) {
            case SHOCKWAVE -> new String[]{"BOUNCES RELEASE A", "BLAST R" + shockRadius(l) + " DMG " + shockDamage(l)};
            case SPARKS -> new String[]{"BOUNCES FIRE " + sparkCount(l) + " SPARKS", "DMG " + sparkDamage(l)};
            case CHAIN_ZAP -> new String[]{"BOUNCES ZAP " + zapTargets(l) + " FOES", "DMG " + zapDamage(l)};
            case HOMING_RINGS -> new String[]{"BOUNCES LAUNCH " + homingCount(l), "SEEKING RINGS DMG " + homingDamage(l)};
            case ORBIT_RINGS -> new String[]{orbitCount(l) + " RINGS CIRCLE YOU", "DMG " + orbitDamage(l)};
            case SONIC_BOOM -> new String[]{"FIRES A PIERCING WAVE", "EVERY " + secs(boomCooldown(l)) + "S DMG " + boomDamage(l)};
            case FLICKIES -> new String[]{"FREED FLICKIES DIVE", flickyCount(l) + " EVERY " + secs(flickyCooldown(l)) + "S DMG 3"};
            case HOMING_DASH -> new String[]{"JUMP IN AIR TO DASH", "AT A FOE IN " + dashRange(l) + "PX"};
            case AIR_JUMP -> new String[]{"JUMP AGAIN IN THE", "AIR " + l + (l == 1 ? " TIME" : " TIMES")};
            case GROUND_POUND -> new String[]{"DOWN IN AIR SLAMS", "QUAKE R" + poundRadius(l) + " DMG " + poundDamage(l)};
            case POWER -> new String[]{"ALL DAMAGE", "+" + 25 * l + "%"};
            case MAGNET -> new String[]{"PULL RINGS FROM", magnetRadius(l, 0) + "PX AWAY"};
            case ARMOR -> new String[]{"HITS COST " + 2 * l + " FEWER", "RINGS"};
            case HASTE -> new String[]{"WEAPON COOLDOWNS", "-" + 10 * l + "%"};
            case GREED -> new String[]{"MORE RING DROPS", "AND +" + 20 * l + "% XP"};
            case SPRING_HEELS -> new String[]{"HIGHER BOUNCES AND", "+" + l + " STOMP DAMAGE"};
            case COMBO_KEEPER -> new String[]{"COMBO SURVIVES", "LANDING FOR " + secs(comboGrace(l)) + "S"};
            default -> new String[]{"A SHIELD RETURNS", "EVERY " + secs(barrierCooldown(l)) + "S"};
        };
    }

    private static String secs(int frames) {
        int tenths = Math.round(frames / 6f);
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    // ---- Weapon numbers. Level 0 means not owned. ----
    static int shockRadius(int l) { return 36 + 12 * l; }
    static int shockDamage(int l) { return 2 + l; }
    static int sparkCount(int l) { return 2 + l + (l >= 5 ? 1 : 0); }
    static int sparkDamage(int l) { return 2 + l / 2; }
    static int zapTargets(int l) { return 1 + l; }
    static int zapDamage(int l) { return 3 + l; }
    static int homingCount(int l) { return 1 + (l >= 3 ? 1 : 0) + (l >= 5 ? 1 : 0); }
    static int homingDamage(int l) { return 3 + l; }
    static int orbitCount(int l) { return 1 + l; }
    static int orbitDamage(int l) { return 1 + (l + 1) / 2; }
    static int boomCooldown(int l) { return 150 - 18 * l; }
    static int boomDamage(int l) { return 4 + l; }
    static int flickyCount(int l) { return 1 + l / 2; }
    static int flickyCooldown(int l) { return 210 - 20 * l; }
    static int dashRange(int l) { return 80 + 32 * l; }
    static int poundRadius(int l) { return 44 + 16 * l; }
    static int poundDamage(int l) { return 3 + 2 * l; }
    static int magnetRadius(int l, int shop) { return 28 + 24 * l + 16 * shop; }
    static int comboGrace(int l) { return 20 * l; }
    static int barrierCooldown(int l) { return 1800 - 300 * (l - 1); }
}
