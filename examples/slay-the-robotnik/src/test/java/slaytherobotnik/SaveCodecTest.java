package slaytherobotnik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.Relic;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.run.MapRoom;
import slaytherobotnik.run.RewardRoom;
import slaytherobotnik.run.Run;
import slaytherobotnik.run.SaveCodec;

/** Saving and resuming gives back the same run, the same fight and the same rewards. */
class SaveCodecTest {
    private final Catalog catalog = Content.build();

    private static String describe(Run run) {
        var s = run.state();
        List<String> deck = new ArrayList<>();
        for (Card c : s.deck()) {
            deck.add(c.name());
        }
        List<String> relics = new ArrayList<>();
        for (Relic r : s.relics()) {
            relics.add(r.id() + "#" + r.counter());
        }
        return s.character().id() + " hp=" + s.hp() + "/" + s.maxHp() + " rings=" + s.rings() + " act=" + s.act()
                + " floor=" + s.floor() + " at=" + s.nodeX() + "," + s.actFloor() + " deck=" + deck + " relics="
                + relics + " potions=" + s.potions().size() + " rng=" + s.rngs().states() + " queue="
                + s.monsterQueue() + " boss=" + s.boss();
    }

    @Test
    void mapSaveRoundTrips() {
        for (String character : List.of("sonic", "tails", "knuckles")) {
            Run run = Run.start(catalog, character, 99);
            RunBot bot = new RunBot(run, 3);
            bot.strengthBoost = 10;
            run.state().setMaxHp(500);
            run.state().setHp(500);
            int rooms = 0;
            while (rooms < 6 && !run.over()) {
                bot.play(bot.steps + 1);
                if (run.room() instanceof MapRoom && run.deckChoice() == null) {
                    rooms++;
                    String saved = SaveCodec.encode(run);
                    Run restored = SaveCodec.decode(catalog, saved);
                    assertNotNull(restored);
                    assertEquals(describe(run), describe(restored));
                    assertTrue(restored.room() instanceof MapRoom);
                    assertEquals(run.reachableNodes().size(), restored.reachableNodes().size());
                }
            }
        }
    }

    @Test
    void resumingAFightReplaysTheSameFight() {
        Run run = Run.start(catalog, "sonic", 1234);
        List<String> saves = new ArrayList<>();
        run.setSaver(r -> saves.add(SaveCodec.encode(r)));
        RunBot bot = new RunBot(run, 5);
        while (!(run.room() instanceof CombatRoom)) {
            bot.play(bot.steps + 1);
        }
        CombatRoom fight = (CombatRoom) run.room();
        Run resumed = SaveCodec.decode(catalog, saves.get(saves.size() - 1));
        assertNotNull(resumed);
        CombatRoom again = (CombatRoom) resumed.room();
        assertEquals(fight.encounterId(), again.encounterId());
        List<Enemy> a = fight.combat().enemies();
        List<Enemy> b = again.combat().enemies();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).maxHp(), b.get(i).maxHp());
            assertEquals(a.get(i).nextMove().id(), b.get(i).nextMove().id());
        }
        List<String> handA = fight.combat().player().hand().stream().map(Card::name).toList();
        List<String> handB = again.combat().player().hand().stream().map(Card::name).toList();
        assertEquals(handA, handB, "same shuffle");
    }

    @Test
    void rewardScreenSurvivesASave() {
        Run run = Run.start(catalog, "knuckles", 777);
        run.state().setMaxHp(500);
        run.state().setHp(500);
        List<String> saves = new ArrayList<>();
        run.setSaver(r -> saves.add(SaveCodec.encode(r)));
        RunBot bot = new RunBot(run, 8);
        bot.strengthBoost = 10;
        while (!(run.room() instanceof RewardRoom)) {
            bot.play(bot.steps + 1);
        }
        RewardRoom rewards = (RewardRoom) run.room();
        rewards.claim(0);
        Run resumed = SaveCodec.decode(catalog, saves.get(saves.size() - 1));
        RewardRoom again = (RewardRoom) resumed.room();
        assertEquals(rewards.rewards().size(), again.rewards().size());
        assertTrue(again.claimed(0));
        assertEquals(run.state().rings(), resumed.state().rings());
    }

    @Test
    void garbageIsNotASave() {
        assertNull(SaveCodec.decode(catalog, "this is not a save"));
        assertNull(SaveCodec.decode(catalog, "version=999\n"));
    }

    @Test
    void sameSeedSamePlaythrough() {
        Run a = Run.start(catalog, "tails", 4242);
        Run b = Run.start(catalog, "tails", 4242);
        new RunBot(a, 1).play(3000);
        new RunBot(b, 1).play(3000);
        assertEquals(describe(a), describe(b));
    }
}
