package starpost.museum;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.SaveSection;

/**
 * Tails's Workshop Museum's save and rules (design doc §6.8): what has been donated, which
 * milestones have paid out, and Hazel's missing Star Post Cap. Engine-free.
 *
 * <p>Donating gives one of each carried item a collection still lacks. Each milestone pays once,
 * when its count is reached; a reward item that does not fit in the monitor slots waits until
 * the next visit. The Star Post Cap went missing in Hazel's four-heart event
 * ({@link #MISSING}); from then until it is back on the shelf it lies buried under the palms east
 * of town (Hazel buries coconuts for winter and forgets where), unless the farmer is carrying it.
 * Donating it completes her thread ({@link #RETURNED}).
 */
public final class Museum implements SaveSection {
    public static final String PREFIX = "museum";
    /** Hazel's four-heart event (starpost.people.cast.Hazel) sets this. */
    public static final String MISSING = "museum_relic_missing";
    /** Set when the cap is back on the shelf and Hazel has seen it. */
    public static final String RETURNED = "museum_cap_returned";

    private final Set<String> donated = new LinkedHashSet<>();
    private final Set<String> claimed = new LinkedHashSet<>();
    /** Today's dug spots ("farm.N", "valley.N", "cap"); not saved, since the game saves at night. */
    final Set<String> dugToday = new LinkedHashSet<>();

    /** What a donation did: the items given and the milestones it paid out. */
    public record Donation(List<String> given, List<Exhibits.Milestone> paid, boolean waiting) {
    }

    public boolean donated(String id) {
        return donated.contains(id);
    }

    public int count(Exhibits.Exhibit e) {
        int n = 0;
        for (String id : e.items()) {
            if (donated.contains(id)) {
                n++;
            }
        }
        return n;
    }

    public boolean complete(Exhibits.Exhibit e) {
        return count(e) == e.items().size();
    }

    public boolean claimed(Exhibits.Exhibit e, Exhibits.Milestone m) {
        return claimed.contains(e.id() + ":" + m.count());
    }

    /** Items the farmer carries that the collection still lacks. */
    public List<String> donatable(Game game, Exhibits.Exhibit e) {
        List<String> out = new ArrayList<>();
        for (String id : e.items()) {
            if (!donated.contains(id) && game.inventory.total(id) > 0) {
                out.add(id);
            }
        }
        return out;
    }

    /** Gives the museum one of every carried item it lacks in this collection, then pays out what that reached. */
    public Donation donate(Game game, Exhibits.Exhibit e) {
        List<String> given = donatable(game, e);
        for (String id : given) {
            game.inventory.remove(id, 1);
            donated.add(id);
        }
        List<Exhibits.Milestone> paid = claim(game, e);
        boolean waiting = false;
        for (Exhibits.Milestone m : e.milestones()) {
            waiting |= count(e) >= m.count() && !claimed(e, m);
        }
        return new Donation(given, paid, waiting);
    }

    /** Pays every reached milestone not yet paid whose reward fits; returns those paid. */
    public List<Exhibits.Milestone> claim(Game game, Exhibits.Exhibit e) {
        List<Exhibits.Milestone> paid = new ArrayList<>();
        int have = count(e);
        for (Exhibits.Milestone m : e.milestones()) {
            if (have < m.count() || claimed(e, m)) {
                continue;
            }
            Item item = m.item() == null ? null : game.item(m.item());
            if (item != null && !game.inventory.fits(item, m.items())) {
                continue;                                   // waits for room
            }
            game.rings += m.rings();
            if (item != null) {
                game.inventory.add(item, m.items());
                String record = MuseumContent.recordFlag(item.id());
                if (record != null) {
                    game.flags.add(record);                 // the jukebox opens its song
                }
            }
            if (m.flag() != null) {
                game.flags.add(m.flag());
            }
            claimed.add(e.id() + ":" + m.count());
            paid.add(m);
        }
        return paid;
    }

    /** Debug and tests: puts an item straight on its shelf and pays what that reached. */
    public void debugDonate(Game game, String id) {
        Exhibits.Exhibit e = Exhibits.of(game.catalog, id);
        if (e != null) {
            donated.add(id);
            claim(game, e);
        }
    }

    // ------------------------------------------------------------------ Hazel's Star Post Cap

    /** Whether the cap lies buried under the palms: missing, not on the shelf, not carried. */
    public boolean capBuried(Game game) {
        return game.flags.contains(MISSING) && !donated.contains(MuseumContent.CAP) && !held(game, MuseumContent.CAP)
                && !dugToday.contains("cap");
    }

    /** Whether the farmer has one, in the monitor slots or in any Item Monitor on the farm. */
    static boolean held(Game game, String id) {
        if (game.inventory.total(id) > 0) {
            return true;
        }
        for (starpost.core.Inventory chest : game.farm.chests.values()) {
            if (chest.total(id) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether donating just put the cap back and Hazel has yet to see it. */
    public boolean capJustReturned(Game game) {
        return donated.contains(MuseumContent.CAP) && !game.flags.contains(RETURNED);
    }

    // ------------------------------------------------------------------ save

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public void save(Map<String, String> out) {
        out.put("donated", String.join(",", donated));
        out.put("claimed", String.join(",", claimed));
    }

    /** Unknown items and malformed milestones are dropped. */
    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        donated.clear();
        claimed.clear();
        for (String id : in.getOrDefault("donated", "").split(",")) {
            if (!id.isBlank() && catalog.hasItem(id) && Exhibits.of(catalog, id) != null) {
                donated.add(id);
            }
        }
        for (String key : in.getOrDefault("claimed", "").split(",")) {
            String[] parts = key.split(":");
            if (parts.length == 2 && parts[1].matches("\\d{1,3}") && (parts[0].equals(Exhibits.MINERALS)
                    || parts[0].equals(Exhibits.SCRAP) || parts[0].equals(Exhibits.RELICS))) {
                claimed.add(key);
            }
        }
    }

    @Override
    public void nextDay(Game game) {
        dugToday.clear();
    }
}
