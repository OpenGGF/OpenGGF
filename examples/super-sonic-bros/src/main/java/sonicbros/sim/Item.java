package sonicbros.sim;

/**
 * A monitor dropped onto the stage. Any attack (or projectile) breaks it open and its contents
 * go to whoever broke it, as in the original games. The Eggman monitor explodes instead.
 */
public final class Item {
    public static final int SUPER_RING = 0;
    public static final int FIRE_SHIELD = 1;
    public static final int BUBBLE_SHIELD = 2;
    public static final int LIGHTNING_SHIELD = 3;
    public static final int INVINCIBILITY = 4;
    public static final int SPEED_SHOES = 5;
    public static final int EGGMAN = 6;
    public static final int SUPER = 7;
    public static final int KINDS = 8;

    /** Display names, by kind. */
    public static String name(int kind) {
        return switch (kind) {
            case SUPER_RING -> "SUPER RING";
            case FIRE_SHIELD -> "FLAME SHIELD";
            case BUBBLE_SHIELD -> "BUBBLE SHIELD";
            case LIGHTNING_SHIELD -> "THUNDER SHIELD";
            case INVINCIBILITY -> "INVINCIBILITY";
            case SPEED_SHOES -> "SPEED SHOES";
            case EGGMAN -> "EGGMAN";
            default -> "SUPER EMERALD";
        };
    }

    /** Relative chance of each kind dropping. */
    public static int weight(int kind) {
        return switch (kind) {
            case SUPER_RING -> 24;
            case FIRE_SHIELD, BUBBLE_SHIELD, LIGHTNING_SHIELD -> 9;
            case INVINCIBILITY -> 8;
            case SPEED_SHOES -> 12;
            case EGGMAN -> 12;
            default -> 4;
        };
    }

    public final int kind;
    public double x;
    public double y;
    public double vy;
    public int ground = -1;
    public int age;
    public boolean dead;

    Item(int kind, double x, double y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
    }

    /** Frames a monitor stays before blinking away. */
    public static final int LIFE = 20 * 60;
}
