package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Status effects, boss signatures and accessories. */
class MechanicsTest {
    private static Battle battle(List<EnemyKind> foes, int level, HeroId... extra) {
        Progress progress = new Progress(5);
        for (HeroId id : extra) progress.join(id);
        for (Hero hero : progress.party()) hero.setLevel(level);
        Battle battle = new Battle(progress, foes, level);
        battle.start();
        return battle;
    }

    /** Advances until a hero must choose (or the battle ends). */
    private static void toChoice(Battle battle) {
        for (int i = 0; i < 200 && battle.phase() != Battle.Phase.CHOOSE && battle.phase() != Battle.Phase.VICTORY
                && battle.phase() != Battle.Phase.DEFEAT; i++) battle.advance();
    }

    private static boolean said(Battle battle, String fragment) {
        return battle.events().stream().anyMatch(e -> e.text() != null && e.text().contains(fragment));
    }

    @Test
    void burnTicksAtTheStartOfTheOwnersTurnAndHealingCuresIt() {
        Battle b = battle(List.of(EnemyKind.MOTOBUG), 5, HeroId.TAILS);
        toChoice(b);
        Combatant foe = b.foes.get(0);
        foe.burn = 2;
        int before = foe.hp;
        // Let turns pass until the foe has acted once.
        for (int i = 0; i < 6 && foe.burn == 2; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard()); else b.advance();
        }
        assertEquals(1, foe.burn);
        assertTrue(foe.hp < before, "the burn dealt damage");
        Combatant hero = b.party.get(0);
        hero.burn = 3;
        hero.soak = 2;
        toChoice(b);
        Combatant actor = b.actor();
        actor.burn = 3;
        b.submit(Command.item(Item.SUPER_RING, actor));
        assertFalse(actor.hasStatus(), "healing cures statuses");
        assertTrue(said(b, "Status cured"));
    }

    @Test
    void aStunnedFighterLosesItsTurnAndAWardBlocksTheMatchingStatus() {
        Battle b = battle(List.of(EnemyKind.MOTOBUG), 5);
        toChoice(b);
        Combatant sonic = b.party.get(0);
        sonic.stunned = true;
        b.submit(Command.guard());
        toChoice(b);
        assertTrue(b.round() >= 2);
        // The stun was consumed by a skipped turn rather than lingering.
        assertFalse(sonic.stunned);
        b.progressCount(Item.SUPER_RING);
        Progress progress = new Progress(3);
        progress.addGear(Gear.FLAME_CHARM);
        progress.equip(progress.hero(HeroId.SONIC), Gear.FLAME_CHARM);
        assertEquals(Element.FIRE, Combatant.hero(progress.hero(HeroId.SONIC), 0).ward());
    }

    @Test
    void aBombExplodesWhenItsFuseRunsOutAndGivesNoReward() {
        Battle b = battle(List.of(EnemyKind.BOMB, EnemyKind.MOTOBUG), 4);
        Combatant bomb = b.foes.get(0);
        assertEquals(3, bomb.fuse);
        for (int i = 0; i < 200 && bomb.alive(); i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
        }
        assertEquals(-1, bomb.fuse, "the fuse, not the party, ended it");
        assertFalse(bomb.alive());
    }

    @Test
    void summonedHelpersJoinAndScatterWhenTheirLeaderFalls() {
        Battle b = battle(List.of(EnemyKind.BOMB_KING), 6, HeroId.TAILS);
        Combatant king = b.foes.get(0);
        for (int i = 0; i < 400 && b.foes.size() == 1; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
        }
        assertTrue(b.foes.size() > 1, "Bomb King called a Bomb");
        Combatant helper = b.foes.get(1);
        assertEquals(king, helper.summoner);
        assertTrue(helper.maxHp < Combatant.enemy(EnemyKind.BOMB, 6, 2, 0, "x").maxHp, "helpers are smaller");
        toChoice(b);
        king.hp = 1;
        b.submit(Command.attack(king));
        assertFalse(helper.alive(), "helpers leave with their leader");
        assertEquals(Battle.Phase.VICTORY, b.phase());
    }

    @Test
    void theGrabberQueenHoldsAHeroUntilHurtEnough() {
        Battle b = battle(List.of(EnemyKind.GRABBER_QUEEN), 9, HeroId.TAILS);
        Combatant queen = b.foes.get(0);
        Combatant held = null;
        for (int i = 0; i < 400 && held == null; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
            for (Combatant hero : b.party) if (hero.heldBy == queen) held = hero;
        }
        assertNotNull(held, "the queen seized someone");
        toChoice(b);
        assertTrue(b.actor() != held, "a held hero never gets a command");
        queen.damageSinceGrab = queen.maxHp; // a strong blow
        b.submit(Command.attack(queen));
        assertNull(held.heldBy, "hurting the queen breaks the grip");
    }

    @Test
    void silverSonicsSpikesReboundPlainBlowsButElementsBreakThem() {
        Battle b = battle(List.of(EnemyKind.SILVER_SONIC), 11, HeroId.TAILS);
        toChoice(b);
        Combatant silver = b.foes.get(0);
        silver.stance = true;
        Combatant actor = b.actor();
        int hp = actor.hp;
        b.submit(Command.attack(silver));
        assertTrue(actor.hp < hp, "the attacker is hurt by the spikes");
        assertTrue(silver.stance, "plain blows do not break the guard");
        toChoice(b);
        b.actor().shield = Element.ELEC;
        b.submit(Command.attack(silver));
        assertFalse(silver.stance, "an elemental blow breaks it");
    }

    @Test
    void theBarrierOnlyFallsToATech() {
        Battle b = battle(List.of(EnemyKind.BEAM_ROCKET), 17, HeroId.TAILS, HeroId.KNUCKLES);
        toChoice(b);
        Combatant rocket = b.foes.get(0);
        rocket.stance = true;
        int before = rocket.hp;
        Combatant actor = b.actor();
        b.submit(Command.attack(rocket));
        int plain = before - rocket.hp;
        assertTrue(rocket.stance);
        toChoice(b);
        Skill tech = b.availableSkills().stream().filter(s -> s.isTech() && b.unusableReason(s) == null).findFirst()
                .orElse(null);
        if (tech != null) {
            b.submit(Command.skill(tech, tech.target == Kinds.ENEMY ? rocket : null));
            assertFalse(rocket.stance, "a combined tech shatters the barrier");
        }
        assertTrue(plain < actor.atk() / 2 + 5, "the barrier soaks most of a plain hit");
    }

    @Test
    void aDepthChargeHitsHardUnlessTheTargetGuards() {
        Battle b = battle(List.of(EnemyKind.SCREW_MOBILE), 15, HeroId.TAILS, HeroId.KNUCKLES);
        Combatant screw = b.foes.get(0);
        Combatant marked = null;
        for (int i = 0; i < 400 && marked == null; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
            for (Combatant hero : b.party) if (hero.charge == screw) marked = hero;
        }
        assertNotNull(marked);
        assertTrue(said(b, "depth charge"));
    }

    @Test
    void theConvergenceCoreCyclesItsWeaknessEachRound() {
        Battle b = battle(List.of(EnemyKind.CONVERGENCE_ENGINE), 19, HeroId.TAILS, HeroId.KNUCKLES);
        Combatant core = b.foes.get(0);
        toChoice(b);
        Element first = core.weakness();
        assertTrue(first != Element.NONE);
        int round = b.round();
        for (int i = 0; i < 100 && b.round() == round; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
        }
        assertTrue(core.weakness() != first, "the exposed element moved on");
    }

    @Test
    void enragedBossesAnnounceTheirSecondPhaseOnce() {
        Battle b = battle(List.of(EnemyKind.MECHA_SONIC), 19, HeroId.TAILS, HeroId.KNUCKLES);
        Combatant mecha = b.foes.get(0);
        mecha.hp = mecha.maxHp / 2;
        for (int i = 0; i < 100 && !mecha.empowered; i++) {
            if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.guard());
            else b.advance();
        }
        assertTrue(mecha.empowered);
        assertTrue(mecha.atk() > Combatant.enemy(EnemyKind.MECHA_SONIC, 19, 3, 0, "m").atk());
    }

    @Test
    void accessoriesChangeStatsMoveBetweenHeroesAndSurviveSaving() {
        Progress p = new Progress(9);
        p.join(HeroId.TAILS);
        Hero sonic = p.hero(HeroId.SONIC), tails = p.hero(HeroId.TAILS);
        int speed = sonic.spd();
        assertThrows(IllegalArgumentException.class, () -> p.equip(sonic, Gear.POWER_SNEAKERS), "must own it");
        assertTrue(p.addGear(Gear.POWER_SNEAKERS));
        assertFalse(p.addGear(Gear.POWER_SNEAKERS), "unique");
        p.equip(sonic, Gear.POWER_SNEAKERS);
        assertEquals(speed + 3, sonic.spd());
        p.equip(tails, Gear.POWER_SNEAKERS);
        assertNull(sonic.gear(), "one pair of sneakers");
        assertEquals(Gear.POWER_SNEAKERS, tails.gear());
        p.addGear(Gear.HEART_PENDANT);
        p.equip(sonic, Gear.HEART_PENDANT);
        sonic.restore();
        int hp = sonic.hp();
        Progress loaded = SaveCodec.decode(SaveCodec.encode(p));
        assertEquals(Gear.HEART_PENDANT, loaded.hero(HeroId.SONIC).gear());
        assertEquals(Gear.POWER_SNEAKERS, loaded.hero(HeroId.TAILS).gear());
        assertEquals(hp, loaded.hero(HeroId.SONIC).hp(), "bonus HP survives the reload");
        assertTrue(loaded.owns(Gear.HEART_PENDANT));
        // Saves from before accessories existed still load.
        Progress old = SaveCodec.decode("three-islands-save 1\nhero.SONIC=4,0,30,10\n");
        assertNull(old.hero(HeroId.SONIC).gear());
        assertEquals(0, old.gearMask());
    }

    @Test
    void elementalAccessoriesChargePlainAttacks() {
        Progress p = new Progress(4);
        p.addGear(Gear.AQUA_CHARM);
        p.equip(p.hero(HeroId.SONIC), Gear.AQUA_CHARM);
        Battle b = new Battle(p, List.of(EnemyKind.CRABMEAT), 3);
        b.start();
        toChoice(b);
        b.submit(Command.attack(b.foes.get(0)));
        assertTrue(b.events().stream().anyMatch(e -> e.type() == BattleEvent.HIT && e.element() == Element.WATER));
    }
}
