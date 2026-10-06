package slaytherobotnik.core;

import java.util.List;
import java.util.function.Consumer;

/**
 * Everything a card effect needs while it resolves: the combat, the card, its target and
 * helpers for the usual Slay the Spire verbs. A typical effect reads like the card text:
 *
 * <pre>{@code
 * .text("Combo {C}: Deal {D} damage. Apply 1 Vulnerable.")
 * .effect(p -> {
 *     p.combo(p::attack);
 *     p.applyToTarget(Powers.vulnerable(1));
 * })
 * }</pre>
 *
 * Effects run immediately. A choice ({@link #chooseFromHand}) is answered later, so any work
 * that depends on the chosen cards goes inside its callback.
 */
public final class Play {
    /** Destinations for {@link #sendTo}. */
    public static final String TO_DISCARD = "discard";
    public static final String TO_EXHAUST = "exhaust";
    public static final String TO_DRAW_PILE = "draw";
    public static final String TO_HAND = "hand";
    /** The card vanishes (it is neither discarded nor exhausted). */
    public static final String TO_NOWHERE = "nowhere";

    private final Combat combat;
    private final Card card;
    private final Enemy target;
    private final int x;
    private String destination;

    Play(Combat combat, Card card, Enemy target, int x) {
        this.combat = combat;
        this.card = card;
        this.target = target;
        this.x = x;
    }

    public Combat combat() { return combat; }
    public Card card() { return card; }
    /** The chosen enemy for single-target cards, otherwise null. */
    public Enemy target() { return target; }
    public Player player() { return combat.player(); }
    /** Energy spent on an X-cost card (after relic adjustments). */
    public int x() { return x; }

    /** Per-hit base damage before modifiers ({@code {D}}). */
    public int damage() {
        return Cards.baseDamage(combat, card, target);
    }

    /** Base Block before Dexterity ({@code {B}}). */
    public int block() { return card.block(); }
    /** The card's magic number ({@code {M}}). */
    public int magic() { return card.magic(); }
    /** How many times Combo effects repeat, including Focus ({@code {C}}). */
    public int combo() { return combat.comboCount(card); }
    public boolean upgraded() { return card.upgraded(); }

    // ----- Attacks -----

    /** Hits the chosen target once (or a random enemy for random-target cards). */
    public void attack() {
        if (card.target().equals(CardTarget.RANDOM_ENEMY)) {
            attackRandom();
        } else if (card.target().equals(CardTarget.ALL_ENEMIES)) {
            attackAll();
        } else {
            attack(target);
        }
    }

    public void attack(Enemy enemy) {
        if (enemy != null && enemy.isActive()) {
            combat.attack(combat.player(), enemy, Cards.baseDamage(combat, card, enemy));
        }
    }

    /** Hits every living enemy once. */
    public void attackAll() {
        for (Enemy e : combat.activeEnemies()) {
            attack(e);
        }
    }

    /** Hits one random living enemy. */
    public void attackRandom() {
        attack(combat.randomEnemy());
    }

    /** Runs {@code action} once per Combo repetition. */
    public void combo(Runnable action) {
        repeat(combo(), action);
    }

    public void repeat(int times, Runnable action) {
        for (int i = 0; i < times && !combat.isOver(); i++) {
            action.run();
        }
    }

    // ----- Defence and resources -----

    /** Gains the card's Block (Dexterity and Frail apply). */
    public void gainBlock() {
        gainBlock(card.block());
    }

    public void gainBlock(int amount) {
        combat.gainBlock(combat.player(), amount, card);
    }

    public void draw(int count) { combat.draw(count); }
    public void gainEnergy(int amount) { combat.gainEnergy(amount); }

    // ----- Powers -----

    public void applyToTarget(Power power) {
        combat.applyPower(combat.player(), target, power);
    }

    public void applyToSelf(Power power) {
        combat.applyPower(combat.player(), combat.player(), power);
    }

    /** Applies a fresh power to every living enemy; {@code factory} is called once per enemy. */
    public void applyToAll(PowerFactory factory) {
        for (Enemy e : combat.activeEnemies()) {
            combat.applyPower(combat.player(), e, factory.create());
        }
    }

    /** Makes a new power instance per enemy for {@link #applyToAll}. */
    @FunctionalInterface
    public interface PowerFactory {
        Power create();
    }

    // ----- Cards -----

    /** Creates {@code count} new cards by id in the hand. */
    public void addToHand(String cardId, int count, boolean upgraded) {
        for (int i = 0; i < count; i++) {
            combat.createInHand(cardId, upgraded);
        }
    }

    /** Asks the player to pick cards from the hand; see {@link Combat#chooseFromHand}. */
    public void chooseFromHand(String prompt, int count, boolean anyNumber, Consumer<List<Card>> then) {
        combat.chooseFromHand(prompt, count, anyNumber, then);
    }

    /** "Discard N cards." */
    public void discardChoice(int count, Consumer<List<Card>> then) {
        chooseFromHand("Discard " + Powers.plural(count, "card") + ".", count, false, chosen -> {
            for (Card c : chosen) {
                combat.discard(c);
            }
            if (then != null) {
                then.accept(chosen);
            }
        });
    }

    /** "Exhaust N cards." */
    public void exhaustChoice(int count, boolean anyNumber, Consumer<List<Card>> then) {
        chooseFromHand("Exhaust " + (anyNumber ? "any number of cards." : Powers.plural(count, "card") + "."),
                count, anyNumber, chosen -> {
                    for (Card c : chosen) {
                        combat.exhaust(c);
                    }
                    if (then != null) {
                        then.accept(chosen);
                    }
                });
    }

    /** Overrides where this card goes after resolving (see the {@code TO_} constants). */
    public void sendTo(String destination) {
        this.destination = destination;
    }

    String destination() {
        return destination;
    }
}
