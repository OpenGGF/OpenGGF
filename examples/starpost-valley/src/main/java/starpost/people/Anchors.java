package starpost.people;

/**
 * The named spots villagers walk between. Doorway ids ({@code inn}, {@code seed_stall}...) come
 * from {@code Valley.places} at run time, so a moved building takes its regulars with it; the
 * values here are the fallback (and what engine-free tests resolve against). The valley is Green
 * Hill act 1's blocks 13, 45, 60 x4, 45, 3, 45, 53, 38, 1, 16, each 256 pixels wide.
 */
public final class Anchors {
    /** Belt-view farm x positions (see {@code FarmView}): the house door, signpost, field and gate. */
    public static final int FARM_DOOR = 76;
    public static final int FARM_SIGNPOST = 150;
    public static final int FARM_FIELD = 176;
    public static final int FARM_GATE = 176 + 60 * 16 + 56;
    /** The valley's farm gate (the Star Post at the west end). */
    public static final int VALLEY_GATE = 150;

    private Anchors() {
    }

    /** The fallback valley x of an anchor, or -1 when the name is unknown. */
    public static int valleyX(String anchor) {
        return switch (anchor) {
            case "farm_gate" -> VALLEY_GATE;
            case "gate" -> 196;            // just east of the Star Post
            case "waterfall" -> 70;
            case "palms_west" -> 300;
            case "path" -> 440;            // the walk into town
            case "seed_stall" -> 2 * 256 + 112;
            case "plaza" -> 2 * 256 + 200;
            case "inn" -> 3 * 256 + 128;
            case "inn_porch" -> 3 * 256 + 186;
            case "workshop" -> 4 * 256 + 128;
            case "workshop_yard" -> 4 * 256 + 196;
            case "egg" -> 5 * 256 + 128;
            case "caravan" -> 5 * 256 + 214;   // Robotnik's Egg Mobile caravan behind EGG
            case "palms_east" -> 6 * 256 + 120;
            case "spring" -> 7 * 256 + 20;
            case "ledge" -> 7 * 256 + 150;     // the totem ledge (Knuckles's lookout)
            case "palms_far" -> 8 * 256 + 120;
            case "slope" -> 10 * 256 + 200;
            case "shack" -> 11 * 256 + 8;      // Pud's shack, just west of the Ruins' mouth
            case "ruins" -> 11 * 256 + 64;
            case "meadow" -> 11 * 256 + 170;
            case "capsule" -> 12 * 256 + 120;
            case "lake" -> 12 * 256 + 228;
            case "jetty" -> 12 * 256 + 214;
            default -> -1;
        };
    }

    /** The farm x of a farm anchor, or -1. */
    public static int farmX(String anchor) {
        return switch (anchor) {
            case "door" -> FARM_DOOR + 26;
            case "signpost" -> FARM_SIGNPOST + 4;
            case "field" -> FARM_FIELD + 120;
            case "house" -> FARM_DOOR - 20;
            case "gate" -> FARM_GATE - 24;
            default -> -1;
        };
    }

    public static boolean known(Spot spot) {
        return spot.farm() ? farmX(spot.anchor()) >= 0 : valleyX(spot.anchor()) >= 0;
    }
}
