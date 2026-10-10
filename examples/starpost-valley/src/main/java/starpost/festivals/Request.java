package starpost.festivals;

/**
 * A request pinned to the Signpost Board (design doc §6.8): a villager wants something brought
 * (a delivery), badniks popped, or, from Robotnik, a suspicious special order. Accepted at the
 * board; a delivery is finished by talking to the villager with the goods, a popping job finishes
 * by itself.
 */
public final class Request {
    public static final int DELIVER = 0;
    public static final int POP = 1;
    public static final int SPECIAL = 2;
    /** Bopping experience per badnik popped (Pests and the Ruins both give 10). */
    public static final int XP_PER_POP = 10;

    public final int id;
    public final String villager;
    public final int type;
    /** The item to deliver (null for popping). */
    public final String item;
    public final int count;
    public final int rings;
    public final int friendship;
    /** The day number it was posted, and the last day it can be done. */
    public final int posted;
    public final int due;
    public boolean accepted;
    /** Popping: Bopping experience when it was accepted. */
    public int base;

    public Request(int id, String villager, int type, String item, int count, int rings, int friendship, int posted,
            int due) {
        this.id = id;
        this.villager = villager;
        this.type = type;
        this.item = item;
        this.count = count;
        this.rings = rings;
        this.friendship = friendship;
        this.posted = posted;
        this.due = due;
    }

    public boolean delivery() {
        return type == DELIVER || type == SPECIAL;
    }

    /** Days left including today (0 when it has run out). */
    public int daysLeft(int today) {
        return Math.max(0, due - today + 1);
    }

    /** Popping: badniks popped since it was accepted. */
    public int popped(int boppingXp) {
        return Math.max(0, (boppingXp - base) / XP_PER_POP);
    }
}
