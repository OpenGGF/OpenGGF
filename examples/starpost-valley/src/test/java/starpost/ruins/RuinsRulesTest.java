package starpost.ruins;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.state.SnapshotRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;

/** The Ruins' rules without a ROM: bands, seeds, hits and rings, air, fainting, ore and the save. */
class RuinsRulesTest {
    private final Catalog catalog = new Catalog();

    @Test
    void bandsZonesAndMusic() {
        assertEquals(RuinsRules.MARBLE, RuinsRules.band(1));
        assertEquals(RuinsRules.MARBLE, RuinsRules.band(15));
        assertEquals(RuinsRules.LABYRINTH, RuinsRules.band(16));
        assertEquals(RuinsRules.LABYRINTH, RuinsRules.band(30));
        assertEquals(RuinsRules.SCRAP_BRAIN, RuinsRules.band(31));
        assertEquals(RuinsRules.SCRAP_BRAIN, RuinsRules.band(40));
        assertEquals(1, RuinsRules.zone(RuinsRules.MARBLE));
        assertEquals(3, RuinsRules.zone(RuinsRules.LABYRINTH));
        assertEquals(5, RuinsRules.zone(RuinsRules.SCRAP_BRAIN));
        assertEquals(0x83, RuinsRules.music(RuinsRules.MARBLE));
        assertEquals(0x82, RuinsRules.music(RuinsRules.LABYRINTH));
        assertEquals(0x86, RuinsRules.music(RuinsRules.SCRAP_BRAIN));
        assertEquals(List.of(1), RuinsRules.starts(0));
        assertEquals(List.of(1, 5, 10), RuinsRules.starts(10));
        assertEquals(9, RuinsRules.starts(40).size());
    }

    @Test
    void chamberSeedsHoldForADay() {
        assertEquals(RuinsRules.chamberSeed(9, 4, 7), RuinsRules.chamberSeed(9, 4, 7));
        assertNotEquals(RuinsRules.chamberSeed(9, 4, 7), RuinsRules.chamberSeed(9, 5, 7));
        assertNotEquals(RuinsRules.chamberSeed(9, 4, 7), RuinsRules.chamberSeed(9, 4, 8));
        assertNotEquals(RuinsRules.chamberSeed(9, 4, 7), RuinsRules.chamberSeed(10, 4, 7));
    }

    @Test
    void ringLossSpraysUpToThirtyTwoInMirroredPairsAtTwoSpeeds() {
        assertEquals(0, RuinsRules.ringBurst(0).length);
        assertEquals(5, RuinsRules.ringBurst(5).length);
        int[][] burst = RuinsRules.ringBurst(100);
        assertEquals(32, burst.length, "RLoss_Count caps the spray at 32");
        for (int i = 0; i < 32; i += 2) {
            assertEquals(-burst[i][0], burst[i + 1][0], "pair " + i + " mirrors x");
            assertEquals(burst[i][1], burst[i + 1][1], "pair " + i + " shares y");
        }
        // $288: angle $88, boost 2 (x4): CalcSine gives about (-0.195, -0.981).
        assertEquals(RuinsRules.sin(0x88) << 2, burst[0][0]);
        assertEquals(RuinsRules.cos(0x88) << 2, burst[0][1]);
        assertTrue(burst[0][1] < -900, "the first ring flies up fast");
        double fast = Math.hypot(burst[2][0], burst[2][1]);
        double slow = Math.hypot(burst[20][0], burst[20][1]);
        assertEquals(1024, fast, 12, "the first sixteen at boost 2");
        assertEquals(512, slow, 6, "the next sixteen at half speed");
        assertEquals(RuinsRules.sin(0x88) << 1, burst[16][0], "the angle wraps back to $88 for the second circle");
    }

    @Test
    void bouncesFollowTheRom() {
        assertEquals(-0x300, RuinsRules.bounce(0x400), "RLoss_Bounce: a quarter lost, upward");
        assertEquals(-0x400, RuinsRules.bopBounce(-0x500, true), "moving up: slowed by $100");
        assertEquals(-0x300, RuinsRules.bopBounce(0x300, true), "falling onto it: bounced back up");
        assertEquals(0x200, RuinsRules.bopBounce(0x300, false), "below it: $100 upward");
    }

    @Test
    void aHitScattersRingsAndAHitWithNoneFaints() {
        assertTrue(RuinsRules.faintsOnHit(0));
        assertFalse(RuinsRules.faintsOnHit(1));
        assertEquals(32, RuinsRules.scatterCount(250));
        assertEquals(255, RuinsRules.LOST_RING_FRAMES, "RLoss_Count's 255-frame timer");
        assertEquals(90, RuinsRules.RING_COLLECT_FLASH, "rings wait until the flashing is under 90 frames");
        assertEquals(120, RuinsRules.FLASH_FRAMES);
    }

    @Test
    void faintingLosesHalfOfUpToThreeStacksButNeverTools() {
        Game game = Game.fresh(catalog, 5, "sonic");          // water shield and 15 radish seeds
        game.inventory.add(catalog.item("marble_ore"), 9);
        game.inventory.add(catalog.item("scrap"), 4);
        game.inventory.add(catalog.item("lava_ruby"), 1);
        int before = game.inventory.total("ring_radish_seeds") + game.inventory.total("marble_ore")
                + game.inventory.total("scrap") + game.inventory.total("lava_ruby");
        List<RuinsRules.Loss> lost = RuinsRules.faint(game, new SnapshotRandom(3));
        assertEquals(3, lost.size());
        int total = 0;
        for (RuinsRules.Loss loss : lost) {
            assertNotEquals("water_shield", loss.id(), "tools are never lost");
            total += loss.count();
        }
        assertEquals(1, game.inventory.total("water_shield"));
        int after = game.inventory.total("ring_radish_seeds") + game.inventory.total("marble_ore")
                + game.inventory.total("scrap") + game.inventory.total("lava_ruby");
        assertEquals(before - total, after);
        for (RuinsRules.Loss loss : lost) {
            assertTrue(loss.count() >= 1);
        }
    }

    @Test
    void oreYieldsDoubleWithTheFireShieldAndRecordsComeOnce() {
        List<RuinsRules.Drop> plain = RuinsRules.oreYield(RuinsRules.MARBLE, 4, false, new SnapshotRandom(8), Set.of());
        List<RuinsRules.Drop> fire = RuinsRules.oreYield(RuinsRules.MARBLE, 4, true, new SnapshotRandom(8), Set.of());
        assertEquals(plain.size(), fire.size(), "the same roll, burned instead of rolled into");
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.get(i).id(), fire.get(i).id());
            int expect = plain.get(i).id().startsWith("record_") ? plain.get(i).count() : plain.get(i).count() * 2;
            assertEquals(expect, fire.get(i).count());
        }
        assertEquals("marble_chip", plain.get(0).id(), "every Marble rock gives marble chips");
        assertEquals("scrap", RuinsRules.oreYield(RuinsRules.SCRAP_BRAIN, 33, false, new SnapshotRandom(1), Set.of())
                .get(0).id());
        SnapshotRandom rng = new SnapshotRandom(11);
        boolean sawRecord = false, sawShard = false;
        for (int i = 0; i < 4000; i++) {
            for (RuinsRules.Drop d : RuinsRules.oreYield(RuinsRules.LABYRINTH, 20, false, rng, Set.of("record_drowning"))) {
                assertNotEquals("record_drowning", d.id(), "an owned Record never turns up again");
                assertTrue(catalog.hasItem(d.id()), d.id() + " is a registered item");
                sawShard |= d.id().equals("emerald_shard");
            }
            for (RuinsRules.Drop d : RuinsRules.oreYield(RuinsRules.LABYRINTH, 20, false, rng, Set.of())) {
                sawRecord |= d.id().equals("record_drowning");
            }
        }
        assertTrue(sawRecord && sawShard, "rare finds do turn up");
    }

    @Test
    void airShieldsAndLava() {
        assertTrue(RuinsRules.airWarning(25) && RuinsRules.airWarning(20) && RuinsRules.airWarning(15));
        assertFalse(RuinsRules.airWarning(24));
        assertEquals(5, RuinsRules.airNumber(11));
        assertEquals(-1, RuinsRules.airNumber(12));
        assertEquals(0, RuinsRules.airNumber(0));
        Game game = Game.fresh(catalog, 5, "sonic");
        game.inventory.select(0);
        assertTrue(RuinsRules.breathes(game), "the Water Shield breathes for you");
        assertFalse(RuinsRules.lavaImmune(game));
        game.inventory.add(catalog.item("fire_shield"), 1);
        game.inventory.select(2);
        assertEquals("fire_shield", game.inventory.selectedId());
        assertTrue(RuinsRules.lavaImmune(game));
        game.inventory.select(1);
        game.flags.add(RuinsSection.LAVA_FLAG);
        assertTrue(RuinsRules.lavaImmune(game), "a Fire Shield Pepper eaten today");
    }

    @Test
    void contentIsRegisteredWithRecordsAndPudsSeed() {
        for (String id : new String[] {"marble_ore", "lava_ruby", "tide_sapphire", "spark_topaz", "emerald_shard",
                "marble_geode", "tide_geode", "scrap_geode", "super_sunflower_seeds"}) {
            assertTrue(catalog.hasItem(id), id);
        }
        assertEquals(0x83, RuinsContent.recordSong("record_marble"));
        assertEquals("record.s1.92", RuinsContent.recordFlag("record_drowning"));
        assertEquals(-1, RuinsContent.recordSong("scrap"));
        assertTrue(RuinsRules.isRecord(catalog.item("record_final")));
    }

    @Test
    void theSectionRoundTripsAndRejectsDamage() {
        Game game = Game.fresh(catalog, 21, "tails");
        RuinsSection section = new RuinsSection();
        section.deepest = 15;
        section.seed = 987654321L;
        section.popped = 44;
        section.freed = 40;
        section.bestChamber = 17;
        section.seedFound = true;
        game.sections.add(section);
        String text = SaveCodec.encode(game);
        assertTrue(text.contains("s.ruins.deepest=15"));
        RuinsSection loaded = new RuinsSection();
        Game back = SaveCodec.decode(catalog, text, List.<SaveSection>of(loaded));
        assertNotNull(back);
        assertSame(loaded, back.section(RuinsSection.class));
        assertEquals(15, loaded.deepest);
        assertEquals(987654321L, loaded.seed);
        assertEquals(44, loaded.popped);
        assertEquals(40, loaded.freed);
        assertEquals(17, loaded.bestChamber);
        assertTrue(loaded.seedFound);
        assertEquals(text, SaveCodec.encode(back), "writes back identically");

        RuinsSection damaged = new RuinsSection();
        Map<String, String> keys = new HashMap<>();
        keys.put("deepest", "13");
        keys.put("popped", "-4");
        keys.put("best", "99");
        damaged.load(keys, catalog);
        assertEquals(10, damaged.deepest, "only elevator chambers count");
        assertEquals(0, damaged.popped);
        assertEquals(RuinsRules.CHAMBERS, damaged.bestChamber);
        assertNull(SaveCodec.decode(catalog, text.replace("s.ruins.seed=987654321", "s.ruins.seed=oops"),
                List.<SaveSection>of(new RuinsSection())), "an unreadable section rejects the save");

        assertTrue(section.reachElevator(20));
        assertFalse(section.reachElevator(20));
        assertFalse(section.reachElevator(21), "not an elevator chamber");
        game.flags.add(RuinsSection.LAVA_FLAG);
        section.nextDay(game);
        assertFalse(game.flags.contains(RuinsSection.LAVA_FLAG), "the pepper wears off overnight");
    }
}
