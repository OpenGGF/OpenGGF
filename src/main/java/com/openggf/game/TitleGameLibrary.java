package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.HubPreference;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class TitleGameLibrary {
    enum Filter { ALL, SONIC, MODS }
    static final int COLUMNS = 4;
    static final int VISIBLE_ROWS = 3;
    private static final int RECENT_SLOTS = 3;

    private final SonicConfigurationService config;
    private final LinkedHashSet<String> kept;
    private final LinkedHashSet<String> recent;
    private List<MasterTitleEntry> catalog = List.of();
    private Filter filter = Filter.ALL;
    private String featured = "";
    private int selected;
    private boolean tabs;

    TitleGameLibrary(SonicConfigurationService config) {
        this.config = config;
        kept = read(HubPreference.KEPT_MOD_GAMES);
        recent = read(HubPreference.RECENT_MOD_GAMES);
    }

    private LinkedHashSet<String> read(HubPreference key) {
        var ids = new LinkedHashSet<>(List.of(key.read(config).split(",")));
        ids.remove("");
        return ids;
    }

    void catalog(List<MasterTitleEntry> all) {
        catalog = all.stream().filter(card -> !(card instanceof MasterTitleEntry.Suspended)).toList();
        selected = Math.clamp(selected, 0, Math.max(0, filtered().size() - 1));
    }

    static boolean isMod(MasterTitleEntry entry) {
        return entry instanceof MasterTitleEntry.ModGame || entry instanceof MasterTitleEntry.Standalone;
    }

    List<MasterTitleEntry> shelf() {
        var stock = catalog.stream().filter(card -> !isMod(card)).toList();
        var mods = catalog.stream().filter(TitleGameLibrary::isMod).toList();
        var shelf = new ArrayList<>(stock);
        for (String owner : kept) addById(shelf, mods, owner);
        boolean lastPlayedNeedsRoom = !recent.isEmpty() && !kept.contains(recent.getFirst())
                && mods.stream().anyMatch(card -> card.gameId().equals(recent.getFirst()));
        int slots = Math.max(lastPlayedNeedsRoom ? 1 : 0, RECENT_SLOTS - (shelf.size() - stock.size()));
        var candidates = new ArrayList<MasterTitleEntry>();
        for (String owner : recent) addById(candidates, mods, owner);
        for (var card : mods) if (!candidates.contains(card)) candidates.add(card);
        candidates.stream().filter(card -> !kept.contains(card.gameId())).limit(slots).forEach(shelf::add);
        if (shelf.size() < catalog.size()) shelf.add(new MasterTitleEntry.Library(catalog.size()));
        return List.copyOf(shelf);
    }

    private static void addById(List<MasterTitleEntry> destination, List<MasterTitleEntry> cards, String id) {
        cards.stream().filter(card -> card.gameId().equals(id)).findFirst().ifPresent(destination::add);
    }

    List<MasterTitleEntry> filtered() {
        return switch (filter) {
            case ALL -> allGames();
            case SONIC -> catalog.stream().filter(card -> !isMod(card)).toList();
            case MODS -> catalog.stream().filter(TitleGameLibrary::isMod).toList();
        };
    }

    private List<MasterTitleEntry> allGames() {
        MasterTitleEntry card = find(featured);
        if (card == null) return catalog;
        var ordered = new ArrayList<>(catalog);
        ordered.remove(card);
        ordered.add(Math.min(5, ordered.size()), card);
        return ordered;
    }

    void open(String preferred) {
        featured = preferred;
        filter = Filter.ALL;
        tabs = false;
        List<MasterTitleEntry> cards = filtered();
        selected = Math.clamp(cards.size() > 20 ? 5 : 1, 0, Math.max(0, cards.size() - 1));
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).gameId().equals(preferred)) { selected = i; break; }
        }
    }

    Filter filter() { return filter; }
    boolean tabs() { return tabs; }
    int index() { return selected; }
    int firstRow() { return Math.max(0, selected / COLUMNS - VISIBLE_ROWS + 1); }
    MasterTitleEntry find(String id) {
        return catalog.stream().filter(card -> card.gameId().equals(id)).findFirst().orElse(null);
    }

    boolean hasMods() { return catalog.stream().anyMatch(TitleGameLibrary::isMod); }
    MasterTitleEntry selected() { return filtered().isEmpty() ? null : filtered().get(selected); }

    void filter(Filter value) {
        filter = value;
        selected = 0;
        tabs = true;
    }

    void select(int index) {
        selected = Math.clamp(index, 0, Math.max(0, filtered().size() - 1));
        tabs = false;
    }

    void horizontal(int direction) {
        if (tabs) {
            filter(Filter.values()[Math.clamp(filter.ordinal() + direction, 0, Filter.values().length - 1)]);
        } else if (selected / COLUMNS == (selected + direction) / COLUMNS) {
            select(selected + direction);
        }
    }

    void vertical(int direction) {
        if (direction < 0 && selected < COLUMNS) tabs = true;
        else if (tabs) {
            if (direction > 0) tabs = false;
        } else if (selected / COLUMNS + direction <= (filtered().size() - 1) / COLUMNS) {
            select(selected + direction * COLUMNS);
        }
    }

    boolean kept(MasterTitleEntry card) { return kept.contains(card.gameId()); }

    void toggleKept(MasterTitleEntry card) throws IOException {
        var next = new LinkedHashSet<>(kept);
        if (!next.remove(card.gameId())) next.add(card.gameId());
        ConfigurationPersistence.apply(config, Map.of(), Map.of(HubPreference.KEPT_MOD_GAMES, String.join(",", next)));
        kept.clear();
        kept.addAll(next);
    }

    void rememberPlayed(MasterTitleEntry card) throws IOException {
        if (!isMod(card)) return;
        var next = new LinkedHashSet<String>();
        next.add(card.gameId());
        next.addAll(recent);
        var first = next.stream().limit(RECENT_SLOTS).toList();
        ConfigurationPersistence.apply(config, Map.of(), Map.of(HubPreference.RECENT_MOD_GAMES, String.join(",", first)));
        recent.clear();
        recent.addAll(first);
    }
}
