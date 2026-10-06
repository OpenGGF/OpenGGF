package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static slaytherobotnik.EventsTest.assertIllustrating;
import static slaytherobotnik.EventsTest.labels;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Characters;
import slaytherobotnik.content.CommonCards;
import slaytherobotnik.content.Content;
import slaytherobotnik.content.Relics;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.run.DeckChoice;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.Run;

/**
 * The mural, scrapyard, tablets, Collector, campfire and pipe show each choice in their picture
 * before its result: the event waits on the picture with the agreed detail and nothing to pick,
 * and the effects and result page come once it has been shown.
 */
class EventsShowTheirChoicesTest {
    private final Catalog catalog = Content.build();

    private Run enter(String hero, long seed, String event) {
        Run run = Run.start(catalog, hero, seed);
        run.state().setPosition(catalog.event(event).acts().stream().min(Integer::compare).orElse(1),
                run.state().floor(), run.state().actFloor(), run.state().nodeX());
        run.enterEvent(event);
        return run;
    }

    @Test
    void muralSocketsShowTheirWordAfterTheCardPick() {
        String[] details = {"forget", "change", "grow"};
        String[] results = {"The socket dims", "The socket flickers", "The socket blazes gold"};
        for (int option = 0; option < 3; option++) {
            Run run = enter(Characters.SONIC, 11, "event:hidden_palace_mural");
            EventRoom room = (EventRoom) run.room();
            assertTrue(room.choose(option));
            DeckChoice choice = run.deckChoice();
            assertNotNull(choice, "the card pick comes first");
            Card picked = choice.options().get(0);
            int deck = run.state().deck().size();
            assertTrue(run.resolveDeckChoice(List.of(picked)));
            assertIllustrating(room, details[option]);
            if (option == 0) {
                assertEquals(deck - 1, run.state().deck().size(), "the card is gone as the socket dims");
            }
            room.illustrationShown();
            assertTrue(room.text().startsWith(results[option]), room.text());
            assertEquals(List.of("[Leave]"), labels(room));
        }
    }

    @Test
    void scrapyardShowsEveryFindBeforeItCounts() {
        Set<String> kinds = new TreeSet<>();
        for (long seed = 1; seed <= 40 && kinds.size() < 4; seed++) {
            Run run = enter(Characters.TAILS, seed, "event:badnik_scrapyard");
            EventRoom room = (EventRoom) run.room();
            while (run.room() == room && !room.finished() && labels(room).contains("[Search]")) {
                int rings = run.state().rings();
                int relics = run.state().relics().size();
                assertTrue(room.choose(0));
                assertTrue(room.awaitingIllustration());
                String detail = room.illustration();
                assertTrue(detail.matches("rings:\\d+|relic:relic:\\w+|bolts|elite:\\w+:\\w+"), detail);
                assertEquals(List.of(), room.options());
                assertEquals(rings, run.state().rings(), "nothing found before the picture shows it");
                assertEquals(relics, run.state().relics().size());
                String kind = detail.substring(0, detail.indexOf(':') < 0 ? detail.length() : detail.indexOf(':'));
                kinds.add(kind);
                room.illustrationShown();
                switch (kind) {
                    case "rings" -> assertEquals(rings + Integer.parseInt(detail.substring(6)), run.state().rings());
                    case "relic" -> assertTrue(run.state().hasRelic(detail.substring(6)), detail);
                    case "elite" -> {
                        assertEquals(List.of("[Fight]"), labels(room));
                        assertTrue(room.text().contains("rises from the scrap"), room.text());
                    }
                    default -> assertTrue(room.text().startsWith("Nothing but bolts"), room.text());
                }
                if (kind.equals("elite")) {
                    break;
                }
            }
        }
        assertEquals(Set.of("bolts", "elite", "relic", "rings"), kinds, "every find has its own picture");
    }

    @Test
    void tabletsLightOneByOneAndTakeTheirTollAfterward() {
        for (int last = 0; last < 2; last++) {
            Run run = enter(Characters.KNUCKLES, 5, "event:echidna_tablets");
            EventRoom room = (EventRoom) run.room();
            String[] reads = {"read:0:1", "read:1:2", "read:2:3"};
            int[] tolls = {1, 2, 3};
            for (int i = 0; i < 3; i++) {
                int hp = run.state().hp();
                assertTrue(room.choose(0));
                assertIllustrating(room, reads[i]);
                assertEquals(hp, run.state().hp(), "the tablet lights before it hurts");
                room.illustrationShown();
                assertEquals(hp - tolls[i], run.state().hp());
            }
            assertEquals(List.of("[Take]", "[Stop]"), labels(room));
            int hp = run.state().hp();
            assertTrue(room.choose(last));
            assertIllustrating(room, last == 0 ? "take:10" : "stop:3");
            assertFalse(run.state().hasRelic(Relics.ANCIENT_TABLET));
            room.illustrationShown();
            assertEquals(hp - (last == 0 ? 10 : 3), run.state().hp());
            assertEquals(last == 0, run.state().hasRelic(Relics.ANCIENT_TABLET));
            assertEquals(List.of("[Leave]"), labels(room));
        }
    }

    @Test
    void collectorTakesTheRelicOnlyOnceTheTradeIsShown() {
        Run run = Run.start(catalog, Characters.SONIC, 9);
        run.state().obtainRelic("relic:blue_shield");
        run.state().obtainRelic("relic:spike_ball");
        run.state().setPosition(2, run.state().floor(), run.state().actFloor(), run.state().nodeX());
        run.enterEvent("event:collector");
        EventRoom room = (EventRoom) run.room();
        assertEquals(List.of("[Trade]", "[Trade]", "[Leave]"), labels(room));
        assertTrue(room.choose(0));
        String detail = room.illustration();
        assertTrue(detail.startsWith("trade:relic:"), detail);
        String traded = detail.substring(6);
        assertIllustrating(room, detail);
        assertTrue(run.state().hasRelic(traded), "the relic is handed over in the picture");
        assertFalse(run.state().hasRelic(Relics.COLLECTORS_BADGE));
        room.illustrationShown();
        assertFalse(run.state().hasRelic(traded));
        assertTrue(run.state().hasRelic(Relics.COLLECTORS_BADGE));
        assertEquals(List.of("[Leave]"), labels(room));
    }

    @Test
    void campfireBurnsTheOfferingThenAnswersByRarity() {
        String[] cards = {CommonCards.LOST_RINGS, null, "sonic:air_dash", "sonic:boost", "sonic:super_sonic"};
        String[] rarities = {CardRarity.CURSE, CardRarity.BASIC, CardRarity.COMMON, CardRarity.UNCOMMON,
                CardRarity.RARE};
        for (int i = 0; i < cards.length; i++) {
            Run run = enter(Characters.SONIC, 21, "event:flicky_campfire");
            run.state().setHp(run.state().maxHp() - 30);
            if (cards[i] != null) {
                run.state().addCard(new Card(catalog.card(cards[i])));
            }
            EventRoom room = (EventRoom) run.room();
            assertTrue(room.choose(0));
            DeckChoice choice = run.deckChoice();
            Card offered = null;
            for (Card card : choice.options()) {
                if (card.rarity().equals(rarities[i]) && (cards[i] == null || card.id().equals(cards[i]))) {
                    offered = card;
                }
            }
            assertNotNull(offered, rarities[i]);
            int hp = run.state().hp();
            int maxHp = run.state().maxHp();
            int relics = run.state().relics().size();
            assertTrue(run.resolveDeckChoice(List.of(offered)));
            assertTrue(room.awaitingIllustration());
            String[] detail = room.illustration().split("\\|");
            assertEquals(rarities[i], detail[0]);
            assertEquals(offered.id(), detail[1]);
            assertEquals(List.of(), room.options());
            assertEquals(hp, run.state().hp(), "the fire answers after the card has burned");
            int gain = Integer.parseInt(detail[3]);
            room.illustrationShown();
            switch (rarities[i]) {
                case CardRarity.CURSE -> assertEquals(relics + 1, run.state().relics().size(), "a gift");
                case CardRarity.BASIC -> assertEquals(hp, run.state().hp());
                case CardRarity.RARE -> {
                    assertEquals(10, gain);
                    assertEquals(maxHp + 10, run.state().maxHp());
                }
                default -> assertEquals(hp + gain, run.state().hp(), "the picture shows the HP healed");
            }
            assertEquals(List.of("[Leave]"), labels(room));
        }
    }

    @Test
    void pipeShowsEachReachThenCutsOrPays() {
        Set<String> outcomes = new TreeSet<>();
        for (long seed = 1; seed <= 12; seed++) {
            Run run = enter(Characters.SONIC, seed, "event:clogged_pipe");
            EventRoom room = (EventRoom) run.room();
            for (int damage = 3; room.awaitingIllustration() || labels(room).contains("[Reach In]"); damage++) {
                int hp = run.state().hp();
                assertTrue(room.choose(0));
                String detail = room.illustration();
                assertTrue(detail.equals("cut:" + damage) || detail.equals("prize:" + damage), detail);
                assertIllustrating(room, detail);
                assertEquals(hp, run.state().hp());
                outcomes.add(detail.substring(0, detail.indexOf(':')));
                room.illustrationShown();
                assertEquals(hp - damage, run.state().hp());
                if (detail.startsWith("prize")) {
                    assertTrue(room.text().startsWith("Your fingers close"), room.text());
                    break;
                }
                assertTrue(room.text().startsWith("Ow!"), room.text());
            }
        }
        assertEquals(Set.of("cut", "prize"), outcomes);
    }

    @Test
    void aFatalReachIsShownAsACutAndEndsTheEvent() {
        Run run = enter(Characters.SONIC, 7, "event:clogged_pipe");
        run.state().setHp(3);
        EventRoom room = (EventRoom) run.room();
        assertTrue(room.choose(0));
        assertIllustrating(room, "cut:3");
        room.illustrationShown();
        assertTrue(run.state().dead());
        assertTrue(room.finished());
    }

    @Test
    void theCollectorsBadgeIsAnEventRelic() {
        Relic badge = catalog.newRelic(Relics.COLLECTORS_BADGE);
        assertEquals(RelicTier.EVENT, badge.tier());
    }
}
