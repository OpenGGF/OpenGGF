package starfall;

import java.util.List;

/** Immutable, original item/recipe catalogue. Costs are paid atomically by World.craft. */
public final class Content {
    public Content() { }
    public enum Item {
        PICK("Rusty pick", 0xFFBBC7D2, -1), AXE("Woodsman's axe", 0xFFD0AE7D, -1),
        SWORD("Copper saber", 0xFFEEA275, -1), WOOD("Timber", 0xFFB38054, World.PLANK),
        DIRT("Earth", 0xFF9F745C, World.DIRT), STONE("Stone", 0xFF8391A2, World.STONE),
        COPPER("Copper ore", 0xFFED9C69, -1), IRON("Iron ore", 0xFFCCD6DC, -1),
        CRYSTAL("Chaos shard", 0xFF78E9EF, -1), GEL("Power ring", 0xFF8BDDAB, -1),
        BERRY("Wild berry", 0xFFF88DAF, -1), TORCH("Lantern", 0xFFFFD996, World.TORCH),
        PLATFORM("Wood platform", 0xFFBB996B, World.PLATFORM), WALL("Timber wall", 0xFF8F6749, -2),
        BENCH("Workbench", 0xFFDAAD71, World.BENCH), FURNACE("Furnace", 0xFFF0996A, World.FURNACE),
        ANVIL("Anvil", 0xFFABBCCD, World.ANVIL), COPPER_BAR("Copper ingot", 0xFFEFA774, -1),
        IRON_BAR("Iron ingot", 0xFFC3D3DC, -1), COPPER_PICK("Copper pick", 0xFFEFA774, -1),
        IRON_PICK("Iron pick", 0xFFD3E6EF, -1), IRON_SWORD("Iron longsword", 0xFFDCE9F2, -1),
        BOW("Ranger bow", 0xFFD6B17A, -1), ARROW("Arrow", 0xFFD4D2C1, -1),
        STAFF("Chaos staff", 0xFF80DFEA, -1), ARMOR("Iron cuirass", 0xFFB7D3DB, -1),
        POTION("Healing draught", 0xFFEE9BA8, -1), ACORN("Acorn", 0xFFACD978, -3),
        HEART("Heartstone", 0xFFF899B0, -1), SIGIL("Sentinel sigil", 0xFFFAD592, -1),
        RELIC("Emerald fragment", 0xFFFCE3A7, -1), BEACON("Starlight core", 0xFFB4EBEE, -1);
        public final String label;
        public final int color, tile;
        Item(String label, int color, int tile) { this.label = label; this.color = color; this.tile = tile; }
        public boolean tool() { return this == PICK || this == COPPER_PICK || this == IRON_PICK || this == AXE; }
        public boolean weapon() { return this == SWORD || this == IRON_SWORD || this == BOW || this == STAFF; }
        public boolean unique() { return tool() || weapon() || this == ARMOR || this == BEACON; }
    }
    public record Cost(Item item, int count) { }
    public record Recipe(Item item, int count, int station, String description, List<Cost> costs) { }
    private static Cost cost(Item i, int n) { return new Cost(i,n); }
    private static Recipe r(Item i, int n, int station, String hint, Cost... costs) {
        return new Recipe(i,n,station,hint,List.of(costs));
    }
    public final List<Recipe> recipes = List.of(
        r(Item.BENCH,1,0,"Your first crafting station",cost(Item.WOOD,10)),
        r(Item.TORCH,4,0,"Light caves and keep foes away",cost(Item.WOOD,1),cost(Item.GEL,1)),
        r(Item.PLATFORM,6,0,"Jump through; DOWN drops below",cost(Item.WOOD,2)),
        r(Item.WALL,8,0,"Background walls make a shelter",cost(Item.WOOD,2)),
        r(Item.FURNACE,1,World.BENCH,"Smelt ore into useful ingots",cost(Item.STONE,20),cost(Item.WOOD,4)),
        r(Item.COPPER_BAR,1,World.FURNACE,"Smelt three pieces of copper",cost(Item.COPPER,3)),
        r(Item.IRON_BAR,1,World.FURNACE,"Smelt three pieces of iron",cost(Item.IRON,3)),
        r(Item.COPPER_PICK,1,World.BENCH,"Mine iron and cut stone faster",cost(Item.COPPER_BAR,4),cost(Item.WOOD,4)),
        r(Item.ANVIL,1,World.BENCH,"Forge equipment and armor",cost(Item.IRON_BAR,4),cost(Item.STONE,6)),
        r(Item.IRON_PICK,1,World.ANVIL,"Extract glowing chaos shards",cost(Item.IRON_BAR,5),cost(Item.WOOD,4)),
        r(Item.IRON_SWORD,1,World.ANVIL,"A wide arc with heavy knockback",cost(Item.IRON_BAR,5),cost(Item.WOOD,2)),
        r(Item.BOW,1,World.BENCH,"Ranged attacks consume arrows",cost(Item.WOOD,12),cost(Item.GEL,3)),
        r(Item.ARROW,20,World.BENCH,"Ammunition for the ranger bow",cost(Item.WOOD,2),cost(Item.STONE,2)),
        r(Item.ARMOR,1,World.ANVIL,"Reduces every incoming hit",cost(Item.IRON_BAR,8)),
        r(Item.POTION,2,0,"Restores 50 health; H to drink",cost(Item.BERRY,3),cost(Item.GEL,1)),
        r(Item.STAFF,1,World.ANVIL,"Regenerating chaos energy",cost(Item.CRYSTAL,8),cost(Item.IRON_BAR,3)),
        r(Item.SIGIL,1,World.ANVIL,"Offer at an underground shrine",cost(Item.CRYSTAL,3),cost(Item.GEL,5)),
        r(Item.BEACON,1,World.ANVIL,"Take to the beacon at base camp",cost(Item.RELIC,3),cost(Item.CRYSTAL,12),cost(Item.IRON_BAR,6))
    );
    public final List<String> questNames = List.of("A place to begin", "Tools of the trade", "Fire in the dark",
            "A stronger edge", "Into the deep", "Eggman's sentinels", "Restore Angel Island");
    public final List<String> questText = List.of(
            "Gather 16 timber. Chop a tree with your axe.",
            "Craft and place a workbench near camp.",
            "Gather stone. Craft and place a furnace.",
            "Smelt copper. Forge a copper pick at the bench.",
            "Mine iron. Make an anvil and an iron pick.",
            "Forge sigils. Defeat all three shrine sentinels.",
            "Craft the starlight core. Restore the camp beacon.");
    public static String stationName(int s) {
        return switch(s) { case World.BENCH -> "Workbench"; case World.FURNACE -> "Furnace";
            case World.ANVIL -> "Anvil"; default -> "By hand"; };
    }
}
