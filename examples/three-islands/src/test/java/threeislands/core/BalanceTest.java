package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A rough balance check: a simple sensible player (heal when low, use the strongest affordable
 * attack, guard when a boss charges) at each zone's level should clear its ordinary
 * fights almost always and its bosses most of the time, and the battles should not drag on.
 */
class BalanceTest {
    static Progress partyFor(Zone zone, long seed, int level) {
        Progress progress = new Progress(seed);
        if (zone.islandIndex >= Island.WEST.ordinal()) progress.join(HeroId.TAILS);
        if (zone.ordinal() > Zone.ANGEL_ISLAND.ordinal()) progress.join(HeroId.KNUCKLES);
        for (Hero hero : progress.party()) hero.setLevel(level);
        progress.addItem(Item.SUPER_RING, 3);
        return progress;
    }

    /** Plays one battle with a simple strategy; returns rounds taken, or -1 on defeat. */
    static int play(Battle battle) {
        battle.start();
        for (int step = 0; step < 4000; step++) {
            switch (battle.phase()) {
                case VICTORY -> {
                    return battle.round();
                }
                case DEFEAT, FLED -> {
                    return -1;
                }
                case CHOOSE -> battle.submit(choose(battle));
                default -> battle.advance();
            }
        }
        return -1;
    }

    private static Command choose(Battle battle) {
        Combatant actor = battle.actor();
        List<Combatant> foes = battle.livingFoes();
        Combatant weakest = foes.get(0);
        for (Combatant foe : foes) if (foe.hp < weakest.hp) weakest = foe;
        Combatant hurt = null;
        for (Combatant ally : battle.livingParty()) if (ally.hp * 100 / ally.maxHp < 40 && (hurt == null || ally.hp < hurt.hp)) hurt = ally;
        if (!battle.fallen().isEmpty()) {
            for (Skill skill : battle.availableSkills()) {
                if (skill.effect == Kinds.REVIVE && battle.unusableReason(skill) == null) {
                    return Command.skill(skill, battle.fallen().get(0));
                }
            }
        }
        if (hurt != null) {
            for (Skill skill : battle.availableSkills()) {
                if (skill.effect == Kinds.HEAL && battle.unusableReason(skill) == null) {
                    return Command.skill(skill, skill.target == Kinds.ALLY ? hurt : null);
                }
            }
        }
        if (hurt != null && battle.events().isEmpty() && hasRing(battle)) return Command.item(Item.SUPER_RING, hurt);
        boolean charging = foes.stream().anyMatch(f -> f.charging);
        if (charging && actor.hp * 100 / actor.maxHp < 60) return Command.guard();
        if (battle.canTransform()) return Command.transform();
        Skill best = null;
        int bestScore = 0;
        for (Skill skill : battle.availableSkills()) {
            if ((skill.effect != Kinds.DAMAGE && skill.effect != Kinds.BREAK) || battle.unusableReason(skill) != null) continue;
            int score = skill.power * (skill.target == Kinds.ALL_ENEMIES ? foes.size() : 1)
                    * (skill.isTech() ? 2 : 1);
            if (actor.ep - skill.ep < 3 && !skill.isTech()) continue;
            if (score > bestScore && score > 12) {
                best = skill;
                bestScore = score;
            }
        }
        if (best != null) return Command.skill(best, best.target == Kinds.ENEMY ? weakest : null);
        return Command.attack(weakest);
    }

    private static boolean hasRing(Battle battle) {
        return battle.progressCount(Item.SUPER_RING) > 0;
    }

    @Test
    void ordinaryFightsAtTheSuggestedLevelAreWonAndBrisk() {
        List<String> report = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            int wins = 0, rounds = 0, games = 60;
            List<EnemyKind> kinds = zone.enemyKinds();
            for (int seed = 1; seed <= games; seed++) {
                Progress progress = partyFor(zone, seed, zone.level);
                List<EnemyKind> group = new ArrayList<>();
                // Field groups: up to two foes for Sonic alone, three once he has company.
                int size = progress.party().size() == 1 ? 2 : 3;
                for (int k = 0; k < size; k++) group.add(kinds.get((seed + k) % kinds.size()));
                int r = play(new Battle(progress, group, zone.level));
                if (r > 0) {
                    wins++;
                    rounds += r;
                }
            }
            double average = wins == 0 ? 99 : rounds / (double) wins;
            report.add(zone.key + " wins " + wins + "/" + games + " rounds " + String.format("%.1f", average));
            assertTrue(wins >= games * 9 / 10, zone + ": " + report);
            assertTrue(average <= 7, zone + " fights drag: " + report);
            assertTrue(average >= 1.5, zone + " fights are over too quickly: " + report);
        }
        System.out.println("Ordinary fights: " + report);
    }

    @Test
    void bossesAreBeatableAtTheSuggestedLevelButNotTrivial() {
        List<String> report = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            for (EnemyKind boss : zone.bossKinds()) {
                int wins = 0, rounds = 0, games = 60;
                for (int seed = 1; seed <= games; seed++) {
                    // A zone's boss is met about a level after its first fights.
                    Progress progress = partyFor(zone, seed * 31L, zone.level + 1);
                    if (boss == EnemyKind.CONVERGENCE_ENGINE) {
                        for (int i = 0; i < 7; i++) progress.addEmerald(i);
                        progress.addRings(150);
                    }
                    int r = play(new Battle(progress, List.of(boss), zone.level));
                    if (r > 0) {
                        wins++;
                        rounds += r;
                    }
                }
                double average = wins == 0 ? 99 : rounds / (double) wins;
                report.add(boss + " wins " + wins + "/" + games + " rounds " + String.format("%.1f", average));
                assertTrue(wins >= games * 6 / 10, boss + " too hard: " + report);
                assertTrue(average >= 3, boss + " too easy: " + report);
                assertTrue(average <= 13, boss + " drags on: " + report);
            }
        }
        System.out.println("Bosses: " + report);
    }
    @Test
    void anExplorationSessionDoesNotAssumeFreshHpEpOrItemsBeforeEveryFight() {
        for (Zone zone : Zone.values()) {
            for (int seed = 1; seed <= 20; seed++) {
                Progress p = partyFor(zone, seed, zone.level);
                // Only the new-game bag: no artificial per-battle supply grants.
                p.addItem(Item.SUPER_RING, -3);
                // The story awards all seven emeralds before boarding the Death Egg.
                if (zone == Zone.DEATH_EGG) for (int emerald = 0; emerald < 7; emerald++) p.addEmerald(emerald);
                threeislands.field.Field field = new threeislands.field.Field(zone, null);
                for (var spot : field.spots) {
                    if (spot.kind != threeislands.field.Field.Kind.ENCOUNTER) continue;
                    assertTrue(play(new Battle(p, spot.group, zone.level)) > 0,
                            zone + " exhausted the party at " + spot.id + " seed " + seed);
                    for (Hero hero : p.party()) assertTrue(hero.hp() >= hero.maxHp() * 3 / 5);
                }
                // The sanctuary is a real, free, repeatable checkpoint before the boss chain.
                p.restAll();
                for (EnemyKind boss : zone.bossKinds()) {
                    assertTrue(play(new Battle(p, List.of(boss), zone.level)) > 0,
                            zone + " boss chain failed at " + boss + " seed " + seed);
                }
            }
        }
    }

    @Test
    void aBeginnerCanUseBasicAttacksThroughTheFirstArea() {
        for (int seed = 1; seed <= 40; seed++) {
            Progress p = new Progress(seed);
            for (int fight = 0; fight < 6; fight++) {
                Battle b = new Battle(p, List.of(EnemyKind.MOTOBUG), Math.min(Zone.GREEN_HILL.level, p.partyLevel() + 1));
                b.start();
                for (int turn = 0; turn < 200 && b.phase() != Battle.Phase.VICTORY && b.phase() != Battle.Phase.DEFEAT; turn++) {
                    if (b.phase() == Battle.Phase.CHOOSE) b.submit(Command.attack(b.livingFoes().get(0)));
                    else b.advance();
                }
                assertTrue(b.phase() == Battle.Phase.VICTORY, "basic attacks: seed " + seed + " fight " + fight);
            }
        }
    }

    @Test
    void theStoryCanBeFinishedWithoutGrindingOrCollectingOptionalClues() {
        for (int seed = 1; seed <= 20; seed++) {
            Progress p = new Progress(seed);
            for (Zone zone : Zone.values()) {
                if (zone == Zone.EMERALD_HILL) p.join(HeroId.TAILS);
                for (EnemyKind boss : zone.bossKinds()) {
                    int level = Math.min(zone.level, p.partyLevel() + 1);
                    assertTrue(play(new Battle(p, List.of(boss), level)) > 0,
                            "story-only route: " + zone + " / " + boss + " seed " + seed);
                }
                p.completeChapter(zone);
                p.addEmerald(zone.emerald);
                if (zone == Zone.ANGEL_ISLAND) p.join(HeroId.KNUCKLES);
                p.restAll(); // a cleared anchor restores the party in the real game
            }
        }
    }

}
