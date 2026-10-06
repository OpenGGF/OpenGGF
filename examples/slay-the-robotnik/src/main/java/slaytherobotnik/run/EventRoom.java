package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.EventContext;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.EventOption;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;

/**
 * A "?" room event in progress. The scene shows {@link #text()} and {@link #options()} and
 * calls {@link #choose}; the event script (see {@link EventDef}) reacts through the
 * {@link EventContext} methods implemented here.
 */
public final class EventRoom implements Room, EventContext {
    private final Run run;
    private final EventDef def;
    private String text = "";
    private List<EventOption> options = List.of();
    private final List<Reward> pendingRewards = new ArrayList<>();
    private String banner;
    private boolean finished;
    private String illustration;
    private Runnable afterIllustration;

    EventRoom(Run run, EventDef def) {
        this.run = run;
        this.def = def;
    }

    void begin() {
        def.script().start(this);
    }

    public EventDef def() { return def; }
    public String text() { return text; }
    public List<EventOption> options() { return options; }
    /** A transient message ("Obtained Speed Shoes!"); the scene clears it after showing. */
    public String banner() { return banner; }
    public void clearBanner() { banner = null; }
    public boolean finished() { return finished; }
    /** What the event's picture is showing ({@link #illustrate}), or null. */
    public String illustration() { return illustration; }
    /** True while the event waits for its picture to show the illustration. */
    public boolean awaitingIllustration() { return afterIllustration != null; }

    /** The picture has shown the illustration: the event carries on. */
    public void illustrationShown() {
        Runnable then = afterIllustration;
        afterIllustration = null;
        if (then != null) {
            then.run();
        }
    }

    /** Picks an option on the current page. */
    public boolean choose(int index) {
        if (finished || index < 0 || index >= options.size() || !options.get(index).enabled()
                || run.deckChoice() != null || awaitingIllustration()) {
            return false;
        }
        options.get(index).action().run();
        return true;
    }

    List<Reward> pendingRewards() {
        return pendingRewards;
    }

    // ----- EventContext -----

    @Override
    public RunState run() {
        return run.state();
    }

    @Override
    public Rng rng() {
        return run.state().rngs().stream(RunRngs.EVENTS);
    }

    @Override
    public void illustrate(String detail, Runnable then) {
        illustration = detail;
        options = List.of();
        afterIllustration = then;
    }

    @Override
    public void page(String text, EventOption... options) {
        this.text = text;
        this.options = List.of(options);
    }

    @Override
    public EventOption leave() {
        return EventOption.of("[Leave]", "", this::finish);
    }

    @Override
    public void finish() {
        if (!finished) {
            finished = true;
            run.eventFinished(this);
        }
    }

    @Override
    public void chooseCards(String prompt, List<Card> options, int min, int max, Consumer<List<Card>> then) {
        run.openDeckChoice(new DeckChoice(prompt, options, min, max, DeckChoice.PICK, then));
    }

    /** Opens the deck grid in a specific preview mode (remove / upgrade / transform). */
    public void chooseFromDeck(String prompt, List<Card> options, int count, String mode, Consumer<List<Card>> then) {
        run.openDeckChoice(new DeckChoice(prompt, options, count, count, mode, then));
    }

    @Override
    public void addReward(Reward reward) {
        pendingRewards.add(reward);
    }

    @Override
    public void fight(String encounterId, Runnable then) {
        finished = true;
        run.startEventFight(this, encounterId, then);
    }

    @Override
    public String obtainRandomRelic() {
        RunState state = run.state();
        String id = state.takeRelicFromPool(state.rollRelicTier(rng()));
        if (id == null) {
            return null;
        }
        state.obtainRelic(id);
        String name = state.relic(id).name();
        notify("Obtained " + name + "!");
        return name;
    }

    @Override
    public void obtainRelic(String id) {
        RunState state = run.state();
        state.obtainRelic(id);
        notify("Obtained " + state.relic(id).name() + "!");
    }

    @Override
    public void obtainCard(String cardId, boolean upgraded) {
        CardDef def = run.state().catalog().card(cardId);
        run.state().addCard(def, upgraded);
        notify("Obtained " + def.name() + (upgraded ? "+" : "") + "!");
    }

    @Override
    public CardDef randomCard(String rarity) {
        RunState state = run.state();
        RewardGenerator gen = new RewardGenerator(state);
        String r = rarity != null ? rarity : gen.rollRarity(slaytherobotnik.core.RoomType.MONSTER, rng());
        return gen.randomCardOf(state.character().color(), r, List.of(), rng());
    }

    @Override
    public void notify(String text) {
        banner = text;
    }

    @Override
    public void removeCards(int count, Runnable then) {
        RunState state = run.state();
        deckChoice("Choose " + plural(count) + " to remove.", state.removableCards(), count, DeckChoice.REMOVE,
                chosen -> chosen.forEach(state::removeCard), then);
    }

    @Override
    public void upgradeCards(int count, Runnable then) {
        RunState state = run.state();
        deckChoice("Choose " + plural(count) + " to upgrade.", state.upgradableCards(), count, DeckChoice.UPGRADE,
                chosen -> chosen.forEach(state::upgradeCard), then);
    }

    @Override
    public void transformCards(int count, Runnable then) {
        RunState state = run.state();
        deckChoice("Choose " + plural(count) + " to transform.", state.removableCards(), count, DeckChoice.TRANSFORM,
                chosen -> {
                    RewardGenerator gen = new RewardGenerator(state);
                    for (Card card : chosen) {
                        state.removeCard(card);
                        state.addCard(new Card(gen.transformTarget(card, state.rngs().stream(RunRngs.CARDS))));
                    }
                }, then);
    }

    @Override
    public void duplicateCard(Runnable then) {
        RunState state = run.state();
        deckChoice("Choose a card to copy.", state.deck(), 1, DeckChoice.PICK,
                chosen -> chosen.forEach(card -> state.addCard(card.duplicate())), then);
    }

    @Override
    public String obtainRandomPotion() {
        RunState state = run.state();
        PotionDef potion = new RewardGenerator(state).randomPotion();
        if (potion == null || !state.addPotion(potion)) {
            return null;
        }
        notify("Obtained " + potion.name() + "!");
        return potion.name();
    }

    @Override
    public String obtainRelicOfTier(String tier) {
        RunState state = run.state();
        String id = state.takeRelicFromPool(tier);
        if (id == null) {
            return null;
        }
        state.obtainRelic(id);
        String name = state.relic(id).name();
        notify("Obtained " + name + "!");
        return name;
    }

    /** Opens a deck grid for {@code count} cards (fewer if the deck is short); skips it when there is nothing to pick. */
    private void deckChoice(String prompt, List<Card> options, int count, String mode, Consumer<List<Card>> apply,
            Runnable then) {
        int n = Math.min(count, options.size());
        if (n == 0) {
            then.run();
            return;
        }
        run.openDeckChoice(new DeckChoice(prompt, options, n, n, mode, chosen -> {
            apply.accept(chosen);
            then.run();
        }));
    }

    private static String plural(int count) {
        return count == 1 ? "a card" : count + " cards";
    }
}
