package threeislands.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten explorable acts. {@code zone}/{@code act} index the stock game's level kit
 * ({@code SceneRomArt.levelKit}); {@code startX}/{@code startY} are the act's Sonic start
 * position from the disassemblies' start-location data (s1disasm {@code startpos/*.bin},
 * s2disasm {@code startpos/*_1.bin}, skdisasm {@code Levels/* /Start Location/Sonic/1.bin}; Angel
 * Island uses its post-intro act 1 start). {@code bosses} lists mid-zone bosses first and the
 * zone boss last. {@code emerald} is the Chaos Emerald the zone boss returns, or -1. {@code level}
 * is the party level the zone is balanced for (its foes' strength and the map's suggestion);
 * {@code tier} is its position in the story.
 */
public enum Zone {
    GREEN_HILL("Green Hill", "ghz", 0, "s1", 0, 0, 0x81, 0x50, 0x3B0,
            "MOTOBUG,CRABMEAT,BUZZ_BOMBER,CHOPPER,NEWTRON", "GIGA_MOTOBUG", -1, 0, 2),
    STAR_LIGHT("Star Light", "slz", 0, "s1", 4, 0, 0x84, 0x40, 0x2CC,
            "BOMB,ORBINAUT_S1,BUZZ_BOMBER,CATERKILLER", "BOMB_KING", 0, 1, 4),
    SPRING_YARD("Spring Yard", "syz", 0, "s1", 2, 0, 0x85, 0x30, 0x3BD,
            "ROLLER,YADRIN,CRABMEAT,BUZZ_BOMBER,BOMB", "EGG_MOBILE", 1, 2, 6),
    EMERALD_HILL("Emerald Hill", "ehz", 1, "s2", 0, 0, 0x81, 0x60, 0x28F,
            "BUZZER,MASHER,COCONUTS", "COCONUTS_CHIEF", -1, 3, 7),
    CHEMICAL_PLANT("Chemical Plant", "cpz", 1, "s2", 1, 0, 0x8C, 0x60, 0x1EC,
            "SPINY,GRABBER,BUZZER", "GRABBER_QUEEN", 2, 4, 9),
    MYSTIC_CAVE("Mystic Cave", "mcz", 1, "s2", 5, 0, 0x84, 0x60, 0x6AC,
            "CRAWLTON,FLASHER,SPINY", "SILVER_SONIC", 3, 5, 11),
    ANGEL_ISLAND("Angel Island", "aiz", 2, "s3k", 0, 0, 0x01, 0x13A0, 0x41A,
            "RHINOBOT,BLOOMINATOR,MONKEY_DUDE", "FLAME_CRAFT,KNUCKLES_RIVAL", 4, 6, 13),
    HYDROCITY("Hydrocity", "hcz", 2, "s3k", 1, 0, 0x03, 0x280, 0x20,
            "JAWZ,BLASTOID,BUGGERNAUT,TURBO_SPIKER,MEGA_CHOPPER,POINTDEXTER", "SCREW_MOBILE", 5, 7, 15),
    LAUNCH_BASE("Launch Base", "lbz", 2, "s3k", 6, 0, 0x0D, 0xB0, 0x650,
            "SNALE_BLASTER,ORBINAUT,RIBOT,FLYBOT", "BEAM_ROCKET", 6, 8, 17),
    DEATH_EGG("Death Egg", "dez", 3, "s3k", 11, 0, 0x16, 0x30, 0x9AC,
            "EGG_ROBO,RHINOBOT,FLYBOT,RIBOT,JAWZ,FLASHER,CATERKILLER", "MECHA_SONIC,CONVERGENCE_ENGINE", -1, 9, 19);

    public final String label;
    public final String key;
    public final int islandIndex;
    public final String game;
    public final int zone;
    public final int act;
    public final int music;
    public final int startX;
    public final int startY;
    public final String enemies;
    public final String bosses;
    public final int emerald;
    public final int tier;
    public final int level;

    Zone(String label, String key, int islandIndex, String game, int zone, int act, int music, int startX, int startY,
            String enemies, String bosses, int emerald, int tier, int level) {
        this.label = label;
        this.key = key;
        this.islandIndex = islandIndex;
        this.game = game;
        this.zone = zone;
        this.act = act;
        this.music = music;
        this.startX = startX;
        this.startY = startY;
        this.enemies = enemies;
        this.bosses = bosses;
        this.emerald = emerald;
        this.tier = tier;
        this.level = level;
    }

    public Island island() {
        return Island.values()[islandIndex];
    }

    public List<EnemyKind> enemyKinds() {
        return kinds(enemies);
    }

    public List<EnemyKind> bossKinds() {
        return kinds(bosses);
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** The zones of one island, in story order. */
    public static List<Zone> of(Island island) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : values()) if (zone.islandIndex == island.ordinal()) out.add(zone);
        return out;
    }

    private static List<EnemyKind> kinds(String csv) {
        List<EnemyKind> out = new ArrayList<>();
        for (String name : csv.split(",")) out.add(EnemyKind.valueOf(name.trim()));
        return out;
    }
}
