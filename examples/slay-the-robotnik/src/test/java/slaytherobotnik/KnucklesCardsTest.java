package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.KnucklesCards;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Cards;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Move;
import slaytherobotnik.core.Powers;
import slaytherobotnik.core.Rng;

/** Knuckles' trickiest cards: Strength multipliers, Block as damage, self-damage and its payoffs. */
class KnucklesCardsTest {
    private static final String G = KnucklesCards.GUARD;

    private static Harness knuckles() {
        return new Harness("knuckles").noRelics().deck(G, G, G, G, G, G, G, G, G, G);
    }

    @Test
    void haymakerCountsStrengthThreeTimesAndFiveWhenUpgraded() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = knuckles().fight(dummy);
        c.applyPower(c.player(), c.player(), Powers.strength(2));
        Card haymaker = Harness.give(c, "knuckles:haymaker");
        assertEquals(20, Cards.previewDamage(c, haymaker, dummy), "14 + 3 x 2 Strength");
        c.playCard(haymaker, dummy);
        assertEquals(80, dummy.hp());

        c.gainEnergy(2);
        Card upgraded = Harness.giveUpgraded(c, "knuckles:haymaker");
        c.playCard(upgraded, dummy);
        assertEquals(56, dummy.hp(), "14 + 5 x 2 Strength");
    }

    @Test
    void shoulderChargeDealsDamageEqualToBlockPlusStrength() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = knuckles().fight(dummy);
        c.gainBlock(c.player(), 12, null);
        c.playCard(Harness.give(c, "knuckles:shoulder_charge"), dummy);
        assertEquals(88, dummy.hp());
        assertEquals(12, c.player().block(), "Block is not spent");

        c.applyPower(c.player(), c.player(), Powers.strength(3));
        Card upgraded = Harness.giveUpgraded(c, "knuckles:shoulder_charge");
        assertEquals(0, c.effectiveCost(upgraded));
        c.playCard(upgraded, dummy);
        assertEquals(73, dummy.hp(), "12 Block + 3 Strength");
    }

    @Test
    void digInDoublesBlockWithoutAddingDexterityAgain() {
        Combat c = knuckles().fight(new TestDummy(50, 0));
        c.applyPower(c.player(), c.player(), Powers.dexterity(2));
        c.playCard(Harness.give(c, G), null);
        assertEquals(7, c.player().block());
        c.playCard(Harness.give(c, "knuckles:dig_in"), null);
        assertEquals(14, c.player().block());
    }

    @Test
    void eternalVigilKeepsBlockAcrossTurns() {
        Combat c = knuckles().fight(new TestDummy(50, 4));
        c.playCard(Harness.give(c, "knuckles:eternal_vigil"), null);
        assertTrue(c.player().has(Powers.BARRICADE));
        c.gainBlock(c.player(), 10, null);
        c.endTurn();
        assertEquals(6, c.player().block(), "10 Block - 4 damage, kept into the next turn");
    }

    @Test
    void battleScarsTurnsSelfDamageButNotEnemyHitsIntoStrength() {
        TestDummy dummy = new TestDummy(100, 5);
        Combat c = knuckles().fight(dummy);
        c.playCard(Harness.give(c, "knuckles:battle_scars"), null);
        int hp = c.player().hp();
        c.playCard(Harness.give(c, "knuckles:bare_knuckle"), dummy);
        assertEquals(hp - 2, c.player().hp());
        assertEquals(1, c.player().strength());
        assertEquals(84, dummy.hp(), "the Strength lands on Bare Knuckle's own hit: 15 + 1");

        c.endTurn();
        assertEquals(hp - 2 - 5, c.player().hp(), "the dummy hit during its turn");
        assertEquals(1, c.player().strength(), "HP lost on the enemy turn gives nothing");
    }

    @Test
    void firedUpBurnsEveryoneAtEndOfTurnAndFeedsBattleScars() {
        TestDummy left = new TestDummy(30, 0);
        TestDummy right = new TestDummy(30, 0);
        Combat c = knuckles().fight(left, right);
        c.playCard(Harness.give(c, "knuckles:fired_up"), null);
        c.playCard(Harness.give(c, "knuckles:battle_scars"), null);
        int hp = c.player().hp();
        c.endTurn();
        assertEquals(hp - 1, c.player().hp());
        assertEquals(25, left.hp());
        assertEquals(25, right.hp());
        assertEquals(1, c.player().strength(), "the end-of-turn HP loss is still your turn");
    }

    @Test
    void toughItOutAndAltarOfferingTradeHpForEnergyAndCards() {
        Combat c = knuckles().fight(new TestDummy(50, 0));
        int hp = c.player().hp();
        Card tough = Harness.give(c, "knuckles:tough_it_out");
        Card offering = Harness.give(c, "knuckles:altar_offering");
        c.playCard(tough, null);
        assertEquals(hp - 3, c.player().hp());
        assertEquals(5, c.player().energy());

        int handBefore = c.player().hand().size();
        c.playCard(offering, null);
        assertEquals(hp - 9, c.player().hp());
        assertEquals(7, c.player().energy());
        assertEquals(handBefore - 1 + 3, c.player().hand().size());
        assertTrue(c.player().exhaustPile().contains(offering));
    }

    @Test
    void huntersTrophyRaisesMaxHpOnlyWhenItKillsANonMinion() {
        TestDummy leader = new TestDummy(50, 0);
        TestDummy weakling = new TestDummy(8, 0);
        Enemy minion = new Enemy("test:minion", "Minion", 5) {
            @Override
            protected void onSpawn(Combat combat) {
                markMinion();
            }

            @Override
            protected void chooseMove(Combat combat, Rng ai) {
                setMove(Move.of("idle", "Idle", IntentKind.UNKNOWN));
            }

            @Override
            protected void perform(Combat combat, Move move) {
            }
        };
        Harness h = knuckles();
        Combat c = h.fight(leader, weakling, minion);
        int maxHp = c.player().maxHp();
        c.playCard(Harness.give(c, "knuckles:hunters_trophy"), leader);
        assertEquals(40, leader.hp());
        assertEquals(maxHp, c.player().maxHp(), "not fatal");

        c.playCard(Harness.give(c, "knuckles:hunters_trophy"), minion);
        assertTrue(minion.isDead());
        assertEquals(maxHp, c.player().maxHp(), "minions don't count");

        c.playCard(Harness.give(c, "knuckles:hunters_trophy"), weakling);
        assertTrue(weakling.isDead());
        assertEquals(maxHp + 3, c.player().maxHp());
        assertEquals(maxHp + 3, h.run.maxHp(), "the raise is permanent");
    }

    @Test
    void spikedKnucklesHitsAttackersBackUntilYourNextTurn() {
        TestDummy dummy = new TestDummy(50, 6);
        Combat c = knuckles().fight(dummy);
        c.playCard(Harness.give(c, "knuckles:spiked_knuckles"), null);
        assertEquals(12, c.player().block());
        c.endTurn();
        assertEquals(46, dummy.hp(), "4 damage back");
        assertFalse(c.player().has("knuckles:spiked_knuckles"));
        c.endTurn();
        assertEquals(46, dummy.hp(), "gone after one round");
    }

    @Test
    void signaturePunchCountsEveryPunchCardIncludingItself() {
        Harness h = new Harness("knuckles").noRelics().deck(KnucklesCards.PUNCH, KnucklesCards.PUNCH,
                KnucklesCards.HAMMER_PUNCH, G, G, G, G);
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = h.fight(dummy);
        Card signature = Harness.give(c, "knuckles:signature_punch");
        // Punch, Punch, Hammer Punch and Signature Punch itself: 6 + 4 x 2.
        assertEquals(14, Cards.previewDamage(c, signature, dummy));
        c.playCard(signature, dummy);
        assertEquals(86, dummy.hp());
    }

    @Test
    void paybackGetsCheaperForEveryFiveHpLost() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = knuckles().fight(dummy);
        Card payback = Harness.give(c, "knuckles:payback");
        assertEquals(4, c.effectiveCost(payback));
        assertNotNull(c.whyUnplayable(payback));
        c.loseHp(c.player(), 5);
        assertEquals(3, c.effectiveCost(payback));
        c.loseHp(c.player(), 9);
        assertEquals(2, c.effectiveCost(payback), "14 HP lost");
        assertEquals(1, c.effectiveCost(Harness.giveUpgraded(c, "knuckles:payback")));
        c.playCard(payback, dummy);
        assertEquals(82, dummy.hp());
        assertEquals(1, c.player().energy());
    }

    @Test
    void boilingPointDoublesStrengthAndOnlyTheUpgradeStays() {
        Combat c = knuckles().fight(new TestDummy(50, 0));
        c.applyPower(c.player(), c.player(), Powers.strength(3));
        Card boil = Harness.give(c, "knuckles:boiling_point");
        c.playCard(boil, null);
        assertEquals(6, c.player().strength());
        assertTrue(c.player().exhaustPile().contains(boil));

        Card upgraded = Harness.giveUpgraded(c, "knuckles:boiling_point");
        c.playCard(upgraded, null);
        assertEquals(12, c.player().strength());
        assertTrue(c.player().discardPile().contains(upgraded));
    }

    @Test
    void ancestralFuryHealsOnlyUnblockedDamage() {
        Harness h = knuckles();
        h.run.setHp(50);
        TestDummy open = new TestDummy(30, 0);
        TestDummy guarded = new TestDummy(30, 0);
        Combat c = h.fight(open, guarded);
        c.gainBlock(guarded, 3, null);
        c.playCard(Harness.give(c, "knuckles:ancestral_fury"), null);
        assertEquals(26, open.hp());
        assertEquals(29, guarded.hp());
        assertEquals(55, c.player().hp(), "4 + 1 unblocked");
    }

    @Test
    void knuckleCrackStrengthLastsOneTurn() {
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = knuckles().fight(dummy);
        c.playCard(Harness.give(c, "knuckles:knuckle_crack"), null);
        assertEquals(2, c.player().strength());
        c.playCard(Harness.give(c, KnucklesCards.PUNCH), dummy);
        assertEquals(92, dummy.hp());
        c.endTurn();
        assertEquals(0, c.player().strength());
        assertFalse(c.player().has(Powers.STRENGTH_DOWN_AT_END));
    }

    @Test
    void trickedAgainTradesVulnerableForEnergyEachTurn() {
        Combat c = knuckles().fight(new TestDummy(50, 0));
        c.playCard(Harness.give(c, "knuckles:tricked_again"), null);
        assertEquals(2, c.player().amount(Powers.VULNERABLE));
        c.endTurn();
        assertEquals(4, c.player().energy());
        assertEquals(1, c.player().amount(Powers.VULNERABLE));
    }

    @Test
    void fisticuffsNeedsAHandOfAttacks() {
        TestDummy dummy = new TestDummy(50, 0);
        Combat c = knuckles().fight(dummy);
        Card fist = Harness.give(c, "knuckles:fisticuffs");
        assertNotNull(c.whyUnplayable(fist), "Guards in hand");
        Harness.clearCards(c);
        c.player().hand().add(fist);
        Harness.give(c, KnucklesCards.PUNCH);
        assertNull(c.whyUnplayable(fist));
        c.playCard(fist, dummy);
        assertEquals(36, dummy.hp());
    }

    @Test
    void knuckleUpAndWallClimbResolveTheirChoices() {
        Combat c = knuckles().fight(new TestDummy(50, 0));
        Harness.clearCards(c);
        Card punch = Harness.give(c, KnucklesCards.PUNCH);
        Card guard = Harness.give(c, G);
        Card upgradedKnuckleUp = Harness.giveUpgraded(c, "knuckles:knuckle_up");
        c.playCard(upgradedKnuckleUp, null);
        assertTrue(punch.upgraded() && guard.upgraded(), "the upgrade upgrades the whole hand");
        assertEquals(5, c.player().block());

        Card first = Harness.give(c, KnucklesCards.PUNCH);
        Card second = Harness.give(c, KnucklesCards.PUNCH);
        c.playCard(Harness.give(c, "knuckles:knuckle_up"), null);
        assertTrue(c.hasPendingChoice());
        assertEquals(List.of(first, second), c.pendingOptions(), "only upgradable cards are offered");
        assertTrue(c.resolveChoice(List.of(second)));
        assertTrue(second.upgraded());
        assertFalse(first.upgraded());

        // Wall Climb: the only other discard is put back on top of the draw pile.
        Harness.clearCards(c);
        Card spent = new Card(c.catalog().card(KnucklesCards.HAMMER_PUNCH));
        c.player().discardPile().add(spent);
        c.playCard(Harness.give(c, "knuckles:wall_climb"), null);
        assertFalse(c.hasPendingChoice());
        assertEquals(spent, c.player().drawPile().get(c.player().drawPile().size() - 1));
    }
}
