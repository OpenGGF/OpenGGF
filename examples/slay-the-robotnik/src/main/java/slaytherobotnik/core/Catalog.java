package slaytherobotnik.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every card, relic, potion, enemy encounter, event, act and character known to the game,
 * looked up by id. Content registers itself here once at start-up; saves store only ids.
 *
 * <p>This is an ordinary instance, not a static registry: OpenGGF mod classes may not hold
 * static state, so the scene creates one catalog and passes it to each run.
 */
public final class Catalog {
    private final Map<String, CardDef> cards = new LinkedHashMap<>();
    private final Map<String, RelicEntry> relics = new LinkedHashMap<>();
    private final Map<String, PotionDef> potions = new LinkedHashMap<>();
    private final Map<String, CharacterDef> characters = new LinkedHashMap<>();
    private final Map<String, EncounterDef> encounters = new LinkedHashMap<>();
    private final Map<String, EventDef> events = new LinkedHashMap<>();
    private final Map<Integer, ActDef> acts = new LinkedHashMap<>();

    /** Relic factory plus the tier/character data needed to build reward pools without instantiating. */
    public record RelicEntry(String id, String tier, String character, Supplier<Relic> factory) {
    }

    // ----- registration -----

    public CardDef add(CardDef card) {
        if (cards.putIfAbsent(card.id(), card) != null) {
            throw new IllegalArgumentException("Duplicate card id " + card.id());
        }
        return card;
    }

    public void addRelic(Supplier<Relic> factory) {
        Relic sample = factory.get();
        if (relics.putIfAbsent(sample.id(), new RelicEntry(sample.id(), sample.tier(), sample.character(), factory))
                != null) {
            throw new IllegalArgumentException("Duplicate relic id " + sample.id());
        }
    }

    public void addPotion(PotionDef potion) {
        if (potions.putIfAbsent(potion.id(), potion) != null) {
            throw new IllegalArgumentException("Duplicate potion id " + potion.id());
        }
    }

    public void addCharacter(CharacterDef character) {
        characters.put(character.id(), character);
    }

    public void addEncounter(EncounterDef encounter) {
        if (encounters.putIfAbsent(encounter.id(), encounter) != null) {
            throw new IllegalArgumentException("Duplicate encounter id " + encounter.id());
        }
    }

    public void addEvent(EventDef event) {
        if (events.putIfAbsent(event.id(), event) != null) {
            throw new IllegalArgumentException("Duplicate event id " + event.id());
        }
    }

    public void addAct(ActDef act) {
        acts.put(act.number(), act);
    }

    // ----- lookup -----

    public CardDef card(String id) {
        CardDef def = cards.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown card " + id);
        }
        return def;
    }

    public boolean hasCard(String id) { return cards.containsKey(id); }

    public Relic newRelic(String id) {
        RelicEntry entry = relics.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown relic " + id);
        }
        return entry.factory().get();
    }

    public boolean hasRelic(String id) { return relics.containsKey(id); }

    public PotionDef potion(String id) {
        PotionDef def = potions.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown potion " + id);
        }
        return def;
    }

    public CharacterDef character(String id) {
        CharacterDef def = characters.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown character " + id);
        }
        return def;
    }

    public EncounterDef encounter(String id) {
        EncounterDef def = encounters.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown encounter " + id);
        }
        return def;
    }

    public EventDef event(String id) {
        EventDef def = events.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown event " + id);
        }
        return def;
    }

    public ActDef act(int number) {
        ActDef def = acts.get(number);
        if (def == null) {
            throw new IllegalArgumentException("Unknown act " + number);
        }
        return def;
    }

    public int actCount() { return acts.size(); }

    // ----- queries -----

    public List<CardDef> allCards() { return List.copyOf(cards.values()); }
    public List<RelicEntry> allRelics() { return List.copyOf(relics.values()); }
    public List<PotionDef> allPotions() { return List.copyOf(potions.values()); }
    public List<CharacterDef> characters() { return List.copyOf(characters.values()); }
    public List<EventDef> allEvents() { return List.copyOf(events.values()); }

    /** Reward-eligible cards of a colour and rarity, in registration order. */
    public List<CardDef> cards(String color, String rarity) {
        List<CardDef> list = new ArrayList<>();
        for (CardDef c : cards.values()) {
            if (c.color().equals(color) && c.rarity().equals(rarity)) {
                list.add(c);
            }
        }
        return list;
    }

    /** Relic ids of a tier available to {@code characterId}. */
    public List<String> relicIds(String tier, String characterId) {
        List<String> list = new ArrayList<>();
        for (RelicEntry e : relics.values()) {
            if (e.tier().equals(tier) && (e.character() == null || e.character().equals(characterId))) {
                list.add(e.id());
            }
        }
        return list;
    }

    public List<PotionDef> potions(String rarity) {
        List<PotionDef> list = new ArrayList<>();
        for (PotionDef p : potions.values()) {
            if (p.rarity().equals(rarity)) {
                list.add(p);
            }
        }
        return list;
    }

    public List<EncounterDef> encounters(int act, String pool) {
        List<EncounterDef> list = new ArrayList<>();
        for (EncounterDef e : encounters.values()) {
            if (e.act() == act && e.pool().equals(pool)) {
                list.add(e);
            }
        }
        return list;
    }
}
