package slaytherobotnik.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything that persists between rooms in one run: the character, HP, rings, deck,
 * relics, potions, position on the map, random streams and bookkeeping. This is what is
 * saved; fights, shops and events are rebuilt from it.
 */
public final class RunState {
    public static final int BASE_POTION_SLOTS = 3;

    private final Catalog catalog;
    private final CharacterDef character;
    private final RunRngs rngs;
    private final RunStats stats = new RunStats();
    private int hp;
    private int maxHp;
    private int rings;
    private final List<Card> deck = new ArrayList<>();
    private final List<Relic> relics = new ArrayList<>();
    private final List<PotionDef> potions = new ArrayList<>();
    private int act = 1;
    private int floor;
    private int actFloor = -1;
    private int nodeX = -1;
    private int ascension;

    private final Map<String, Deque<String>> relicPools = new LinkedHashMap<>();
    private int cardRareBonus = -5;
    private int potionChance = 40;
    private int shopRemovals;
    private final Set<String> seenEvents = new LinkedHashSet<>();
    private final List<String> monsterQueue = new ArrayList<>();
    private final List<String> eliteQueue = new ArrayList<>();
    private String boss;
    private int unknownMonsterChance = 10;
    private int unknownShopChance = 3;
    private int unknownTreasureChance = 2;
    private final Map<String, Integer> counters = new LinkedHashMap<>();

    public RunState(Catalog catalog, CharacterDef character, long seed) {
        this.catalog = catalog;
        this.character = character;
        this.rngs = new RunRngs(seed);
        this.maxHp = character.maxHp();
        this.hp = character.maxHp();
        this.rings = character.startingRings();
    }

    /** A new run with the starting deck, starting relic and shuffled relic pools. */
    public static RunState start(Catalog catalog, CharacterDef character, long seed) {
        RunState run = new RunState(catalog, character, seed);
        for (String id : character.startingDeck()) {
            run.deck.add(new Card(catalog.card(id)));
        }
        run.initRelicPools();
        run.obtainRelic(catalog.newRelic(character.startingRelic()));
        return run;
    }

    void initRelicPools() {
        Rng rng = rngs.stream(RunRngs.RELICS);
        for (String tier : new String[] {RelicTier.COMMON, RelicTier.UNCOMMON, RelicTier.RARE, RelicTier.BOSS,
                RelicTier.SHOP}) {
            List<String> ids = new ArrayList<>(catalog.relicIds(tier, character.id()));
            rng.shuffle(ids);
            relicPools.put(tier, new ArrayDeque<>(ids));
        }
    }

    // ----- identity -----

    public Catalog catalog() { return catalog; }
    public CharacterDef character() { return character; }
    public RunRngs rngs() { return rngs; }
    public long seed() { return rngs.seed(); }
    public RunStats stats() { return stats; }
    public int ascension() { return ascension; }
    public void setAscension(int value) { ascension = value; }

    // ----- HP -----

    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public boolean dead() { return hp <= 0; }

    public void setHp(int value) {
        hp = Math.max(0, Math.min(maxHp, value));
    }

    public void setMaxHp(int value) {
        maxHp = Math.max(1, value);
        hp = Math.min(hp, maxHp);
    }

    /** Heals outside combat, through relic modifiers. Returns HP actually restored. */
    public int heal(int amount) {
        for (Relic relic : relics) {
            amount = relic.modifyHeal(this, amount);
        }
        int before = hp;
        setHp(hp + amount);
        return hp - before;
    }

    /** Loses HP outside combat (event costs). */
    public void loseHp(int amount) {
        stats.damageTaken += Math.min(amount, hp);
        setHp(hp - amount);
    }

    public void gainMaxHp(int amount) {
        maxHp += amount;
        if (amount > 0) {
            hp += amount;
        }
        setMaxHp(maxHp);
    }

    // ----- rings -----

    public int rings() { return rings; }

    public void gainRings(int amount) {
        if (amount <= 0) {
            return;
        }
        rings += amount;
        stats.ringsCollected += amount;
    }

    /** Pays rings; returns false (paying nothing) when the player can't afford it. */
    public boolean spendRings(int amount) {
        if (amount > rings) {
            return false;
        }
        rings -= amount;
        stats.ringsSpent += amount;
        return true;
    }

    public void loseRings(int amount) {
        rings = Math.max(0, rings - amount);
    }

    // ----- deck -----

    public List<Card> deck() { return deck; }

    public Card addCard(CardDef def, boolean upgraded) {
        Card card = new Card(def, upgraded);
        addCard(card);
        return card;
    }

    public void addCard(Card card) {
        deck.add(card);
        stats.cardsAdded++;
        for (Relic relic : List.copyOf(relics)) {
            relic.onCardAddedToDeck(this, card);
        }
    }

    public void removeCard(Card card) {
        if (deck.remove(card)) {
            stats.cardsRemoved++;
        }
    }

    public void upgradeCard(Card card) {
        if (card.canUpgrade()) {
            card.upgrade();
            stats.cardsUpgraded++;
        }
    }

    public List<Card> upgradableCards() {
        List<Card> list = new ArrayList<>();
        for (Card c : deck) {
            if (c.canUpgrade()) {
                list.add(c);
            }
        }
        return list;
    }

    /** Cards that may be removed (curses that forbid removal are excluded). */
    public List<Card> removableCards() {
        List<Card> list = new ArrayList<>();
        for (Card c : deck) {
            if (!c.def().id().endsWith(":unremovable")) {
                list.add(c);
            }
        }
        return list;
    }

    // ----- relics -----

    public List<Relic> relics() { return relics; }

    public boolean hasRelic(String id) {
        return relic(id) != null;
    }

    public Relic relic(String id) {
        for (Relic r : relics) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public void obtainRelic(Relic relic) {
        relics.add(relic);
        for (Deque<String> pool : relicPools.values()) {
            pool.remove(relic.id());
        }
        relic.onEquip(this);
    }

    public void obtainRelic(String id) {
        obtainRelic(catalog.newRelic(id));
    }

    /**
     * Takes the next relic of {@code tier} from its shuffled pool (Slay the Spire style),
     * falling back through rarer tiers when a pool runs dry. Returns null when nothing is left.
     */
    public String takeRelicFromPool(String tier) {
        String[] order = switch (tier) {
            case RelicTier.COMMON -> new String[] {RelicTier.COMMON, RelicTier.UNCOMMON, RelicTier.RARE};
            case RelicTier.UNCOMMON -> new String[] {RelicTier.UNCOMMON, RelicTier.RARE, RelicTier.COMMON};
            case RelicTier.RARE -> new String[] {RelicTier.RARE, RelicTier.UNCOMMON, RelicTier.COMMON};
            case RelicTier.SHOP -> new String[] {RelicTier.SHOP, RelicTier.UNCOMMON, RelicTier.COMMON};
            default -> new String[] {tier};
        };
        for (String t : order) {
            Deque<String> pool = relicPools.get(t);
            while (pool != null && !pool.isEmpty()) {
                String id = pool.pollFirst();
                if (!hasRelic(id)) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Random tier for a relic reward: 50% common, 33% uncommon, 17% rare. */
    public String rollRelicTier(Rng rng) {
        int roll = rng.nextInt(100);
        if (roll < 50) {
            return RelicTier.COMMON;
        }
        return roll < 83 ? RelicTier.UNCOMMON : RelicTier.RARE;
    }

    public Map<String, Deque<String>> relicPools() { return relicPools; }

    // ----- potions -----

    public int potionSlots() {
        int slots = BASE_POTION_SLOTS;
        for (Relic r : relics) {
            slots += r.potionSlotBonus();
        }
        return slots;
    }

    public List<PotionDef> potions() { return potions; }

    /** Potion in a slot, or null. */
    public PotionDef potionAt(int slot) {
        return slot >= 0 && slot < potions.size() ? potions.get(slot) : null;
    }

    public boolean potionsFull() {
        return potions.size() >= potionSlots();
    }

    /** Adds a potion; returns false when every slot is full. */
    public boolean addPotion(PotionDef potion) {
        if (potionsFull()) {
            return false;
        }
        potions.add(potion);
        return true;
    }

    public void removePotion(int slot) {
        if (slot >= 0 && slot < potions.size()) {
            potions.remove(slot);
        }
    }

    public int potionPotency(PotionDef potion) {
        int potency = potion.potency();
        for (Relic r : relics) {
            potency = r.modifyPotionPotency(potency);
        }
        return potency;
    }

    /** Uses an automatic revive potion (the 1-Up monitor) when the player would die. */
    boolean useRevivePotion(Combat combat) {
        for (int i = 0; i < potions.size(); i++) {
            PotionDef p = potions.get(i);
            if (p.target().equals(PotionDef.AUTO)) {
                potions.remove(i);
                combat.events().add(new CombatEvent.PotionUsed(p));
                p.effect().use(combat, null, potionPotency(p));
                return combat.player().hp() > 0;
            }
        }
        return false;
    }

    /** Chance (percent) of a potion dropping after a fight; rises 10 each miss, falls 10 each drop. */
    public int potionChance() { return potionChance; }
    public void setPotionChance(int value) { potionChance = Math.max(0, Math.min(100, value)); }

    // ----- map position -----

    public int act() { return act; }
    /** Floors climbed in the whole run (per-floor random streams derive from this). */
    public int floor() { return floor; }
    /** Row on the current act map: -1 before the first room, 15 at the boss. */
    public int actFloor() { return actFloor; }
    /** Column on the current act map, -1 before the first room. */
    public int nodeX() { return nodeX; }

    /** Moves onto a map node and advances the floor counter. */
    public void moveTo(int x, int y) {
        nodeX = x;
        actFloor = y;
        floor++;
        stats.floorsClimbed = floor;
        rngs.setFloor(floor);
    }

    /** Starts the next act at its bottom. */
    public void advanceAct() {
        act++;
        actFloor = -1;
        nodeX = -1;
        monsterQueue.clear();
        eliteQueue.clear();
        boss = null;
    }

    public void setPosition(int act, int floor, int actFloor, int nodeX) {
        this.act = act;
        this.floor = floor;
        this.actFloor = actFloor;
        this.nodeX = nodeX;
        rngs.setFloor(floor);
    }

    // ----- encounters and pools -----

    public List<String> monsterQueue() { return monsterQueue; }
    public List<String> eliteQueue() { return eliteQueue; }
    public String boss() { return boss; }
    public void setBoss(String id) { boss = id; }
    public Set<String> seenEvents() { return seenEvents; }

    public int cardRareBonus() { return cardRareBonus; }
    public void setCardRareBonus(int value) { cardRareBonus = value; }
    public int shopRemovals() { return shopRemovals; }
    public void setShopRemovals(int value) { shopRemovals = value; }

    public int unknownMonsterChance() { return unknownMonsterChance; }
    public int unknownShopChance() { return unknownShopChance; }
    public int unknownTreasureChance() { return unknownTreasureChance; }

    public void setUnknownChances(int monster, int shop, int treasure) {
        unknownMonsterChance = monster;
        unknownShopChance = shop;
        unknownTreasureChance = treasure;
    }

    /** Free-form counters for content (event progress, collected emeralds). Saved with the run. */
    public int counter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public void setCounter(String key, int value) {
        counters.put(key, value);
    }

    public void addCounter(String key, int delta) {
        counters.put(key, counter(key) + delta);
    }

    public Map<String, Integer> counters() { return counters; }
}
