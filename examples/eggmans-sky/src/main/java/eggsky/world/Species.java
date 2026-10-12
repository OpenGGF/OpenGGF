package eggsky.world;

import com.openggf.mods.scene.SceneSprite;
import eggsky.art.FaunaDef;
import eggsky.core.Recolor;

/**
 * A discoverable species on one planet: a creature (a ROM body re-coloured and re-sized), a
 * plant or a mineral (procedural sprites). Its {@link #id} is the key the discovery log uses.
 */
public final class Species {
    public static final int FAUNA = 0;
    public static final int FLORA = 1;
    public static final int MINERAL = 2;

    public static final int SKITTISH = 0;
    public static final int PASSIVE = 1;
    public static final int AGGRESSIVE = 2;
    public static final int PREDATOR = 3;

    public final String id;
    public final int kind;
    public final String name;
    public final long seed;
    // Creatures.
    public FaunaDef body;
    public Recolor recolor;
    public float scale = 1;
    public int temperament;
    public int health;
    public float speed;
    public String diet = "";
    public String note = "";
    public float heightM;
    public float weightKg;
    // Plants and minerals.
    public SceneSprite[] sprites;
    public int yield;
    public int yieldCount;
    public int value;
    public int colour;

    public Species(String id, int kind, String name, long seed) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.seed = seed;
    }

    public String kindName() {
        return switch (kind) {
            case FAUNA -> body != null && body.animal() ? "Fauna" : "Robotic fauna";
            case FLORA -> "Flora";
            default -> "Mineral";
        };
    }

    public String temperamentName() {
        return switch (temperament) {
            case SKITTISH -> "Skittish";
            case PASSIVE -> "Passive";
            case AGGRESSIVE -> "Aggressive";
            default -> "Predatory";
        };
    }
}
