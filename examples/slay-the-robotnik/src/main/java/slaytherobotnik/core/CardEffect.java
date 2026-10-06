package slaytherobotnik.core;

/**
 * What a card does when played. The {@link Play} argument carries the combat, the card
 * instance, the chosen target and helpers for the common Slay the Spire verbs.
 */
@FunctionalInterface
public interface CardEffect {
    void play(Play p);
}
