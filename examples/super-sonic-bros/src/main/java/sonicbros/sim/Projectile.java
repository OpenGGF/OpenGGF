package sonicbros.sim;

/** A thrown or fired object in flight: rings, bombs, lasers, mines and shockwaves. */
public final class Projectile {
    /**
     * What a projectile is.
     *
     * @param kind      the view's picture for it ({@code K_} constants)
     * @param vx        launch speed forward
     * @param vy        launch speed down (negative lobs it upward)
     * @param gravity   added to vy each frame
     * @param life      frames before it fizzles (or explodes, when {@code blast} is set)
     * @param hit       its contact hit (start/end are ignored)
     * @param blast     an explosion hit when it bursts, or null
     * @param bounces   floor bounces before it bursts or stops
     * @param mine      sits where it lands until someone walks into it
     * @param pierce    passes through fighters it hits
     * @param maxOwned  how many one fighter may have out at once
     * @param ground    slides along the floor (shockwaves): follows the stage surface, ends at its edge
     */
    public record Def(int kind, double vx, double vy, double gravity, int life, double radius, Hit hit, Hit blast,
            int bounces, boolean mine, boolean pierce, int maxOwned, boolean ground) { }

    public static final int K_RING = 0;
    public static final int K_BOMB = 1;
    public static final int K_LASER = 2;
    public static final int K_MISSILE = 3;
    public static final int K_MINE = 4;
    public static final int K_SHOCKWAVE = 5;
    public static final int K_SPIKEBALL = 6;
    public static final int K_FIREBALL = 7;
    public static final int K_ENERGY = 8;
    public static final int K_SPRING = 9;

    public final Def def;
    public final Fighter owner;
    public double x;
    public double y;
    public double vx;
    public double vy;
    public int age;
    public int facing;
    public int bouncesLeft;
    public boolean resting;
    public boolean dead;
    /** Set when reflected: the reflector now owns its hits. */
    public Fighter reflectedBy;
    final java.util.Set<Fighter> struck = new java.util.HashSet<>();

    Projectile(Def def, Fighter owner, double x, double y, int facing) {
        this.def = def;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.facing = facing;
        this.vx = def.vx() * facing;
        this.vy = def.vy();
        this.bouncesLeft = def.bounces();
    }

    /** Whose hit this is now. */
    public Fighter attacker() { return reflectedBy != null ? reflectedBy : owner; }
}
