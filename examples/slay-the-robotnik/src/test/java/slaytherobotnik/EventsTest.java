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
    void slotMachineWaitsForItsReelsThenPaysTheFaceShown() {
        Catalog catalog = Content.build();
        java.util.Map<String, String> names = java.util.Map.of("1", "SONIC!", "2", "TAILS!", "3", "KNUCKLES!",
                "4", "ROBOTNIK!", "5", "RING!", "6", "BAR!");
        Set<String> seen = new TreeSet<>();
        for (int seed = 0; seed < 40; seed++) {
            Run run = Run.start(catalog, HEROES[seed % 3], 5000L + seed);
            run.state().setPosition(1, run.state().floor(), run.state().actFloor(), run.state().nodeX());
            run.enterEvent("event:slot_machine");
            EventRoom room = (EventRoom) run.room();
            assertTrue(room.choose(0), "pull the lever");
            assertTrue(room.awaitingIllustration(), "the event waits for the reels");
            assertEquals(List.of(), room.options(), "nothing to pick while they spin");
            String face = room.illustration();
            seen.add(face);
            room.illustrationShown();
            assertFalse(room.awaitingIllustration());
            if (face.equals("3")) {
                // Knuckles punches a card out of the deck: the removal comes first.
                assertTrue(run.deckChoice() != null, "seed " + seed + ": Knuckles asks for a card");
            } else {
                assertTrue(room.text().startsWith(names.get(face)), "seed " + seed + " face " + face + ": "
                        + room.text());
            }
        }
        assertEquals(Set.of("1", "2", "3", "4", "5", "6"), seen, "every outcome has its own face");
    }

    @Test
    void emeraldAltarGivesTheIdolAndAToll() {
        Catalog catalog = Content.build();
        Run run = Run.start(catalog, Characters.SONIC, 7L);
        int maxHp = run.state().maxHp();
        run.enterEvent("event:emerald_altar");
        EventRoom event = (EventRoom) run.room();
        assertTrue(event.choose(0));
        assertIllustrating(event, "take");
        assertFalse(run.state().hasRelic(Relics.EMERALD_IDOL), "the idol is taken once the picture shows it");
        event.illustrationShown();
        assertTrue(run.state().hasRelic(Relics.EMERALD_IDOL));
        assertEquals(List.of("[Outrun]", "[Smash]", "[Hide]"), labels(event));
        int loss = maxHp * 8 / 100;
        assertTrue(event.choose(2)); // Hide: lose 8% Max HP.
        assertIllustrating(event, "hide:" + loss);
        assertEquals(maxHp, run.state().maxHp());
        event.illustrationShown();
        assertEquals(maxHp - loss, run.state().maxHp());
        assertTrue(event.text().startsWith("The boulder scrapes past"), event.text());
    }

    @Test
    void emeraldAltarShowsTheBoulderBeforeEachEscape() {
        Catalog catalog = Content.build();
        String[] details = {"outrun", "smash:", "hide:"};
        for (int choice = 0; choice < 3; choice++) {
            Run run = Run.start(catalog, Characters.KNUCKLES, 70L + choice);
            run.enterEvent("event:emerald_altar");
            EventRoom event = (EventRoom) run.room();
            event.choose(0);
            event.illustrationShown();
            int hp = run.state().hp();
            int deck = run.state().deck().size();
            assertTrue(event.choose(choice));
            assertTrue(event.awaitingIllustration());
            assertTrue(event.illustration().startsWith(details[choice]), event.illustration());
            String detail = event.illustration();
            assertEquals(List.of(), event.options());
            assertEquals(hp, run.state().hp(), "nothing happens before the picture shows it");
            assertEquals(deck, run.state().deck().size());
            event.illustrationShown();
            assertEquals(List.of("[Leave]"), labels(event));
            if (choice == 0) {
                assertEquals(deck + 1, run.state().deck().size(), "outrunning it sprains an ankle");
            } else if (choice == 1) {
                int damage = run.state().maxHp() / 4;
                assertEquals("smash:" + damage, detail, "the picture shows the damage taken");
                assertEquals(hp - damage, run.state().hp());
            }
        }
    }

    /** The event waits on its picture showing {@code detail}, with nothing to pick meanwhile. */
    static void assertIllustrating(EventRoom event, String detail) {
        assertTrue(event.awaitingIllustration(), "waiting for the picture to show " + detail);
        assertEquals(detail, event.illustration());
        assertEquals(List.of(), event.options(), "no options while the picture plays");
    }

    static List<String> labels(EventRoom event) {
        List<String> labels = new ArrayList<>();
        event.options().forEach(o -> labels.add(o.label()));
        return labels;
    }
}
