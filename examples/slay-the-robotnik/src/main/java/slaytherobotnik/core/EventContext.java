package slaytherobotnik.core;

import java.util.List;
import java.util.function.Consumer;

/**
 * What an event script can do. Events are written as small dialogues: show a page of text
 * with options, and each option changes the run and shows the next page or leaves.
 *
 * <pre>{@code
 * ctx -> ctx.page("A Giant Ring hangs in the air, humming with power.",
 *         EventOption.of("[Jump in]", "Lose 8 HP. Obtain a random relic.", () -> {
 *             ctx.run().loseHp(8);
 *             ctx.obtainRandomRelic();
 *             ctx.page("You tumble back out, clutching something shiny.", ctx.leave());
 *         }),
 *         ctx.leave())
 * }</pre>
 */
public interface EventContext {
    RunState run();

    /** The event's random stream. */
    Rng rng();

    /** Replaces the current page. */
    void page(String text, EventOption... options);

    /** A standard "[Leave]" option that ends the event. */
    EventOption leave();

    /** Ends the event (pending rewards are shown first). */
    void finish();

    /** Lets the player pick cards from {@code options} (the deck, or offered cards). */
    void chooseCards(String prompt, List<Card> options, int min, int max, Consumer<List<Card>> then);

    /** Adds rewards shown after the event ends (or after its fight). */
    void addReward(Reward reward);

    /** Starts a fight; {@code then} runs after victory, before rewards. */
    void fight(String encounterId, Runnable then);

    /** Obtains a random relic from the common/uncommon/rare pools and reports its name. */
    String obtainRandomRelic();

    /** Obtains a specific relic. */
    void obtainRelic(String id);

    /** Adds a card to the deck by id. */
    void obtainCard(String cardId, boolean upgraded);

    /** A random reward-eligible card of the player's colour, of {@code rarity} (or any rarity when null). */
    CardDef randomCard(String rarity);

    /** Shows a short banner ("Obtained Speed Shoes!") over the event. */
    void notify(String text);
}
