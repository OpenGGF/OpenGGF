package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The turn-based battle rules: full wait, techs, elements, shields, rewards and Super Sonic. */
class BattleTest {
    private static Progress party(long seed, int level, HeroId... heroes) {
        Progress progress = new Progress(seed);
        for (HeroId id : heroes) progress.join(id);
        for (Hero hero : progress.party()) hero.setLevel(level);
        return progress;
    }

    /** Runs enemy turns until a hero must choose (or the battle ends). */
    private static void toHeroTurn(Battle battle) {
        for (int i = 0; i < 200 && battle.phase() == Battle.Phase.RESOLVE; i++) battle.advance();
    }

    @Test
    void theBattleWaitsCompletelyForEachHeroCommand() {
        Battle battle = new Battle(party(1, 5, HeroId.TAILS), List.of(EnemyKind.MOTOBUG, EnemyKind.CRABMEAT), 1);
        battle.start();
        toHeroTurn(battle);
        assertEquals(Battle.Phase.CHOOSE, battle.phase());
        Combatant waiting = battle.actor();
        int[] hp = battle.foes.stream().mapToInt(c -> c.hp).toArray();
        int[] party = battle.party.stream().mapToInt(c -> c.hp).toArray();
        for (int i = 0; i < 50; i++) battle.advance();
        assertSame(waiting, battle.actor(), "advancing never skips a pending hero turn");
        assertEquals(Battle.Phase.CHOOSE, battle.phase());
        assertEquals(List.of(hp[0], hp[1]), battle.foes.stream().map(c -> c.hp).toList(), "foes waited too");
        assertEquals(List.of(party[0], party[1]), battle.party.stream().map(c -> c.hp).toList());
        battle.submit(Command.attack(battle.livingFoes().get(0)));
        assertTrue(battle.events().stream().anyMatch(e -> e.type() == BattleEvent.HIT));
    }

    @Test
    void roundsAreOrderedBySpeed() {
        Battle battle = new Battle(party(2, 10), List.of(EnemyKind.YADRIN), 0);
        battle.start();
        battle.advance();
        // Level-10 Sonic (SPD 32) always outruns a tier-0 Yadrin (SPD 4) despite the small random spread.
        assertEquals(Battle.Phase.CHOOSE, battle.phase());
        assertEquals("Sonic", battle.actor().name);
    }

    @Test
    void aTechNeedsItsPartnerStillWaitingAndSpendsBothTurns() {
        Progress progress = party(3, 8, HeroId.TAILS);
        Battle battle = new Battle(progress, List.of(EnemyKind.SPINY, EnemyKind.SPINY), 4);
        battle.start();
        toHeroTurn(battle);
        Combatant actor = battle.actor();
        Combatant partner = battle.party.stream().filter(c -> c != actor).findFirst().orElseThrow();
        assertTrue(battle.knows(Skill.TORNADO_SPIN));
        if (battle.upcoming().contains(partner)) {
            assertNull(battle.unusableReason(Skill.TORNADO_SPIN));
            int actorEp = actor.ep, partnerEp = partner.ep;
            battle.submit(Command.skill(Skill.TORNADO_SPIN, null));
            assertEquals(actorEp - Skill.TORNADO_SPIN.ep, actor.ep);
            assertEquals(partnerEp - Skill.TORNADO_SPIN.ep, partner.ep);
            assertFalse(battle.upcoming().contains(partner), "the partner's turn was used");
        } else {
            assertNotNull(battle.unusableReason(Skill.TORNADO_SPIN));
        }
        assertFalse(battle.knows(Skill.DOUBLE_SPIN), "Knuckles has not joined");
    }

    @Test
    void aTechIsRefusedWhenThePartnerHasAlreadyActed() {
        Progress progress = party(4, 8, HeroId.TAILS);
        Battle battle = new Battle(progress, List.of(EnemyKind.GRABBER, EnemyKind.GRABBER, EnemyKind.GRABBER), 4);
        battle.start();
        toHeroTurn(battle);
        Combatant first = battle.actor();
        battle.submit(Command.guard());
        toHeroTurn(battle);
        if (battle.phase() == Battle.Phase.CHOOSE && battle.actor() != first && battle.round() == 1) {
            String reason = battle.unusableReason(Skill.TORNADO_SPIN);
            assertNotNull(reason);
            assertTrue(reason.contains("already acted"), reason);
            assertThrows(IllegalArgumentException.class, () -> battle.submit(Command.skill(Skill.TORNADO_SPIN, null)));
        }
    }

    @Test
    void shieldsBlockAWholeHitAndChargeAttacksWithTheirElement() {
        Progress progress = party(5, 6);
        progress.addItem(Item.BUBBLE_SHIELD, 1);
        Battle battle = new Battle(progress, List.of(EnemyKind.BOMB), 1);
        battle.start();
        toHeroTurn(battle);
        Combatant sonic = battle.actor();
        battle.submit(Command.item(Item.BUBBLE_SHIELD, sonic));
        assertEquals(Element.WATER, sonic.shield);
        assertEquals(0, progress.count(Item.BUBBLE_SHIELD));
        int before = sonic.hp;
        Combatant bomb = battle.foes.get(0);
        assertEquals(0, battle.strike(bomb, sonic, 10, Element.FIRE, false));
        assertEquals(before, sonic.hp);
        assertNull(sonic.shield, "a shield is spent by the hit it blocks");
        assertTrue(battle.strike(bomb, sonic, 10, Element.FIRE, false) > 0);
    }

    @Test
    void weaknessesHitHarderAndResistancesSofter() {
        double weak = 0, plain = 0, resisted = 0;
        for (int seed = 1; seed <= 200; seed++) {
            Battle battle = new Battle(party(seed, 10), List.of(EnemyKind.CATERKILLER), 3);
            Combatant sonic = battle.party.get(0);
            Combatant cat = battle.foes.get(0);
            cat.hp = 9999;
            weak += battle.strike(sonic, cat, 10, Element.WATER, false);
            plain += battle.strike(sonic, cat, 10, Element.NONE, false);
            resisted += battle.strike(sonic, cat, 10, Element.FIRE, false);
        }
        assertTrue(weak > plain * 1.3, "Caterkiller is weak to Water");
        assertTrue(resisted < plain * 0.7, "Caterkiller resists Fire");
    }

    @Test
    void victoryPaysExperienceRingsAndTeachesSkills() {
        Progress progress = party(6, 2);
        int rings = progress.rings();
        Battle battle = new Battle(progress, List.of(EnemyKind.MOTOBUG, EnemyKind.MOTOBUG), 6);
        battle.start();
        battle.forceVictory();
        assertEquals(Battle.Phase.VICTORY, battle.phase());
        assertTrue(battle.xpReward() > 0);
        assertEquals(rings + battle.ringReward(), progress.rings());
        assertFalse(battle.levelUps().isEmpty(), "two level-6 Motobugs lift a level-2 Sonic");
        assertTrue(battle.levelUps().get(0).contains("Homing Attack"), battle.levelUps().get(0));
        assertTrue(progress.hero(HeroId.SONIC).level() >= 3);
    }

    @Test
    void bossesCannotBeFledButOrdinaryFightsCan() {
        Battle boss = new Battle(party(7, 5), List.of(EnemyKind.GIGA_MOTOBUG), 0);
        boss.start();
        toHeroTurn(boss);
        assertFalse(boss.canFlee());
        assertThrows(IllegalArgumentException.class, () -> boss.submit(Command.flee()));
        boolean escaped = false;
        for (int seed = 1; seed < 40 && !escaped; seed++) {
            Battle fight = new Battle(party(seed, 9), List.of(EnemyKind.YADRIN), 0);
            fight.start();
            toHeroTurn(fight);
            fight.submit(Command.flee());
            escaped = fight.phase() == Battle.Phase.FLED;
        }
        assertTrue(escaped);
    }

    @Test
    void superSonicNeedsEveryEmeraldAndFiftyRingsThenDrainsRings() {
        Progress progress = party(8, 15);
        Battle battle = new Battle(progress, List.of(EnemyKind.MECHA_SONIC), 9);
        battle.start();
        toHeroTurn(battle);
        assertFalse(battle.canTransform(), "no emeralds yet");
        for (int i = 0; i < 7; i++) progress.addEmerald(i);
        progress.addRings(200);
        assertTrue(battle.canTransform());
        int rings = progress.rings();
        battle.submit(Command.transform());
        Combatant sonic = battle.party.get(0);
        assertTrue(sonic.superForm);
        assertEquals(rings - Battle.SUPER_DRAIN, progress.rings());
        assertEquals(0, battle.strike(battle.foes.get(0), sonic, 20, Element.NONE, false), "Super Sonic is untouchable");
        battle.advance();
        toHeroTurn(battle);
        assertTrue(progress.rings() <= rings - 2 * Battle.SUPER_DRAIN, "each turn drains rings");
    }

    @Test
    void anAllDownPartyLosesAndHeroesCarryTheirHpBack() {
        Progress progress = party(9, 1);
        Battle battle = new Battle(progress, List.of(EnemyKind.CONVERGENCE_ENGINE), 9);
        battle.start();
        for (int i = 0; i < 400 && battle.phase() != Battle.Phase.DEFEAT; i++) {
            if (battle.phase() == Battle.Phase.CHOOSE) battle.submit(Command.guard());
            else battle.advance();
        }
        assertEquals(Battle.Phase.DEFEAT, battle.phase());
        assertEquals(0, progress.hero(HeroId.SONIC).hp());
    }

    @Test
    void revivingNeedsAFallenAllyAndRestoresHalfTheirHp() {
        Progress progress = party(10, 12, HeroId.TAILS);
        progress.addItem(Item.ONE_UP, 1);
        Battle battle = new Battle(progress, List.of(EnemyKind.JAWZ), 7);
        battle.start();
        toHeroTurn(battle);
        Combatant actor = battle.actor();
        Combatant other = battle.party.stream().filter(c -> c != actor).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> battle.submit(Command.item(Item.ONE_UP, other)));
        other.hp = 0;
        battle.submit(Command.item(Item.ONE_UP, other));
        assertEquals(other.maxHp / 2, other.hp);
    }
    @Test
    void fallenHeroesShareRewardsAndThePartyRecoversAfterVictory() {
        Progress progress = new Progress(1);
        progress.join(HeroId.TAILS);
        Battle battle = new Battle(progress, List.of(EnemyKind.MOTOBUG), 1);
        battle.party.get(0).hp = 0;
        battle.party.get(0).ep = 0;
        battle.party.get(1).hp = 1;
        battle.forceVictory();
        assertTrue(progress.hero(HeroId.SONIC).xp() > 0, "fallen heroes must not fall behind in XP");
        for (Hero hero : progress.party()) {
            assertTrue(hero.hp() >= hero.maxHp() * 3 / 5);
            assertTrue(hero.ep() >= 2);
            assertTrue(hero.ep() <= hero.maxEp());
        }
    }

}
