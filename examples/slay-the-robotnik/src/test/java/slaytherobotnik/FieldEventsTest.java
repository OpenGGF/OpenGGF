package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Characters;
import slaytherobotnik.content.CommonCards;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.RunState;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.RewardRoom;
import slaytherobotnik.run.Run;

/**
 * The Giant Ring, Monitor Row, Ring Shrine, Strange Mushrooms, Purifying Waterfall, Special
 * Stage Warp and Chao Cart events show each choice in their picture before its result: the
 * event waits with no options on the detail the picture plays, and the effects land only once
 * the picture has shown it.
 */
class FieldEventsTest {
    private final Catalog catalog = Content.build();

    /** A fresh run in the event's first act with rings to spare, standing in the event. */
    private Run enter(String eventId, String hero, long seed) {
        Run run = Run.start(catalog, hero, seed);
        RunState state = run.state();
        int act = catalog.event(eventId).acts().stream().min(Integer::compare).orElse(1);
        state.setPosition(act, state.floor(), state.actFloor(), state.nodeX());
        state.gainRings(300);
        run.enterEvent(eventId);
        return run;
    }

    private static void assertWaitsOn(EventRoom room, String detail) {
        assertTrue(room.awaitingIllustration(), "the event waits for its picture");
        assertEquals(detail, room.illustration());
        assertEquals(List.of(), room.options(), "nothing to pick while it plays");
    }

    @Test
    void giantRingShowsTheJumpThenCostsHpAndGivesTheRelicItShowed() {
        for (long seed = 0; seed < 12; seed++) {
            EventRoom room = (EventRoom) enter("event:giant_ring", Characters.SONIC, 100 + seed).room();
            RunState state = room.run();
            int hp = state.hp();
            int relics = state.relics().size();
            assertTrue(room.choose(0));
            String detail = room.illustration();
            assertTrue(detail.equals("jump") || detail.startsWith("jump:relic:"), detail);
            assertWaitsOn(room, detail);
            assertEquals(hp, state.hp(), "no HP lost before the picture shows the jump");
            assertEquals(relics, state.relics().size());
            room.illustrationShown();
            assertFalse(room.awaitingIllustration());
            assertEquals(hp - 8, state.hp());
            if (detail.startsWith("jump:")) {
                assertTrue(state.hasRelic(detail.substring(5)), "the relic the picture showed is the one obtained");
            }
            assertTrue(room.text().startsWith("The world spins"), room.text());
        }
    }

    @Test
    void monitorRowBreaksTheChosenMonitorBeforeItsEffect() {
        String[] heroes = {Characters.SONIC, Characters.TAILS, Characters.KNUCKLES};
        for (int option = 0; option < 3; option++) {
            EventRoom room = (EventRoom) enter("event:monitor_row", heroes[option], 7).room();
            RunState state = room.run();
            state.setHp(state.maxHp() / 2);
            int hp = state.hp();
            int maxHp = state.maxHp();
            int deck = state.deck().size();
            int relics = state.relics().size();
            assertTrue(room.choose(option));
            assertWaitsOn(room, Integer.toString(option));
            assertEquals(hp, state.hp());
            assertEquals(maxHp, state.maxHp());
            assertEquals(deck, state.deck().size());
            room.illustrationShown();
            switch (option) {
                case 0 -> assertEquals(hp + maxHp / 3, state.hp(), "the Super Ring heals");
                case 1 -> assertEquals(maxHp + 5, state.maxHp(), "the 1-Up adds Max HP");
                default -> {
                    assertEquals(relics + 1, state.relics().size(), "Robotnik's monitor gives a relic");
                    assertTrue(state.deck().stream().anyMatch(c -> c.id().equals(CommonCards.ROBOTNIKS_LAUGH)));
                }
            }
        }
    }

    @Test
    void ringShrineDrawsTheRingsInBeforeCountingThem() {
        EventRoom pray = (EventRoom) enter("event:ring_shrine", Characters.TAILS, 3).room();
        int rings = pray.run().rings();
        assertTrue(pray.choose(0));
        assertWaitsOn(pray, "pray");
        assertEquals(rings, pray.run().rings());
        pray.illustrationShown();
        assertEquals(rings + 100, pray.run().rings());

        EventRoom desecrate = (EventRoom) enter("event:ring_shrine", Characters.KNUCKLES, 3).room();
        rings = desecrate.run().rings();
        assertTrue(desecrate.choose(1));
        assertWaitsOn(desecrate, "desecrate");
        assertEquals(rings, desecrate.run().rings());
        desecrate.illustrationShown();
        assertEquals(rings + 275, desecrate.run().rings());
        assertTrue(desecrate.run().deck().stream().anyMatch(c -> c.id().equals(CommonCards.LOST_RINGS)));
    }

    @Test
    void strangeMushroomsStompWakesTheBadniksThenFightsAndEatingHealsAfterTheBite() {
        Run stompRun = enter("event:strange_mushrooms", Characters.SONIC, 11);
        EventRoom stomp = (EventRoom) stompRun.room();
        assertTrue(stomp.choose(0));
        assertWaitsOn(stomp, "stomp");
        assertTrue(stompRun.room() == stomp, "no fight until the badniks have burst out");
        stomp.illustrationShown();
        assertTrue(stompRun.room() instanceof CombatRoom, "the stomp starts the fight");

        EventRoom eat = (EventRoom) enter("event:strange_mushrooms", Characters.TAILS, 11).room();
        RunState state = eat.run();
        state.setHp(10);
        int deck = state.deck().size();
        assertTrue(eat.choose(1));
        assertWaitsOn(eat, "eat");
        assertEquals(10, state.hp());
        eat.illustrationShown();
        assertEquals(10 + state.maxHp() / 4, state.hp());
        assertEquals(deck + 1, state.deck().size());
        assertTrue(state.deck().stream().anyMatch(c -> c.id().equals(CommonCards.TANGLED)));
    }

    @Test
    void waterfallCarriesOffTheCardThatWasRemoved() {
        Run run = enter("event:waterfall", Characters.SONIC, 21);
        EventRoom room = (EventRoom) run.room();
        assertTrue(room.choose(0));
        assertNotNull(run.deckChoice(), "the card is picked first");
        Card picked = run.deckChoice().options().get(2);
        assertTrue(run.resolveDeckChoice(List.of(picked)));
        assertWaitsOn(room, "wash:" + picked.id() + (picked.upgraded() ? "+" : ""));
        assertFalse(run.state().deck().contains(picked));
        room.illustrationShown();
        assertTrue(room.text().startsWith("The water carries it off"), room.text());
    }

    @Test
    void specialStageShowsTheOldCardGoingInAndTheNewOneComingOut() {
        Run run = enter("event:special_stage", Characters.KNUCKLES, 31);
        EventRoom room = (EventRoom) run.room();
        List<Card> before = new ArrayList<>(run.state().deck());
        assertTrue(room.choose(0));
        Card picked = run.deckChoice().options().get(0);
        assertTrue(run.resolveDeckChoice(List.of(picked)));
        String detail = room.illustration();
        assertTrue(detail.startsWith("enter:" + picked.id() + (picked.upgraded() ? "+" : "") + ">"), detail);
        assertWaitsOn(room, detail);
        Card added = null;
        for (Card card : run.state().deck()) {
            if (!before.contains(card)) {
                added = card;
            }
        }
        assertNotNull(added);
        assertTrue(detail.endsWith(">" + added.id() + (added.upgraded() ? "+" : "")), detail);
        room.illustrationShown();
        assertTrue(room.text().startsWith("Blue sphere"), room.text());
    }

    @Test
    void chaoCartTakesTheRingsAndHandsOverTheMonitorsItShowed() {
        for (int deal = 0; deal < 3; deal++) {
            Run run = enter("event:chao_cart", Characters.TAILS, 41 + deal);
            EventRoom room = (EventRoom) run.room();
            int rings = run.state().rings();
            assertTrue(room.choose(deal));
            String detail = room.illustration();
            assertTrue(detail.startsWith("buy:"), detail);
            String[] ids = detail.substring(4).split(",");
            assertEquals(deal + 1, ids.length, detail);
            assertWaitsOn(room, detail);
            assertEquals(rings, run.state().rings(), "paid only once the picture has shown it");
            room.illustrationShown();
            assertEquals(rings - (20 + deal * 10), run.state().rings());
            assertTrue(room.choose(0), "leave");
            RewardRoom rewards = (RewardRoom) run.room();
            List<String> potions = new ArrayList<>();
            for (Reward reward : rewards.rewards()) {
                if (reward instanceof Reward.Potion potion) {
                    potions.add(potion.potion().id());
                }
            }
            assertEquals(List.of(ids), potions, "the potions handed over are the monitors shown");
        }
    }

}
