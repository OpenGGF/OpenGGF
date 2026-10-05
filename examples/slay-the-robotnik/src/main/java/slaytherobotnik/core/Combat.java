package slaytherobotnik.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One fight: the player, the enemies, the card piles and the turn loop.
 *
 * <h2>How it runs</h2>
 * Every rule is applied immediately when called — dealing damage changes HP right away —
 * and each visible change is appended to {@link #events()}. The combat screen drains that
 * list and animates the events one after another, so the rules never wait for animations.
 * Tests drive a combat by calling {@link #playCard} and {@link #endTurn} and reading state.
 *
 * <p>The only thing that pauses a combat is a {@link ChoiceRequest} ("Exhaust a card"):
 * while one is pending, cards cannot be played and the turn cannot end until the player
 * answers through {@link #resolveChoice}.
 *
 * <h2>Turn order</h2>
 * Player turn: Block resets, Energy refills, start-of-turn powers and relics, draw 5, then
 * the player plays cards. End of turn: end-of-turn powers, Ethereal cards exhaust, the hand
 * is discarded except Retained cards. Each enemy then loses its Block, acts, and picks its
 * next intent. End of round: turn-based debuffs tick down.
 */
public final class Combat {
    public static final String NOT_STARTED = "NotStarted";
    public static final String PLAYER_TURN = "PlayerTurn";
    public static final String ENEMY_TURN = "EnemyTurn";
    public static final String VICTORY = "Victory";
    public static final String DEFEAT = "Defeat";

    public static final int CARDS_PER_TURN = 5;

    private final RunState run;
    private final Player player;
    private final List<Enemy> enemies = new ArrayList<>();
    private final String roomType;
    private final Rng shuffleRng;
    private final Rng aiRng;
    private final Rng cardRng;
    private final Rng miscRng;
    private final List<CombatEvent> events = new ArrayList<>();
    private final ArrayDeque<ChoiceRequest> choices = new ArrayDeque<>();
    private List<Card> activeOptions;
    private String phase = NOT_STARTED;
    private int turn;

    private int cardsPlayedThisTurn;
    private int attacksPlayedThisTurn;
    private int skillsPlayedThisTurn;
    private int cardsDiscardedThisTurn;
    private int comboCardsPlayedThisTurn;
    private int cardsExhaustedThisCombat;
    private int hpLostThisCombat;
    private final List<Card> playedThisCombat = new ArrayList<>();
    private Card cardBeingPlayed;

    /**
     * Creates a fight for the current floor of {@code run}. The player's deck is copied, so
     * nothing that happens in the fight changes the deck unless an effect says so.
     */
    public Combat(RunState run, List<Enemy> enemies, String roomType) {
        this.run = run;
        this.roomType = roomType;
        this.shuffleRng = run.rngs().floorRng("shuffle");
        this.aiRng = run.rngs().floorRng("ai");
        this.cardRng = run.rngs().floorRng("cardRandom");
        this.miscRng = run.rngs().floorRng("misc");
        int energy = CharacterDef.ENERGY_PER_TURN;
        for (Relic relic : run.relics()) {
            energy += relic.energyBonus();
        }
        this.player = new Player(run.character(), run.hp(), run.maxHp(), energy);
        this.enemies.addAll(enemies);
        for (Card card : run.deck()) {
            player.drawPile().add(card.combatCopy());
        }
    }

    // ------------------------------------------------------------------ accessors

    public RunState run() { return run; }
    public Player player() { return player; }
    /** All enemies in screen order (left to right), including dead ones until the fight ends. */
    public List<Enemy> enemies() { return enemies; }
    public String roomType() { return roomType; }
    public String phase() { return phase; }
    public int turn() { return turn; }
    public List<CombatEvent> events() { return events; }
    /** Stream for random effects of cards and relics (random targets, random cards). */
    public Rng cardRng() { return cardRng; }
    public Rng miscRng() { return miscRng; }
    public Catalog catalog() { return run.catalog(); }

    public int cardsPlayedThisTurn() { return cardsPlayedThisTurn; }
    public int attacksPlayedThisTurn() { return attacksPlayedThisTurn; }
    public int skillsPlayedThisTurn() { return skillsPlayedThisTurn; }
    public int cardsDiscardedThisTurn() { return cardsDiscardedThisTurn; }
    public int comboCardsPlayedThisTurn() { return comboCardsPlayedThisTurn; }
    public int cardsExhaustedThisCombat() { return cardsExhaustedThisCombat; }
    public int hpLostThisCombat() { return hpLostThisCombat; }
    public List<Card> playedThisCombat() { return List.copyOf(playedThisCombat); }

    public boolean isOver() {
        return phase.equals(VICTORY) || phase.equals(DEFEAT);
    }

    public boolean won() {
        return phase.equals(VICTORY);
    }

    /** Living enemies still in the fight, in screen order. */
    public List<Enemy> activeEnemies() {
        List<Enemy> list = new ArrayList<>();
        for (Enemy e : enemies) {
            if (e.isActive()) {
                list.add(e);
            }
        }
        return list;
    }

    /** A random living enemy, or null when none remain. */
    public Enemy randomEnemy() {
        List<Enemy> list = activeEnemies();
        return list.isEmpty() ? null : list.get(cardRng.nextInt(list.size()));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts the fight: spawn effects, start-of-combat relics, first intents, first draw. */
    public void start() {
        if (!phase.equals(NOT_STARTED)) {
            return;
        }
        shuffleDrawPileWithInnateOnTop();
        for (Enemy e : List.copyOf(enemies)) {
            e.onSpawn(this);
        }
        for (Relic relic : relics()) {
            relic.atBattleStart(this);
        }
        for (Enemy e : activeEnemies()) {
            e.rollMove(this, aiRng);
        }
        startPlayerTurn();
    }

    private void shuffleDrawPileWithInnateOnTop() {
        List<Card> pile = player.drawPile();
        shuffleRng.shuffle(pile);
        List<Card> innate = new ArrayList<>();
        for (Card c : pile) {
            if (c.has(Keyword.INNATE)) {
                innate.add(c);
            }
        }
        pile.removeAll(innate);
        pile.addAll(innate);
    }

    private void startPlayerTurn() {
        turn++;
        phase = PLAYER_TURN;
        cardsPlayedThisTurn = 0;
        attacksPlayedThisTurn = 0;
        skillsPlayedThisTurn = 0;
        cardsDiscardedThisTurn = 0;
        comboCardsPlayedThisTurn = 0;
        for (Card c : allCombatCards()) {
            c.clearTurnCost();
        }
        events.add(new CombatEvent.TurnStarted(turn, true));
        if (player.block() > 0 && !retainsBlock(player)) {
            int keep = 0;
            for (Relic relic : relics()) {
                keep = Math.max(keep, relic.blockKeptAtTurnStart(this, player.block()));
            }
            player.setBlock(keep);
        }
        player.setEnergy(player.energyPerTurn());
        events.add(new CombatEvent.EnergyChanged(player.energy()));
        for (Power p : player.powers()) {
            p.atTurnStart(this);
        }
        for (Relic relic : relics()) {
            relic.atTurnStart(this);
        }
        int drawCount = CARDS_PER_TURN;
        for (Relic relic : relics()) {
            drawCount += relic.drawBonus();
        }
        draw(drawCount);
        for (Relic relic : relics()) {
            relic.atTurnStartPostDraw(this);
        }
        for (Power p : player.powers()) {
            p.atTurnStartPostDraw(this);
        }
        pumpChoices();
    }

    /**
     * Ends the player's turn and runs the enemies' turn. Returns false (doing nothing)
     * when it is not the player's turn or a choice is pending.
     */
    public boolean endTurn() {
        if (!phase.equals(PLAYER_TURN) || hasPendingChoice()) {
            return false;
        }
        for (Power p : player.powers()) {
            p.atTurnEnd(this);
        }
        for (Relic relic : relics()) {
            relic.atTurnEnd(this);
        }
        for (Card card : List.copyOf(player.hand())) {
            CardHooks hooks = card.def().hooks();
            if (hooks != null && hooks.onEndOfTurnInHand() != null && !isOver()) {
                hooks.onEndOfTurnInHand().run(this, card);
            }
        }
        if (isOver()) {
            return true;
        }
        for (Card card : List.copyOf(player.hand())) {
            if (card.has(Keyword.ETHEREAL) && !card.has(Keyword.RETAIN)) {
                exhaust(card);
            }
        }
        for (Card card : List.copyOf(player.hand())) {
            if (card.has(Keyword.RETAIN) || retainsHand()) {
                card.setRetainThisTurn(false);
                events.add(new CombatEvent.CardRetained(card));
                CardHooks hooks = card.def().hooks();
                if (hooks != null && hooks.onRetain() != null) {
                    hooks.onRetain().run(this, card);
                }
            } else {
                player.hand().remove(card);
                player.discardPile().add(card);
                events.add(new CombatEvent.CardDiscarded(card, false));
            }
        }
        runEnemyTurn();
        return true;
    }

    private boolean retainsBlock(Creature creature) {
        for (CombatListener l : listenersOf(creature)) {
            if (l.retainsBlock(this)) {
                return true;
            }
        }
        return false;
    }

    private boolean retainsHand() {
        for (Relic relic : relics()) {
            if (relic.retainsHand(this)) {
                return true;
            }
        }
        return false;
    }

    private void runEnemyTurn() {
        phase = ENEMY_TURN;
        events.add(new CombatEvent.TurnStarted(turn, false));
        for (Enemy e : List.copyOf(enemies)) {
            if (!e.isActive()) {
                continue;
            }
            if (!retainsBlock(e)) {
                e.setBlock(0);
            }
            for (Power p : e.powers()) {
                p.atTurnStart(this);
            }
            if (!e.isActive()) {
                continue;
            }
            events.add(new CombatEvent.EnemyMove(e, e.nextMove()));
            e.takeTurn(this);
            if (isOver()) {
                return;
            }
            if (e.isActive()) {
                e.rollMove(this, aiRng);
            }
        }
        for (Enemy e : activeEnemies()) {
            for (Power p : e.powers()) {
                p.atTurnEnd(this);
            }
        }
        if (isOver()) {
            return;
        }
        endRound();
        if (!isOver()) {
            startPlayerTurn();
        }
    }

    private void endRound() {
        List<Creature> all = new ArrayList<>();
        all.add(player);
        all.addAll(activeEnemies());
        for (Creature creature : all) {
            for (Power p : creature.powers()) {
                p.atRoundEnd(this);
                if (p.turnBased() && creature.has(p.id())) {
                    if (p.justApplied()) {
                        p.setJustApplied(false);
                    } else {
                        reducePower(creature, p.id(), 1);
                    }
                }
            }
        }
        for (Relic relic : relics()) {
            relic.atRoundEnd(this);
        }
    }

    // ------------------------------------------------------------------ playing cards

    /** Energy cost of {@code card} right now (X-cost cards report the Energy they would spend). */
    public int effectiveCost(Card card) {
        int cost = card.baseCost();
        if (cost == CardDef.COST_X) {
            return player.energy();
        }
        if (cost < 0) {
            return cost;
        }
        CardHooks hooks = card.def().hooks();
        if (hooks != null && hooks.costAdjuster() != null) {
            cost = hooks.costAdjuster().adjust(this, card, cost);
        }
        for (CombatListener l : playerListeners()) {
            cost = l.modifyCost(this, card, cost);
        }
        return Math.max(0, cost);
    }

    /** Why {@code card} cannot be played now, or null when it can. */
    public String whyUnplayable(Card card) {
        if (!phase.equals(PLAYER_TURN)) {
            return "It's not your turn.";
        }
        if (hasPendingChoice()) {
            return "Make your choice first.";
        }
        if (card.has(Keyword.UNPLAYABLE) || card.baseCost() == CardDef.COST_NONE) {
            return "This card can't be played.";
        }
        for (CombatListener l : playerListeners()) {
            String veto = l.vetoCardPlay(this, card);
            if (veto != null) {
                return veto;
            }
        }
        if (!card.isXCost() && !card.freeToPlayOnce() && effectiveCost(card) > player.energy()) {
            return "Not enough Energy.";
        }
        CardDef.PlayCondition condition = card.def().condition();
        if (condition != null) {
            return condition.blockedReason(this, card);
        }
        return null;
    }

    public boolean canPlay(Card card) {
        return player.hand().contains(card) && whyUnplayable(card) == null;
    }

    /**
     * Plays {@code card} from the hand at {@code target} (ignored unless the card targets
     * one enemy). Returns false, with a {@link CombatEvent.Message}, when it cannot be played.
     */
    public boolean playCard(Card card, Enemy target) {
        if (!player.hand().contains(card)) {
            return false;
        }
        String reason = whyUnplayable(card);
        if (reason != null) {
            events.add(new CombatEvent.Message(reason));
            return false;
        }
        if (CardTarget.needsChoice(card.target())) {
            if (target == null || !target.isActive() || !enemies.contains(target)) {
                events.add(new CombatEvent.Message("Choose a target."));
                return false;
            }
        } else {
            target = null;
        }
        int x = card.isXCost() ? player.energy() : 0;
        int spent = card.freeToPlayOnce() ? 0 : (card.isXCost() ? player.energy() : effectiveCost(card));
        for (Relic relic : relics()) {
            x = relic.modifyXValue(this, card, x);
        }
        if (spent > 0) {
            player.setEnergy(player.energy() - spent);
            events.add(new CombatEvent.EnergyChanged(player.energy()));
        }
        player.hand().remove(card);
        cardsPlayedThisTurn++;
        switch (card.type()) {
            case CardType.ATTACK -> attacksPlayedThisTurn++;
            case CardType.SKILL -> skillsPlayedThisTurn++;
            default -> { }
        }
        if (card.combo() > 0) {
            comboCardsPlayedThisTurn++;
        }
        playedThisCombat.add(card);
        events.add(new CombatEvent.CardPlayed(card, target));
        for (CombatListener l : playerListeners()) {
            l.onCardPlayed(this, card);
        }
        for (Enemy e : activeEnemies()) {
            for (Power p : e.powers()) {
                p.onPlayerCardPlayed(this, card);
            }
        }
        for (Card other : List.copyOf(player.hand())) {
            CardHooks hooks = other.def().hooks();
            if (hooks != null && hooks.onOtherCardPlayed() != null) {
                hooks.onOtherCardPlayed().run(this, other, card);
            }
        }
        Play play = new Play(this, card, target, x);
        cardBeingPlayed = card;
        card.def().effect().play(play);
        cardBeingPlayed = null;
        card.setFreeToPlayOnce(false);
        sendPlayedCard(card, play.destination());
        for (CombatListener l : playerListeners()) {
            l.afterCardPlayed(this, card);
        }
        pumpChoices();
        return true;
    }

    private void sendPlayedCard(Card card, String destination) {
        if (card.type().equals(CardType.POWER)) {
            return;
        }
        String dest = destination;
        if (dest == null) {
            dest = card.has(Keyword.EXHAUST) ? Play.TO_EXHAUST : Play.TO_DISCARD;
        }
        switch (dest) {
            case Play.TO_EXHAUST -> {
                player.exhaustPile().add(card);
                afterExhaust(card);
            }
            case Play.TO_DRAW_PILE -> {
                player.drawPile().add(cardRng.nextInt(player.drawPile().size() + 1), card);
            }
            case Play.TO_HAND -> addCardToHandRaw(card);
            case Play.TO_NOWHERE -> { }
            default -> player.discardPile().add(card);
        }
    }

    /** The card currently resolving, or null. */
    public Card cardBeingPlayed() {
        return cardBeingPlayed;
    }

    /** How many times a Combo effect on {@code card} repeats now: its Combo value plus Focus and bonuses. */
    public int comboCount(Card card) {
        return comboCount(card, card.combo());
    }

    /** Combo count for a given base (X-cost cards pass X). Never below 0, or below 1 for printed Combo cards. */
    public int comboCount(Card card, int base) {
        int count = base;
        for (CombatListener l : playerListeners()) {
            count = l.modifyCombo(this, card, count);
        }
        return Math.max(card.isXCost() ? 0 : 1, count);
    }

    // ------------------------------------------------------------------ choices

    public boolean hasPendingChoice() {
        return !choices.isEmpty();
    }

    /** The choice waiting for the player, or null. */
    public ChoiceRequest pendingChoice() {
        return choices.peekFirst();
    }

    /** Cards the pending choice may pick from. */
    public List<Card> pendingOptions() {
        return activeOptions == null ? List.of() : List.copyOf(activeOptions);
    }

    /** Asks the player to choose; see {@link ChoiceRequest}. */
    public void requestChoice(ChoiceRequest request) {
        choices.addLast(request);
        if (cardBeingPlayed == null) {
            pumpChoices();
        }
    }

    /** Shorthand: choose {@code count} cards from the hand (fewer if the hand is smaller). */
    public void chooseFromHand(String prompt, int count, boolean anyNumber, Consumer<List<Card>> onChosen) {
        requestChoice(new ChoiceRequest(prompt, () -> List.copyOf(player.hand()), anyNumber ? 0 : count,
                count, true, onChosen));
    }

    /** Shorthand: choose cards from an arbitrary list (a pile, or freshly created cards). */
    public void chooseFrom(String prompt, Supplier<List<Card>> options, int min, int max,
            Consumer<List<Card>> onChosen) {
        requestChoice(new ChoiceRequest(prompt, options, min, max, false, onChosen));
    }

    /**
     * Answers the pending choice. Returns false when {@code picked} is not a valid answer
     * (wrong count or a card that was not offered).
     */
    public boolean resolveChoice(List<Card> picked) {
        ChoiceRequest request = choices.peekFirst();
        if (request == null) {
            return false;
        }
        if (picked.size() < Math.min(request.min(), activeOptions.size()) || picked.size() > request.max()) {
            return false;
        }
        for (Card c : picked) {
            if (!activeOptions.contains(c)) {
                return false;
            }
        }
        choices.pollFirst();
        activeOptions = null;
        request.onChosen().accept(List.copyOf(picked));
        pumpChoices();
        return true;
    }

    private void pumpChoices() {
        while (!choices.isEmpty() && !isOver()) {
            ChoiceRequest head = choices.peekFirst();
            if (activeOptions == null) {
                activeOptions = new ArrayList<>(head.options().get());
            }
            if (activeOptions.size() <= head.min() || head.max() == 0) {
                List<Card> all = List.copyOf(activeOptions);
                choices.pollFirst();
                activeOptions = null;
                head.onChosen().accept(all);
                continue;
            }
            return;
        }
        if (isOver()) {
            choices.clear();
            activeOptions = null;
        }
    }

    // ------------------------------------------------------------------ damage

    /**
     * Damage {@code source} would deal to {@code target} with an attack of {@code base}:
     * Strength and Weak on the attacker, Vulnerable and Intangible on the target, and any
     * relic modifiers. Also used for intents and card previews, so it changes nothing.
     */
    public int calculateDamage(Creature source, Creature target, int base, String type) {
        float damage = base;
        if (type.equals(DamageType.ATTACK)) {
            if (source != null) {
                for (CombatListener l : listenersOf(source)) {
                    damage = l.modifyDamageDealt(this, damage, type, target);
                }
            }
            if (target != null) {
                for (CombatListener l : listenersOf(target)) {
                    damage = l.modifyDamageTaken(this, damage, type, source);
                }
            }
        }
        int result = Math.max(0, (int) Math.floor(damage));
        if (target != null) {
            for (CombatListener l : listenersOf(target)) {
                result = l.modifyFinalDamageTaken(this, result, type, source);
            }
        }
        return Math.max(0, result);
    }

    /** Attack: calculate with modifiers, then apply. */
    public void attack(Creature source, Creature target, int base) {
        if (target == null || isOver()) {
            return;
        }
        dealDamage(source, target, calculateDamage(source, target, base, DamageType.ATTACK), DamageType.ATTACK);
    }

    /** Applies already-calculated damage: Block first (unless HP loss), then HP. */
    public void dealDamage(Creature source, Creature target, int amount, String type) {
        if (target == null || isOver() || target.isDead()) {
            return;
        }
        if (target instanceof Enemy enemy && enemy.escaped()) {
            return;
        }
        int blocked = 0;
        if (!type.equals(DamageType.HP_LOSS)) {
            blocked = Math.min(target.block(), amount);
            target.setBlock(target.block() - blocked);
        } else {
            amount = calculateDamage(source, target, amount, DamageType.HP_LOSS);
        }
        int hpLoss = amount - blocked;
        for (CombatListener l : listenersOf(target)) {
            hpLoss = Math.max(0, l.modifyHpLoss(this, hpLoss, source, type));
        }
        hpLoss = Math.min(hpLoss, target.hp());
        target.setHp(target.hp() - hpLoss);
        events.add(new CombatEvent.Damaged(source, target, amount, blocked, hpLoss, type));
        if (hpLoss > 0) {
            if (target.isPlayer()) {
                hpLostThisCombat += hpLoss;
            }
            for (CombatListener l : listenersOf(target)) {
                l.onHpLost(this, hpLoss);
            }
        }
        if (!type.equals(DamageType.HP_LOSS)) {
            for (CombatListener l : listenersOf(target)) {
                l.onAttacked(this, source, amount, hpLoss, type);
            }
            if (source != null && !source.isDead()) {
                for (CombatListener l : listenersOf(source)) {
                    l.onAttack(this, target, amount, hpLoss, type);
                }
            }
        }
        if (target.hp() <= 0) {
            handleDeath(target);
        }
    }

    /** The player loses HP directly (ignores Block). */
    public void loseHp(Creature target, int amount) {
        dealDamage(null, target, amount, DamageType.HP_LOSS);
    }

    /** Damage to the player that is not an attack (Burn, explosions). Block applies. */
    public void damagePlayer(int amount) {
        dealDamage(null, player, amount, DamageType.THORNS);
    }

    float vulnerableMultiplier(Creature target) {
        float mult = 1.5f;
        for (CombatListener l : playerListeners()) {
            mult = l.modifyVulnerableMultiplier(this, target, mult);
        }
        return mult;
    }

    float weakMultiplier(Creature owner) {
        float mult = 0.75f;
        for (Relic relic : relics()) {
            mult = relic.modifyWeakMultiplier(this, owner, mult);
        }
        return mult;
    }

    private void handleDeath(Creature target) {
        if (target.isPlayer()) {
            for (Relic relic : relics()) {
                if (!relic.usedUp() && relic.preventDeath(this) && player.hp() > 0) {
                    flashRelic(relic);
                    return;
                }
            }
            if (run.useRevivePotion(this)) {
                return;
            }
            phase = DEFEAT;
            run.setHp(0);
            events.add(new CombatEvent.Defeat());
            return;
        }
        Enemy enemy = (Enemy) target;
        for (Power p : enemy.powers()) {
            p.onDeath(this);
        }
        enemy.onDeath(this);
        if (enemy.hp() > 0 || enemy.halfDead()) {
            return;
        }
        events.add(new CombatEvent.EnemyDied(enemy));
        run.stats().enemiesDefeated++;
        for (CombatListener l : playerListeners()) {
            l.onEnemyDeath(this, enemy);
        }
        checkVictory();
    }

    /** Ends the fight in the player's favour once every non-minion enemy is dead or gone. */
    public void checkVictory() {
        if (isOver()) {
            return;
        }
        boolean anyLeader = false;
        boolean leaderAlive = false;
        for (Enemy e : enemies) {
            if (!e.isMinion()) {
                anyLeader = true;
                if (e.isActive() || e.halfDead()) {
                    leaderAlive = true;
                }
            }
        }
        boolean anyAlive = !activeEnemies().isEmpty();
        if (anyLeader ? leaderAlive : anyAlive) {
            return;
        }
        for (Enemy e : activeEnemies()) {
            e.escapeSilently();
            events.add(new CombatEvent.EnemyEscaped(e));
        }
        phase = VICTORY;
        for (Relic relic : relics()) {
            relic.onVictory(this);
        }
        for (Power p : player.powers()) {
            p.onVictory(this);
        }
        run.setHp(player.hp());
        events.add(new CombatEvent.Victory());
    }

    // ------------------------------------------------------------------ block, healing, energy

    /**
     * Gives {@code target} Block. When {@code card} is non-null the Block comes from a card
     * and Dexterity and Frail apply; Block from relics and powers is unmodified.
     */
    public int gainBlock(Creature target, int base, Card card) {
        if (target == null || target.isDead() || isOver()) {
            return 0;
        }
        int amount = card == null ? base : calculateBlock(target, base, card);
        if (amount <= 0) {
            return 0;
        }
        target.setBlock(target.block() + amount);
        events.add(new CombatEvent.BlockGained(target, amount));
        for (CombatListener l : listenersOf(target)) {
            l.onBlockGained(this, amount);
        }
        return amount;
    }

    /** Block a card would give (for previews). */
    public int calculateBlock(Creature target, int base, Card card) {
        float block = base;
        for (CombatListener l : listenersOf(target)) {
            block = l.modifyBlock(this, block, card);
        }
        return Math.max(0, (int) Math.floor(block));
    }

    public void removeBlock(Creature target) {
        target.setBlock(0);
    }

    /** Heals a creature; healing the player passes through relic modifiers. */
    public void heal(Creature target, int amount) {
        if (target == null || target.isDead() || amount <= 0) {
            return;
        }
        if (target.isPlayer()) {
            for (Relic relic : relics()) {
                amount = relic.modifyHeal(run, amount);
            }
        }
        int before = target.hp();
        target.setHp(target.hp() + amount);
        int healed = target.hp() - before;
        if (healed > 0) {
            events.add(new CombatEvent.Healed(target, healed));
        }
        if (target.isPlayer()) {
            run.setHp(player.hp());
        }
    }

    public void healPlayer(int amount) {
        heal(player, amount);
    }

    /** Brings the player back from 0 HP (revive relics and potions); ignores healing modifiers. */
    public void revivePlayer(int hp) {
        int value = Math.max(player.hp(), Math.max(1, Math.min(player.maxHp(), hp)));
        player.setHp(value);
        run.setHp(value);
        events.add(new CombatEvent.Healed(player, value));
    }

    /** Permanently raises (or lowers) the player's max HP, healing by the same amount when raising. */
    public void gainMaxHp(int amount) {
        player.setMaxHp(player.maxHp() + amount);
        run.setMaxHp(player.maxHp());
        events.add(new CombatEvent.MaxHpChanged(player, amount));
        if (amount > 0) {
            heal(player, amount);
        }
        run.setHp(player.hp());
    }

    public void gainEnergy(int amount) {
        player.setEnergy(player.energy() + amount);
        events.add(new CombatEvent.EnergyChanged(player.energy()));
    }

    public void loseEnergy(int amount) {
        player.setEnergy(player.energy() - amount);
        events.add(new CombatEvent.EnergyChanged(player.energy()));
    }

    /** Rings gained mid-fight (for example from a fatal blow); added to the run immediately. */
    public void gainRings(int amount) {
        run.gainRings(amount);
        events.add(new CombatEvent.RingsGained(amount));
    }

    // ------------------------------------------------------------------ powers

    /** Applies {@code power} to {@code target}, stacking with an existing power of the same id. */
    public void applyPower(Creature source, Creature target, Power power) {
        if (target == null || target.isDead() || isOver()) {
            return;
        }
        if (power.isDebuff() && target.has(Powers.ARTIFACT) && source != target) {
            reducePower(target, Powers.ARTIFACT, 1);
            events.add(new CombatEvent.DebuffNegated(target, power.id()));
            return;
        }
        boolean enemyDuringEnemyTurn = source instanceof Enemy && phase.equals(ENEMY_TURN);
        Power existing = target.power(power.id());
        if (existing != null) {
            if (!existing.stacks()) {
                return;
            }
            existing.addAmount(power.amount());
            if (existing.turnBased() && enemyDuringEnemyTurn && target.isPlayer()) {
                existing.setJustApplied(true);
            }
            events.add(new CombatEvent.PowerChanged(target, power.id(), power.amount(), false));
            if (existing.amount() == 0) {
                removePower(target, power.id());
            }
        } else {
            if (power.amount() == 0 && power.stacks()) {
                return;
            }
            power.attach(target);
            if (power.turnBased() && enemyDuringEnemyTurn && target.isPlayer()) {
                power.setJustApplied(true);
            }
            target.putPower(power);
            events.add(new CombatEvent.PowerChanged(target, power.id(), power.amount(), false));
        }
        if (source != null) {
            Power applied = target.power(power.id());
            for (CombatListener l : listenersOf(source)) {
                l.onPowerApplied(this, applied == null ? power : applied, target);
            }
        }
    }

    /** Reduces a power's amount, removing it at zero. */
    public void reducePower(Creature target, String powerId, int amount) {
        Power p = target.power(powerId);
        if (p == null || amount == 0) {
            return;
        }
        p.addAmount(-amount);
        events.add(new CombatEvent.PowerChanged(target, powerId, -amount, false));
        if (p.amount() == 0 || (!p.canGoNegative() && p.amount() < 0)) {
            removePower(target, powerId);
        }
    }

    public void removePower(Creature target, String powerId) {
        if (target.has(powerId)) {
            target.removePower(powerId);
            events.add(new CombatEvent.PowerChanged(target, powerId, 0, true));
        }
    }

    // ------------------------------------------------------------------ cards and piles

    /** Draws {@code count} cards, shuffling the discard pile in when the draw pile runs out. */
    public void draw(int count) {
        for (int i = 0; i < count; i++) {
            if (player.has(Powers.NO_DRAW) || player.hand().size() >= Player.MAX_HAND) {
                return;
            }
            if (player.drawPile().isEmpty()) {
                if (player.discardPile().isEmpty()) {
                    return;
                }
                shuffleDiscardIntoDraw();
            }
            Card card = player.drawPile().remove(player.drawPile().size() - 1);
            player.hand().add(card);
            events.add(new CombatEvent.CardDrawn(card));
            CardHooks hooks = card.def().hooks();
            if (hooks != null && hooks.onDraw() != null) {
                hooks.onDraw().run(this, card);
            }
            for (CombatListener l : playerListeners()) {
                l.onCardDrawn(this, card);
            }
        }
    }

    private void shuffleDiscardIntoDraw() {
        List<Card> discard = player.discardPile();
        int n = discard.size();
        player.drawPile().addAll(discard);
        discard.clear();
        shuffleRng.shuffle(player.drawPile());
        events.add(new CombatEvent.Shuffled(n));
        for (CombatListener l : playerListeners()) {
            l.onShuffle(this);
        }
    }

    /** Discards a card from the hand because an effect said so (counts as a manual discard). */
    public void discard(Card card) {
        if (!player.hand().remove(card)) {
            return;
        }
        player.discardPile().add(card);
        cardsDiscardedThisTurn++;
        events.add(new CombatEvent.CardDiscarded(card, true));
        CardHooks hooks = card.def().hooks();
        if (hooks != null && hooks.onManualDiscard() != null) {
            hooks.onManualDiscard().run(this, card);
        }
        for (CombatListener l : playerListeners()) {
            l.onCardDiscarded(this, card);
        }
    }

    /** Exhausts a card from whichever pile holds it. */
    public void exhaust(Card card) {
        boolean removed = player.hand().remove(card) || player.drawPile().remove(card)
                || player.discardPile().remove(card);
        if (!removed && card != cardBeingPlayed) {
            return;
        }
        player.exhaustPile().add(card);
        afterExhaust(card);
    }

    private void afterExhaust(Card card) {
        cardsExhaustedThisCombat++;
        events.add(new CombatEvent.CardExhausted(card));
        CardHooks hooks = card.def().hooks();
        if (hooks != null && hooks.onExhaust() != null) {
            hooks.onExhaust().run(this, card);
        }
        for (CombatListener l : playerListeners()) {
            l.onCardExhausted(this, card);
        }
    }

    /** Creates a new card (by id) in the hand; it goes to the discard pile when the hand is full. */
    public Card createInHand(String cardId, boolean upgraded) {
        Card card = new Card(catalog().card(cardId), upgraded);
        addCreatedCard(card, "hand");
        return card;
    }

    /** Adds an existing card object (for example a copy) to the hand as a created card. */
    public void addCreatedCard(Card card, String pile) {
        String dest = pile;
        switch (pile) {
            case "draw" -> player.drawPile().add(cardRng.nextInt(player.drawPile().size() + 1), card);
            case "discard" -> player.discardPile().add(card);
            default -> {
                if (player.hand().size() >= Player.MAX_HAND) {
                    player.discardPile().add(card);
                    dest = "discard";
                } else {
                    player.hand().add(card);
                }
            }
        }
        events.add(new CombatEvent.CardCreated(card, dest));
        for (CombatListener l : playerListeners()) {
            l.onCardCreated(this, card);
        }
    }

    private void addCardToHandRaw(Card card) {
        if (player.hand().size() >= Player.MAX_HAND) {
            player.discardPile().add(card);
        } else {
            player.hand().add(card);
        }
    }

    /** Moves a card from the discard or draw pile into the hand. */
    public void moveToHand(Card card) {
        if (player.hand().size() >= Player.MAX_HAND) {
            return;
        }
        if (player.discardPile().remove(card) || player.drawPile().remove(card)
                || player.exhaustPile().remove(card)) {
            player.hand().add(card);
            events.add(new CombatEvent.CardDrawn(card));
        }
    }

    /** Upgrades a card for the rest of this combat. */
    public void upgradeForCombat(Card card) {
        if (card.canUpgrade() || (card.type().equals(CardType.STATUS) && !card.upgraded())) {
            card.forceUpgrade();
            events.add(new CombatEvent.CardUpgraded(card));
        }
    }

    /** Upgrades a card for this combat and its deck original for the rest of the run. */
    public void upgradePermanently(Card card) {
        upgradeForCombat(card);
        if (card.master() != null) {
            card.master().upgrade();
        }
    }

    /** Every card in the hand and all piles. */
    public List<Card> allCombatCards() {
        List<Card> all = new ArrayList<>(player.hand());
        all.addAll(player.drawPile());
        all.addAll(player.discardPile());
        all.addAll(player.exhaustPile());
        return all;
    }

    // ------------------------------------------------------------------ enemies

    /** Adds an enemy mid-fight (summons, splits) at screen position {@code index} (-1 = rightmost). */
    public void spawnEnemy(Enemy enemy, int index) {
        if (index < 0 || index > enemies.size()) {
            enemies.add(enemy);
        } else {
            enemies.add(index, enemy);
        }
        events.add(new CombatEvent.EnemySpawned(enemy));
        enemy.onSpawn(this);
        enemy.rollMove(this, aiRng);
    }

    /** Replaces the enemy's intent immediately (for example when it is stunned). */
    public void rerollIntent(Enemy enemy) {
        enemy.rollMove(this, aiRng);
    }

    public Rng aiRng() {
        return aiRng;
    }

    // ------------------------------------------------------------------ potions

    /** Uses the potion in {@code slot}; returns false when the slot is empty or a target is missing. */
    public boolean usePotion(int slot, Enemy target) {
        PotionDef potion = run.potionAt(slot);
        if (potion == null || isOver() || hasPendingChoice() || !phase.equals(PLAYER_TURN)
                || potion.target().equals(PotionDef.AUTO)) {
            // Automatic potions (the 1-Up) only trigger themselves.
            return false;
        }
        if (CardTarget.needsChoice(potion.target()) && (target == null || !target.isActive())) {
            return false;
        }
        run.removePotion(slot);
        events.add(new CombatEvent.PotionUsed(potion));
        potion.effect().use(this, target, run.potionPotency(potion));
        for (CombatListener l : playerListeners()) {
            l.onPotionUsed(this, potion);
        }
        pumpChoices();
        return true;
    }

    // ------------------------------------------------------------------ listeners

    List<Relic> relics() {
        List<Relic> list = new ArrayList<>();
        for (Relic relic : run.relics()) {
            if (!relic.usedUp()) {
                list.add(relic);
            }
        }
        return list;
    }

    void flashRelic(Relic relic) {
        events.add(new CombatEvent.RelicTriggered(relic));
    }

    /** The player's powers followed by relics. */
    private List<CombatListener> playerListeners() {
        List<CombatListener> list = new ArrayList<>(sortedPowers(player));
        list.addAll(relics());
        return list;
    }

    private List<CombatListener> listenersOf(Creature creature) {
        if (creature.isPlayer()) {
            return playerListeners();
        }
        return new ArrayList<>(sortedPowers(creature));
    }

    private static List<Power> sortedPowers(Creature creature) {
        List<Power> powers = creature.powers();
        powers.sort(Comparator.comparingInt(Power::priority));
        return powers;
    }
}
