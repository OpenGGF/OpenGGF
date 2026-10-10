package starpost.festivals;

import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * What the festivals add to the catalogue: the Records won at them (each opens its own song on
 * the Lamppost Inn's jukebox, and like every Record can be slotted in for a bonus track). Called
 * from {@code Content.register}.
 */
public final class FestivalContent {
    private final Catalog catalog;

    public FestivalContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        record("record_special_stage", "RECORD: SPECIAL STAGE");
        record("record_migration", "RECORD: THE MIGRATION");
        record("record_slots", "RECORD: SLOT BONUS");
        record("record_death_egg", "RECORD: DEATH EGG");
        record("record_icecap_s3", "RECORD: ICECAP (S3)");
    }

    /**
     * The song a festival Record opens, as "game.hex" ({@code s1.89}), or null. Sonic 1 ids are
     * bgm_* in s1disasm's _Constants.asm; Sonic 3 &amp; Knuckles ids are Sonic3kMusic's (the S3
     * IceCap is the S3 driver's variant, $100 | $0B).
     */
    public static String recordSong(String id) {
        return switch (id) {
            case "record_special_stage" -> "s1.89";   // bgm_SS
            case "record_migration" -> "s3k.32";      // Ending
            case "record_slots" -> "s3k.1d";          // Bonus Stage - Slots
            case "record_death_egg" -> "s3k.16";      // Death Egg Zone Act 1
            case "record_icecap_s3" -> "s3k.10b";     // IceCap Zone Act 1 (S3)
            default -> null;
        };
    }

    /** The story flag a festival Record sets when won ({@code record.s1.89}), or null. */
    public static String recordFlag(String id) {
        String song = recordSong(id);
        return song == null ? null : "record." + song;
    }

    private void record(String id, String name) {
        catalog.add(new Item(id, name, Kind.RELIC, 0, 0, id, "WON AT A FESTIVAL. THE JUKEBOX CAN PLAY IT NOW."));
    }
}
