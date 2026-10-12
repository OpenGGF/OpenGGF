package starpost.orchard;

import java.util.LinkedHashMap;
import java.util.Map;
import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.PlaceableDef;
import starpost.core.Recipe;
import starpost.core.Sneakers;

/**
 * What the orchard and the sneakers add to the catalogue: the three saplings (placed on a plot
 * like any farm object, and grown by {@link Orchard}), the Chaos Cherry, Tails's recipes for the
 * saplings, and the four sneaker tiers (tools that are never carried: they name and picture the
 * tiers Tails builds; the tier itself is a story flag, {@link Sneakers#flag}). Called from
 * {@code Content.register} before the barn, so the Monitor Jar takes Chaos Cherries too.
 */
public final class OrchardContent {
    private final Catalog catalog;

    public OrchardContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        sapling(Orchard.PALM, "GREEN HILL PALM SAPLING",
                "GROWS IN 14 DAYS. COCONUTS IN SUMMER AND FALL; STORMS SHAKE MORE LOOSE.");
        sapling(Orchard.RING_FRUIT, "RING FRUIT SAPLING",
                "GROWS IN 28 DAYS, THEN 10 RINGS A DAY SPRING TO FALL. SHAKE IT AND CATCH THEM.");
        sapling(Orchard.CHAOS_CHERRY, "CHAOS CHERRY SAPLING",
                "GROWS IN 21 DAYS. BEARS EVEN IN WINTER, MORE THE MORE ANIMALS ARE FREE.");
        catalog.add(new Item(Orchard.CHERRY, "CHAOS CHERRY", Kind.FORAGE, 320, 45, Orchard.CHERRY,
                "FROM THE CHAOS CHERRY. IT SHIMMERS THROUGH ALL SEVEN COLOURS."));
        recipe(Orchard.PALM, 400, null, "palm_wood", 15, "fibre", 10);
        recipe(Orchard.RING_FRUIT, 1500, null, "palm_wood", 20, "marble_chip", 20);
        recipe(Orchard.CHAOS_CHERRY, 3000, Orchard.CHERRY_FLAG, "emerald_shard", 1, "palm_wood", 20);
        sneaker(Sneakers.SNEAKERS, "YOUR SPIN DASH TILLS THE ROW YOU ROLL ALONG.");
        sneaker(Sneakers.POWER, "THE DASH ALSO TILLS THE ROW IN FRONT.");
        sneaker(Sneakers.SPEED, "THE DASH TILLS BOTH NEIGHBOURING ROWS TOO.");
        sneaker(Sneakers.CHAOS, "AS SPEED SHOES, AND YOU CAN RUN ACROSS WATER. KEEP MOVING!");
    }

    private void sapling(String id, String name, String text) {
        catalog.add(new Item(id, name, Kind.PLACEABLE, 0, 0, id, text));
        catalog.add(new PlaceableDef(id, PlaceableDef.Role.TREE, 0));
    }

    private void sneaker(int tier, String text) {
        String id = Sneakers.item(tier);
        catalog.add(new Item(id, Sneakers.name(tier), Kind.TOOL, 0, 0, id, text));
    }

    private void recipe(String product, int rings, String unlock, String a, int na, String b, int nb) {
        Map<String, Integer> inputs = new LinkedHashMap<>();
        inputs.put(a, na);
        inputs.put(b, nb);
        catalog.add(new Recipe(product, 1, rings, inputs, unlock));
    }
}
