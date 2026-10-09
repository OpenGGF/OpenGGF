package eggsky.surface;

/**
 * Loot flying out of something broken: resources, rings or Chaos Shards. It bursts out, settles,
 * then is pulled into the Egg Mobile.
 */
public final class Pickup {
    public static final int ITEM = 0;
    public static final int RINGS = 1;
    public static final int SHARDS = 2;

    public final int type;
    public final int item;
    public int amount;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public int age;
    public boolean alive = true;
    /** Lost by a hero: free for anyone, fades after a while. */
    public boolean scattered;

    public Pickup(int type, int item, int amount, float x, float y, float vx, float vy) {
        this.type = type;
        this.item = item;
        this.amount = amount;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
    }
}
