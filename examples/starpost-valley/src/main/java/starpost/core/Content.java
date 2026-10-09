package starpost.core;

/**
 * The game's items and crops. Every named thing should change a rule (design doc §1, pillar 3),
 * so the descriptions say what each one does that its neighbours do not.
 *
 * <p>Numbers follow the genre's proven economy at Sonic's scale (rings): a starter crop costs
 * 20 and sells for 35; the slow, valuable crops pay several times their seed per harvest.
 */
final class Content {
    private static final int SPRING = 1;
    private static final int SUMMER = 2;
    private static final int FALL = 4;

    private final Catalog catalog;

    Content(Catalog catalog) {
        this.catalog = catalog;
    }

    void register() {
        tools();
        springCrops();
        summerCrops();
        fallCrops();
        forage();
        materials();
        food();
    }

    private void tools() {
        tool("water_shield", "WATER SHIELD", "WATERS PLOTS. REFILL AT ANY WATER.");
        tool("fire_shield", "FIRE SHIELD", "BREAKS ROCKS AND STUMPS, BURNS WEEDS.");
        tool("lightning_shield", "LIGHTNING SHIELD", "PULLS RIPE CROPS AND RINGS TO YOU.");
        tool("fishing_rod", "FISHING ROD", "TAILS MADE IT FROM A BUZZ BOMBER'S STINGER.");
    }

    private void springCrops() {
        crop("ring_radish", "RING RADISH", SPRING, 4, 0, 1, false, false, 20, 35,
                "A GOLD RING GROWS UNDER THE LEAVES.");
        crop("sunflower", "GREEN HILL SUNFLOWER", SPRING | SUMMER, 7, 3, 1, false, false, 40, 45,
                "REGROWS. ITS SEEDS FEED CUCKIES.");
        crop("palm_bean", "PALM BEAN", SPRING, 10, 3, 1, false, true, 60, 40,
                "CLIMBS A TRELLIS. REGROWS EVERY 3 DAYS.");
        crop("checker_cauliflower", "CHECKER CAULIFLOWER", SPRING, 12, 0, 1, true, false, 80, 175,
                "A FULL 3X3 PATCH CAN GROW GIANT.");
        crop("spring_tulip", "SPRING TULIP", SPRING, 6, 0, 1, false, false, 20, 30,
                "THE SUNFLOWER PARADE'S FLOWER.");
        crop("spin_spud", "SPIN SPUD", SPRING, 6, 0, 1, false, false, 50, 80,
                "SOMETIMES SPINS UP A SECOND SPUD.");
    }

    private void summerCrops() {
        crop("emerald_melon", "EMERALD MELON", SUMMER, 12, 0, 1, true, false, 80, 250,
                "A FULL 3X3 PATCH CAN GROW GIANT.");
        crop("motobug_tomato", "MOTOBUG TOMATO", SUMMER, 11, 4, 1, false, false, 50, 60,
                "REGROWS. RED AS A MOTOBUG'S SHELL.");
        crop("fire_pepper", "FIRE SHIELD PEPPER", SUMMER, 5, 3, 1, false, false, 40, 40,
                "EAT ONE FOR A DAY'S LAVA IMMUNITY.");
        crop("bluesphere_berry", "BLUESPHERE BERRY", SUMMER, 13, 4, 3, false, false, 80, 50,
                "REGROWS. THREE BERRIES A PICK.");
        crop("starpost_corn", "STAR POST CORN", SUMMER | FALL, 14, 4, 1, false, false, 150, 50,
                "GROWS THROUGH SUMMER AND FALL.");
        crop("spring_yard_hops", "SPRING YARD HOPS", SUMMER, 11, 1, 1, false, true, 60, 25,
                "TRELLIS. REGROWS EVERY DAY.");
    }

    private void fallCrops() {
        crop("eggman_pumpkin", "EGGMAN PUMPKIN", FALL, 13, 0, 1, true, false, 100, 320,
                "GROWS A FAMILIAR MOUSTACHE.");
        crop("egg_plant", "EGG-PLANT", FALL, 5, 5, 1, false, false, 20, 60,
                "REGROWS. ROBOTNIK LOVES THEM.");
        crop("marble_grape", "MARBLE GRAPE", FALL, 10, 3, 1, false, true, 60, 80,
                "TRELLIS. KNUCKLES LOVES THEM.");
        crop("ruby_berry", "RUBY BERRY", FALL, 7, 5, 2, false, false, 240, 75,
                "REGROWS. TWO BERRIES A PICK.");
        crop("totem_choke", "TOTEM CHOKE", FALL, 8, 0, 1, false, false, 30, 160,
                "STANDS TALL AS A TOTEM POLE.");
        crop("scrap_amaranth", "SCRAP BRAIN AMARANTH", FALL, 7, 0, 1, false, false, 70, 150,
                "TOUGH AS SCRAP BRAIN'S FLOORS.");
    }

    private void forage() {
        item("totem_leek", "TOTEM LEEK", Kind.FORAGE, 60, 15, "SPRING FORAGE BY THE TOTEMS.");
        item("hill_daffodil", "HILL DAFFODIL", Kind.FORAGE, 30, 0, "SPRING FORAGE. A GOOD GIFT.");
        item("loop_berry", "LOOP BERRY", Kind.FORAGE, 20, 10, "SUMMER FORAGE ON THE LOOP'S EDGE.");
        item("palm_coconut", "PALM COCONUT", Kind.FORAGE, 80, 20, "FALLS FROM GREEN HILL'S PALMS.");
        item("snow_spud", "SNOW SPUD", Kind.FORAGE, 40, 12, "WINTER FORAGE UNDER THE SNOW.");
        item("frost_ring", "FROST RING", Kind.FORAGE, 100, 0, "A RING FROZEN IN ICE. FIRE SHIELD IT.");
    }

    private void materials() {
        item("palm_wood", "PALM WOOD", Kind.MATERIAL, 2, 0, "FOR BUILDING.");
        item("marble_chip", "MARBLE CHIP", Kind.MATERIAL, 2, 0, "FROM ROCKS AND THE RUINS.");
        item("fibre", "FIBRE", Kind.MATERIAL, 1, 0, "FROM WEEDS.");
        item("scrap", "SCRAP", Kind.MATERIAL, 10, 0, "WHAT IS LEFT OF A POPPED BADNIK.");
    }

    private void food() {
        item("chili_dog", "CHILI DOG", Kind.FOOD, 120, 60, "THE BEST MOMENTUM IN THE VALLEY.");
    }

    private void tool(String id, String name, String text) {
        catalog.add(new Item(id, name, Kind.TOOL, 0, 0, id, text));
    }

    private void item(String id, String name, Kind kind, int price, int momentum, String text) {
        catalog.add(new Item(id, name, kind, price, momentum, id, text));
    }

    private void crop(String id, String name, int seasons, int days, int regrow, int yield, boolean giant,
            boolean trellis, int seedPrice, int sellPrice, String text) {
        String seed = id + "_seeds";
        catalog.add(new Item(seed, name + " SEEDS", Kind.SEED, seedPrice / 2, 0, seed,
                "PLANT IN " + seasonsText(seasons) + ". " + days + " DAYS."));
        catalog.add(new Item(id, name, Kind.CROP, sellPrice, Math.max(0, sellPrice / 12), id, text));
        catalog.add(new CropDef(id, seed, id, seasons, days, regrow, yield, giant, trellis), seedPrice);
    }

    private static String seasonsText(int seasons) {
        StringBuilder out = new StringBuilder();
        String[] names = {"SPRING", "SUMMER", "FALL", "WINTER"};
        for (int s = 0; s < 4; s++) {
            if ((seasons & (1 << s)) != 0) {
                out.append(out.isEmpty() ? "" : "/").append(names[s]);
            }
        }
        return out.toString();
    }
}
