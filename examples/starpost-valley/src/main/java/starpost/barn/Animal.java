package starpost.barn;

/** One animal on the farm (engine-free state, saved by {@link Barn}). */
public final class Animal {
    public static final int MAX_AFFECTION = 1000;

    public final int id;
    public final String kind;
    public String name;
    /** 0-1000: five hearts. */
    public int affection;
    /** Fed for today (the hopper, grazing, or Rocky's own fishing). */
    public boolean fed = true;
    public boolean petted;
    public int age;
    /** Days since it last gave. */
    public int since;
    /** Rocky's catch, waiting to be handed over (an item id), or null. */
    public String holding;

    public Animal(int id, String kind, String name) {
        this.id = id;
        this.kind = kind;
        this.name = name;
    }

    public int hearts() {
        return Animals.hearts(affection);
    }
}
