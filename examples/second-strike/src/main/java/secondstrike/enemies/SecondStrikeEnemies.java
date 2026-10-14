package secondstrike.enemies;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;

/** Owned enemy recipes for the native Second Strike campaign. */
public final class SecondStrikeEnemies {
    public static final String OWNER = "second-strike";
    public static final String SPOTTER = OWNER + ":spotter";
    public static final String GUARD = OWNER + ":guard";
    public static final String ARTILLERY = OWNER + ":artillery";
    public static final String SHOT = OWNER + ":shot";

    // AIZ's normal PLC art provider owns these ROM-backed mapping/DPLC sheets.
    static final String MONKEY_ART = "aiz_monkey_dude";
    static final String GUARD_ART = "aiz_rhinobot";
    static final String GUNNER_ART = "aiz_bloominator";
    static final String DASH_ART = "fire_shield_dash";

    private SecondStrikeEnemies() { }

    /** Genuine native Bloom anchors on forest and early physical continuation platforms. */
    static boolean reinforcedAnchor(int x, int y) {
        return x == 0x1dc0 && y == 0x27c || x == 0x2370 && y == 0x37c
                || x == 0x09f0 && y == 0x3fc || x == 0x0e10 && y == 0x33c;
    }

    public static void register(ModContext context) {
        context.registerObject("spotter", (spawn, registry) -> new SpotterBadnik(spawn));
        context.registerObject("guard", (spawn, registry) -> new GuardBadnik(spawn));
        context.registerObject("artillery", (spawn, registry) -> new ArtilleryBadnik(spawn));
        context.registerObject("shot", (spawn, registry) -> new CommittedShot(spawn));
    }

    public static ObjectSpawn ownedSpawn(String key, int x, int y, int layoutIndex) {
        if (!SPOTTER.equals(key) && !GUARD.equals(key) && !ARTILLERY.equals(key) && !SHOT.equals(key)) {
            throw new IllegalArgumentException("Unknown Second Strike enemy key: " + key);
        }
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, layoutIndex, OWNER, key);
    }

}
