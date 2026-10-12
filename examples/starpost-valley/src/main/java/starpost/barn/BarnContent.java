package starpost.barn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.core.Catalog;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.PlaceableDef;
import starpost.core.Recipe;

/**
 * The barn's items, registered from {@code Content.register} after the crops: the animals' goods,
 * the artisan goods (a jar for every crop and forage, a fizz for every fruit, cloth and oils), the
 * machines and the Flicky Roost as placeables, and their recipes at Tails's workshop. The roost's
 * recipe is offered once the Capsule's Bulletin chamber (or Robomart) grants {@code flicky_roost}.
 */
public final class BarnContent {
    public static final String ROOST = "flicky_roost";
    /** Columns each side of a roost its Flickies harvest. */
    public static final int ROOST_REACH = 6;

    private final Catalog catalog;

    public BarnContent(Catalog catalog) {
        this.catalog = catalog;
    }

    public void register() {
        goods();
        artisanGoods();
        placeables();
        recipes();
    }

    private void goods() {
        item("cucky_egg", "CUCKY EGG", Kind.ANIMAL_GOOD, 50, 0, "FRESH FROM A FED, HAPPY CUCKY. DAILY.");
        item("ice_egg", "ICE EGG", Kind.ANIMAL_GOOD, 160, 0, "A PECKY'S EGG. WINTER ONLY. BARNABY LOVES THEM.");
        item("pocky_fluff", "POCKY FLUFF", Kind.ANIMAL_GOOD, 340, 0, "EVERY THREE DAYS. THE LOOM SPINS IT INTO CLOTH.");
        item("hill_truffle", "HILL TRUFFLE", Kind.ANIMAL_GOOD, 600, 0, "A PICKY DIGS THEM FROM OPEN GRASS ON DRY DAYS.");
    }

    private void artisanGoods() {
        List<Item> inputs = new ArrayList<>();
        for (Item item : catalog.items()) {
            if (item.kind() == Kind.CROP || item.kind() == Kind.FORAGE) {
                inputs.add(item);
            }
        }
        for (Item in : inputs) {
            item("jar_" + in.id(), in.name() + " JAR", Kind.ARTISAN, in.price() * 2 + 50, Math.max(10, in.momentum() * 2),
                    "FROM THE MONITOR JAR. KEEPS FOREVER.");
            if (Artisan.fruit(in.id())) {
                item("fizz_" + in.id(), in.name() + " FIZZ", Kind.ARTISAN, in.price() * 3, 25,
                        "FROM THE SPRING YARD KEG. IT SPRINGS.");
            }
        }
        item("spring_yard_fizz", "SPRING YARD FIZZ", Kind.ARTISAN, 220, 60, "THE KEG'S OWN, FROM HOPS. A SPRING IN EVERY SIP.");
        item("hill_cloth", "HILL CLOTH", Kind.ARTISAN, 470, 0, "SPUN FROM FLUFF. TAILS BUILDS BIG COOPS FROM IT.");
        item(Artisan.OIL, "SUNFLOWER OIL", Kind.ARTISAN, 100, 0, "DAB IT ON A WORKING MACHINE: A DAY SOONER.");
        item("truffle_oil", "TRUFFLE OIL", Kind.ARTISAN, 1100, 0, "THE VALLEY'S FINEST. CLEMENTINE DREAMS OF IT.");
    }

    private void placeables() {
        place(Artisan.JAR, "MONITOR JAR", PlaceableDef.Role.MACHINE, 0, "A CROP OR FORAGE IN, A JAR OUT IN 3 DAYS.");
        place(Artisan.KEG, "SPRING YARD KEG", PlaceableDef.Role.MACHINE, 0, "FRUIT FIZZ IN 5 DAYS; HOPS FIZZ IN 2.");
        place(Artisan.LOOM, "FLUFF LOOM", PlaceableDef.Role.MACHINE, 0, "POCKY FLUFF IN, HILL CLOTH OUT OVERNIGHT.");
        place(Artisan.PRESS, "SUNFLOWER PRESS", PlaceableDef.Role.MACHINE, 0, "SUNFLOWERS TO OIL; TRUFFLES TO TRUFFLE OIL.");
        place(ROOST, "FLICKY ROOST", PlaceableDef.Role.ROOST, ROOST_REACH,
                "FLICKIES PICK RIPE CROPS 6 PLOTS EACH WAY ON ITS ROW.");
    }

    private void recipes() {
        recipe(Artisan.JAR, 200, null, "palm_wood", 30, "marble_chip", 20);
        recipe(Artisan.KEG, 500, null, "palm_wood", 30, "scrap", 5);
        recipe(Artisan.LOOM, 1000, null, "palm_wood", 60, "fibre", 30);
        recipe(Artisan.PRESS, 600, null, "marble_chip", 20, "scrap", 10);
        recipe(ROOST, 1500, "flicky_roost", "palm_wood", 40, "sunflower", 2);
    }

    private void item(String id, String name, Kind kind, int price, int momentum, String text) {
        catalog.add(new Item(id, name, kind, price, momentum, id, text));
    }

    private void place(String id, String name, PlaceableDef.Role role, int reach, String text) {
        catalog.add(new Item(id, name, Kind.PLACEABLE, 0, 0, id, text));
        catalog.add(new PlaceableDef(id, role, reach));
    }

    private void recipe(String product, int rings, String unlock, String a, int na, String b, int nb) {
        Map<String, Integer> inputs = new LinkedHashMap<>();
        inputs.put(a, na);
        inputs.put(b, nb);
        catalog.add(new Recipe(product, 1, rings, inputs, unlock));
    }
}
