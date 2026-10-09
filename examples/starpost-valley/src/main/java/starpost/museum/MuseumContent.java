package starpost.museum;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * What the museum adds to the catalogue (design doc §6.8): the badnik parts that fill the Scrap
 * Collection (dropped by popped badniks; the fishing lane's catches fill the rest), the relics
 * (totem pieces, ring moulds, Star Post and signpost parts, found in the Ruins and by digging), the
 * museum's own Star Post Cap that went missing in Hazel's four-heart event, and the three Records
 * the museum's milestones award. Called from {@code Content.register}.
 */
public final class MuseumContent {
    /** The museum's own Star Post Cap: missing since Hazel's four-heart event. */
    public static final String CAP = "museum_starpost_cap";
    /** Icon keys of badnik parts (drawn from the badnik's ROM sprite). */
    public static final String PART_ICON = "part:";
    /** Icon keys of relics (crops of ROM pictures). */
    public static final String RELIC_ICON = "relic:";

    private final Catalog catalog;

    public MuseumContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        // Badnik parts: Sonic 1's badniks of the farm (Motobug) and the Ruins' three bands.
        part("motobug_shell", "MOTOBUG SHELL", 50, "motobug", "A MOTOBUG'S RED SHELL. IT STILL WANTS TO ROLL.");
        part("buzz_bomber_wing", "BUZZ BOMBER WING", 60, "buzz_bomber", "ONE GAUZE WING. IT BUZZES IF YOU BLOW ON IT.");
        part("caterkiller_segment", "CATERKILLER SEGMENT", 60, "caterkiller", "ONE SPIKED SEGMENT. THE REST WENT ON WITHOUT IT.");
        part("batbrain_wing", "BATBRAIN WING", 70, "batbrain", "A MARBLE CAVE BAT'S WING. FOLDS UP NEATLY.");
        part("yadrin_spike", "YADRIN SPIKE", 70, "yadrin", "THE SPIKES ON ITS BACK. NEVER JUMP ON ONE.");
        part("burrobot_drill", "BURROBOT DRILL", 80, "burrobot", "A DRILL NOSE. IT DUG THE LABYRINTH'S TUNNELS.");
        part("orbinaut_core", "ORBINAUT CORE", 90, "orbinaut", "THE BALL THE SPIKES CIRCLED. IT HUMS.");
        part("bomb_fuse", "BOMB FUSE", 90, "bomb", "A WALKING BOMB'S FUSE. SAFE NOW. PROBABLY.");
        part("ball_hog_cannon", "BALL HOG CANNON", 100, "ball_hog", "A BALL HOG'S BOUNCING-BOMB LAUNCHER.");
        // Relics: crops of ROM pictures (totem pole, rings, Star Post, signpost, monitor, spring).
        relic("totem_chip", "TOTEM CHIP", 60, "A CHIP OFF A GREEN HILL TOTEM POLE.");
        relic("totem_wing", "TOTEM WING", 120, "A TOTEM'S CARVED WING. IT NEVER FLEW.");
        relic("totem_face", "TOTEM FACE", 200, "A WHOLE CARVED FACE, VERY PLEASED WITH ITSELF.");
        relic("ring_mould", "RING MOULD", 150, "POUR GOLD IN AND A RING COMES OUT. WATER MAKES A PUDDLE.");
        relic("giant_ring_shard", "GIANT RING SHARD", 250, "A PIECE OF A GIANT RING. IT LED SOMEWHERE SPECIAL.");
        relic("spring_coil", "SPRING COIL", 90, "A RUSTY YELLOW SPRING. STILL A LITTLE BOUNCE IN IT.");
        relic("old_monitor", "OLD MONITOR", 140, "A CRACKED ITEM MONITOR. SOMETHING WAS INSIDE ONCE.");
        relic("signpost_plate", "SIGNPOST PLATE", 180, "THE SPINNING PLATE OF AN END-OF-ACT SIGNPOST.");
        relic("lamppost_globe", "LAMPPOST GLOBE", 160, "THE GLOBE OF AN OLD LAMPPOST. IT REMEMBERS WHERE YOU WERE.");
        relic(CAP, "STAR POST CAP", 0, "THE MUSEUM'S OWN STAR POST CAP. HAZEL HAS LOOKED EVERYWHERE.");
        // The museum's Records (each opens its own song on the Lamppost Inn's jukebox).
        record("record_lava_reef", "RECORD: LAVA REEF", "THE MINERALS' PRIZE. THE JUKEBOX CAN PLAY IT NOW.");
        record("record_mini_boss", "RECORD: MINI-BOSS", "THE SCRAP COLLECTION'S PRIZE. THE JUKEBOX CAN PLAY IT NOW.");
        record("record_sandopolis", "RECORD: SANDOPOLIS", "THE RELICS' PRIZE. THE JUKEBOX CAN PLAY IT NOW.");
    }

    /** Badnik part ids in the Scrap Collection's order (the fishing lane's catches are added by {@link Exhibits}). */
    public static List<String> parts() {
        return new ArrayList<>(List.of("motobug_shell", "buzz_bomber_wing", "caterkiller_segment", "batbrain_wing",
                "yadrin_spike", "burrobot_drill", "orbinaut_core", "bomb_fuse", "ball_hog_cannon"));
    }

    /** Relic ids in display order, the museum's own Star Post Cap last. */
    public static List<String> relics() {
        return new ArrayList<>(List.of("totem_chip", "totem_wing", "totem_face", "ring_mould", "giant_ring_shard",
                "spring_coil", "old_monitor", "signpost_plate", "lamppost_globe", CAP));
    }

    /**
     * The song a museum Record opens, as "game.hex", or null. Sonic 3 &amp; Knuckles ids are
     * Sonic3kMusic's: Lava Reef act 1 $13, Mini-Boss $18, Sandopolis act 1 $11.
     */
    public static String recordSong(String id) {
        return switch (id) {
            case "record_lava_reef" -> "s3k.13";
            case "record_mini_boss" -> "s3k.18";
            case "record_sandopolis" -> "s3k.11";
            default -> null;
        };
    }

    /** The story flag a museum Record sets when awarded ({@code record.s3k.13}), or null. */
    public static String recordFlag(String id) {
        String song = recordSong(id);
        return song == null ? null : "record." + song;
    }

    private void part(String id, String name, int price, String badnik, String text) {
        catalog.add(new Item(id, name, Kind.MATERIAL, price, 0, PART_ICON + badnik, text));
    }

    private void relic(String id, String name, int price, String text) {
        catalog.add(new Item(id, name, Kind.RELIC, price, 0, RELIC_ICON + id, text));
    }

    private void record(String id, String name, String text) {
        catalog.add(new Item(id, name, Kind.RELIC, 0, 0, id, text));
    }
}
