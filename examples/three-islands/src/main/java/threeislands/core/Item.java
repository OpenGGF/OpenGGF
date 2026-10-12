package threeislands.core;

/**
 * Items are the classic monitors. {@code icon} is the S3K {@code Map_Monitor} icon frame
 * (1 1-Up, 3 rings, 4 shoes, 5 flame, 6 lightning, 7 bubble, 8 invincibility), or -1 for an
 * item drawn as a blue sphere.
 */
public enum Item {
    SUPER_RING("Super Ring", 30, Kinds.HEAL, 40, Kinds.ALLY, 3, "Ten rings' worth of energy: restores 40 HP."),
    RING_BUNDLE("Ring Bundle", 90, Kinds.HEAL, 35, Kinds.ALL_ALLIES, 3, "Restores 35 HP to the whole party."),
    BLUE_SPHERE("Blue Sphere", 50, Kinds.RESTORE_EP, 12, Kinds.ALLY, -1, "A Special Stage sphere: restores 12 EP."),
    SPEED_SHOES("Speed Shoes", 40, Kinds.HASTE, 3, Kinds.ALL_ALLIES, 4, "The party acts sooner for 3 turns."),
    FLAME_SHIELD("Flame Shield", 60, Kinds.SHIELD, Kinds.FIRE, Kinds.ALLY, 5,
            "Blocks one hit; the holder's attacks burn with Fire."),
    BUBBLE_SHIELD("Bubble Shield", 60, Kinds.SHIELD, Kinds.WATER, Kinds.ALLY, 7,
            "Blocks one hit; the holder's attacks splash with Water."),
    LIGHTNING_SHIELD("Lightning Shield", 60, Kinds.SHIELD, Kinds.ELEC, Kinds.ALLY, 6,
            "Blocks one hit; the holder's attacks crackle with Lightning."),
    INVINCIBILITY("Invincibility", 120, Kinds.INVINCIBLE, 2, Kinds.ALLY, 8, "One ally takes no damage for 2 turns."),
    ONE_UP("1-Up", 100, Kinds.REVIVE, 50, Kinds.FALLEN_ALLY, 1, "Revives a fallen ally with half their HP.");

    public final String label;
    public final int price;
    public final int effect;
    public final int amount;
    public final int target;
    public final int icon;
    public final String description;

    Item(String label, int price, int effect, int amount, int target, int icon, String description) {
        this.label = label;
        this.price = price;
        this.effect = effect;
        this.amount = amount;
        this.target = target;
        this.icon = icon;
        this.description = description;
    }
}
