package starpost.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Great Capsule (design doc §6.4): Robotnik's cracked animal prison on the plateau, restored
 * chamber by chamber. Flickies in each chamber take bundles of the valley's goods; a finished
 * chamber unlocks something and the whole capsule, once restored, becomes the valley's heart.
 *
 * <p>Bundles name items by id; a bundle whose items the game does not know (a system not yet
 * installed) is left out, and its chamber completes without it. The Vault takes rings.
 */
public final class Capsule implements SaveSection {
    /** One bundle: a name and the items (id, count) it wants; {@code rings} for the Vault's. */
    public record Bundle(String id, String name, Map<String, Integer> wants, int rings) {
    }

    /** One chamber: its bundles and the flag its reward sets. */
    public record Chamber(String id, String name, String reward, String rewardText, List<Bundle> bundles) {
    }

    private final List<Chamber> chambers = new ArrayList<>();
    /** Delivered counts: "bundle.item" to count; "bundle.rings" for ring bundles. */
    private final Map<String, Integer> delivered = new LinkedHashMap<>();

    public Capsule() {
        chamber("pantry", "PANTRY", "capsule_garden", "THE CAPSULE GARDEN: ANY CROP, ANY SEASON",
                bundle("spring_crops", "SPRING CROPS", 0, "ring_radish", 1, "sunflower", 1, "palm_bean", 1, "checker_cauliflower", 1),
                bundle("summer_crops", "SUMMER CROPS", 0, "emerald_melon", 1, "motobug_tomato", 1, "fire_pepper", 1, "bluesphere_berry", 1),
                bundle("fall_crops", "FALL CROPS", 0, "eggman_pumpkin", 1, "egg_plant", 1, "marble_grape", 1, "totem_choke", 1));
        chamber("hatchery", "HATCHERY", "big_coop", "A BIGGER COOP AND PEN",
                bundle("eggs", "EGGS AND FLUFF", 0, "cucky_egg", 5, "pocky_fluff", 3),
                bundle("truffle", "PIGGY BANK", 0, "hill_truffle", 3, "ice_egg", 2));
        chamber("reef", "REEF", "lake_bridge", "THE LAKE BRIDGE IS REBUILT",
                bundle("lake_fish", "LAKE FISH", 0, "bubble_bass", 1, "loop_pike", 1, "ring_carp", 1),
                bundle("badnik_catch", "BADNIK CATCH", 0, "chopper_shell", 1, "jaws_fin", 1));
        chamber("scrapyard", "SCRAPYARD", "minecart", "THE RUINS MINECART RUNS AGAIN",
                bundle("scrap_heap", "SCRAP HEAP", 0, "scrap", 30, "marble_chip", 50),
                bundle("gems", "GEMS", 0, "lava_ruby", 1, "tide_sapphire", 1, "spark_topaz", 1));
        chamber("bulletin", "BULLETIN", "flicky_roost", "FLICKY ROOSTS CAN BE BUILT",
                bundle("forage", "HILL FORAGE", 0, "totem_leek", 1, "hill_daffodil", 1, "loop_berry", 1, "palm_coconut", 1),
                bundle("winter", "WINTER FINDS", 0, "snow_spud", 1, "frost_ring", 1));
        chamber("vault", "VAULT", "farm_open", "THE WHOLE FARM IS CLEARED FOR YOU",
                bundle("vault1", "2,500 RINGS", 2500),
                bundle("vault2", "5,000 RINGS", 5000),
                bundle("vault3", "10,000 RINGS", 10000),
                bundle("vault4", "25,000 RINGS", 25000));
    }

    private void chamber(String id, String name, String reward, String rewardText, Bundle... bundles) {
        chambers.add(new Chamber(id, name, reward, rewardText, List.of(bundles)));
    }

    private static Bundle bundle(String id, String name, int rings, Object... pairs) {
        Map<String, Integer> wants = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            wants.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return new Bundle(id, name, wants, rings);
    }

    /** The chambers with only the bundles this game can fill. */
    public List<Chamber> chambers(Catalog catalog) {
        List<Chamber> out = new ArrayList<>();
        for (Chamber chamber : chambers) {
            List<Bundle> bundles = new ArrayList<>();
            for (Bundle bundle : chamber.bundles()) {
                if (bundle.wants().keySet().stream().allMatch(catalog::hasItem)) {
                    bundles.add(bundle);
                }
            }
            if (!bundles.isEmpty()) {
                out.add(new Chamber(chamber.id(), chamber.name(), chamber.reward(), chamber.rewardText(), bundles));
            }
        }
        return out;
    }

    public int delivered(Bundle bundle, String item) {
        return delivered.getOrDefault(bundle.id() + "." + item, 0);
    }

    public boolean complete(Bundle bundle) {
        if (bundle.rings() > 0) {
            return delivered(bundle, "rings") >= bundle.rings();
        }
        for (Map.Entry<String, Integer> want : bundle.wants().entrySet()) {
            if (delivered(bundle, want.getKey()) < want.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean complete(Chamber chamber) {
        return chamber.bundles().stream().allMatch(this::complete);
    }

    /**
     * Gives what the farmer carries toward a bundle. Returns how many things went in (rings for
     * the Vault). Completing a chamber sets its reward flag in the game.
     */
    public int deliver(Game game, Chamber chamber, Bundle bundle) {
        int given = 0;
        if (bundle.rings() > 0) {
            int need = bundle.rings() - delivered(bundle, "rings");
            int pay = Math.min(need, game.rings);
            if (pay < need) {
                return 0;            // the Vault takes each sum whole
            }
            game.rings -= pay;
            delivered.merge(bundle.id() + ".rings", pay, Integer::sum);
            given = pay;
        } else {
            for (Map.Entry<String, Integer> want : bundle.wants().entrySet()) {
                int need = want.getValue() - delivered(bundle, want.getKey());
                int take = game.inventory.remove(want.getKey(), Math.max(0, need));
                if (take > 0) {
                    delivered.merge(bundle.id() + "." + want.getKey(), take, Integer::sum);
                    given += take;
                }
            }
        }
        if (given > 0 && complete(chamber) && game.flags.add(chamber.reward())) {
            reward(game, chamber.reward());
        }
        return given;
    }

    private static void reward(Game game, String flag) {
        if (flag.equals("farm_open")) {
            game.farm.open(Farm.COLUMNS);
        }
    }

    /** Debug and promo captures: every bundle delivered and every chamber's reward granted. */
    public void debugRestore(Game game, Catalog catalog) {
        for (Chamber chamber : chambers(catalog)) {
            for (Bundle bundle : chamber.bundles()) {
                if (bundle.rings() > 0) {
                    delivered.put(bundle.id() + ".rings", bundle.rings());
                }
                for (Map.Entry<String, Integer> want : bundle.wants().entrySet()) {
                    delivered.put(bundle.id() + "." + want.getKey(), want.getValue());
                }
            }
            if (game.flags.add(chamber.reward())) {
                reward(game, chamber.reward());
            }
        }
    }

    /** Whether every chamber the game can fill is done: the capsule is restored. */
    public boolean restored(Catalog catalog) {
        List<Chamber> all = chambers(catalog);
        return !all.isEmpty() && all.stream().allMatch(this::complete);
    }

    @Override
    public String prefix() {
        return "capsule";
    }

    @Override
    public void save(Map<String, String> out) {
        for (Map.Entry<String, Integer> e : delivered.entrySet()) {
            out.put(e.getKey(), Integer.toString(e.getValue()));
        }
    }

    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        delivered.clear();
        for (Map.Entry<String, String> e : in.entrySet()) {
            int count = Integer.parseInt(e.getValue());
            if (count > 0 && count <= 100000 && known(e.getKey())) {
                delivered.put(e.getKey(), count);
            }
        }
    }

    /** Only keys naming a real bundle and one of its wants are kept. */
    private boolean known(String key) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            return false;
        }
        String bundleId = key.substring(0, dot), item = key.substring(dot + 1);
        for (Chamber chamber : chambers) {
            for (Bundle bundle : chamber.bundles()) {
                if (bundle.id().equals(bundleId) && (bundle.rings() > 0 ? item.equals("rings") : bundle.wants().containsKey(item))) {
                    return true;
                }
            }
        }
        return false;
    }
}
