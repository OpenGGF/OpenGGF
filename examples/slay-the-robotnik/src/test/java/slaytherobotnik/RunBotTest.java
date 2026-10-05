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
    void sturdyRunsReachTheBossAndWin() {
        for (CharacterDef character : catalog.characters()) {
            for (long seed = 1; seed <= 15; seed++) {
                Run run = Run.start(catalog, character.id(), seed * 31 + character.id().hashCode());
                run.state().setMaxHp(1500);
                run.state().setHp(1500);
                RunBot bot = new RunBot(run, seed);
                bot.strengthBoost = 10;
                assertTrue(bot.play(50_000), character.id() + " seed " + seed + " stuck in " + run.room());
                assertTrue(run.room() instanceof VictoryRoom,
                        character.id() + " seed " + seed + " ended in " + run.room());
                assertEquals(1, run.state().stats().bossesDefeated);
            }
        }
    }
}
