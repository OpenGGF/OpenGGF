package eggsky.surface;

/** A projectile: Eggman's blaster bolt, Tails' ring bomb or a creature's spit. */
public final class Shot {
    public static final int BOLT = 0;
    public static final int RING_BOMB = 1;
    public static final int SPIT = 2;

    public final int type;
    public final boolean hostile;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public float damage;
    public int life;
    public boolean alive = true;

    public Shot(int type, boolean hostile, float x, float y, float vx, float vy, float damage, int life) {
        this.type = type;
        this.hostile = hostile;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.life = life;
    }
}
