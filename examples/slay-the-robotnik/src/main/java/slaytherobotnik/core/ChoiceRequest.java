package slaytherobotnik.core;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A question the rules ask the player in the middle of a combat: "Exhaust a card",
 * "Choose 1 of these 3 cards to add to your hand". The combat pauses (no further card
 * plays or turn end) until the screen calls {@link Combat#resolveChoice}.
 *
 * <p>The options are computed when the request reaches the front of the queue, not when
 * it is created, so two requests made by one card each see an up-to-date hand. If there are
 * no more options than the minimum, the request resolves itself with all of them (exhausting
 * "a card" from a one-card hand needs no prompt).
 *
 * @param prompt   text shown above the choice
 * @param options  cards that may be picked (from the hand, a pile, or newly created)
 * @param min      minimum number of cards to pick
 * @param max      maximum number of cards to pick
 * @param fromHand true when the options are cards in hand (the screen raises them in place)
 * @param onChosen what to do with the picked cards
 */
public record ChoiceRequest(
        String prompt,
        Supplier<List<Card>> options,
        int min,
        int max,
        boolean fromHand,
        Consumer<List<Card>> onChosen) {
}
