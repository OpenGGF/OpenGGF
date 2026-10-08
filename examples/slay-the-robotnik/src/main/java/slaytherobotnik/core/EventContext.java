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

    /** Lets the player remove {@code count} cards from the deck, then runs {@code then}. */
    void removeCards(int count, Runnable then);

    /** Lets the player upgrade {@code count} cards, then runs {@code then}. */
    void upgradeCards(int count, Runnable then);

    /** Lets the player transform {@code count} cards into random cards of the same colour. */
    void transformCards(int count, Runnable then);

    /** Lets the player pick a card to copy into the deck. */
    void duplicateCard(Runnable then);

    /** Obtains a random potion when a slot is free; returns its name, or null. */
    String obtainRandomPotion();

    /** Obtains a random relic of {@code tier} ({@link RelicTier}); returns its name, or null. */
    String obtainRelicOfTier(String tier);

    /**
     * Shows {@code detail} in the event's picture (the slot machine's winning reel face, "3"),
     * keeps the current text with no options, and runs {@code then} once the picture has shown
     * it: when the scene's reels stop, or at once where nothing animates it.
     */
    void illustrate(String detail, Runnable then);
}
