package threeislands.core;

/**
 * Accessories: each hero wears one. {@code shop} is the island ordinal whose travelling stall
 * stocks it (-1 when it is only found); {@code element} gives plain attacks that element and
 * immunity to its status; {@code crit} adds critical-hit percent and {@code regen} restores EP
 * at the start of each of the wearer's turns.
 */
public enum Gear {
    // Pocky's stock grows island by island.
    POWER_SNEAKERS("Power Sneakers", 0, 220, 0, 0, 3, 0, 0, Kinds.NONE, 0, 0, "SPD +3. Light, springy and red."),
    GUARD_BAND("Guard Band", 0, 240, 0, 4, 0, 0, 0, Kinds.NONE, 0, 0, "DEF +4. A padded wristband."),
    POWER_GLOVES("Power Gloves", 1, 420, 4, 0, 0, 0, 0, Kinds.NONE, 0, 0, "ATK +4. Reinforced knuckle plates."),
    LIFE_CHARM("Life Charm", 1, 440, 0, 0, 0, 25, 0, Kinds.NONE, 0, 0, "Max HP +25."),
    RING_LOCKET("Ring Locket", 2, 620, 0, 0, 0, 0, 8, Kinds.NONE, 0, 0, "Max EP +8. A ring that hums faintly."),
    SPIKED_BRACERS("Spiked Bracers", 2, 760, 7, 0, 0, 0, 0, Kinds.NONE, 0, 0, "ATK +7. Heavy, but they hit hard."),

    // Hidden in each dungeon's treasure room.
    FLICKY_FEATHER("Flicky Feather", -1, 0, 0, 0, 1, 0, 4, Kinds.NONE, 0, 0, "EP +4, SPD +1. A gift from the shrine."),
    VOLT_CHARM("Volt Charm", -1, 0, 0, 0, 2, 0, 0, Kinds.ELEC, 0, 0,
            "Attacks crackle with Lightning. SPD +2. Cannot be stunned."),
    SPRING_BOOTS("Spring Boots", -1, 0, 0, 2, 2, 0, 0, Kinds.NONE, 0, 0, "DEF +2, SPD +2. Freight-yard springs."),
    WORK_GOGGLES("Work Goggles", -1, 0, 2, 0, 0, 0, 0, Kinds.NONE, 10, 0, "ATK +2, critical hits +10%."),
    AQUA_CHARM("Aqua Charm", -1, 0, 0, 3, 0, 0, 0, Kinds.WATER, 0, 0,
            "Attacks splash with Water. DEF +3. Cannot be soaked."),
    MINER_LAMP("Miner's Lamp", -1, 0, 0, 3, 0, 15, 0, Kinds.NONE, 0, 0, "Max HP +15, DEF +3."),
    FLAME_CHARM("Flame Charm", -1, 0, 3, 0, 0, 0, 0, Kinds.FIRE, 0, 0,
            "Attacks burn with Fire. ATK +3. Cannot be burned."),
    TIDE_AMULET("Tide Amulet", -1, 0, 0, 0, 0, 20, 0, Kinds.NONE, 0, 1, "Max HP +20; recover 1 EP each turn."),
    ROCKET_BOOTS("Rocket Boots", -1, 0, 3, 0, 5, 0, 0, Kinds.NONE, 0, 0, "SPD +5, ATK +3."),
    CHAOS_RING("Chaos Ring", -1, 0, 3, 3, 3, 20, 6, Kinds.NONE, 0, 2,
            "Every stat rises; recover 2 EP each turn."),

    // Gifts from the rescued animals.
    LUCKY_STAR("Lucky Star", -1, 0, 0, 0, 2, 0, 0, Kinds.NONE, 15, 0, "SPD +2, critical hits +15%."),
    HEART_PENDANT("Heart Pendant", -1, 0, 0, 2, 0, 40, 0, Kinds.NONE, 0, 0, "Max HP +40, DEF +2."),
    GOLD_MEDALLION("Gold Medallion", -1, 0, 5, 5, 3, 20, 4, Kinds.NONE, 5, 0, "A hero's medal: every stat rises."),

    // Prizes from the Rift Echoes, optional duels with memories of beaten machines.
    WARP_SHOES("Warp Shoes", -1, 0, 2, 0, 7, 0, 0, Kinds.NONE, 5, 0, "SPD +7, ATK +2. They hum with rift light."),
    RIFT_MANTLE("Rift Mantle", -1, 0, 0, 8, 0, 35, 0, Kinds.NONE, 0, 0, "DEF +8, Max HP +35."),
    HYPER_RING("Hyper Ring", -1, 0, 0, 0, 0, 0, 12, Kinds.NONE, 0, 2, "Max EP +12; recover 2 EP each turn."),
    MASTER_SHARD("Master Shard", -1, 0, 9, 2, 2, 0, 0, Kinds.NONE, 10, 0, "ATK +9, critical hits +10%. A sliver of green light.");

    public final String label;
    public final int shop;
    public final int price;
    public final int atk, def, spd, hp, ep;
    public final int elementIndex;
    public final int crit;
    public final int regen;
    public final String description;

    Gear(String label, int shop, int price, int atk, int def, int spd, int hp, int ep, int elementIndex, int crit,
            int regen, String description) {
        this.label = label;
        this.shop = shop;
        this.price = price;
        this.atk = atk;
        this.def = def;
        this.spd = spd;
        this.hp = hp;
        this.ep = ep;
        this.elementIndex = elementIndex;
        this.crit = crit;
        this.regen = regen;
        this.description = description;
    }

    public Element element() {
        return Element.values()[elementIndex];
    }

    public int bit() {
        return 1 << ordinal();
    }
}
