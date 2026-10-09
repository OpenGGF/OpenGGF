package starpost.ruins;

import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * What the Ruins add to the catalogue (design doc §6.5): the ores and gems of the three bands,
 * their geodes, the Master Emerald's shards, the Records the Sound Test plays, and Pud's golden
 * seed. Called from {@code Content.register}. Every item says what it does that its neighbours
 * do not; the rules the Ruins themselves own are in {@link RuinsRules}.
 */
public final class RuinsContent {
    /** Records: S1 driver IDs of the songs they unlock (Sonic1Music; bgm_* in s1disasm's _Constants.asm). */
    public static final String RECORD_FLAG = "record.s1.";

    private final Catalog catalog;

    public RuinsContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        mineral("marble_ore", "MARBLE ORE", 25, "TAILS' FURNACE MAKES MARBLE BLOCKS OF IT.");
        mineral("lava_ruby", "LAVA RUBY", 120, "STILL WARM. PUD'S FAVOURITE GIFT.");
        mineral("tide_sapphire", "TIDE SAPPHIRE", 150, "HOLDS ONE BREATH: SAVES YOU FROM DROWNING.");
        mineral("spark_topaz", "SPARK TOPAZ", 180, "CARRY ONE: POPPED BADNIKS ALWAYS DROP SCRAP.");
        mineral("emerald_shard", "EMERALD SHARD", 300, "A SLIVER OF THE MASTER EMERALD. KNUCKLES WANTS IT.");
        mineral("marble_geode", "MARBLE GEODE", 50, "TAILS CAN CRACK IT OPEN FOR A MINERAL.");
        mineral("tide_geode", "TIDE GEODE", 60, "WET INSIDE. TAILS CAN CRACK IT OPEN.");
        mineral("scrap_geode", "SCRAP GEODE", 70, "TICKS. TAILS CAN CRACK IT OPEN.");
        record("record_marble", "RECORD: MARBLE ZONE");
        record("record_labyrinth", "RECORD: LABYRINTH ZONE");
        record("record_scrap_brain", "RECORD: SCRAP BRAIN ZONE");
        record("record_drowning", "RECORD: DROWNING");
        record("record_boss", "RECORD: BOSS");
        record("record_invincible", "RECORD: INVINCIBILITY");
        record("record_final", "RECORD: FINAL ZONE");
        // Pud's Super Sunflower seed is a crop of the farm's (Content), planted in the Capsule Garden.
    }

    /** The Sonic 1 song a Record unlocks in the Sound Test, or -1 when {@code id} is not a Record. */
    public static int recordSong(String id) {
        return switch (id) {
            case "record_marble" -> 0x83;       // bgm_MZ
            case "record_labyrinth" -> 0x82;    // bgm_LZ
            case "record_scrap_brain" -> 0x86;  // bgm_SBZ
            case "record_drowning" -> 0x92;     // bgm_Drowning
            case "record_boss" -> 0x8C;         // bgm_Boss
            case "record_invincible" -> 0x87;   // bgm_Invincible
            case "record_final" -> 0x8D;        // bgm_FZ
            default -> -1;
        };
    }

    /** The story flag a Record sets when found ({@code record.s1.83} for Marble Zone's). */
    public static String recordFlag(String id) {
        return RECORD_FLAG + Integer.toHexString(recordSong(id));
    }

    private void mineral(String id, String name, int price, String text) {
        catalog.add(new Item(id, name, Kind.MINERAL, price, 0, id, text));
    }

    private void record(String id, String name) {
        catalog.add(new Item(id, name, Kind.RELIC, 0, 0, id, "THE SOUND TEST CAN PLAY IT NOW."));
    }
}
