package starpost.museum;

import com.openggf.mods.state.SnapshotRandom;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.ruins.Badnik;
import starpost.ruins.RuinsRules;

/**
 * Where the museum's pieces come from, engine-free so the odds are tested without a ROM.
 * <ul>
 *   <li><b>Badnik parts.</b> A popped badnik leaves its part one time in {@link #PART_ODDS}: the
 *       farm's Motobugs, and in the Ruins each band's badniks (the Labyrinth's Jaws leaves the
 *       same fin the lake's Jaws does). The Walking Bomb can't be popped; it leaves its fuse when
 *       it goes off.</li>
 *   <li><b>Relics in the Ruins.</b> A broken rock turns one up one time in
 *       {@link #RUINS_RELIC_ODDS}, from its band's three: Marble Zone's totem pieces, Labyrinth's
 *       ring moulds, giant ring shards and springs, Scrap Brain's monitors, signposts and
 *       lampposts.</li>
 *   <li><b>Relics underground.</b> Digging (Knuckles's dig, or any farmer at a glinting spot)
 *       finds the season's relics: spring's totem chips and springs, summer's ring moulds and
 *       lamppost globes, fall's totem wings, faces and signpost plates, winter's old monitors and
 *       giant ring shards.</li>
 * </ul>
 */
public final class Finds {
    /** One popped badnik in this many leaves its part. */
    public static final int PART_ODDS = 5;
    /** One broken Ruins rock in this many turns up a relic. */
    public static final int RUINS_RELIC_ODDS = 20;
    /** Percent of glinting spots that hold a relic (the rest hold chips, a geode or rings). */
    public static final int SPOT_RELIC_PERCENT = 45;

    private Finds() {
    }

    /** The part a badnik leaves (by its key: "motobug", "jaws"...), or null. */
    public static String part(String badnik, SnapshotRandom rng) {
        String id = partOf(badnik);
        return id != null && rng.nextInt(PART_ODDS) == 0 ? id : null;
    }

    /** The part a Ruins badnik kind leaves, or null. */
    public static String ruinsPart(int kind, SnapshotRandom rng) {
        return part(ruinsKey(kind), rng);
    }

    /** A badnik's part id, whether or not it drops. */
    public static String partOf(String badnik) {
        return switch (badnik) {
            case "motobug" -> "motobug_shell";
            case "buzz_bomber" -> "buzz_bomber_wing";
            case "caterkiller" -> "caterkiller_segment";
            case "batbrain" -> "batbrain_wing";
            case "yadrin" -> "yadrin_spike";
            case "jaws" -> "jaws_fin";
            case "burrobot" -> "burrobot_drill";
            case "orbinaut" -> "orbinaut_core";
            case "bomb" -> "bomb_fuse";
            case "ball_hog" -> "ball_hog_cannon";
            default -> null;
        };
    }

    /** The key of a Ruins badnik kind ({@link Badnik} constants). */
    public static String ruinsKey(int kind) {
        return switch (kind) {
            case Badnik.CATERKILLER -> "caterkiller";
            case Badnik.BATBRAIN -> "batbrain";
            case Badnik.BUZZ_BOMBER -> "buzz_bomber";
            case Badnik.YADRIN -> "yadrin";
            case Badnik.JAWS -> "jaws";
            case Badnik.BURROBOT -> "burrobot";
            case Badnik.ORBINAUT -> "orbinaut";
            case Badnik.BOMB -> "bomb";
            default -> "ball_hog";
        };
    }

    /** The relics a Ruins band holds. */
    public static String[] ruinsRelics(int band) {
        return switch (band) {
            case RuinsRules.MARBLE -> new String[] {"totem_chip", "totem_wing", "totem_face"};
            case RuinsRules.LABYRINTH -> new String[] {"ring_mould", "giant_ring_shard", "spring_coil"};
            default -> new String[] {"old_monitor", "signpost_plate", "lamppost_globe"};
        };
    }

    /** A relic from a broken rock in a band, or null (most rocks). */
    public static String ruinsRelic(int band, SnapshotRandom rng) {
        if (rng.nextInt(RUINS_RELIC_ODDS) != 0) {
            return null;
        }
        String[] relics = ruinsRelics(band);
        return relics[rng.nextInt(relics.length)];
    }

    /** The relics buried in a season. */
    public static String[] buried(int season) {
        return switch (season) {
            case Calendar.SPRING -> new String[] {"totem_chip", "spring_coil"};
            case Calendar.SUMMER -> new String[] {"ring_mould", "lamppost_globe"};
            case Calendar.FALL -> new String[] {"totem_wing", "totem_face", "signpost_plate"};
            default -> new String[] {"old_monitor", "giant_ring_shard"};
        };
    }

    /** One of the season's buried relics. */
    public static String buriedRelic(Game game) {
        String[] relics = buried(game.calendar.season());
        return relics[game.rng.nextInt(relics.length)];
    }

    /**
     * What a glinting spot holds: a relic of the season ({@link #SPOT_RELIC_PERCENT}%), otherwise
     * marble chips, a marble geode or rings, as {@code "id:count"} ({@code "rings:N"} for rings).
     */
    public static String spot(Game game) {
        int roll = game.rng.nextInt(100);
        if (roll < SPOT_RELIC_PERCENT) {
            return buriedRelic(game) + ":1";
        }
        if (roll < 70) {
            return "marble_chip:" + (2 + game.rng.nextInt(3));
        }
        if (roll < 85 && game.catalog.hasItem("marble_geode")) {
            return "marble_geode:1";
        }
        return "rings:" + (10 + game.rng.nextInt(21));
    }
}
