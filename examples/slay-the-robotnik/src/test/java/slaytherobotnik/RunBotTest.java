package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.CharacterDef;
import slaytherobotnik.run.Run;
import slaytherobotnik.run.VictoryRoom;

/** Whole runs, played by {@link RunBot}, always finish. */
class RunBotTest {
    private final Catalog catalog = Content.build();

    @Test
    void runsAlwaysFinishForEveryCharacter() {
        Map<Integer, Integer> deathFloors = new TreeMap<>();
        for (CharacterDef character : catalog.characters()) {
            for (long seed = 1; seed <= 40; seed++) {
                Run run = Run.start(catalog, character.id(), seed * 7919 + character.id().hashCode());
                RunBot bot = new RunBot(run, seed);
                assertTrue(bot.play(20_000), character.id() + " seed " + seed + " stuck in " + run.room());
                deathFloors.merge(run.state().floor(), 1, Integer::sum);
            }
        }
        System.out.println("RunBot final floors (floor=count): " + deathFloors);
    }

    @Test
    void sturdyRunsReachEveryActAndUsuallyWin() {
        // A random bot with a big HP pool, healed before each fight, still dies to some
        // deck-clogging elites; what matters is that runs reach every act and boss and end.
        int runs = 0;
        int wins = 0;
        Map<String, Integer> killers = new TreeMap<>();
        for (CharacterDef character : catalog.characters()) {
            for (long seed = 1; seed <= 15; seed++) {
                Run run = Run.start(catalog, character.id(), seed * 31 + character.id().hashCode());
                run.state().setMaxHp(1500);
                run.state().setHp(1500);
                RunBot bot = new RunBot(run, seed);
                bot.strengthBoost = 10;
                assertTrue(bot.play(400_000), character.id() + " seed " + seed + " stuck in " + run.room());
                runs++;
                if (run.room() instanceof VictoryRoom) {
                    wins++;
                    assertEquals(catalog.actCount(), run.state().stats().bossesDefeated, "one boss per act");
                } else if (run.room() instanceof slaytherobotnik.run.GameOverRoom over) {
                    killers.merge(over.killedBy(), 1, Integer::sum);
                }
            }
        }
        System.out.println("Sturdy runs: " + wins + "/" + runs + " won; losses to " + killers);
        assertTrue(wins * 4 >= runs * 3, wins + "/" + runs + " sturdy runs won; losses " + killers);
    }
}
