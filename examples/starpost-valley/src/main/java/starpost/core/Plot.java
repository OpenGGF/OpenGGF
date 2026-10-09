package starpost.core;

/** One square of the farm field: grass, debris, tilled soil, or a planted crop. */
public final class Plot {
    /** What covers untilled ground. */
    public static final int GRASS = 0;
    public static final int WEED = 1;
    public static final int ROCK = 2;
    public static final int STUMP = 3;

    public int cover = GRASS;
    public boolean tilled;
    public boolean watered;
    /** The crop's id, or null. */
    public String crop;
    /** Days the crop has grown (watered days). */
    public int age;
    /** Days since it last went without water and growing failed; a dead crop is a husk. */
    public boolean dead;
    /** A harvested regrowing crop waits this many days. */
    public int regrowIn;

    public boolean empty() {
        return crop == null;
    }

    public void clearCrop() {
        crop = null;
        age = 0;
        dead = false;
        regrowIn = 0;
    }
}
