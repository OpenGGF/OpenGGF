package slaytherobotnik.core;

/**
 * Hooks into combat, shared by {@link Power}s (buffs and debuffs on a creature) and
 * {@link Relic}s (the player's run-long items). Every method has a no-op default, so an
 * implementation overrides only what it reacts to.
 *
 * <p>"Owner" means the creature holding the power, or the player for relics. Turn hooks
 * fire for the owner's own turn: an enemy's {@code atTurnStart} runs at the start of the
 * enemies' turn.
 *
 * <p>Modifier hooks run while damage or Block is being calculated (including for the
 * numbers previewed on cards and intents), so they must not change any state.
 */
public interface CombatListener {
    default void atBattleStart(Combat c) { }
    /** Start of the owner's turn, before the player draws. */
    default void atTurnStart(Combat c) { }
    /** Start of the player's turn, after drawing (relics and player powers only). */
    default void atTurnStartPostDraw(Combat c) { }
    /** End of the owner's turn, before the hand is discarded. */
    default void atTurnEnd(Combat c) { }
    /** After every enemy has acted. Turn-based debuffs tick down here. */
    default void atRoundEnd(Combat c) { }

    /** Return true to keep the owner's Block when its turn starts (Barricade, Afterimage). */
    default boolean retainsBlock(Combat c) { return false; }

    /** A card is about to resolve (its Energy is already spent). */
    default void onCardPlayed(Combat c, Card card) { }
    /** Enemy powers only: the player played {@code card} (Gremlin Nob's Enrage reacts here). */
    default void onPlayerCardPlayed(Combat c, Card card) { }
    /** A card has resolved and moved to its destination pile. */
    default void afterCardPlayed(Combat c, Card card) { }
    default void onCardDrawn(Combat c, Card card) { }
    default void onCardExhausted(Combat c, Card card) { }
    /** A card effect discarded a card (not the end-of-turn discard). */
    default void onCardDiscarded(Combat c, Card card) { }
    /** A card was created in combat (a token such as Ring Bomb, or a copy). */
    default void onCardCreated(Combat c, Card card) { }
    default void onShuffle(Combat c) { }
    default void onPotionUsed(Combat c, PotionDef potion) { }

    /** Owner is dealing damage; return the modified amount. */
    default float modifyDamageDealt(Combat c, float damage, String type, Creature target) { return damage; }
    /** Owner is receiving damage; return the modified amount. */
    default float modifyDamageTaken(Combat c, float damage, String type, Creature source) { return damage; }
    /** Last step after flooring (for example Intangible capping damage at 1). */
    default int modifyFinalDamageTaken(Combat c, int damage, String type, Creature source) { return damage; }
    /** Owner is gaining Block from a card; return the modified amount. */
    default float modifyBlock(Combat c, float block, Card card) { return block; }
    /** Adjusts the Energy cost of a card in hand. */
    default int modifyCost(Combat c, Card card, int cost) { return cost; }
    /** Return a reason to forbid playing {@code card} (shown to the player), or null. */
    default String vetoCardPlay(Combat c, Card card) { return null; }
    /** Owner is about to lose HP after Block; return the modified loss (Buffer, Tungsten Rod). */
    default int modifyHpLoss(Combat c, int hpLoss, Creature source, String type) { return hpLoss; }
    /** Adjusts the Vulnerable multiplier (normally 1.5) when {@code target} takes an attack. */
    default float modifyVulnerableMultiplier(Combat c, Creature target, float multiplier) { return multiplier; }
    /** Number of times a Combo effect repeats; return the modified count. */
    default int modifyCombo(Combat c, Card card, int count) { return count; }

    /** Owner was hit; {@code hpLost} excludes damage absorbed by Block. */
    default void onAttacked(Combat c, Creature source, int damage, int hpLost, String type) { }
    /** Owner hit {@code target}. */
    default void onAttack(Combat c, Creature target, int damage, int hpLost, String type) { }
    default void onBlockGained(Combat c, int amount) { }
    /** Owner applied {@code power} to {@code target}. */
    default void onPowerApplied(Combat c, Power power, Creature target) { }
    /** Owner lost HP from any source. */
    default void onHpLost(Combat c, int amount) { }
    /** An enemy died (player powers and relics only). */
    default void onEnemyDeath(Combat c, Enemy enemy) { }
    /** The owner died. */
    default void onDeath(Combat c) { }
    /** The combat was won (relics and player powers only). */
    default void onVictory(Combat c) { }
}
