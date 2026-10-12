package threeislands.core;

/**
 * Damage elements. The three elemental shields of Sonic 3 &amp; Knuckles become the game's
 * elements: a Flame, Bubble or Lightning Shield monitor wraps a hero in that element.
 */
public enum Element {
    NONE("Normal", 0xFFE0E0E0),
    FIRE("Fire", 0xFFFF8040),
    WATER("Water", 0xFF50A0FF),
    ELEC("Lightning", 0xFFFFE040);

    public final String label;
    public final int color;

    Element(String label, int color) {
        this.label = label;
        this.color = color;
    }
}
