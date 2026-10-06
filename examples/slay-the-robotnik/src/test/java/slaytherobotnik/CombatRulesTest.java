package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.AngelIsland;
import slaytherobotnik.content.CommonCards;
import slaytherobotnik.content.Relics;
import slaytherobotnik.content.SonicCards;
import slaytherobotnik.content.TailsCards;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Powers;
import slaytherobotnik.core.Rng;

/** The combat rules, checked against Slay the Spire's numbers. */
class CombatRulesTest {

    private static Harness sonic() {
        return new Harness("sonic").noRelics().deck(SonicCards.SIDE_STEP, SonicCards.SIDE_STEP, SonicCards.SIDE_STEP,
                SonicCards.SIDE_STEP, SonicCards.SIDE_STEP, SonicCards.SIDE_STEP, SonicCards.SIDE_STEP);
    }

    @Test
    void strikeDealsPrintedDamageAndUsesEnergy() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(50, 0);
        Combat c = h.fight(dummy);
        Card strike = Harness.give(c, SonicCards.SPIN_ATTACK);
        assertTrue(c.playCard(strike, dummy));
        assertEquals(44, dummy.hp());
        assertEquals(2, c.player().energy());
        assertTrue(c.player().discardPile().contains(strike));
    }

    @Test
    void strengthWeakAndVulnerableCombineLikeSlayTheSpire() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = h.fight(dummy);
        c.applyPower(c.player(), c.player(), Powers.strength(2));
        c.applyPower(null, c.player(), Powers.weak(1));
        c.applyPower(c.player(), dummy, Powers.vulnerable(1));
        // (6 + 2) * 0.75 = 6, * 1.5 = 9.
        assertEquals(9, c.calculateDamage(c.player(), dummy, 6, "Attack"));
        Card strike = Harness.give(c, SonicCards.SPIN_ATTACK);
        c.playCard(strike, dummy);
        assertEquals(91, dummy.hp());
    }

    @Test
    void comboRepeatsAndFocusAddsRepetitions() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = h.fight(dummy);
        Card homing = Harness.give(c, SonicCards.HOMING_ATTACK);
        c.playCard(homing, dummy);
        assertEquals(94, dummy.hp(), "Combo 2 x 3 damage");

        c.applyPower(c.player(), c.player(), Powers.focus(1));
        Card again = Harness.give(c, SonicCards.HOMING_ATTACK);
        assertEquals(3, c.comboCount(again));
        c.playCard(again, dummy);
        assertEquals(85, dummy.hp(), "Focus 1 adds a third hit");
    }

    @Test
    void redSneakersBoostOnlyTheFirstComboCardEachTurn() {
        Harness h = sonic().relic(Relics.RED_SNEAKERS);
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = h.fight(dummy);
        Card first = Harness.give(c, SonicCards.HOMING_ATTACK);
        Card second = Harness.give(c, SonicCards.HOMING_ATTACK);
        assertEquals(3, c.comboCount(first));
        c.playCard(first, dummy);
        assertEquals(91, dummy.hp());
        assertEquals(2, c.comboCount(second));
        c.playCard(second, dummy);
        assertEquals(85, dummy.hp());
    }

    @Test
    void dexterityAndFrailModifyCardBlockOnly() {
        Harness h = sonic();
        Combat c = h.fight(new TestDummy(10, 0));
        c.applyPower(c.player(), c.player(), Powers.dexterity(2));
        Card defend = Harness.give(c, SonicCards.SIDE_STEP);
        c.playCard(defend, null);
        assertEquals(7, c.player().block());
        c.applyPower(null, c.player(), Powers.frail(1));
        Card defend2 = Harness.give(c, SonicCards.SIDE_STEP);
        c.playCard(defend2, null);
        assertEquals(7 + 5, c.player().block(), "(5 + 2) * 0.75 floored = 5");
        c.gainBlock(c.player(), 4, null);
        assertEquals(16, c.player().block(), "non-card Block ignores Dexterity and Frail");
    }

    @Test
    void blockAbsorbsDamageAndResetsEachTurn() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(10, 8);
        Combat c = h.fight(dummy);
        c.gainBlock(c.player(), 5, null);
        int hpBefore = c.player().hp();
        c.endTurn();
        assertEquals(hpBefore - 3, c.player().hp());
        assertEquals(0, c.player().block());
        c.gainBlock(c.player(), 20, null);
        c.endTurn();
        assertEquals(0, c.player().block(), "unused Block is lost at the start of the turn");
    }

    @Test
    void vulnerableFromPlayerExpiresAtEndOfRound() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(100, 0);
        Combat c = h.fight(dummy);
        c.applyPower(c.player(), dummy, Powers.vulnerable(1));
        c.endTurn();
        assertFalse(dummy.has(Powers.VULNERABLE));
    }

    @Test
    void enemyDebuffOnPlayerLastsThroughTheNextPlayerTurn() {
        Harness h = sonic();
        Combat c = h.fight(new Enemy("test:debuffer", "Debuffer", 50) {
            @Override
            protected void chooseMove(Combat combat, Rng ai) {
                setMove(slaytherobotnik.core.Move.of("weak", "Weaken", IntentKind.DEBUFF));
            }

            @Override
            protected void perform(Combat combat, slaytherobotnik.core.Move move) {
                if (turnsTaken() == 1) {
                    combat.applyPower(this, combat.player(), Powers.weak(1));
                }
            }
        });
        c.endTurn();
        assertTrue(c.player().has(Powers.WEAK), "Weak 1 applied on the enemy turn is still there on your turn");
        c.endTurn();
        assertFalse(c.player().has(Powers.WEAK));
    }

    @Test
    void artifactNegatesADebuff() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(50, 0);
        Combat c = h.fight(dummy);
        c.applyPower(dummy, dummy, Powers.artifact(1));
        c.applyPower(c.player(), dummy, Powers.vulnerable(2));
        assertFalse(dummy.has(Powers.VULNERABLE));
        assertFalse(dummy.has(Powers.ARTIFACT));
    }

    @Test
    void drawingReshufflesTheDiscardPile() {
        Harness h = sonic();
        Combat c = h.fight(new TestDummy(50, 0));
        assertEquals(5, c.player().hand().size());
        assertEquals(2, c.player().drawPile().size());
        c.endTurn();
        assertEquals(5, c.player().hand().size());
        assertEquals(7, c.player().hand().size() + c.player().drawPile().size() + c.player().discardPile().size());
    }

    @Test
    void exhaustEtherealAndRetain() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(50, 0);
        Combat c = h.fight(dummy);
        Card insta = Harness.give(c, "sonic:insta_shield");
        c.playCard(insta, null);
        assertTrue(c.player().exhaustPile().contains(insta));

        Card dizzy = Harness.give(c, CommonCards.DIZZY);
        Card drop = Harness.give(c, "sonic:drop_dash");
        c.endTurn();
        assertTrue(c.player().exhaustPile().contains(dizzy), "Ethereal cards exhaust at end of turn");
        assertTrue(c.player().hand().contains(drop), "Retain keeps the card");
        assertEquals(9, drop.damage(), "Drop Dash grows by 3 each time it is Retained");
    }

    @Test
    void choicesPauseTheCombatUntilAnswered() {
        Harness h = sonic();
        Combat c = h.fight(new TestDummy(50, 0));
        Card skid = Harness.give(c, "sonic:skid_stop");
        c.playCard(skid, null);
        assertTrue(c.hasPendingChoice());
        assertFalse(c.endTurn(), "cannot end the turn with a choice pending");
        Card pick = c.pendingOptions().get(0);
        assertTrue(c.resolveChoice(List.of(pick)));
        assertTrue(c.player().exhaustPile().contains(pick));
        assertFalse(c.hasPendingChoice());
    }

    @Test
    void singleOptionChoicesResolveThemselves() {
        Harness h = sonic();
        Combat c = h.fight(new TestDummy(50, 0));
        Harness.clearCards(c);
        Card lone = Harness.give(c, SonicCards.SPIN_ATTACK);
        Card skid = Harness.give(c, "sonic:skid_stop");
        c.playCard(skid, null);
        assertFalse(c.hasPendingChoice());
        assertTrue(c.player().exhaustPile().contains(lone));
    }

    @Test
    void tailsStartsCombatWithRingBombs() {
        Harness h = new Harness("tails");
        Combat c = h.fight(new TestDummy(50, 0));
        long bombs = c.player().hand().stream().filter(card -> card.id().equals(TailsCards.RING_BOMB)).count();
        assertEquals(2, bombs);
        assertEquals(7, c.player().hand().size());
    }

    @Test
    void victoryWritesHpBackAndTriggersRelics() {
        Harness h = new Harness("knuckles");
        h.run.setHp(50);
        TestDummy dummy = new TestDummy(5, 0);
        Combat c = h.fight(dummy);
        c.attack(c.player(), dummy, 10);
        assertTrue(c.won());
        assertEquals(56, h.run.hp(), "Master Emerald Shard heals 6");
    }

    @Test
    void defeatEndsTheCombat() {
        Harness h = sonic();
        h.run.setHp(5);
        Combat c = h.fight(new TestDummy(50, 20));
        c.endTurn();
        assertTrue(c.isOver());
        assertFalse(c.won());
        assertEquals(0, h.run.hp());
    }

    @Test
    void rhinobotOpensWithACharge() {
        Harness h = sonic();
        AngelIsland.Rhinobot rhino = new AngelIsland.Rhinobot(new Rng(1));
        Combat c = h.fight(rhino);
        assertEquals("charge", rhino.nextMove().id());
        assertEquals(11, rhino.intent(c).damage());
    }

    @Test
    void bigCaterkillerSplitsAtHalfHealth() {
        Harness h = sonic();
        AngelIsland.BigCaterkiller big = new AngelIsland.BigCaterkiller(new Rng(3));
        Combat c = h.fight(big);
        c.dealDamage(c.player(), big, big.maxHp() / 2 + 1, "Thorns");
        assertEquals("split", big.nextMove().id());
        int remaining = big.hp();
        c.endTurn();
        List<Enemy> alive = c.activeEnemies();
        assertEquals(2, alive.size());
        assertEquals(remaining, alive.get(0).hp());
        assertFalse(c.isOver());
    }

    @Test
    void megaRhinobotEnragesOnSkills() {
        Harness h = sonic();
        AngelIsland.MegaRhinobot nob = new AngelIsland.MegaRhinobot(new Rng(5));
        Combat c = h.fight(nob);
        c.endTurn();
        assertNotNull(nob.power("aiz:enrage"));
        Card defend = Harness.give(c, SonicCards.SIDE_STEP);
        c.playCard(defend, null);
        assertEquals(2, nob.amount(Powers.STRENGTH));
    }

    @Test
    void notEnoughEnergyIsRefused() {
        Harness h = sonic();
        TestDummy dummy = new TestDummy(50, 0);
        Combat c = h.fight(dummy);
        c.loseEnergy(3);
        Card strike = Harness.give(c, SonicCards.SPIN_ATTACK);
        assertEquals("Not enough Energy.", c.whyUnplayable(strike));
        assertFalse(c.playCard(strike, dummy));
        assertNull(c.whyUnplayable(Harness.give(c, "sonic:jump_dash")));
    }

    @Test
    void oneUpMonitorOnlyTriggersOnDeath() {
        Harness h = sonic();
        h.run.addPotion(h.catalog.potion(slaytherobotnik.content.Potions.EXTRA_LIFE));
        h.run.setHp(40);
        Combat c = h.fight(new TestDummy(50, 100));
        assertFalse(c.usePotion(0, null), "the 1-Up can't be broken by hand");
        assertEquals(40, c.player().hp());
        c.endTurn();
        assertFalse(c.isOver(), "the 1-Up saved the player");
        assertEquals(c.player().maxHp() * 30 / 100, c.player().hp());
        assertTrue(h.run.potions().isEmpty());
    }
}
