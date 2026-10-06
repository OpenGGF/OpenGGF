package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.CommonCards;
import slaytherobotnik.content.TailsCards;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Cards;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Powers;

/** Tails' trickier cards: Ring Bomb makers and boosters, discard triggers, Dexterity, X costs and Retain. */
class TailsCardsTest {

    private static Harness tails() {
        return new Harness("tails").noRelics().deck(TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD,
                TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD,
                TailsCards.TAIL_GUARD);
    }

    /** An empty hand and piles, with {@code drawPile} Tail Guards to draw from. */
    private static void reset(Combat c, int drawPile) {
        Harness.clearCards(c);
        for (int i = 0; i < drawPile; i++) {
            c.player().drawPile().add(new Card(c.catalog().card(TailsCards.TAIL_GUARD)));
        }
    }

    private static long count(List<Card> cards, String id) {
        return cards.stream().filter(card -> card.id().equals(id)).count();
    }

    @Test
    void bombBagAndDummyRingAddRingBombs() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:bomb_bag"), null);
        assertEquals(3, count(c.player().hand(), TailsCards.RING_BOMB));
        c.playCard(Harness.giveUpgraded(c, "tails:bomb_bag"), null);
        assertEquals(7, count(c.player().hand(), TailsCards.RING_BOMB), "upgraded Bomb Bag adds 4");
        c.playCard(Harness.giveUpgraded(c, "tails:dummy_ring"), null);
        assertEquals(9, count(c.player().hand(), TailsCards.RING_BOMB), "upgraded Dummy Ring adds 2");
        assertEquals(6, c.player().block());
        assertEquals(0, c.player().energy());
    }

    @Test
    void blastRadiusBoostsOnlyRingBombsAndShowsInTheirText() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = tails().fight(dummy);
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:blast_radius"), null);
        Card bomb = Harness.give(c, TailsCards.RING_BOMB);
        assertEquals(8, Cards.previewDamage(c, bomb, dummy));
        assertTrue(Cards.describe(bomb, c, dummy).contains("<g>8</g>"), Cards.describe(bomb, c, dummy));
        c.playCard(bomb, dummy);
        assertEquals(92, dummy.hp());
        c.playCard(Harness.give(c, TailsCards.TAIL_SWIPE), dummy);
        assertEquals(86, dummy.hp(), "other Attacks are unaffected");
        c.playCard(Harness.giveUpgraded(c, TailsCards.RING_BOMB), dummy);
        assertEquals(76, dummy.hp(), "Ring Bomb+ deals 6 + 4");
    }

    @Test
    void largeBombCountsRingBombsPlayedThisCombat() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = tails().fight(dummy);
        reset(c, 0);
        for (int i = 0; i < 3; i++) {
            c.playCard(Harness.give(c, TailsCards.RING_BOMB), dummy);
        }
        assertEquals(88, dummy.hp());
        Card large = Harness.give(c, "tails:large_bomb");
        Card largeUp = Harness.giveUpgraded(c, "tails:large_bomb");
        assertEquals(16, Cards.previewDamage(c, large, dummy), "10 + 2 per Ring Bomb");
        assertEquals(19, Cards.previewDamage(c, largeUp, dummy), "10 + 3 per Ring Bomb");
        c.playCard(large, dummy);
        assertEquals(72, dummy.hp());
    }

    @Test
    void recyclerAndBombBarrageTurnTheDiscardedHandIntoBombs() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:recycler"), null);
        Card barrage = Harness.giveUpgraded(c, "tails:bomb_barrage");
        Harness.give(c, TailsCards.TAIL_SWIPE);
        Harness.give(c, TailsCards.TAIL_GUARD);
        Harness.give(c, TailsCards.TAIL_FLICK);
        c.playCard(barrage, null);
        assertEquals(3, c.cardsDiscardedThisTurn());
        assertEquals(6, c.player().hand().size(), "3 from Recycler, 3 from Bomb Barrage");
        assertEquals(6, count(c.player().hand(), TailsCards.RING_BOMB));
        assertEquals(3, c.player().hand().stream().filter(Card::upgraded).count(), "Bomb Barrage+ makes Ring Bomb+");
    }

    @Test
    void remoteRobotAddsARingBombEachTurnAndIsInnateWhenUpgraded() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 10);
        c.playCard(Harness.give(c, "tails:remote_robot"), null);
        c.endTurn();
        assertEquals(6, c.player().hand().size());
        assertEquals(1, count(c.player().hand(), TailsCards.RING_BOMB));

        Harness h = new Harness("tails").noRelics().deck("tails:remote_robot+", TailsCards.TAIL_GUARD,
                TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD,
                TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD, TailsCards.TAIL_GUARD);
        Combat innate = h.fight(new TestDummy(50, 0));
        assertEquals(1, count(innate.player().hand(), "tails:remote_robot"));
    }

    @Test
    void trialAndErrorDiscardsTheHandAndFiresDiscardTriggers() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 10);
        Card gamble = Harness.give(c, "tails:trial_and_error");
        Harness.give(c, "tails:battery_pack");
        Harness.give(c, "tails:junk_drawer");
        Harness.give(c, TailsCards.TAIL_SWIPE);
        c.playCard(gamble, null);
        assertEquals(4, c.player().energy(), "Battery Pack gives 1 Energy when discarded");
        assertEquals(5, c.player().hand().size(), "2 from Junk Drawer, then 3 for the 3 discarded");
        assertEquals(5, c.player().drawPile().size());
        assertEquals(3, c.player().discardPile().size());
        assertTrue(c.player().exhaustPile().contains(gamble));

        Card again = Harness.giveUpgraded(c, "tails:trial_and_error");
        c.playCard(again, null);
        assertTrue(c.player().discardPile().contains(again), "upgraded, it no longer Exhausts");
    }

    @Test
    void discardTriggersIgnoreTheEndOfTurnDiscard() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 7);
        Card drawer = Harness.give(c, "tails:junk_drawer");
        assertFalse(c.canPlay(drawer), "Junk Drawer is Unplayable");
        c.endTurn();
        assertEquals(2, c.player().drawPile().size(), "only the normal 5 cards were drawn");
        assertTrue(c.player().discardPile().contains(drawer));
    }

    @Test
    void napalmBombGetsCheaperAndBoobyTrapRefundsAfterADiscard() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = tails().fight(dummy);
        reset(c, 0);
        Card napalm = Harness.give(c, "tails:napalm_bomb");
        Card trap = Harness.give(c, "tails:booby_trap");
        Card tinker = Harness.give(c, TailsCards.TINKER);
        Card swipe = Harness.give(c, TailsCards.TAIL_SWIPE);
        assertEquals(3, c.effectiveCost(napalm));
        c.playCard(tinker, null);
        assertTrue(c.resolveChoice(List.of(swipe)));
        assertEquals(2, c.effectiveCost(napalm), "1 card discarded this turn");
        c.playCard(trap, dummy);
        assertEquals(88, dummy.hp());
        assertEquals(2, c.player().energy(), "2 spent, 2 refunded");
        c.playCard(napalm, dummy);
        assertEquals(67, dummy.hp(), "7 damage 3 times");
        assertEquals(0, c.player().energy());
    }

    @Test
    void propellerFlightCarriesDexterityIntoNextTurnsBlock() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 10);
        c.playCard(Harness.give(c, "tails:aerobatics"), null);
        assertEquals(2, c.player().dexterity());
        Card flight = Harness.give(c, "tails:propeller_flight");
        assertEquals("Gain 6 Block. Next turn, gain 6 Block.",
                Cards.describe(flight, c, null).replaceAll("</?[grk]>", ""));
        c.playCard(flight, null);
        assertEquals(6, c.player().block());
        c.playCard(Harness.give(c, TailsCards.TAIL_GUARD), null);
        assertEquals(13, c.player().block());
        c.endTurn();
        assertEquals(6, c.player().block(), "old Block is gone; the 6 promised arrives");
    }

    @Test
    void holdingPatternRetainsTheHandForOneTurnExceptEtherealCards() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 10);
        Card hold = Harness.give(c, "tails:holding_pattern");
        Card swipe = Harness.give(c, TailsCards.TAIL_SWIPE);
        Card flick = Harness.give(c, TailsCards.TAIL_FLICK);
        Card dizzy = Harness.give(c, CommonCards.DIZZY);
        c.playCard(hold, null);
        assertEquals(13, c.player().block());
        c.endTurn();
        assertTrue(c.player().hand().containsAll(List.of(swipe, flick)));
        assertTrue(c.player().exhaustPile().contains(dizzy), "Ethereal cards still Exhaust");
        assertEquals(7, c.player().hand().size());
        c.endTurn();
        assertFalse(c.player().hand().contains(swipe), "only for one turn");
        assertFalse(c.player().hand().contains(flick));
    }

    @Test
    void xCostCardsScaleWithEnergy() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = tails().fight(dummy);
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:energy_cannon"), dummy);
        assertEquals(79, dummy.hp(), "3 Energy: 7 damage 3 times");
        assertEquals(0, c.player().energy());
        assertTrue(c.playCard(Harness.give(c, "tails:energy_cannon"), dummy), "playable for X = 0");
        assertEquals(79, dummy.hp());

        c.endTurn();
        Card hack = Harness.giveUpgraded(c, "tails:system_hack");
        c.playCard(hack, dummy);
        assertEquals(-4, dummy.amount(Powers.STRENGTH), "X + 1 with 3 Energy");
        assertEquals(4, dummy.amount(Powers.WEAK));
        assertTrue(c.player().exhaustPile().contains(hack));
    }

    @Test
    void superTailsHitsEveryEnemyForEachLaterCard() {
        TestDummy left = new TestDummy(50, 0);
        TestDummy right = new TestDummy(50, 0);
        Combat c = tails().fight(left, right);
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:super_tails"), null);
        assertEquals(50, left.hp(), "playing Super Tails itself does not trigger it");
        c.playCard(Harness.give(c, "tails:duck_and_cover"), null);
        c.playCard(Harness.give(c, "tails:hammer_tap"), left);
        assertEquals(42, left.hp());
        assertEquals(48, right.hp());
    }

    @Test
    void lockOnDoublesAttackDamageOnTheNextTurnOnly() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = tails().fight(dummy);
        reset(c, 0);
        c.playCard(Harness.give(c, "tails:lock_on"), null);
        c.playCard(Harness.give(c, TailsCards.TAIL_SWIPE), dummy);
        assertEquals(94, dummy.hp(), "not this turn");
        c.endTurn();
        c.playCard(Harness.give(c, TailsCards.TAIL_SWIPE), dummy);
        c.playCard(Harness.give(c, TailsCards.RING_BOMB), dummy);
        assertEquals(74, dummy.hp(), "12 + 8");
        c.endTurn();
        c.playCard(Harness.give(c, TailsCards.TAIL_SWIPE), dummy);
        assertEquals(68, dummy.hp());
    }

    @Test
    void signalJammerStrengthReturnsAfterTheEnemyTurnUnlessArtifactBlocksIt() {
        TestDummy hitter = new TestDummy(50, 10);
        TestDummy shielded = new TestDummy(50, 0);
        Combat c = tails().fight(hitter, shielded);
        reset(c, 0);
        c.applyPower(shielded, shielded, Powers.artifact(1));
        c.playCard(Harness.give(c, "tails:signal_jammer"), null);
        assertEquals(-6, hitter.amount(Powers.STRENGTH));
        assertFalse(shielded.has(Powers.STRENGTH));
        assertFalse(shielded.has("tails:jammed"), "nothing to give back");
        int hp = c.player().hp();
        c.endTurn();
        assertEquals(hp - 4, c.player().hp(), "10 - 6");
        assertFalse(hitter.has(Powers.STRENGTH), "regained at the end of its turn");
        assertFalse(hitter.has("tails:jammed"));
    }

    @Test
    void workbenchDrawsThenAsksForADiscardEachTurn() {
        Combat c = tails().fight(new TestDummy(50, 0));
        reset(c, 10);
        c.playCard(Harness.give(c, "tails:workbench"), null);
        c.endTurn();
        assertEquals(6, c.player().hand().size());
        assertTrue(c.hasPendingChoice());
        assertTrue(c.resolveChoice(List.of(c.pendingOptions().get(0))));
        assertEquals(5, c.player().hand().size());
        assertEquals(1, c.cardsDiscardedThisTurn());
    }
}
