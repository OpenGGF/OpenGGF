package slaytherobotnik.core;

/**
 * Something visible that happened in a combat. The rules apply every change immediately;
 * the combat screen replays these events one after another with animations, so the rules
 * never wait for the presentation and tests can ignore events entirely.
 */
public sealed interface CombatEvent {
    record TurnStarted(int turn, boolean playerTurn) implements CombatEvent { }

    record CardDrawn(Card card) implements CombatEvent { }

    record CardPlayed(Card card, Enemy target) implements CombatEvent { }

    /** {@code manual} is true for discards caused by card effects. */
    record CardDiscarded(Card card, boolean manual) implements CombatEvent { }

    record CardExhausted(Card card) implements CombatEvent { }

    record CardRetained(Card card) implements CombatEvent { }

    /** {@code pile} is one of "hand", "draw", "discard". */
    record CardCreated(Card card, String pile) implements CombatEvent { }

    record CardUpgraded(Card card) implements CombatEvent { }

    /** The discard pile was shuffled into the draw pile. */
    record Shuffled(int cards) implements CombatEvent { }

    /** {@code amount} before Block; {@code hpLost} after. {@code source} is null for HP loss from nowhere. */
    record Damaged(Creature source, Creature target, int amount, int blocked, int hpLost, String type)
            implements CombatEvent { }

    record BlockGained(Creature target, int amount) implements CombatEvent { }

    /** {@code delta} is how much the power's amount changed; {@code removed} when it disappeared. */
    record PowerChanged(Creature target, String powerId, int delta, boolean removed) implements CombatEvent { }

    /** Artifact negated a debuff. */
    record DebuffNegated(Creature target, String powerId) implements CombatEvent { }

    record Healed(Creature target, int amount) implements CombatEvent { }

    record MaxHpChanged(Creature target, int delta) implements CombatEvent { }

    record EnergyChanged(int energy) implements CombatEvent { }

    /** An enemy starts carrying out {@code move}: the screen plays its attack or buff animation. */
    record EnemyMove(Enemy enemy, Move move) implements CombatEvent { }

    record EnemySpawned(Enemy enemy) implements CombatEvent { }

    record EnemyDied(Enemy enemy) implements CombatEvent { }

    record EnemyEscaped(Enemy enemy) implements CombatEvent { }

    record RelicTriggered(Relic relic) implements CombatEvent { }

    record PotionUsed(PotionDef potion) implements CombatEvent { }

    record RingsGained(int amount) implements CombatEvent { }

    /** A short line of text shown over the battlefield ("Not enough Energy"). */
    record Message(String text) implements CombatEvent { }

    /** Free-form cue for content-specific effects (a boss's special animation); {@code key} is up to the content. */
    record Cue(Creature source, String key) implements CombatEvent { }

    record Victory() implements CombatEvent { }

    record Defeat() implements CombatEvent { }
}
