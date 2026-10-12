package eggsky.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Every item, refining recipe, crafting recipe and technology. Ids are the {@code static final}
 * ints below (the save stores them); the descriptions live in instance lists built here, since
 * mods keep no static collections.
 */
public final class Catalog {
    // ---- Elements (mined or harvested) ----
    public static final int CARBON = 1;
    public static final int CONDENSED_CARBON = 2;
    public static final int FERRITE = 3;
    public static final int PURE_FERRITE = 4;
    public static final int MAGNETISED_FERRITE = 5;
    public static final int OXYGEN = 6;
    public static final int SODIUM = 7;
    public static final int DIHYDROGEN = 8;
    public static final int DIHYDROGEN_JELLY = 9;
    public static final int COBALT = 10;
    public static final int GOLD = 11;
    public static final int COPPER = 12;
    public static final int CADMIUM = 13;
    public static final int EMERIL = 14;
    public static final int INDIUM = 15;
    public static final int CHROMATIC_METAL = 16;
    public static final int STORM_CRYSTAL = 17;
    public static final int BADNIK_SCRAP = 18;
    public static final int ECHIDNA_RELIC = 19;
    public static final int SOLANIUM = 20;
    public static final int FROST_CRYSTAL = 21;
    public static final int FUNGAL_MOULD = 22;
    public static final int GAMMA_ROOT = 23;
    public static final int STAR_BULB = 24;
    public static final int SILVER = 25;
    // ---- Products (crafted) ----
    public static final int EGG_FUEL = 30;
    public static final int LIFE_GEL = 31;
    public static final int ION_BATTERY = 32;
    public static final int ANTIMATTER = 33;
    public static final int ANTIMATTER_HOUSING = 34;
    public static final int WARP_CELL = 35;
    public static final int METAL_PLATING = 36;
    public static final int BADNIK_CORE = 37;
    public static final int STARSHIP_REPAIR = 38;
    public static final int SHIELD_CELL = 39;
    public static final int MAX_ITEM = 40;

    public static final int KIND_ELEMENT = 0;
    public static final int KIND_PRODUCT = 1;
    public static final int KIND_VALUABLE = 2;

    /** An item: its name, colour, stack size, base value in rings and what it is for. */
    public record Item(int id, String name, String code, int colour, int stack, int value, int kind, String info) {
    }

    /** {@code inCount} of {@code input} refines into {@code outCount} of {@code output}. */
    public record Refine(int input, int inCount, int output, int outCount) {
    }

    /** A crafted product from up to three ingredients. */
    public record Recipe(int output, int outCount, int[] inputs, int[] counts) {
    }

    // ---- Technology ----
    public static final int T_HULL = 0;
    public static final int T_SHIELD = 1;
    public static final int T_LIFE = 2;
    public static final int T_HAZARD = 3;
    public static final int T_BEAM = 4;
    public static final int T_COOLER = 5;
    public static final int T_SCANNER = 6;
    public static final int T_SURVEY = 7;
    public static final int T_JETS = 8;
    public static final int T_THRUSTERS = 9;
    public static final int T_CARGO = 10;
    public static final int T_BLASTER = 11;
    public static final int T_SHAPER = 12;
    public static final int T_AUTO = 13;
    public static final int T_PULSE = 14;
    public static final int T_DEFLECTOR = 15;
    public static final int T_CANNON = 16;
    public static final int T_HYPERDRIVE = 17;
    public static final int T_RED_DRIVE = 18;
    public static final int T_GREEN_DRIVE = 19;
    public static final int T_BLUE_DRIVE = 20;
    public static final int T_MAGNET = 21;
    public static final int T_CLOAK = 22;
    public static final int TECH_COUNT = 23;

    /**
     * A technology with {@code max} levels: level n costs {@code shards * n} Chaos Shards plus
     * {@code count * n} of {@code material}. {@code where} is 0 for the Egg Mobile's own tools,
     * 1 for its space systems.
     */
    public record Tech(int id, String name, String info, int max, int shards, int material, int count, int where) {
    }

    private final Item[] items = new Item[MAX_ITEM];
    private final List<Refine> refines = new ArrayList<>();
    private final List<Recipe> recipes = new ArrayList<>();
    private final Tech[] techs = new Tech[TECH_COUNT];

    public Catalog() {
        item(CARBON, "Carbon", "C", 0xFF48C048, 250, 7, KIND_ELEMENT, "Harvested from plants. Refines into Condensed Carbon.");
        item(CONDENSED_CARBON, "Condensed Carbon", "C+", 0xFF20A060, 250, 24, KIND_ELEMENT, "Dense carbon for antimatter and gels.");
        item(FERRITE, "Ferrite Dust", "Fe", 0xFFB4B4B4, 250, 14, KIND_ELEMENT, "Mined from rock and terrain.");
        item(PURE_FERRITE, "Pure Ferrite", "Fe+", 0xFFD8D8E8, 250, 28, KIND_ELEMENT, "Refined ferrite for plating and fuel.");
        item(MAGNETISED_FERRITE, "Magnetised Ferrite", "Fe++", 0xFF9090FF, 250, 82, KIND_ELEMENT, "Hyperdrive-grade metal.");
        item(OXYGEN, "Oxygen", "O2", 0xFFE04848, 250, 34, KIND_ELEMENT, "From oxygen plants. Recharges life support.");
        item(SODIUM, "Sodium", "Na", 0xFFF0C030, 250, 41, KIND_ELEMENT, "From sodium plants. Recharges hazard protection.");
        item(DIHYDROGEN, "Di-hydrogen", "H", 0xFF4890FF, 250, 34, KIND_ELEMENT, "Blue crystals. Fuels launches.");
        item(DIHYDROGEN_JELLY, "Di-hydrogen Jelly", "H+", 0xFF2060E0, 250, 120, KIND_ELEMENT, "Refined launch-fuel base.");
        item(COBALT, "Cobalt", "Co", 0xFF3070D0, 250, 198, KIND_ELEMENT, "Found deep in caves. Ion batteries.");
        item(GOLD, "Gold", "Au", 0xFFFFD040, 250, 202, KIND_VALUABLE, "Precious metal. Sells well.");
        item(SILVER, "Silver", "Ag", 0xFFE0E0F0, 250, 101, KIND_VALUABLE, "Precious metal. Sells well.");
        item(COPPER, "Copper", "Cu", 0xFFE08840, 250, 110, KIND_ELEMENT, "Yellow-star metal. Refines to Chromatic Metal.");
        item(CADMIUM, "Cadmium", "Cd", 0xFFE03030, 250, 234, KIND_ELEMENT, "Red-star metal. Rich chromatic yield.");
        item(EMERIL, "Emeril", "Em", 0xFF30E080, 250, 275, KIND_ELEMENT, "Green-star metal. Richer still.");
        item(INDIUM, "Indium", "In", 0xFF30C0FF, 250, 464, KIND_ELEMENT, "Blue-star metal. The richest.");
        item(CHROMATIC_METAL, "Chromatic Metal", "Cr", 0xFFFF80E0, 250, 245, KIND_ELEMENT, "Shifting metal for warp tech.");
        item(STORM_CRYSTAL, "Storm Crystal", "SC", 0xFFC0F0FF, 10, 3500, KIND_VALUABLE, "Grows only during storms. Very valuable.");
        item(BADNIK_SCRAP, "Badnik Scrap", "Bx", 0xFF808890, 250, 60, KIND_ELEMENT, "Salvaged robot parts.");
        item(ECHIDNA_RELIC, "Echidna Relic", "Rl", 0xFFE0A030, 10, 4200, KIND_VALUABLE, "An ancient artifact from the ruins.");
        item(SOLANIUM, "Solanium", "So", 0xFFFF7040, 250, 70, KIND_ELEMENT, "Heat-loving plant matter.");
        item(FROST_CRYSTAL, "Frost Crystal", "Fr", 0xFFA0E0FF, 250, 12, KIND_ELEMENT, "Ice-bound mineral.");
        item(FUNGAL_MOULD, "Fungal Mould", "Mo", 0xFF90C030, 250, 16, KIND_ELEMENT, "Toxic growth.");
        item(GAMMA_ROOT, "Gamma Root", "Gr", 0xFFC0FF40, 250, 16, KIND_ELEMENT, "Radioactive tuber.");
        item(STAR_BULB, "Star Bulb", "Sb", 0xFFFF60C0, 250, 32, KIND_ELEMENT, "Exotic glowing seed.");
        item(EGG_FUEL, "Egg Fuel", "EF", 0xFFFF9020, 10, 450, KIND_PRODUCT, "Fills the launch thrusters.");
        item(LIFE_GEL, "Life Support Gel", "LG", 0xFFFF6080, 10, 200, KIND_PRODUCT, "Fully recharges life support.");
        item(ION_BATTERY, "Ion Battery", "IB", 0xFF60A0FF, 10, 600, KIND_PRODUCT, "Fully recharges hazard protection.");
        item(ANTIMATTER, "Antimatter", "AM", 0xFFFF40FF, 10, 5233, KIND_PRODUCT, "Unstable matter for warp cells.");
        item(ANTIMATTER_HOUSING, "Antimatter Housing", "AH", 0xFFC080FF, 10, 1240, KIND_PRODUCT, "Contains antimatter.");
        item(WARP_CELL, "Warp Cell", "WC", 0xFF80FFFF, 10, 46750, KIND_PRODUCT, "Powers one hyperdrive jump.");
        item(METAL_PLATING, "Metal Plating", "MP", 0xFFA0A8C0, 10, 800, KIND_PRODUCT, "Repairs the Egg Mobile's hull.");
        item(BADNIK_CORE, "Badnik Core", "BC", 0xFFFF4040, 10, 3000, KIND_PRODUCT, "A robot's heart. Used in advanced tech.");
        item(STARSHIP_REPAIR, "Repair Kit", "RK", 0xFF60FF60, 10, 1500, KIND_PRODUCT, "Repairs damaged systems instantly.");
        item(SHIELD_CELL, "Shield Cell", "SH", 0xFF40FFC0, 10, 900, KIND_PRODUCT, "Recharges the deflector in flight.");

        refines.add(new Refine(CARBON, 2, CONDENSED_CARBON, 1));
        refines.add(new Refine(FERRITE, 1, PURE_FERRITE, 1));
        refines.add(new Refine(PURE_FERRITE, 2, MAGNETISED_FERRITE, 1));
        refines.add(new Refine(DIHYDROGEN, 4, DIHYDROGEN_JELLY, 1));
        refines.add(new Refine(COPPER, 2, CHROMATIC_METAL, 1));
        refines.add(new Refine(CADMIUM, 1, CHROMATIC_METAL, 1));
        refines.add(new Refine(EMERIL, 2, CHROMATIC_METAL, 3));
        refines.add(new Refine(INDIUM, 1, CHROMATIC_METAL, 2));
        refines.add(new Refine(SILVER, 1, GOLD, 1));
        refines.add(new Refine(FROST_CRYSTAL, 2, DIHYDROGEN, 3));
        refines.add(new Refine(SOLANIUM, 1, SODIUM, 2));
        refines.add(new Refine(FUNGAL_MOULD, 1, CARBON, 3));
        refines.add(new Refine(GAMMA_ROOT, 1, OXYGEN, 1));
        refines.add(new Refine(STAR_BULB, 1, OXYGEN, 2));
        refines.add(new Refine(BADNIK_SCRAP, 4, PURE_FERRITE, 6));
        refines.add(new Refine(OXYGEN, 1, CARBON, 2));
        refines.add(new Refine(SODIUM, 1, CARBON, 2));

        recipe(EGG_FUEL, 1, DIHYDROGEN_JELLY, 1, PURE_FERRITE, 20, 0, 0);
        recipe(LIFE_GEL, 1, CONDENSED_CARBON, 10, OXYGEN, 20, 0, 0);
        recipe(ION_BATTERY, 1, FERRITE, 20, COBALT, 10, 0, 0);
        recipe(METAL_PLATING, 1, FERRITE, 50, 0, 0, 0, 0);
        recipe(ANTIMATTER, 1, CHROMATIC_METAL, 25, CONDENSED_CARBON, 20, 0, 0);
        recipe(ANTIMATTER_HOUSING, 1, OXYGEN, 25, PURE_FERRITE, 25, 0, 0);
        recipe(WARP_CELL, 1, ANTIMATTER, 1, ANTIMATTER_HOUSING, 1, 0, 0);
        recipe(BADNIK_CORE, 1, BADNIK_SCRAP, 40, CHROMATIC_METAL, 30, GOLD, 5);
        recipe(STARSHIP_REPAIR, 1, METAL_PLATING, 1, SODIUM, 20, 0, 0);
        recipe(SHIELD_CELL, 1, SODIUM, 30, PURE_FERRITE, 15, 0, 0);

        tech(T_HULL, "Hull Plating", "+25 hull per level", 5, 80, METAL_PLATING, 1, 0);
        tech(T_SHIELD, "Egg Barrier", "Personal shield; faster recharge", 4, 120, SODIUM, 40, 0);
        tech(T_LIFE, "Life Support Tank", "+30% life support", 4, 90, OXYGEN, 30, 0);
        tech(T_HAZARD, "Hazard Shielding", "Hazards drain 25% slower", 4, 110, SODIUM, 30, 0);
        tech(T_BEAM, "Mining Laser Power", "Mine 35% faster", 5, 100, CHROMATIC_METAL, 15, 0);
        tech(T_COOLER, "Laser Coolant", "Laser heats 30% slower", 4, 80, COBALT, 10, 0);
        tech(T_SCANNER, "Scanner Range", "Wider scan pulse, faster analysis", 4, 70, CARBON, 60, 0);
        tech(T_SURVEY, "Survey Bonus", "+40% rings from discoveries", 4, 140, CHROMATIC_METAL, 20, 0);
        tech(T_JETS, "Hover Jets", "+35% jet energy", 4, 90, DIHYDROGEN, 40, 0);
        tech(T_THRUSTERS, "Thrusters", "+15% speed", 4, 100, PURE_FERRITE, 40, 0);
        tech(T_CARGO, "Cargo Pod", "+4 inventory slots", 6, 150, MAGNETISED_FERRITE, 10, 0);
        tech(T_BLASTER, "Egg Blaster", "Holding fire also shoots bolts", 4, 160, BADNIK_SCRAP, 30, 0);
        tech(T_SHAPER, "Terrain Shaper", "The laser digs through terrain", 3, 120, PURE_FERRITE, 30, 0);
        tech(T_AUTO, "Auto Recharger", "Uses oxygen and sodium automatically", 1, 200, BADNIK_CORE, 1, 0);
        tech(T_MAGNET, "Ring Magnet", "Pulls rings and loot from further away", 3, 60, GOLD, 5, 0);
        tech(T_CLOAK, "Sentinel Jammer", "Wanted level rises 30% slower", 3, 180, BADNIK_CORE, 1, 0);
        tech(T_PULSE, "Pulse Engine", "+25% space speed", 4, 100, DIHYDROGEN_JELLY, 3, 1);
        tech(T_DEFLECTOR, "Deflector", "+40% ship shield", 4, 110, SODIUM, 50, 1);
        tech(T_CANNON, "Photon Cannon", "+40% ship damage", 4, 120, CHROMATIC_METAL, 25, 1);
        tech(T_HYPERDRIVE, "Hyperdrive Range", "+120 light years per jump", 5, 200, MAGNETISED_FERRITE, 15, 1);
        tech(T_RED_DRIVE, "Cadmium Drive", "Warp to red stars", 1, 400, CADMIUM, 100, 1);
        tech(T_GREEN_DRIVE, "Emeril Drive", "Warp to green stars", 1, 700, EMERIL, 150, 1);
        tech(T_BLUE_DRIVE, "Indium Drive", "Warp to blue stars", 1, 1100, INDIUM, 150, 1);
    }

    private void item(int id, String name, String code, int colour, int stack, int value, int kind, String info) {
        items[id] = new Item(id, name, code, colour, stack, value, kind, info);
    }

    private void recipe(int output, int outCount, int a, int ac, int b, int bc, int c, int cc) {
        int n = c != 0 ? 3 : b != 0 ? 2 : 1;
        int[] in = new int[n];
        int[] counts = new int[n];
        in[0] = a;
        counts[0] = ac;
        if (n > 1) {
            in[1] = b;
            counts[1] = bc;
        }
        if (n > 2) {
            in[2] = c;
            counts[2] = cc;
        }
        recipes.add(new Recipe(output, outCount, in, counts));
    }

    private void tech(int id, String name, String info, int max, int shards, int material, int count, int where) {
        techs[id] = new Tech(id, name, info, max, shards, material, count, where);
    }

    public Item item(int id) {
        return id > 0 && id < items.length ? items[id] : null;
    }

    public String name(int id) {
        Item item = item(id);
        return item == null ? "?" : item.name();
    }

    public List<Refine> refines() {
        return refines;
    }

    public Refine refineFor(int input) {
        for (Refine r : refines) {
            if (r.input() == input) {
                return r;
            }
        }
        return null;
    }

    public List<Recipe> recipes() {
        return recipes;
    }

    public Tech tech(int id) {
        return id >= 0 && id < techs.length ? techs[id] : null;
    }

    /** All item ids that exist. */
    public List<Integer> itemIds() {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < items.length; i++) {
            if (items[i] != null) {
                out.add(i);
            }
        }
        return out;
    }
}
