package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Characters;
import slaytherobotnik.content.CommonCards;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.RunState;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.Run;

/**
 * The Wandering Medic, Abandoned Workbench, Mirror Monitor, Archive Terminal, Robotnik's Lab,
 * Hyper Remote and Robotnik's Offer show each choice in their picture before its result: the
 * room waits on the detail its picture plays, with nothing to pick, and the effects and result
 * page follow only once the picture is done.
 */
class IllustratedChoicesTest {
    private final Catalog catalog = Content.build();

    private Run enter(String eventId, String hero, long seed) {
        Run run = Run.start(catalog, hero, seed);
        EventDef def = catalog.event(eventId);
        RunState s = run.state();
        s.setPosition(def.acts().stream().min(Integer::compare).orElse(1), s.floor(), s.actFloor(), s.nodeX());
        s.gainRings(400);
        run.enterEvent(eventId);
        return run;
    }

    private static EventRoom room(Run run) {
        return (EventRoom) run.room();
    }

    /** The room waits on {@code detail}, offers nothing and refuses picks until the picture is done. */
    private static void awaits(EventRoom room, String detail) {
        assertTrue(room.awaitingIllustration(), "waits for its picture");
        assertEquals(detail, room.illustration());
        assertEquals(List.of(), room.options(), "nothing to pick while it plays");
        assertFalse(room.choose(0));
    }

    private static String named(Card card) {
        return card.id() + (card.upgraded() ? "+" : "");
    }

    @Test
    void medicHealsAfterTheStarpostSpins() {
        Run run = enter("event:starpost_medic", Characters.SONIC, 11L);
        RunState s = run.state();
        s.setHp(10);
        int rings = s.rings();
        assertTrue(room(run).choose(0), "[Heal]");
        awaits(room(run), "heal");
        assertEquals(10, s.hp(), "not healed yet");
        assertEquals(rings, s.rings(), "not paid yet");
        room(run).illustrationShown();
        assertEquals(10 + s.maxHp() / 4, s.hp());
        assertEquals(rings - 35, s.rings());
        assertEquals("You feel much better.", room(run).text());
    }

    @Test
    void medicLiftsTheChosenCardAwayBeforeItLeavesTheDeck() {
        Run run = enter("event:starpost_medic", Characters.KNUCKLES, 12L);
        RunState s = run.state();
        int rings = s.rings();
        assertTrue(room(run).choose(1), "[Purify]");
        Card card = run.deckChoice().options().get(0);
        assertTrue(run.resolveDeckChoice(List.of(card)));
        awaits(room(run), "purify:" + named(card));
        assertTrue(s.deck().contains(card), "still in the deck while it rises");
        room(run).illustrationShown();
        assertFalse(s.deck().contains(card));
        assertEquals(rings - 50, s.rings());
        assertTrue(room(run).text().contains("lighter"));
    }

    @Test
    void workbenchShowsTheUpgradedCard() {
        Run run = enter("event:workbench", Characters.TAILS, 13L);
        assertTrue(room(run).choose(0), "[Tinker]");
        Card card = run.deckChoice().options().get(0);
        assertTrue(run.resolveDeckChoice(List.of(card)));
        assertTrue(card.upgraded());
        awaits(room(run), "tinker:" + card.id() + "+");
        room(run).illustrationShown();
        assertEquals("Good as new. Better, even.", room(run).text());
    }

    @Test
    void mirrorMonitorShowsTheCopy() {
        Run run = enter("event:mirror_monitor", Characters.SONIC, 14L);
        int size = run.state().deck().size();
        assertTrue(room(run).choose(0), "[Copy]");
        Card card = run.deckChoice().options().get(3);
        assertTrue(run.resolveDeckChoice(List.of(card)));
        awaits(room(run), "copy:" + named(card));
        assertEquals(size + 1, run.state().deck().size());
        room(run).illustrationShown();
        assertTrue(room(run).text().startsWith("The screen fizzles out."));
    }

    @Test
    void terminalDownloadsTheFileBeforeItJoinsTheDeck() {
        Run run = enter("event:archive_terminal", Characters.TAILS, 15L);
        assertTrue(room(run).choose(0), "[Read]");
        Card card = run.deckChoice().options().get(0);
        int size = run.state().deck().size();
        assertTrue(run.resolveDeckChoice(List.of(card)));
        awaits(room(run), "read:" + named(card));
        assertEquals(size, run.state().deck().size(), "still downloading");
        room(run).illustrationShown();
        assertEquals(size + 1, run.state().deck().size());
        assertEquals(card.id(), run.state().deck().get(size).id());
    }

    @Test
    void terminalRestsBeforeItHeals() {
        Run run = enter("event:archive_terminal", Characters.SONIC, 16L);
        RunState s = run.state();
        s.setHp(5);
        assertTrue(room(run).choose(1), "[Rest]");
        awaits(room(run), "rest");
        assertEquals(5, s.hp());
        room(run).illustrationShown();
        assertEquals(5 + s.maxHp() / 3, s.hp());
        assertTrue(room(run).text().contains("sleep"));
    }

    @Test
    void labDropsTheThreePotionsItFinds() {
        for (long seed = 20; seed < 30; seed++) {
            Run run = enter("event:robotnik_lab", Characters.KNUCKLES, seed);
            assertTrue(room(run).choose(0), "[Search]");
            String detail = room(run).illustration();
            assertTrue(detail.startsWith("search:"), detail);
            String[] ids = detail.substring("search:".length()).split(",");
            assertEquals(3, ids.length, detail);
            for (String id : ids) {
                assertNotNull(catalog.potion(id));
            }
            awaits(room(run), detail);
            room(run).illustrationShown();
            assertTrue(room(run).text().startsWith("You fill your pockets"));
            assertTrue(room(run).choose(0), "[Leave]");
            assertEquals(3, ((slaytherobotnik.run.RewardRoom) run.room()).rewards().size(),
                    "the three potions shown are the three found");
        }
    }

    @Test
    void hyperRemoteShowsEachButtonBeforeItsResult() {
        // [Rematch]: the boss shown on the screen is the one fought.
        Run run = enter("event:hyper_remote", Characters.SONIC, 31L);
        assertTrue(room(run).choose(0));
        String detail = room(run).illustration();
        String boss = detail.substring("rematch:".length());
        assertEquals(EncounterDef.BOSS, catalog.encounter(boss).pool());
        awaits(room(run), detail);
        room(run).illustrationShown();
        assertTrue(run.room() instanceof CombatRoom, "the fight starts once the screen has shown it");

        // [Jackpot]: rings rain first, then they are counted.
        run = enter("event:hyper_remote", Characters.TAILS, 32L);
        int rings = run.state().rings();
        assertTrue(room(run).choose(1));
        awaits(room(run), "jackpot");
        assertEquals(rings, run.state().rings());
        room(run).illustrationShown();
        assertEquals(rings + 999, run.state().rings());
        assertEquals(2, run.state().deck().stream().filter(c -> c.id().equals(CommonCards.LOST_RINGS)).count());

        // [Overclock]: the lightning runs through the deck, then every card is upgraded.
        run = enter("event:hyper_remote", Characters.KNUCKLES, 33L);
        assertTrue(room(run).choose(2));
        awaits(room(run), "overclock");
        assertFalse(run.state().upgradableCards().isEmpty());
        room(run).illustrationShown();
        assertEquals(List.of(), run.state().upgradableCards());
        assertTrue(run.state().deck().stream().anyMatch(c -> c.id().equals(CommonCards.ROBOTNIKS_LAUGH)));
    }

    @Test
    void robotniksOfferShowsTheArmsOrTheScreenGoingDark() {
        Run run = enter("event:robotnik_offer", Characters.SONIC, 41L);
        int maxHp = run.state().maxHp();
        assertTrue(room(run).choose(0), "[Accept]");
        awaits(room(run), "accept");
        assertEquals(maxHp, run.state().maxHp(), "the arms arrive first");
        room(run).illustrationShown();
        assertTrue(run.state().maxHp() < maxHp);
        assertEquals(5, run.state().deck().stream().filter(c -> c.id().equals(CommonCards.ROBO_ARM)).count());

        run = enter("event:robotnik_offer", Characters.TAILS, 42L);
        assertTrue(room(run).choose(1), "[Refuse]");
        awaits(room(run), "refuse");
        room(run).illustrationShown();
        assertTrue(room(run).text().endsWith("clicks off."));
        assertEquals("[Leave]", room(run).options().get(0).label());
    }
}
