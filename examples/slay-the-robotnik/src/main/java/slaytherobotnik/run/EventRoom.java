package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.EventContext;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.EventOption;
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

    /** Picks an option on the current page. */
    public boolean choose(int index) {
        if (finished || index < 0 || index >= options.size() || !options.get(index).enabled()
                || run.deckChoice() != null) {
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
}
