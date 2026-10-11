package threeislands.core;

/** Literal codes shared by the content tables (enums may only hold scalar fields). */
public final class Kinds {
    private Kinds() {
    }

    // Targets.
    public static final int ENEMY = 0;
    public static final int ALL_ENEMIES = 1;
    public static final int ALLY = 2;
    public static final int ALL_ALLIES = 3;
    public static final int SELF = 4;
    public static final int FALLEN_ALLY = 5;

    // Effects.
    public static final int DAMAGE = 0;
    public static final int HEAL = 1;
    public static final int HASTE = 2;
    public static final int SCAN = 3;
    public static final int BREAK = 4;
    public static final int TAUNT = 5;
    public static final int REVIVE = 6;
    public static final int RESTORE_EP = 7;
    public static final int SHIELD = 8;
    public static final int INVINCIBLE = 9;

    // Enemy behaviours.
    public static final int BASIC = 0;
    public static final int SWEEP = 1;
    public static final int CHARGER = 2;
    public static final int FLURRY = 3;
    public static final int RIVAL = 4;

    // Signature mechanics layered over a foe's pattern (see Battle).
    public static final int SIG_NONE = 0;
    public static final int SIG_FUSE = 1;      // explodes over the party after a countdown
    public static final int SIG_SUMMON = 2;    // calls minions while it has room
    public static final int SIG_ENRAGE = 3;    // hits harder once below half HP
    public static final int SIG_GRAB = 4;      // seizes a hero until hurt enough to let go
    public static final int SIG_COUNTER = 5;   // spiked stance reflects plain blows
    public static final int SIG_NAPALM = 6;    // fire sweeps that burn readily
    public static final int SIG_DEPTH = 7;     // plants charges that only guarding contains
    public static final int SIG_BARRIER = 8;   // a barrier only techs can crack
    public static final int SIG_SUPER = 9;     // turns gold and acts twice below half HP
    public static final int SIG_CORE = 10;     // weakness cycles each round; calls guards

    // Status effects (turn counters on Combatant).
    public static final int BURN = 0;
    public static final int SOAK = 1;
    public static final int STUN = 2;

    // Enemy movement for presentation.
    public static final int WALK = 0;
    public static final int FLY = 1;
    public static final int HOP = 2;

    // Element ordinals for the tables.
    public static final int NONE = 0;
    public static final int FIRE = 1;
    public static final int WATER = 2;
    public static final int ELEC = 3;

    public static boolean targetsEnemies(int target) {
        return target == ENEMY || target == ALL_ENEMIES;
    }

    public static boolean targetsAll(int target) {
        return target == ALL_ENEMIES || target == ALL_ALLIES;
    }
}
