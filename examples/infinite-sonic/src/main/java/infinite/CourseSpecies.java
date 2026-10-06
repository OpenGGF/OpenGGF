package infinite;

import com.openggf.level.objects.ObjectArtKeys;

/**
 * The stock badniks each course zone uses, drawn with the ROM art the S1 object art provider
 * already loads for that zone. Collision sizes, priorities, half-widths, standing radii and
 * animation frames come from the shipped objects; patrols, bobbing and the zone line-ups are
 * mod design choices. Ids are stored in the spawn subtype, so only append new species.
 * (Mod classes may not hold static state, so this is ids and switches rather than an enum.)
 */
public final class CourseSpecies {
    public static final int MOTOBUG = 0;
    public static final int BUZZ_BOMBER = 1;
    public static final int CRABMEAT = 2;
    public static final int YADRIN = 3;
    public static final int ROLLER = 4;
    public static final int BATBRAIN = 5;
    public static final int BURROBOT = 6;
    public static final int ORBINAUT = 7;
    public static final int BOMB = 8;
    public static final int BALL_HOG = 9;
    public static final int COUNT = 10;

    /** Spike collision from Orb_MoveOrb ($98: harmful, size $18). */
    public static final int ORBINAUT_SPIKE = 0x98;

    /**
     * @param collision     obColType; bit 7 set marks a hazard Sonic cannot destroy
     * @param depth         ground species: standing radius above the floor; flyers: lowest
     *                      drawn pixel below centre
     * @param flipFacingLeft the mappings face right, so the art is flipped while moving left
     */
    public record Traits(int id, String name, String artKey, boolean flying, int collision, int priority,
            int halfWidth, int depth, int ticksPerFrame, boolean flipFacingLeft, int[] frames) {
        public int frame(int ticks) { return frames[(ticks / ticksPerFrame) % frames.length]; }
        public boolean hazard() { return (collision & 0x80) != 0; }
        /** Ball Hogs stand their ground; everything else patrols. */
        public boolean patrols() { return id != BALL_HOG; }
        /** A flyer a falling Sonic can break and rebound from: no hazard flag and no circling spikes. */
        public boolean bounceable() { return flying && !hazard() && id != ORBINAUT; }
    }

    public static Traits of(int id) {
        return switch (Math.floorMod(id, COUNT)) {
            // Moto_Main col_40x32 ($0C), obPriority 4, y_radius $E; Ani_Moto drive 0,1,0,2.
            case MOTOBUG -> new Traits(MOTOBUG, "Motobug", ObjectArtKeys.MOTOBUG, false, 0x0c, 4, 20, 14, 8,
                    false, new int[]{0, 1, 0, 2});
            // Buzz_Main col_48x24 ($08), obPriority 3; Ani_Buzz flight 2,3.
            case BUZZ_BOMBER -> new Traits(BUZZ_BOMBER, "Buzz Bomber", ObjectArtKeys.BUZZ_BOMBER, true, 0x08, 3,
                    24, 12, 4, false, new int[]{2, 3});
            // Crab_Main col $06, obPriority 3, y_radius $10, obActWid 42/2; flat walk 1,0.
            case CRABMEAT -> new Traits(CRABMEAT, "Crabmeat", ObjectArtKeys.CRABMEAT, false, 0x06, 3, 21, 0x10, 8,
                    true, new int[]{1, 0});
            // Yadrin col $0C, obPriority 4, y_radius $11, obActWid $14; Ani_Yad walk 0,3,1,4,0,3,2,5 at
            // speed 7. The mod treats it as an ordinary enemy: React_Yadrin's spiked top is not modeled.
            case YADRIN -> new Traits(YADRIN, "Yadrin", ObjectArtKeys.YADRIN, false, 0x0c, 4, 0x14, 0x11, 8,
                    false, new int[]{0, 3, 1, 4, 0, 3, 2, 5});
            // Rolling Roller: Roll_Action_FromLeft sets obColType $8E (harmful, cannot be destroyed),
            // obPriority 4, y_radius $E, obActWid $10; roll 3,4,2 at speed 3.
            case ROLLER -> new Traits(ROLLER, "Roller", ObjectArtKeys.ROLLER, false, 0x8e, 4, 0x10, 0x0e, 4,
                    true, new int[]{3, 4, 2});
            // Basaran col $0B, obPriority 2, obActWid 32/2, obHeight 24/2; .fly 1,2,3,2 at speed 3.
            case BATBRAIN -> new Traits(BATBRAIN, "Batbrain", ObjectArtKeys.BATBRAIN, true, 0x0b, 2, 16, 12, 4,
                    false, new int[]{1, 2, 3, 2});
            // Burrobot col $05, obPriority 4, obHeight 38/2, obActWid 24/2; walk 0,1.
            case BURROBOT -> new Traits(BURROBOT, "Burrobot", ObjectArtKeys.BURROBOT, false, 0x05, 4, 12, 0x13, 4,
                    false, new int[]{0, 1});
            // Orbinaut col $0B, obPriority 4; four Orb_MoveOrb spikes ($98, frame 3) circle at radius 16,
            // so the drawn depth is 16 + the spike's 8.
            case ORBINAUT -> new Traits(ORBINAUT, "Orbinaut", ObjectArtKeys.ORBINAUT, true, 0x0b, 4, 24, 24, 16,
                    false, new int[]{0});
            // Bom_Main col_24x24|col_hurt ($9A: cannot be destroyed), obPriority 3, obActWid 24/2;
            // walk 5,4,3,2 at speed $13. The mod's bomb only walks: no fuse, no shrapnel.
            case BOMB -> new Traits(BOMB, "Walking Bomb", ObjectArtKeys.BOMB, false, 0x9a, 3, 12, 12, 20,
                    false, new int[]{5, 4, 3, 2});
            // Ball Hog col $05, obPriority 4, obHeight 38/2, obActWid 24/2; hops in place with its
            // 0,0,2,2,3,2 frames at speed 9, firing no cannonballs.
            default -> new Traits(BALL_HOG, "Ball Hog", ObjectArtKeys.BALL_HOG, false, 0x05, 4, 12, 0x13, 10,
                    true, new int[]{0, 0, 2, 2, 3, 2});
        };
    }

    /** The species for a stock S1 object id (Sonic1ObjectIds), or -1 if the course has none. */
    public static int fromObjectId(int objectId) {
        return switch (objectId) {
            case 0x40 -> MOTOBUG;
            case 0x22 -> BUZZ_BOMBER;
            case 0x1f -> CRABMEAT;
            case 0x50 -> YADRIN;
            case 0x43 -> ROLLER;
            case 0x55 -> BATBRAIN;
            case 0x2d -> BURROBOT;
            case 0x60 -> ORBINAUT;
            case 0x5f -> BOMB;
            case 0x1e -> BALL_HOG;
            default -> -1;
        };
    }

    /**
     * Line-up for an act: one entry per badnik the stock act places (so common species are
     * picked more often), split into ground or air; the zone defaults below fill an empty side.
     */
    public static int[] lineUp(java.util.List<com.openggf.level.objects.ObjectSpawn> stockObjects,
            boolean flying, int romZone) {
        int[] picks = stockObjects.stream().mapToInt(o -> fromObjectId(o.objectId()))
                .filter(id -> id >= 0 && of(id).flying() == flying).toArray();
        return picks.length > 0 ? picks : flying ? air(romZone) : ground(romZone);
    }

    /** Fallback by stock S1 zone id (Level.getZoneIndex): GHZ 0, LZ 1, MZ 2, SLZ 3, SYZ 4, SBZ 5. */
    public static int[] ground(int romZone) {
        return switch (romZone) {
            case 1 -> new int[]{BURROBOT};
            case 2 -> new int[]{YADRIN};
            case 3 -> new int[]{BOMB};
            case 4 -> new int[]{YADRIN, CRABMEAT, ROLLER};
            case 5 -> new int[]{BALL_HOG, BOMB};
            default -> new int[]{MOTOBUG, CRABMEAT};
        };
    }

    /** Jaws are left out of the dry Labyrinth course: they only swim. */
    public static int[] air(int romZone) {
        return switch (romZone) {
            case 1, 3, 5 -> new int[]{ORBINAUT};
            case 2 -> new int[]{BATBRAIN, BUZZ_BOMBER};
            default -> new int[]{BUZZ_BOMBER};
        };
    }

    private CourseSpecies() { }
}
