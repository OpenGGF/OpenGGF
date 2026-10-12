package starpost.museum;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.state.SnapshotRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Farm;
import starpost.core.Farmers;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Kind;
import starpost.core.Plot;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;
import starpost.fishing.FishDef;
import starpost.fishing.FishTable;
import starpost.ruins.Badnik;
import starpost.ruins.RuinsContent;
import starpost.ruins.RuinsRules;

/** Tails's Workshop Museum: its collections, donations and milestones, where pieces come from, Hazel's cap, and the save. */
class MuseumRulesTest {
    private final Catalog catalog = new Catalog();

    private Game game() {
        Game game = new Game(catalog, 5);
        game.calendar.set(1, Calendar.SPRING, 3, Calendar.DAY_START);
        game.sections.add(new Museum());
        return game;
    }

    private Exhibits.Exhibit exhibit(String id) {
        for (Exhibits.Exhibit e : Exhibits.all(catalog)) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        throw new AssertionError(id);
    }

    @Test
    void theThreeCollectionsHoldTheValleysMineralsBadniksAndRelics() {
        List<Exhibits.Exhibit> all = Exhibits.all(catalog);
        assertEquals(3, all.size());
        assertEquals(8, exhibit(Exhibits.MINERALS).items().size());
        assertEquals(14, exhibit(Exhibits.SCRAP).items().size());
        assertEquals(10, exhibit(Exhibits.RELICS).items().size());
        Set<String> seen = new HashSet<>();
        for (Exhibits.Exhibit e : all) {
            for (String id : e.items()) {
                assertTrue(catalog.hasItem(id), id);
                assertTrue(seen.add(id), "one shelf each: " + id);
            }
            int last = 0;
            for (Exhibits.Milestone m : e.milestones()) {
                assertTrue(m.count() > last && m.count() <= e.items().size(), e.id() + " " + m.count());
                assertTrue(m.item() == null || catalog.hasItem(m.item()), m.item());
                assertTrue(m.rings() > 0 || m.item() != null, "every milestone pays something");
                last = m.count();
            }
            assertEquals(e.items().size(), last, "the last milestone is the whole collection");
        }
        for (String id : exhibit(Exhibits.MINERALS).items()) {
            assertEquals(Kind.MINERAL, catalog.item(id).kind(), id);
        }
        for (String id : exhibit(Exhibits.RELICS).items()) {
            assertEquals(Kind.RELIC, catalog.item(id).kind(), id);
        }
    }

    @Test
    void everyBadnikOnTheShelfHasASourceTheGameReallyHas() {
        Set<String> fromPops = new HashSet<>();
        fromPops.add(Finds.partOf("motobug"));
        for (int kind = 0; kind < Badnik.KINDS; kind++) {
            fromPops.add(Finds.partOf(Finds.ruinsKey(kind)));
        }
        Set<String> fromFishing = new HashSet<>();
        for (FishDef def : new FishTable().all()) {
            if (def.isBadnik()) {
                fromFishing.add(def.id());
            }
        }
        for (String id : exhibit(Exhibits.SCRAP).items()) {
            assertTrue(fromPops.contains(id) || fromFishing.contains(id), id + " can be found");
            String icon = catalog.item(id).icon();
            assertTrue(icon.startsWith(MuseumContent.PART_ICON) || icon.startsWith("badnik:"), id + " pictures its badnik");
        }
        assertTrue(fromPops.contains("jaws_fin") && fromFishing.contains("jaws_fin"), "the Labyrinth's Jaws and the lake's are one fin");
    }

    @Test
    void everyRelicButTheCapIsInTheRuinsAndUnderground() {
        Set<String> ruins = new HashSet<>(), dug = new HashSet<>();
        for (int band = RuinsRules.MARBLE; band <= RuinsRules.SCRAP_BRAIN; band++) {
            ruins.addAll(Arrays.asList(Finds.ruinsRelics(band)));
        }
        for (int season = Calendar.SPRING; season <= Calendar.WINTER; season++) {
            dug.addAll(Arrays.asList(Finds.buried(season)));
        }
        for (String id : exhibit(Exhibits.RELICS).items()) {
            if (id.equals(MuseumContent.CAP)) {
                assertFalse(ruins.contains(id) || dug.contains(id), "the cap is only under the palms");
                continue;
            }
            assertTrue(ruins.contains(id), id + " in a Ruins band");
            assertTrue(dug.contains(id), id + " dug up in a season");
        }
    }

    @Test
    void badniksLeaveTheirPartsAboutOneTimeInFiveAndRocksARelicOneTimeInTwenty() {
        SnapshotRandom rng = new SnapshotRandom(99);
        int parts = 0, relics = 0;
        for (int i = 0; i < 5000; i++) {
            String part = Finds.ruinsPart(Badnik.ORBINAUT, rng);
            if (part != null) {
                assertEquals("orbinaut_core", part);
                parts++;
            }
            String relic = Finds.ruinsRelic(RuinsRules.LABYRINTH, rng);
            if (relic != null) {
                assertTrue(Arrays.asList(Finds.ruinsRelics(RuinsRules.LABYRINTH)).contains(relic), relic);
                relics++;
            }
        }
        assertTrue(parts > 800 && parts < 1200, "about 1000: " + parts);
        assertTrue(relics > 170 && relics < 330, "about 250: " + relics);
        assertNull(Finds.part("crabmeat", rng), "badniks the valley never pops leave nothing");
    }

    @Test
    void aGlintingSpotHoldsTheSeasonsRelicsChipsAGeodeOrRings() {
        Game game = game();
        game.calendar.set(1, Calendar.SUMMER, 4, Calendar.DAY_START);
        int relics = 0;
        for (int i = 0; i < 2000; i++) {
            String[] find = Finds.spot(game).split(":");
            int count = Integer.parseInt(find[1]);
            assertTrue(count >= 1);
            if (find[0].equals("rings")) {
                assertTrue(count >= 10 && count <= 30);
                continue;
            }
            assertTrue(catalog.hasItem(find[0]), find[0]);
            if (catalog.item(find[0]).kind() == Kind.RELIC) {
                assertTrue(Arrays.asList(Finds.buried(Calendar.SUMMER)).contains(find[0]));
                relics++;
            }
        }
        assertTrue(relics > 800 && relics < 1000, "45%: " + relics);
    }

    @Test
    void knucklesDigsTurnUpTheSeasonsRelicsNowAndThen() {
        Game digger = game();
        digger.farmer = Farmers.KNUCKLES;
        digger.calendar.set(1, Calendar.FALL, 3, Calendar.DAY_START);
        int relics = 0;
        for (int i = 0; i < 4000; i++) {
            String found = Farmers.dig(digger);
            if (found != null && !found.startsWith("rings:") && catalog.item(found).kind() == Kind.RELIC) {
                relics++;
                assertTrue(Arrays.asList(Finds.buried(Calendar.FALL)).contains(found), found);
            }
        }
        assertTrue(relics > 4000 / 64 / 2 && relics < 4000 / 64 * 2, "one dig in 64 or so: " + relics);
    }

    @Test
    void donatingGivesOneOfEachMissingPieceAndKeepsTheRest() {
        Game game = game();
        Museum museum = game.section(Museum.class);
        Exhibits.Exhibit minerals = exhibit(Exhibits.MINERALS);
        game.inventory.add(catalog.item("lava_ruby"), 3);
        game.inventory.add(catalog.item("marble_ore"), 1);
        game.inventory.add(catalog.item("totem_chip"), 1);
        Museum.Donation d = museum.donate(game, minerals);
        assertEquals(List.of("marble_ore", "lava_ruby"), d.given());
        assertEquals(2, game.inventory.total("lava_ruby"));
        assertEquals(0, game.inventory.total("marble_ore"));
        assertEquals(1, game.inventory.total("totem_chip"), "a relic is not a mineral");
        assertTrue(museum.donated("lava_ruby"));
        assertTrue(museum.donate(game, minerals).given().isEmpty(), "never the same piece twice");
        assertEquals(2, museum.count(minerals));
    }

    @Test
    void milestonesPayOnceAndAWaitingRewardPaysWhenThereIsRoom() {
        Game game = game();
        Museum museum = game.section(Museum.class);
        Exhibits.Exhibit minerals = exhibit(Exhibits.MINERALS);
        List<String> ids = minerals.items();
        for (int i = 0; i < 3; i++) {
            game.inventory.add(catalog.item(ids.get(i)), 1);
        }
        int rings = game.rings;
        Museum.Donation d = museum.donate(game, minerals);
        assertEquals(1, d.paid().size());
        assertEquals(rings + 800, game.rings);
        assertTrue(museum.donate(game, minerals).paid().isEmpty(), "paid once");
        // Fill every slot, then reach the sapling milestone: it waits for room.
        for (int i = 0; i < game.inventory.size(); i++) {
            game.inventory.set(i, "fibre", Inventory.MAX_STACK);
        }
        game.inventory.set(0, ids.get(3), 2);              // two each, so the slots stay full
        game.inventory.set(1, ids.get(4), 2);
        game.inventory.set(2, ids.get(5), 2);
        d = museum.donate(game, minerals);
        assertTrue(d.paid().isEmpty());
        assertTrue(d.waiting());
        assertFalse(game.flags.contains(Exhibits.CHERRY_FLAG));
        game.inventory.clear(5);
        assertEquals(1, museum.claim(game, minerals).size());
        assertEquals(1, game.inventory.total("chaos_cherry_sapling"));
        assertTrue(game.flags.contains(Exhibits.CHERRY_FLAG), "the flag reward: Tails can grow more");
    }

    @Test
    void aWholeCollectionsRecordOpensItsSongOnTheJukebox() {
        Game game = game();
        Museum museum = game.section(Museum.class);
        for (String id : exhibit(Exhibits.SCRAP).items()) {
            museum.debugDonate(game, id);
        }
        assertTrue(museum.complete(exhibit(Exhibits.SCRAP)));
        assertEquals(1, game.inventory.total("record_mini_boss"));
        assertTrue(game.flags.contains("record.s3k.18"));
        assertEquals(1, game.inventory.total("caterkiller_crawler"));
        assertEquals(Game.START_RINGS + 2500, game.rings);
        for (String record : List.of("record_lava_reef", "record_mini_boss", "record_sandopolis")) {
            assertNotNull(MuseumContent.recordFlag(record));
            assertEquals(Kind.RELIC, catalog.item(record).kind());
        }
        assertNull(MuseumContent.recordFlag("record_marble"), "the Ruins' Records stay theirs");
        assertNotNull(RuinsContent.recordFlag("record_marble"));
    }

    @Test
    void hazelsCapIsBuriedOnlyWhileItIsMissingFromTheShelfAndFromTheFarmer() {
        Game game = game();
        Museum museum = game.section(Museum.class);
        assertFalse(museum.capBuried(game), "nothing is missing before Hazel's four-heart event");
        game.flags.add(Museum.MISSING);
        assertTrue(museum.capBuried(game));
        game.inventory.add(catalog.item(MuseumContent.CAP), 1);
        assertFalse(museum.capBuried(game), "carried");
        game.inventory.remove(MuseumContent.CAP, 1);
        game.farm.plot(0, 2).object = "item_monitor";
        game.farm.chest(0, 2, 24).add(catalog.item(MuseumContent.CAP), 1);
        assertFalse(museum.capBuried(game), "kept in an Item Monitor");
        game.farm.chests.clear();
        assertTrue(museum.capBuried(game), "lost (given away): it can be found again");
        game.inventory.add(catalog.item(MuseumContent.CAP), 1);
        assertFalse(museum.capJustReturned(game));
        museum.donate(game, exhibit(Exhibits.RELICS));
        assertTrue(museum.capJustReturned(game), "Hazel's scene is due");
        assertFalse(museum.capBuried(game));
        game.flags.add(Museum.RETURNED);
        assertFalse(museum.capJustReturned(game), "and plays once");
        assertEquals(0, catalog.item(MuseumContent.CAP).price(), "the museum's own cap can't be shipped");
    }

    @Test
    void glintsAppearOnOpenGrassOneForMostFarmersTwoForKnucklesAndMoveDaily() {
        Game sonic = game();
        Game knuckles = game();
        knuckles.farmer = Farmers.KNUCKLES;
        List<int[]> today = DigSpots.farm(sonic);
        assertEquals(1, today.size());
        assertEquals(2, DigSpots.farm(knuckles).size());
        int[] rc = today.get(0);
        Plot plot = sonic.farm.plot(rc[0], rc[1]);
        assertTrue(rc[1] >= Farm.STARTER_PATCH && rc[1] < sonic.farm.open());
        assertTrue(!plot.tilled && plot.cover == Plot.GRASS && plot.object == null && plot.crop == null);
        assertArrayEquals(rc, DigSpots.farm(sonic).get(0), "the same all day");
        int x = DigSpots.valleyX(sonic, 13 * 256);
        assertTrue(x >= 300 && x < 13 * 256 - 300);
        boolean moved = false;
        for (int day = 4; day < 10 && !moved; day++) {
            sonic.calendar.set(1, Calendar.SPRING, day, Calendar.DAY_START);
            moved = !Arrays.equals(rc, DigSpots.farm(sonic).get(0));
        }
        assertTrue(moved, "a new place on another morning");
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLUMNS; c++) {
                sonic.farm.raw(r, c).tilled = true;
            }
        }
        assertTrue(DigSpots.farm(sonic).isEmpty(), "never on tilled soil");
    }

    @Test
    void theMuseumRoundTripsThroughTheSaveAndDropsWhatItDoesNotKnow() {
        Game game = game();
        Museum museum = game.section(Museum.class);
        museum.debugDonate(game, "lava_ruby");
        museum.debugDonate(game, "totem_chip");
        museum.debugDonate(game, "chopper_shell");
        for (String id : exhibit(Exhibits.MINERALS).items().subList(0, 3)) {
            museum.debugDonate(game, id);
        }
        List<SaveSection> sections = List.of(new Museum());
        Game back = SaveCodec.decode(catalog, SaveCodec.encode(game), sections);
        Museum loaded = back.section(Museum.class);
        assertTrue(loaded.donated("lava_ruby") && loaded.donated("totem_chip") && loaded.donated("chopper_shell"));
        assertTrue(loaded.claimed(exhibit(Exhibits.MINERALS), exhibit(Exhibits.MINERALS).milestones().get(0)));
        Museum damaged = new Museum();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("donated", "lava_ruby,no_such_thing,palm_wood,,totem_chip");
        keys.put("claimed", "minerals:3,relics:x,attic:3,scrap:12345");
        damaged.load(keys, catalog);
        Map<String, String> out = new LinkedHashMap<>();
        damaged.save(out);
        assertEquals("lava_ruby,totem_chip", out.get("donated"), "unknown items and items no shelf holds are dropped");
        assertEquals("minerals:3", out.get("claimed"));
    }
}
