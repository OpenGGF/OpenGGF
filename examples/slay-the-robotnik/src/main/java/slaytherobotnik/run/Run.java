package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import slaytherobotnik.core.ActDef;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.RestOption;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;
import slaytherobotnik.map.ActMap;
import slaytherobotnik.map.MapGenerator;
import slaytherobotnik.map.MapNode;

/**
 * Drives a run from the first Tornado flight to victory or defeat: which room is current,
 * how rooms lead into each other, and when the run is saved.
 *
 * <p>The flow per act is: Tornado bonus → map → rooms (fight → rewards, event, shop,
 * Starpost, treasure) → boss → rewards and boss relic → next act. The scene reads
 * {@link #room()} to decide what to draw and calls the room's methods; it never changes the
 * run state directly.
 *
 * <p>Save points: the start of each act, entering a room (before anything random happens, so
 * resuming replays the same fight or shop), the reward screen, and the map. A saver installed
 * with {@link #setSaver} is called at each.
 */
public final class Run {
    /** Save point names stored in the save file. */
    static final String SAVE_START = "start";
    static final String SAVE_ROOM = "room";
    static final String SAVE_REWARDS = "rewards";
    static final String SAVE_MAP = "map";

    /** The boss sits above the last map row. */
    public static final int BOSS_FLOOR = ActMap.HEIGHT;

    private final RunState state;
    private ActMap map;
    private Room room;
    private DeckChoice deckChoice;
    private Consumer<Run> saver;
    private String savePoint = SAVE_START;
    private boolean bossRewards;
    private List<Reward> eventRewards = List.of();

    Run(RunState state) {
        this.state = state;
    }

    /** Starts a new run. */
    public static Run start(Catalog catalog, String characterId, long seed) {
        Run run = new Run(RunState.start(catalog, catalog.character(characterId), seed));
        run.beginAct();
        return run;
    }

    public RunState state() { return state; }
    public ActMap map() { return map; }
    public Room room() { return room; }
    public ActDef act() { return state.catalog().act(state.act()); }

    /** Card grid waiting for an answer (drawn over the current room), or null. */
    public DeckChoice deckChoice() { return deckChoice; }

    public boolean over() {
        return room instanceof GameOverRoom || room instanceof VictoryRoom;
    }

    /** Called at every save point with this run; the scene stores {@link SaveCodec#encode}. */
    public void setSaver(Consumer<Run> saver) {
        this.saver = saver;
    }

    String savePoint() { return savePoint; }
    boolean bossRewards() { return bossRewards; }

    void autosave() {
        if (saver != null && !over()) {
            saver.accept(this);
        }
    }

    // ------------------------------------------------------------------ acts

    void beginAct() {
        map = buildMap();
        prepareEncounters();
        savePoint = SAVE_START;
        autosave();
        enterStart();
    }

    ActMap buildMap() {
        ActDef act = act();
        if (act.fixedLayout()) {
            return MapGenerator.linear(act.fixedRooms());
        }
        return MapGenerator.generate(state.rngs().mapRng(state.act()));
    }

    void enterStart() {
        room = new StartRoom(this, tornadoSpeech(), TornadoBonuses.forAct(this));
    }

    private String tornadoSpeech() {
        String zone = act().zoneName();
        return switch (state.character().id()) {
            case "tails" -> "You level the Tornado out over " + zone
                    + ". Sonic gives you a thumbs-up from the wing. Time to raid the cargo hold!";
            case "knuckles" -> "Tails: \"Hang on tight, Knuckles! " + zone
                    + " is right below us. Grab whatever you need from the back!\"";
            default -> "Tails: \"There it is, Sonic, " + zone
                    + "! I packed some gear in the cargo hold. Take your pick!\"";
        };
    }

    private void prepareEncounters() {
        if (!state.monsterQueue().isEmpty() || state.boss() != null) {
            return;
        }
        Catalog catalog = state.catalog();
        Rng rng = state.rngs().stream(RunRngs.MONSTERS);
        int act = state.act();
        fillQueue(state.monsterQueue(), catalog.encounters(act, EncounterDef.WEAK), 3, rng);
        fillQueue(state.monsterQueue(), catalog.encounters(act, EncounterDef.STRONG), 12, rng);
        fillQueue(state.eliteQueue(), catalog.encounters(act, EncounterDef.ELITE), 10, rng);
        List<EncounterDef> bosses = catalog.encounters(act, EncounterDef.BOSS);
        if (!bosses.isEmpty()) {
            state.setBoss(weighted(bosses, rng).id());
        }
    }

    /** Appends {@code count} weighted picks, avoiding a repeat of either of the last two. */
    private static void fillQueue(List<String> queue, List<EncounterDef> pool, int count, Rng rng) {
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            EncounterDef pick = weighted(pool, rng);
            for (int attempt = 0; attempt < 20 && pool.size() > 2 && recent(queue, pick.id()); attempt++) {
                pick = weighted(pool, rng);
            }
            queue.add(pick.id());
        }
    }

    private static boolean recent(List<String> queue, String id) {
        int n = queue.size();
        return (n >= 1 && queue.get(n - 1).equals(id)) || (n >= 2 && queue.get(n - 2).equals(id));
    }

    private static EncounterDef weighted(List<EncounterDef> pool, Rng rng) {
        int total = 0;
        for (EncounterDef e : pool) {
            total += Math.max(1, e.weight());
        }
        int roll = rng.nextInt(total);
        for (EncounterDef e : pool) {
            roll -= Math.max(1, e.weight());
            if (roll < 0) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ------------------------------------------------------------------ map

    /** Nodes the player may travel to (empty unless on the map). */
    public List<MapNode> reachableNodes() {
        if (!(room instanceof MapRoom) || deckChoice != null) {
            return List.of();
        }
        if (state.actFloor() < 0) {
            return map.nextChoices(null);
        }
        if (state.actFloor() >= BOSS_FLOOR) {
            return List.of();
        }
        return map.node(state.nodeX(), state.actFloor()).children();
    }

    /** True when the player stands on a node that leads to the boss. */
    public boolean bossReachable() {
        if (!(room instanceof MapRoom) || deckChoice != null || state.actFloor() < 0
                || state.actFloor() >= BOSS_FLOOR) {
            return false;
        }
        return map.node(state.nodeX(), state.actFloor()).connectsToBoss();
    }

    /** The node the player stands on, or null at the bottom of the map or at the boss. */
    public MapNode currentNode() {
        if (state.actFloor() < 0 || state.actFloor() >= BOSS_FLOOR) {
            return null;
        }
        return map.node(state.nodeX(), state.actFloor());
    }

    public void travelTo(MapNode node) {
        if (!reachableNodes().contains(node)) {
            throw new IllegalArgumentException("Not reachable: " + node);
        }
        state.moveTo(node.x(), node.y());
        savePoint = SAVE_ROOM;
        autosave();
        enterRoom(node.room());
    }

    public void travelToBoss() {
        if (!bossReachable()) {
            throw new IllegalStateException("Boss not reachable");
        }
        state.moveTo(state.nodeX(), BOSS_FLOOR);
        savePoint = SAVE_ROOM;
        autosave();
        enterBoss();
    }

    void reenterCurrentRoom() {
        if (state.actFloor() >= BOSS_FLOOR) {
            enterBoss();
        } else {
            enterRoom(map.node(state.nodeX(), state.actFloor()).room());
        }
    }

    private void enterBoss() {
        for (Relic relic : List.copyOf(state.relics())) {
            relic.onEnterRoom(state, RoomType.BOSS);
        }
        startCombat(state.boss(), RoomType.BOSS, null);
    }

    /** Enters a room of {@code type} ({@link RoomType}), as stepping onto a map node does; public for debugging. */
    public void enterRoom(String type) {
        for (Relic relic : List.copyOf(state.relics())) {
            relic.onEnterRoom(state, type);
        }
        switch (type) {
            case RoomType.MONSTER -> startCombat(nextFrom(state.monsterQueue(), EncounterDef.STRONG), RoomType.MONSTER,
                    null);
            case RoomType.ELITE -> startCombat(nextFrom(state.eliteQueue(), EncounterDef.ELITE), RoomType.ELITE, null);
            case RoomType.TREASURE -> room = new TreasureRoom(this, rollChestSize());
            case RoomType.SHOP -> room = new ShopRoom(this);
            case RoomType.REST -> room = new RestRoom(this, restOptions());
            default -> resolveUnknown();
        }
    }

    private String nextFrom(List<String> queue, String refillPool) {
        if (queue.isEmpty()) {
            fillQueue(queue, state.catalog().encounters(state.act(), refillPool), 5,
                    state.rngs().stream(RunRngs.MONSTERS));
        }
        return queue.remove(0);
    }

    /**
     * A "?" room may turn out to be a fight, shop or treasure; the chances grow each time it
     * doesn't, as in Slay the Spire.
     */
    private void resolveUnknown() {
        Rng rng = state.rngs().stream(RunRngs.EVENTS);
        int roll = rng.nextInt(100);
        int monster = state.unknownMonsterChance();
        int shop = state.unknownShopChance();
        int treasure = state.unknownTreasureChance();
        if (roll < monster) {
            state.setUnknownChances(10, shop + 3, treasure + 2);
            startCombat(nextFrom(state.monsterQueue(), EncounterDef.STRONG), RoomType.MONSTER, null);
            return;
        }
        if (roll < monster + shop) {
            state.setUnknownChances(monster + 10, 3, treasure + 2);
            room = new ShopRoom(this);
            return;
        }
        if (roll < monster + shop + treasure) {
            state.setUnknownChances(monster + 10, shop + 3, 2);
            room = new TreasureRoom(this, rollChestSize());
            return;
        }
        state.setUnknownChances(monster + 10, shop + 3, treasure + 2);
        List<EventDef> pool = new ArrayList<>();
        for (EventDef e : state.catalog().allEvents()) {
            if (e.available(state)) {
                pool.add(e);
            }
        }
        if (pool.isEmpty()) {
            startCombat(nextFrom(state.monsterQueue(), EncounterDef.STRONG), RoomType.MONSTER, null);
            return;
        }
        openEvent(rng.pick(pool));
    }

    /** Starts a specific fight straight away, in its own act (debugging, tests and screenshots). */
    public void enterFight(String encounterId) {
        EncounterDef encounter = state.catalog().encounter(encounterId);
        state.setPosition(encounter.act(), state.floor(), state.actFloor(), state.nodeX());
        String type = encounter.pool().equals(EncounterDef.BOSS) ? RoomType.BOSS
                : encounter.pool().equals(EncounterDef.ELITE) ? RoomType.ELITE : RoomType.MONSTER;
        startCombat(encounterId, type, null);
    }

    /** Opens a specific event straight away, ignoring its acts and condition (debugging and tests). */
    public void enterEvent(String eventId) {
        openEvent(state.catalog().event(eventId));
    }

    private void openEvent(EventDef def) {
        state.seenEvents().add(def.id());
        state.stats().eventsSeen++;
        EventRoom event = new EventRoom(this, def);
        room = event;
        event.begin();
    }

    // ------------------------------------------------------------------ fights

    void startCombat(String encounterId, String roomType, Runnable afterVictory) {
        EncounterDef encounter = state.catalog().encounter(encounterId);
        List<Enemy> enemies = encounter.spawner().spawn(state.rngs().floorRng("hp"));
        if (state.counter(TornadoBonuses.WEAK_ENEMIES) > 0 && !roomType.equals(RoomType.BOSS)) {
            state.addCounter(TornadoBonuses.WEAK_ENEMIES, -1);
            for (Enemy e : enemies) {
                e.setStartingHp(1);
            }
        }
        Combat combat = new Combat(state, enemies, roomType);
        room = new CombatRoom(this, combat, encounter.id(), encounter.name(), afterVictory);
        combat.start();
    }

    void startEventFight(EventRoom event, String encounterId, Runnable then) {
        eventRewards = new ArrayList<>(event.pendingRewards());
        String pool = state.catalog().encounter(encounterId).pool();
        startCombat(encounterId, pool.equals(EncounterDef.ELITE) ? RoomType.ELITE : RoomType.MONSTER, then);
    }

    void combatFinished(CombatRoom finished, Runnable afterVictory) {
        Combat combat = finished.combat();
        if (!combat.won()) {
            state.setHp(0);
            room = new GameOverRoom(finished.encounterName(), score(false));
            return;
        }
        String type = finished.roomType();
        if (type.equals(RoomType.ELITE)) {
            state.stats().elitesDefeated++;
        } else if (type.equals(RoomType.BOSS)) {
            state.stats().bossesDefeated++;
        }
        if (afterVictory != null) {
            afterVictory.run();
        }
        List<Reward> rewards = new RewardGenerator(state).combatRewards(type);
        rewards.addAll(eventRewards);
        eventRewards = List.of();
        bossRewards = type.equals(RoomType.BOSS);
        showRewards(type.equals(RoomType.BOSS) ? "Boss Defeated!" : "Victory!", rewards);
    }

    void showRewards(String title, List<Reward> rewards) {
        room = new RewardRoom(this, title, rewards);
        savePoint = SAVE_REWARDS;
        autosave();
    }

    // ------------------------------------------------------------------ other rooms

    private String rollChestSize() {
        int roll = state.rngs().stream(RunRngs.TREASURE).nextInt(100);
        return roll < 50 ? TreasureRoom.SMALL : roll < 83 ? TreasureRoom.MEDIUM : TreasureRoom.LARGE;
    }

    void openTreasure(TreasureRoom treasure) {
        Rng rng = state.rngs().stream(RunRngs.TREASURE);
        int roll = rng.nextInt(100);
        String tier;
        int ringChance;
        int rings;
        switch (treasure.size()) {
            case TreasureRoom.LARGE -> {
                tier = roll < 75 ? RelicTier.UNCOMMON : RelicTier.RARE;
                ringChance = 50;
                rings = rng.range(68, 82);
            }
            case TreasureRoom.MEDIUM -> {
                tier = roll < 35 ? RelicTier.COMMON : roll < 85 ? RelicTier.UNCOMMON : RelicTier.RARE;
                ringChance = 35;
                rings = rng.range(45, 55);
            }
            default -> {
                tier = roll < 75 ? RelicTier.COMMON : RelicTier.UNCOMMON;
                ringChance = 50;
                rings = rng.range(23, 27);
            }
        }
        List<Reward> rewards = new ArrayList<>();
        if (rng.nextInt(100) < ringChance) {
            rewards.add(new Reward.Rings(rings));
        }
        String relic = state.takeRelicFromPool(tier);
        if (relic != null) {
            rewards.add(new Reward.RelicReward(relic));
        }
        bossRewards = false;
        showRewards("Treasure!", rewards);
    }

    private List<RestOption> restOptions() {
        List<RestOption> options = new ArrayList<>();
        int heal = Math.max(1, Math.round(state.maxHp() * 0.3f));
        for (Relic relic : state.relics()) {
            heal = relic.modifyRestHeal(state, heal);
        }
        boolean canRest = true;
        for (Relic relic : state.relics()) {
            canRest &= relic.canRest(state);
        }
        int healAmount = heal;
        options.add(new RestOption("rest", "Rest", "Heal " + healAmount + " HP.", canRest, () -> {
            state.heal(healAmount);
            state.stats().restsTaken++;
            for (Relic relic : List.copyOf(state.relics())) {
                relic.onRest(state);
            }
        }));
        options.add(new RestOption("smith", "Tune Up", "Upgrade a card in your deck.",
                !state.upgradableCards().isEmpty(), () -> openDeckChoice(new DeckChoice("Choose a card to upgrade.",
                        state.upgradableCards(), 0, 1, DeckChoice.UPGRADE, chosen -> {
                            if (chosen.isEmpty()) {
                                if (room instanceof RestRoom rest) {
                                    rest.cancelChoice();
                                }
                                return;
                            }
                            state.upgradeCard(chosen.get(0));
                            state.stats().smiths++;
                        }))));
        for (Relic relic : state.relics()) {
            relic.addRestOptions(state, options);
        }
        return options;
    }

    void eventFinished(EventRoom event) {
        if (!event.pendingRewards().isEmpty() && room == event) {
            bossRewards = false;
            showRewards("Rewards", new ArrayList<>(event.pendingRewards()));
            return;
        }
        if (room == event) {
            leaveRoom();
        }
    }

    // ------------------------------------------------------------------ leaving rooms

    /** Back to the map, or on to the next act after a boss. */
    void leaveRoom() {
        if (deckChoice != null || over()) {
            return;
        }
        if (room instanceof RewardRoom && bossRewards) {
            bossRewards = false;
            if (state.act() >= state.catalog().actCount()) {
                room = new VictoryRoom(score(true));
                return;
            }
            state.advanceAct();
            beginAct();
            return;
        }
        room = new MapRoom();
        savePoint = SAVE_MAP;
        autosave();
    }

    // ------------------------------------------------------------------ deck choices

    /** Shows a card grid; with nothing to choose from it resolves immediately. */
    public void openDeckChoice(DeckChoice choice) {
        if (choice.options().isEmpty()) {
            choice.onChosen().accept(List.of());
            return;
        }
        deckChoice = choice;
    }

    /** Answers the open card grid; false when the selection is not allowed. */
    public boolean resolveDeckChoice(List<Card> picked) {
        DeckChoice choice = deckChoice;
        if (choice == null) {
            return false;
        }
        int min = Math.min(choice.min(), choice.options().size());
        if (picked.size() < min || picked.size() > choice.max()) {
            return false;
        }
        for (Card c : picked) {
            if (!choice.options().contains(c)) {
                return false;
            }
        }
        deckChoice = null;
        choice.onChosen().accept(List.copyOf(picked));
        return true;
    }

    /** Closes a cancellable card grid without choosing. */
    public boolean cancelDeckChoice() {
        if (deckChoice == null || !deckChoice.cancellable()) {
            return false;
        }
        return resolveDeckChoice(List.of());
    }

    /** Score shown at the end: floors, kills, elites, bosses and a victory bonus. */
    public int score(boolean victory) {
        var s = state.stats();
        return state.floor() * 5 + s.enemiesDefeated * 2 + s.elitesDefeated * 10 + s.bossesDefeated * 50
                + state.relics().size() * 3 + (victory ? 250 : 0);
    }

    // ------------------------------------------------------------------ resume

    void restore(ActMap map, Room room, String savePoint, boolean bossRewards) {
        this.map = map;
        this.room = room;
        this.savePoint = savePoint;
        this.bossRewards = bossRewards;
    }

    void setRoom(Room room) {
        this.room = room;
    }

    void setBossRewards(boolean value) {
        bossRewards = value;
    }
}
