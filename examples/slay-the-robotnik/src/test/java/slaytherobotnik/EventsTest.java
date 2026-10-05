package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Characters;
import slaytherobotnik.content.Content;
import slaytherobotnik.content.Relics;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.RunState;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.MapRoom;
import slaytherobotnik.run.Run;

/** Every event, entered directly and played with random choices, ends cleanly back on the map. */
class EventsTest {
    private static final String[] HEROES = {Characters.SONIC, Characters.TAILS, Characters.KNUCKLES};

    @Test
    void everyEventFinishesWithEveryCharacter() {
        Catalog catalog = Content.build();
        List<String> problems = new ArrayList<>();
        for (EventDef def : catalog.allEvents()) {
            Set<String> labels = new TreeSet<>();
            for (int seed = 0; seed < 30; seed++) {
                Run run = Run.start(catalog, HEROES[seed % 3], 1000L + seed);
                RunState state = run.state();
                int act = def.acts().stream().min(Integer::compare).orElse(1);
                state.setPosition(act, state.floor(), state.actFloor(), state.nodeX());
                state.gainRings(seed % 2 == 0 ? 400 : 0);
                state.obtainRelic("relic:blue_shield");
                state.obtainRelic("relic:spike_ball");
                state.setHp(state.maxHp());
                run.enterEvent(def.id());
                RunBot bot = new RunBot(run, seed);
                bot.strengthBoost = 40;
                // Record the labels on offer before each choice, so the test proves options were reached.
                boolean done = bot.playUntil(2000, r -> {
                    if (r.room() instanceof EventRoom e) {
                        e.options().forEach(o -> labels.add(o.label()));
                    }
                    return r.room() instanceof MapRoom;
                });
                if (!done || !(run.room() instanceof MapRoom) && !run.over()) {
                    problems.add(def.id() + " seed " + seed + " stuck in " + run.room().getClass().getSimpleName());
                }
            }
            assertTrue(labels.size() >= 2, def.id() + " showed only " + labels);
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void thereAreAboutTwentyEventsSpreadOverTheFirstThreeActs() {
        Catalog catalog = Content.build();
        assertTrue(catalog.allEvents().size() >= 20, "events: " + catalog.allEvents().size());
        for (int act = 1; act <= 3; act++) {
            int n = 0;
            for (EventDef def : catalog.allEvents()) {
                if (def.acts().contains(act)) {
                    n++;
                }
            }
            assertTrue(n >= 8, "act " + act + " has only " + n + " events");
        }
    }

    @Test
    void emeraldAltarGivesTheIdolAndAToll() {
        Catalog catalog = Content.build();
        Run run = Run.start(catalog, Characters.SONIC, 7L);
        int maxHp = run.state().maxHp();
        run.enterEvent("event:emerald_altar");
        EventRoom event = (EventRoom) run.room();
        assertTrue(event.choose(0));
        assertTrue(run.state().hasRelic(Relics.EMERALD_IDOL));
        assertTrue(event.choose(2)); // Hide: lose 8% Max HP.
        assertEquals(maxHp - maxHp * 8 / 100, run.state().maxHp());
    }
}
