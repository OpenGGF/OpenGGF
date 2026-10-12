package starpost.fishing;

/**
 * One thing that can bite (design doc §6.6): a fish, or a submerged badnik. Where and when it
 * bites decides who catches it; how it fights decides how hard the Bubble Bar is.
 *
 * @param id         item id (the shell's id for a badnik)
 * @param name       shown name
 * @param price      what the shipping signpost pays
 * @param spots      {@link #POND} and/or {@link #LAKE}
 * @param seasons    bit {@code 1 << season} for each season it bites in
 * @param from       first minute of the day it bites (06:00 is 360)
 * @param to         last minute (02:00 is 1560)
 * @param weather    {@link #ANY}, {@link #DRY} ... {@link #AURORA}
 * @param difficulty 0 (a minnow) to 100 (the Red Chopper)
 * @param motion     how it moves in the Bubble Bar
 * @param weight     how often it bites, relative to the others there
 * @param badnik     the badnik's sprite key, or null for a fish
 * @param flag       a story flag it needs before it bites, or null
 * @param once       caught at most once a game
 * @param deep       the least cast depth, in percent, it bites at (lake casts reach 0-100; the pond is 50)
 * @param text       its description
 */
public record FishDef(String id, String name, int price, int spots, int seasons, int from, int to, int weather,
        int difficulty, int motion, int weight, String badnik, String flag, boolean once, int deep, String text) {
    public static final int POND = 1;
    public static final int LAKE = 2;

    public static final int ANY = 0;
    /** Sunny days only. */
    public static final int DRY = 1;
    /** Rain or storm. */
    public static final int WET = 2;
    public static final int STORM = 3;
    public static final int SNOW = 4;
    /** The morning after the badniks swarm. */
    public static final int SWARM = 5;
    /** A night under the Emerald Aurora. */
    public static final int AURORA = 6;

    public static final int SMOOTH = 0;
    public static final int DART = 1;
    public static final int SINKER = 2;
    public static final int FLOATER = 3;
    public static final int MIXED = 4;
    /** Up and down in steady loops (the Loop Pike). */
    public static final int LOOPS = 5;
    /** Sonic 1's Chopper leap. */
    public static final int CHOPPER = 6;
    /** Sonic 1's Jaws swim. */
    public static final int JAWS = 7;
    /** S3K's Jawz torpedo. */
    public static final int JAWZ = 8;
    /** S3K's Blastoid turret. */
    public static final int BLASTOID = 9;
    /** The Red Chopper: leaps of every height, and darts between them. */
    public static final int RED_CHOPPER = 10;

    public boolean isBadnik() {
        return badnik != null;
    }

    public boolean bitesIn(int season) {
        return (seasons & (1 << season)) != 0;
    }

    public boolean bitesAt(int minutes) {
        return minutes >= from && minutes <= to;
    }
}
