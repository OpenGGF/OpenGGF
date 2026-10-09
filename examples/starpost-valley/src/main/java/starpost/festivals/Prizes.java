package starpost.festivals;

import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.people.People;

/**
 * Handing out festival prizes: items go into the farmer's monitors (or wait at the Signpost
 * Board when there is no room), Records also open their song on the jukebox, and friendship goes
 * through the neighbours' rules. Engine-free.
 */
final class Prizes {
    /** Speed Shoes, the Great Valley Race's first prize: this much more Momentum, for good. */
    static final int SPEED_SHOES = 20;

    private Prizes() {
    }

    /** Gives {@code count} of an item (skipped when the item's system is not installed). */
    static void give(Game game, Festivals festivals, String id, int count, List<String> notices) {
        if (!game.catalog.hasItem(id) || count <= 0) {
            return;
        }
        Item item = game.item(id);
        String song = FestivalContent.recordFlag(id);
        if (song != null) {
            game.flags.add(song);
        }
        int left = game.inventory.add(item, count);
        if (left > 0) {
            festivals.owe(id, left);
            notices.add(item.name() + " WAITS AT THE BOARD");
        } else {
            notices.add("GOT " + (count > 1 ? count + " " : "") + item.name() + "!");
        }
    }

    /** Friendship from a festival (nothing when the neighbours are not installed). */
    static void friendship(Game game, String villager, int points) {
        People people = game.section(People.class);
        if (people != null && people.cast.get(villager) != null && people.present(people.cast.get(villager), game)) {
            people.add(villager, points);
        }
    }

    /** Friendship with everyone in the valley today. */
    static void everyone(Game game, int points) {
        People people = game.section(People.class);
        if (people == null) {
            return;
        }
        for (var v : people.cast.all()) {
            if (people.present(v, game) && !v.isPet()) {
                people.add(v.id, points);
            }
        }
    }
}
