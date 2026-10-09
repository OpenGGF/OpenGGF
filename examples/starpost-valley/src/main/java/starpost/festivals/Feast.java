package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;

/**
 * The Star Light Feast (Winter 25): Clementine's table under Star Light Zone's sky, and the
 * secret gift exchange. The farmer's secret friend was named by letter on Winter 18; a gift they
 * love counts three times over (the neighbours' own taste rules), and someone else's present for
 * the farmer is waiting too. Everyone eats: Momentum to full, and the valley's friendship grows.
 * Engine-free.
 */
public final class Feast {
    public static final int GIFT_FACTOR = 3;
    public static final int EVERYONE = 40;

    private Feast() {
    }

    /** A gift's name as a friend says it: "A CHILI DOG", "AN EMERALD MELON", "RING RADISH SEEDS". */
    public static String some(String name) {
        if (name.endsWith("S") && !name.endsWith("SS")) {
            return name;
        }
        return ("AEIOU".indexOf(name.charAt(0)) >= 0 ? "AN " : "A ") + name;
    }

    /** What a gift is worth to the secret friend: their taste, three times over. */
    public static int giftPoints(VillagerDef friend, Item gift) {
        return People.points(friend.taste(gift)) * GIFT_FACTOR;
    }

    /** The present someone brings for the farmer (an item id and how many; skipped items fall back to a Chili Dog). */
    public static String[] present(String giver) {
        return switch (giver) {
            case "tails" -> new String[] {"buzz_waterer", "1"};
            case "sonic" -> new String[] {"chili_dog", "3"};
            case "knuckles" -> new String[] {"emerald_shard", "1"};
            case "robotnik" -> new String[] {"robo_cola", "6"};
            case "rusty" -> new String[] {"scrap", "10"};
            case "pip" -> new String[] {"hill_daffodil", "3"};
            case "dandel" -> new String[] {"sunflower_seeds", "10"};
            case "clementine" -> new String[] {"loop_pie", "2"};
            case "pud" -> new String[] {"lava_ruby", "1"};
            case "barnaby" -> new String[] {"loop_pie", "1"};
            case "frost" -> new String[] {"frost_ring", "2"};
            case "hazel" -> new String[] {"palm_coconut", "3"};
            default -> new String[] {"chili_dog", "1"};
        };
    }

    /**
     * The feast's results: the gift to the secret friend (null when none was brought), the
     * present received, a full stomach and everyone's friendship. Returns notices.
     */
    public static List<String> reward(Game game, Festivals festivals, String friend, Item gift, String giver) {
        List<String> notices = new ArrayList<>();
        People people = game.section(People.class);
        int points = 0;
        if (friend != null && gift != null && people != null && people.cast.get(friend) != null) {
            VillagerDef v = people.cast.get(friend);
            points = giftPoints(v, gift);
            game.inventory.remove(gift.id(), 1);
            people.add(friend, points);
            Taste taste = v.taste(gift);
            notices.add(v.name + (taste == Taste.LOVE ? " LOVED IT!" : taste == Taste.LIKE ? " LIKED IT."
                    : taste == Taste.NEUTRAL ? " SAID THANK YOU." : " TRIED TO SMILE."));
        }
        if (giver != null) {
            String[] present = present(giver);
            String id = game.catalog.hasItem(present[0]) ? present[0] : "chili_dog";
            int count = game.catalog.hasItem(present[0]) ? Integer.parseInt(present[1]) : 1;
            Prizes.give(game, festivals, id, count, notices);
        }
        game.momentum = game.maxMomentum;
        Prizes.everyone(game, EVERYONE);
        festivals.record(FestivalBook.FEAST, game.calendar.year(), 0, Math.max(0, points));
        return notices;
    }
}
