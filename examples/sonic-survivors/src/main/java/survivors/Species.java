package survivors;

/**
 * The stock Sonic 2 badniks the arenas use, drawn with the ROM art the zone's PLCs already load.
 * Art keys, mapping frames, facing and touch sizes come from the shipped objects; hitpoints,
 * speeds, AI archetypes and fire rates are mod design. Ids are stored in the spawn subtype.
 */
final class Species {
    private Species() { }

    static final int BUZZER = 0, COCONUTS = 1, MASHER = 2, SPINY = 3, GRABBER = 4, WHISP = 5, CHOPCHOP = 6,
            CRAWL = 7, SPIKER = 8, SOL = 9, CRAWLTON = 10, FLASHER = 11, AQUIS = 12, OCTUS = 13,
            SHELLCRACKER = 14, SLICER = 15, ASTERON = 16, CLUCKER = 17, BALKIRY = 18;
    static final int COUNT = 19;

    /** Walks along the floor toward Sonic. */
    static final int WALKER = 0;
    /** Hops toward Sonic in arcs. */
    static final int HOPPER = 1;
    /** Steers through the air toward Sonic, bobbing. */
    static final int FLYER = 2;
    /** Hovers at Sonic's height off-screen, then charges straight across. */
    static final int DIVER = 3;
    /** Leaps high out of the ground near Sonic (Masher's jump). */
    static final int LEAPER = 4;
    /** Rises out of the floor and fires (Clucker). */
    static final int TURRET = 5;
    /** Drifts in and bursts into five spikes near Sonic (Asteron). */
    static final int EXPLODER = 6;

    /**
     * @param artFacesRight the mapping art faces right, so it is mirrored while facing left
     * @param collision     touch size index ({@code collision_flags & $3F}) of the stock object
     * @param speed         movement speed, 1/256 px per frame
     * @param depth         ground species: centre height above the floor
     * @param spiked        a stomp from above hurts Sonic instead (weapons still work)
     * @param shotKey       projectile art key, or null for none
     * @param shotPeriod    frames between shots
     */
    record Traits(int id, String name, String artKey, int[] frames, int ticksPerFrame, boolean artFacesRight,
                  int collision, int hp, int speed, int archetype, int halfWidth, int depth, boolean spiked,
                  String shotKey, int shotFrame, int shotPeriod) {
        int frame(int ticks) { return frames[(ticks / ticksPerFrame) % frames.length]; }
        boolean flying() { return archetype == FLYER || archetype == DIVER || archetype == EXPLODER; }
        boolean shoots() { return shotKey != null && archetype != EXPLODER; }
    }

    static Traits of(int id) {
        return switch (Math.floorMod(id, COUNT)) {
            // Obj4B Buzzer: collision $0A; flies, frames 0/1, shot frames 3+.
            case BUZZER -> new Traits(BUZZER, "BUZZER", "buzzer", new int[]{0}, 8, false, 0x0A, 2, 0x120,
                    FLYER, 24, 12, false, "buzzer", 3, 150);
            // Obj9D Coconuts: collision $09; climb frames 0/1, coconut frame 3.
            case COCONUTS -> new Traits(COCONUTS, "COCONUTS", "coconuts", new int[]{0, 1}, 8, false, 0x09, 3, 0x100,
                    HOPPER, 12, 16, false, "coconuts", 3, 180);
            // Obj5C Masher: collision $09; bite frames 0/1.
            case MASHER -> new Traits(MASHER, "MASHER", "masher", new int[]{0, 1}, 4, false, 0x09, 2, 0x80,
                    LEAPER, 12, 16, false, null, 0, 0);
            // ObjA5 Spiny: collision $0B; crawl frames 0/1, shot frame 6.
            case SPINY -> new Traits(SPINY, "SPINY", "spiny", new int[]{0, 1}, 8, false, 0x0B, 3, 0xC0,
                    WALKER, 16, 8, false, "spiny", 6, 160);
            // ObjA7 Grabber: collision $0B; frames 0/1.
            case GRABBER -> new Traits(GRABBER, "GRABBER", "grabber", new int[]{0, 1}, 8, false, 0x0B, 3, 0x100,
                    FLYER, 16, 12, false, null, 0, 0);
            // Obj8C Whisp: collision $0B; art faces right; wing frames 0/1 every frame.
            case WHISP -> new Traits(WHISP, "WHISP", "whisp", new int[]{0, 1}, 2, true, 0x0B, 1, 0x1C0,
                    FLYER, 12, 8, false, null, 0, 0);
            // Obj91 Chop Chop: collision $02; frames 0/1.
            case CHOPCHOP -> new Traits(CHOPCHOP, "CHOP CHOP", "chopchop", new int[]{0, 1}, 8, false, 0x02, 4, 0x300,
                    DIVER, 16, 12, false, null, 0, 0);
            // ObjC8 Crawl: walk frames 0/1. Armoured: double hitpoints.
            // Casino Night's only badnik: armoured, but not a wall for weapon-light builds.
            case CRAWL -> new Traits(CRAWL, "CRAWL", "crawl", new int[]{0, 1}, 10, false, 0x0C, 5, 0x80,
                    WALKER, 20, 16, false, null, 0, 0);
            // Obj92 Spiker: collision $12; walk frames 0/1 with the drill up.
            case SPIKER -> new Traits(SPIKER, "SPIKER", "spiker", new int[]{0, 1}, 6, false, 0x12, 4, 0xA0,
                    WALKER, 12, 16, true, null, 0, 0);
            // Obj95 Sol: collision $0B; frames 0-2, fireball frame 3.
            case SOL -> new Traits(SOL, "SOL", "sol", new int[]{0, 1, 2}, 8, false, 0x0B, 3, 0xE0,
                    FLYER, 12, 12, false, "sol", 3, 140);
            // Obj9E Crawlton: collision $0B; head frame 0 lunges.
            case CRAWLTON -> new Traits(CRAWLTON, "CRAWLTON", "crawlton", new int[]{0}, 8, false, 0x0B, 3, 0x340,
                    DIVER, 12, 12, false, null, 0, 0);
            // ObjA3 Flasher: collision $06; frames 0-2, lit 3/4 (harmful while lit).
            case FLASHER -> new Traits(FLASHER, "FLASHER", "flasher", new int[]{0, 1, 2}, 4, false, 0x06, 2, 0x140,
                    FLYER, 12, 8, false, null, 0, 0);
            // Obj50 Aquis: collision $0A; frames 0/3/4, shot frame 5.
            case AQUIS -> new Traits(AQUIS, "AQUIS", "aquis", new int[]{0, 3, 4, 3}, 6, false, 0x0A, 4, 0xC0,
                    FLYER, 16, 16, false, "aquis", 5, 150);
            // Obj4A Octus: collision $0A; frames 0-4, shot frame 5.
            case OCTUS -> new Traits(OCTUS, "OCTUS", "octus", new int[]{0, 1, 2, 3, 4}, 6, false, 0x0A, 4, 0xC0,
                    HOPPER, 16, 16, false, "octus", 5, 170);
            // Obj9F Shellcracker: collision $0A; walk frames 0-2.
            case SHELLCRACKER -> new Traits(SHELLCRACKER, "SHELLCRACKER", "shellcracker", new int[]{0, 1, 2}, 8, false,
                    0x0A, 6, 0x90, WALKER, 24, 12, false, null, 0, 0);
            // ObjA1 Slicer: collision $06; walk frames 0-2, pincer frames 5-8.
            case SLICER -> new Traits(SLICER, "SLICER", "slicer", new int[]{0, 1, 2}, 8, false, 0x06, 5, 0x90,
                    WALKER, 16, 16, false, "slicer", 5, 180);
            // ObjA4 Asteron: collision $0B; frames 0/1, spikes 2-4.
            case ASTERON -> new Traits(ASTERON, "ASTERON", "asteron", new int[]{0, 1}, 4, false, 0x0B, 3, 0x140,
                    EXPLODER, 12, 12, true, "asteron", 2, 0);
            // ObjAE Clucker: collision $06; rises 3-7, fires 8-11, shot frame 13.
            case CLUCKER -> new Traits(CLUCKER, "CLUCKER", "clucker", new int[]{7}, 8, false, 0x06, 6, 0,
                    TURRET, 16, 12, false, "clucker", 13, 110);
            // ObjAC Balkiry: collision $08; frames 0/1.
            default -> new Traits(BALKIRY, "BALKIRY", "balkiry", new int[]{0, 1}, 4, false, 0x08, 4, 0x380,
                    DIVER, 24, 12, false, null, 0, 0);
        };
    }
}
