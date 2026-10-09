package starpost.festivals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.Kind;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;
import starpost.core.Skills;
import starpost.people.People;
import starpost.people.Spot;
import starpost.people.VillagerDef;
import starpost.valley.Runner;

/**
 * Festivals and the Signpost Board without a ROM: the calendar and its gathering of the valley,
 * each festival's scoring and winning rules, the board's requests, the secret friend's letter,
 * and the section's save (round trip and damaged input).
 */
class FestivalRulesTest {
    private final Catalog catalog = new Catalog();

    private Game game(String farmer) {
        Game game = Game.fresh(catalog, 42, farmer);
        game.sections.add(new People());
        game.sections.add(new Skills());
        game.sections.add(new Festivals());
        return game;
    }

    private static Festivals festivals(Game game) {
        return game.section(Festivals.class);
    }

    private static People people(Game game) {
        return game.section(People.class);
    }

    private static void at(Game game, int season, int day, int hhmm) {
        game.calendar.set(game.calendar.year(), season, day, hhmm / 100 * 60 + hhmm % 100);
    }

    // ------------------------------------------------------------------ the calendar

    @Test
    void theEightFestivalsFallOnTheirDaysTwoASeason() {
        FestivalBook book = new FestivalBook();
        assertEquals(8, book.all().size());
        String[] expected = {"ring_hunt@0/13", "sunflower_parade@0/24", "valley_race@1/11", "flickies@1/28",
            "valley_fair@2/16", "scrap_brain_night@2/27", "ice_cap@3/8", "star_light_feast@3/25"};
        for (String e : expected) {
            String id = e.substring(0, e.indexOf('@'));
            int season = Integer.parseInt(e.substring(e.indexOf('@') + 1, e.indexOf('/')));
            int day = Integer.parseInt(e.substring(e.indexOf('/') + 1));
            Festival f = book.on(season, day);
            assertNotNull(f, e);
            assertEquals(id, f.id);
            assertTrue(f.open < f.close, id + " opens before it closes");
        }
        Set<String> dates = new HashSet<>();
        int[] perSeason = new int[4];
        for (Festival f : book.all()) {
            assertTrue(dates.add(f.season + "/" + f.day), "one festival a day");
            perSeason[f.season]++;
        }
        for (int count : perSeason) {
            assertEquals(2, count);
        }
        assertNull(book.on(Calendar.SPRING, 12));
        assertTrue(book.get(FestivalBook.FLICKIES).night(), "the Flickies fly at night");
        assertTrue(book.get(FestivalBook.SCRAP_BRAIN).night());
        assertFalse(book.get(FestivalBook.RING_HUNT).night());
    }

    @Test
    void theMorningCardNamesTheFestivalAndItsEve() {
        Game game = game("sonic");
        at(game, Calendar.SPRING, 13, 600);
        assertEquals("RING HUNT TODAY AT 9AM", festivals(game).morningNote(game));
        at(game, Calendar.SPRING, 12, 600);
        assertEquals("TOMORROW: THE RING HUNT", festivals(game).morningNote(game));
        at(game, Calendar.SPRING, 2, 600);
        assertNull(festivals(game).morningNote(game));
        at(game, Calendar.WINTER, 28, 600);
        assertNull(festivals(game).morningNote(game), "spring's first day has no festival");
        // The card centres one line in the menu font (10 pixels a letter) on a 400-pixel screen.
        for (Festival f : festivals(game).book.all()) {
            for (int day : new int[] {f.day, f.day - 1}) {
                at(game, f.season, day, 600);
                String note = festivals(game).morningNote(game);
                assertTrue(note.length() <= 36, "the card's line fits the screen: " + note);
            }
        }
    }

    @Test
    void aFestivalDayGathersTheValleyAndLetsItGoAfter() {
        Game game = game("sonic");
        People people = people(game);
        VillagerDef tails = people.cast.get("tails");
        VillagerDef pud = people.cast.get("pud");
        VillagerDef totem = people.cast.get("elder_totem");
        VillagerDef moto = people.cast.get("moto");
        // Spring 13, the Ring Hunt: from half an hour before the doors to closing time.
        at(game, Calendar.SPRING, 13, 1000);
        Spot host = people.spotFor(tails, game);
        assertEquals("plaza", host.anchor(), "Tails hosts the hunt by its sign");
        assertFalse(host.inside());
        Spot crowd = people.spotFor(pud, game);
        assertEquals("plaza", crowd.anchor(), "Pud leaves his shack for the plaza");
        assertNotEquals(host.dx(), crowd.dx(), "the crowd stands apart from the host");
        assertNotEquals("plaza", people.spotFor(totem, game).anchor(), "the totem stays on its hill");
        assertTrue(people.spotFor(moto, game).farm(), "the pet stays on the farm");
        at(game, Calendar.SPRING, 13, 830);
        assertEquals("plaza", people.spotFor(pud, game).anchor(), "gathering half an hour early");
        at(game, Calendar.SPRING, 13, 800);
        assertEquals(scheduled(pud, game), people.spotFor(pud, game), "too early: Pud's own day");
        at(game, Calendar.SPRING, 13, 1400);
        assertEquals(scheduled(pud, game), people.spotFor(pud, game), "closed: back to the schedule");
        assertEquals(scheduled(tails, game), people.spotFor(tails, game));
        at(game, Calendar.SPRING, 14, 1000);
        assertEquals(scheduled(pud, game), people.spotFor(pud, game), "an ordinary day is the schedule's");
        // Every gathered villager has a distinct place in the crowd.
        at(game, Calendar.SPRING, 13, 1000);
        Set<Integer> places = new HashSet<>();
        for (VillagerDef v : people.cast.all()) {
            Spot s = people.spotFor(v, game);
            if (s != null && s.anchor().equals("plaza") && !s.farm() && people.present(v, game) && !v.isPet()) {
                assertTrue(places.add(s.dx()), v.id + " has a place of their own");
            }
        }
        assertTrue(places.size() >= 10, "most of the valley turns out");
    }

    private static Spot scheduled(VillagerDef v, Game game) {
        Calendar c = game.calendar;
        return v.schedule().resolve(c.season(), c.weekday(), game.raining, c.minutes(), game.flags);
    }

    @Test
    void theNightOfTheFlickiesGathersAtTheMeadowAfterDark() {
        Game game = game("sonic");
        People people = people(game);
        at(game, Calendar.SUMMER, 28, 2100);
        assertEquals("meadow", people.spotFor(people.cast.get("pip"), game).anchor(), "Pip hosts");
        assertEquals("meadow", people.spotFor(people.cast.get("knuckles"), game).anchor(),
                "Knuckles, arrived in summer, comes down from his ledge");
        at(game, Calendar.SPRING, 13, 1000);
        assertNotEquals("plaza", people.spotFor(people.cast.get("knuckles"), game) == null ? ""
                : people.spotFor(people.cast.get("knuckles"), game).anchor(), "not before he has arrived");
    }

    @Test
    void aFarmingHeroHandsTheHostingOver() {
        Festival hunt = new FestivalBook().get(FestivalBook.RING_HUNT);
        assertEquals("tails", Festivals.host(hunt, game("sonic")));
        assertEquals("sonic", Festivals.host(hunt, game("tails")));
        assertEquals("sonic", RingHunt.champion(game("tails")), "Sonic defends the Ring Hunt when Tails farms");
    }

    // ------------------------------------------------------------------ the Ring Hunt

    @Test
    void theRingHuntChampionKeepsTheTitleOnATie() {
        assertFalse(RingHunt.farmerWins(20, 20));
        assertTrue(RingHunt.farmerWins(21, 20));
        assertFalse(RingHunt.farmerWins(0, 0));
    }

    @Test
    void theRingHuntLayoutIsTheYearsAndTheChampionLeavesRingsToWin() {
        List<RingHunt.Spot> year1 = RingHunt.layout(1, x -> 192, 1812, 1824, 2016, 96);
        List<RingHunt.Spot> again = RingHunt.layout(1, x -> 192, 1812, 1824, 2016, 96);
        List<RingHunt.Spot> year2 = RingHunt.layout(2, x -> 192, 1812, 1824, 2016, 96);
        assertEquals(year1, again, "the same year lays the same rings");
        assertNotEquals(year1, year2, "a new year, a new hunt");
        assertTrue(year1.size() >= 50, "plenty of rings: " + year1.size());
        // The champion alone, for the whole minute, from the plaza.
        RingHunt.Champion champion = new RingHunt.Champion(712, 156);
        boolean[] taken = new boolean[year1.size()];
        for (int tick = 0; tick < RingHunt.TICKS; tick++) {
            champion.step(year1, taken);
        }
        assertTrue(champion.score >= 20, "the champion is a real rival: " + champion.score);
        assertTrue(champion.score <= year1.size() / 2,
                "but leaves more than half the rings to a farmer who keeps moving: " + champion.score + " of " + year1.size());
    }

    @Test
    void theRingHuntsTownRingsHangUnderTheShopSigns() {
        int floor = 192, spring = 1812, ledgeTop = 96;
        for (int year = 1; year <= 5; year++) {
            for (RingHunt.Spot s : RingHunt.layout(year, x -> floor, spring, 1824, 2016, ledgeTop)) {
                boolean special = s.y() == ledgeTop - 16 || Math.abs(s.x() - (spring + 6)) < 1;
                if (!special) {
                    assertTrue(floor - s.y() <= 52, "a ring in the street hangs under the name boards: " + s);
                    assertTrue(floor - s.y() >= 16, "and above the grass: " + s);
                }
            }
        }
    }

    @Test
    void theRingHuntsFirstWinTakesTheRecordAndLaterWinsAPurse() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        int rings = game.rings;
        List<String> notices = RingHunt.reward(game, festivals, 25, 20);
        assertEquals(rings + 25 * RingHunt.RINGS_EACH, game.rings);
        assertTrue(game.inventory.total("record_special_stage") == 1, "the Special Stage Record");
        assertTrue(game.flags.contains("record.s1.89"), "its song opens on the jukebox");
        assertTrue(festivals.trophies().contains(FestivalBook.RING_HUNT), "the cup goes on the shelf");
        assertEquals(1, festivals.place(FestivalBook.RING_HUNT, 1));
        assertFalse(notices.isEmpty());
        game.calendar.set(2, Calendar.SPRING, 13, 900);
        rings = game.rings;
        RingHunt.reward(game, festivals, 30, 10);
        assertEquals(rings + 30 * RingHunt.RINGS_EACH + RingHunt.WIN_PURSE, game.rings, "a purse, not a second Record");
        assertEquals(1, game.inventory.total("record_special_stage"));
        rings = game.rings;
        RingHunt.reward(game, festivals, 5, 30);
        assertEquals(rings + 5 * RingHunt.RINGS_EACH, game.rings, "losing still pays the rings found");
        assertEquals(30, festivals.best(FestivalBook.RING_HUNT));
    }

    // ------------------------------------------------------------------ the Sunflower Parade

    @Test
    void theParadesJudgeLikesSunflowersAndSniffsOutPlastic() {
        Game game = game("sonic");
        List<Parade.Entry> rivals = Parade.rivals(game);
        Parade.Entry robotnik = rivals.stream().filter(e -> e.who().equals("robotnik")).findFirst().orElseThrow();
        assertEquals(0, robotnik.score(), "Robotnik's plastic sunflower is disqualified");
        assertEquals(1, Parade.place(Parade.score("sunflower"), rivals), "a grown sunflower wins");
        assertEquals(1, Parade.place(Parade.score("super_sunflower"), rivals));
        assertTrue(Parade.place(Parade.score("spring_tulip"), rivals) > 1, "a tulip is a lovely runner-up");
        assertEquals(0, Parade.place(Parade.score("ring_radish"), rivals), "a radish is not a flower");
        assertFalse(Parade.flower("ring_radish"));
    }

    @Test
    void theParadesFirstWinGivesPudsGoldenSeedOnce() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        Parade.reward(game, festivals, "sunflower");
        assertEquals(1, game.inventory.total("super_sunflower_seeds"));
        assertTrue(festivals.trophies().contains(FestivalBook.PARADE));
        Parade.reward(game, festivals, "sunflower");
        assertEquals(1, game.inventory.total("super_sunflower_seeds"), "the golden seed is a one-time prize");
        int before = game.inventory.total("sunflower_seeds");
        Parade.reward(game, festivals, null);
        assertEquals(before + Parade.SPECTATOR_SEEDS, game.inventory.total("sunflower_seeds"),
                "spectators go home with sunflower seeds");
    }

    // ------------------------------------------------------------------ the Great Valley Race

    @Test
    void theRaceFieldDependsOnWhoFarmsAndWhoHasArrived() {
        Game sonic = game("sonic");
        at(sonic, Calendar.SUMMER, 11, 900);
        assertEquals(List.of("tails", "knuckles", "robotnik"), Race.rivals(sonic));
        Game tails = game("tails");
        at(tails, Calendar.SUMMER, 11, 900);
        assertEquals(List.of("sonic", "knuckles", "robotnik"), Race.rivals(tails));
        Game early = game("knuckles");
        at(early, Calendar.SPRING, 5, 900);
        assertEquals(List.of("tails", "robotnik"), Race.rivals(early));
    }

    /** A flat, endless track for racing without a ROM. */
    private static final class Flat implements Runner.Ground {
        @Override
        public boolean solid(int x, int y) {
            return y >= 192;
        }

        @Override
        public int floorBelow(int x, int fromY) {
            return Math.max(192, fromY);
        }

        @Override
        public int left() {
            return -1_000_000;
        }

        @Override
        public int right() {
            return 1_000_000;
        }
    }

    /** Races a farmer (holding right, with or without spin dashes) against the field; returns the place. */
    private static int race(boolean spinDashes) {
        Game game = Game.fresh(new Catalog(), 1, "sonic");
        Flat ground = new Flat();
        int distance = Race.LAPS * 18 * 256;
        List<Race.Rival> rivals = new ArrayList<>();
        for (String who : new String[] {"tails", "knuckles", "robotnik"}) {
            rivals.add(new Race.Rival(who, 0, 192));
        }
        Runner farmer = new Runner(0, 192);
        int finish = -1;
        int dashTimer = 0;
        for (int tick = -Race.COUNTDOWN; tick < 60 * 120 && (finish < 0 || rivals.stream().anyMatch(r -> r.finish < 0));
                tick++) {
            float leader = farmer.x;
            for (Race.Rival r : rivals) {
                leader = Math.max(leader, r.x());
            }
            for (Race.Rival r : rivals) {
                r.step(ground, tick, leader, 18 * 256);
                if (r.finish < 0 && r.x() >= distance) {
                    r.finish = tick;
                }
            }
            boolean down = false, jump = false, left = false, right = tick >= 0;
            if (spinDashes) {
                // A practised racer: rev a spin dash, roll while it beats running, hop out, brake
                // on landing, and rev again (the cycle averages about a fifth faster than running).
                if (tick < 0) {
                    down = tick > -60;
                    jump = tick > -60 && tick % 10 == 0;
                    right = false;
                } else if (farmer.dashing) {
                    down = ++dashTimer < 30;
                    jump = dashTimer % 8 == 0;
                    right = false;
                } else if (farmer.rolling && farmer.onGround) {
                    jump = farmer.speed < Runner.TOP + 0.5f;
                    right = false;
                } else if (farmer.onGround && farmer.speed >= 1) {
                    left = true;
                    right = false;
                } else if (farmer.onGround) {
                    down = true;
                    jump = true;
                    right = false;
                    dashTimer = 0;
                }
            }
            farmer.step(ground, left, right, down, jump, jump, tick);
            if (finish < 0 && farmer.x >= distance) {
                finish = tick;
            }
        }
        game.rings = 0;
        return Race.place(finish, rivals);
    }

    @Test
    void holdingRightFinishesWithThePackButSpinDashingWins() {
        assertTrue(race(false) > 1, "running alone does not beat Tails's start and Robotnik's booster");
        assertEquals(1, race(true), "a farmer who spin dashes and keeps the roll wins");
    }

    @Test
    void robotnikBoostsOnceALapWhenBehind() {
        Race.Rival egg = new Race.Rival("robotnik", 0, 192);
        Flat ground = new Flat();
        for (int tick = 0; tick < 10; tick++) {
            egg.step(ground, tick, 0, 4608);
        }
        assertFalse(egg.boosting(), "no boost while leading");
        egg.step(ground, 10, egg.eggX + Race.EGG_BEHIND + 1, 4608);
        assertTrue(egg.boosting(), "the booster fires when he falls behind");
        for (int tick = 11; tick < 11 + Race.EGG_BOOST_TICKS + 5; tick++) {
            egg.step(ground, tick, egg.eggX + 1000, 4608);
        }
        assertFalse(egg.boosting(), "once per lap: not again on the same lap");
    }

    @Test
    void theRacesFirstWinBringsSpeedShoesForGood() {
        Game game = game("sonic");
        int max = game.maxMomentum;
        Race.reward(game, festivals(game), 1, 1800);
        assertEquals(max + Prizes.SPEED_SHOES, game.maxMomentum, "more Momentum, permanently");
        assertTrue(game.flags.contains("speed_shoes"));
        Race.reward(game, festivals(game), 1, 1700);
        assertEquals(max + Prizes.SPEED_SHOES, game.maxMomentum, "one pair of shoes");
        assertEquals(1700, festivals(game).bestTime(FestivalBook.RACE));
        assertEquals(2, Race.place(-1, List.of(finished("tails", 100))), "an unfinished farmer is behind finishers");
    }

    private static Race.Rival finished(String who, int tick) {
        Race.Rival r = new Race.Rival(who, 0, 192);
        r.finish = tick;
        return r;
    }

    // ------------------------------------------------------------------ the Night of the Flickies

    @Test
    void asManyFlickiesFlyAsTheValleyHasFreed() {
        Game game = game("sonic");
        assertEquals(6, FlickyNight.count(game), "the six who were always here");
        game.population = 42;
        assertEquals(42, FlickyNight.count(game));
        List<FlickyNight.Bird> flock = FlickyNight.flock(42);
        assertEquals(42, flock.size());
        assertEquals(5, FlickyNight.waves(42), "waves of nine");
        assertEquals(0, flock.get(0).rank(), "each wave has a leader at the front");
        assertEquals(0, flock.get(9).rank());
        assertTrue(FlickyNight.overhead(0, FlickyNight.waveStart(0) + 100));
        assertFalse(FlickyNight.overhead(0, FlickyNight.waveStart(0) + FlickyNight.OVERHEAD + 1));
    }

    @Test
    void theNightPaysInFriendshipAndPipsRecordOnce() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        People people = people(game);
        FlickyNight.reward(game, festivals, 3);
        assertEquals(30 + 70 + 30, people.bond("pip").points, "everyone 30, Pip more for each wave");
        assertEquals(30, people.bond("dandel").points);
        assertEquals(1, game.inventory.total("record_migration"));
        FlickyNight.reward(game, festivals, 0);
        assertEquals(1, game.inventory.total("record_migration"));
    }

    // ------------------------------------------------------------------ the Valley Fair

    @Test
    void theSlotBoothPaysLikeCasinoNight() {
        assertEquals(30, Fair.slotReward(Fair.SONIC, Fair.SONIC, Fair.SONIC));
        assertEquals(150, Fair.slotReward(Fair.JACKPOT, Fair.JACKPOT, Fair.JACKPOT));
        assertEquals(60, Fair.slotReward(Fair.JACKPOT, Fair.SONIC, Fair.SONIC), "a jackpot beside a pair doubles");
        assertEquals(60, Fair.slotReward(Fair.SONIC, Fair.SONIC, Fair.JACKPOT));
        assertEquals(60, Fair.slotReward(Fair.SONIC, Fair.JACKPOT, Fair.SONIC));
        assertEquals(120, Fair.slotReward(Fair.JACKPOT, Fair.JACKPOT, Fair.SONIC), "two jackpots quadruple");
        assertEquals(120, Fair.slotReward(Fair.SONIC, Fair.JACKPOT, Fair.JACKPOT));
        assertEquals(120, Fair.slotReward(Fair.JACKPOT, Fair.SONIC, Fair.JACKPOT));
        assertEquals(4, Fair.slotReward(Fair.BAR, Fair.SONIC, Fair.BAR), "two rings a bar");
        assertEquals(0, Fair.slotReward(Fair.SONIC, Fair.TAILS, Fair.RING));
        assertEquals(-Fair.ROBOTNIK_TAKES, Fair.payout(Fair.ROBOTNIK_FACE, Fair.ROBOTNIK_FACE, Fair.ROBOTNIK_FACE),
                "three Robotniks take rings");
        assertEquals(30 * Fair.RING_VALUE, Fair.payout(Fair.SONIC, Fair.SONIC, Fair.SONIC));
        assertEquals(8, Fair.strip(0).length);
    }

    @Test
    void robotnikJudgesTheGrangeAndOnlyTheUndeniableBeatsHim() {
        Game game = game("sonic");
        List<String> modest = List.of("ring_radish", "ring_radish", "palm_wood");
        assertTrue(Fair.place(Fair.score(game, modest)) > 1, "a modest table loses to his hamper");
        List<String> nineOfFourKinds = List.of("checker_cauliflower", "totem_leek", "chili_dog", "marble_ore",
                "palm_coconut", "emerald_melon", "lava_ruby", "eggman_pumpkin", "frost_ring");
        assertEquals(2, Fair.place(Fair.score(game, nineOfFourKinds)), "rich, but four kinds: a close second");
        List<String> grand = List.of("checker_cauliflower", "totem_leek", "chili_dog", "marble_ore", "palm_coconut",
                "palm_wood", "lava_ruby", "eggman_pumpkin", "frost_ring");
        int score = Fair.score(game, grand);
        assertTrue(score >= Fair.ROBOTNIK + Fair.UNDENIABLE, "nine goods of five kinds: undeniable, " + score);
        assertEquals(1, Fair.place(score));
        List<String> withCola = List.of("ring_radish", "ring_radish", "palm_wood", "robo_cola");
        assertTrue(Fair.score(game, withCola) - Fair.score(game, modest) >= Fair.ROBOMART_BIAS + 3 + 6,
                "Robomart goods impress him, on top of a new item and a new kind");
        assertEquals(0, Fair.place(Fair.score(game, List.of())), "no table, no place");
        assertFalse(Fair.displayable(game.item("water_shield")), "tools are not goods");
    }

    @Test
    void theSpringTestRingsTheBellOnlyNearFullPower() {
        assertTrue(Fair.height(100) >= Fair.BELL, "full power rings the bell");
        assertTrue(Fair.height(50) < Fair.BELL);
        assertTrue(Fair.height(80) > Fair.height(40), "more power, more height");
        assertEquals(0, Fair.meter(0));
        assertEquals(100, Fair.meter(Fair.METER_PERIOD / 2));
        assertEquals(120, Fair.strengthRings(Fair.BELL));
        assertEquals(0, Fair.strengthRings(10));
    }

    @Test
    void anEmptyGrangeTableEndsTheFairWithNoPlace() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        int rings = game.rings;
        Fair.reward(game, festivals, Fair.score(game, java.util.Arrays.asList(null, null, null)));
        assertEquals(rings, game.rings, "nothing judged, nothing paid");
        assertTrue(festivals.joined(FestivalBook.FAIR, 1), "but the fair was joined: it is over for the year");
        assertEquals(0, festivals.place(FestivalBook.FAIR, 1));
        assertEquals("JOINED", festivals.resultLine(FestivalBook.FAIR, 1));
        assertFalse(festivals.trophies().contains(FestivalBook.FAIR));
    }

    @Test
    void theFairsBlueRibbonPaysOnceInFull() {
        Game game = game("sonic");
        int rings = game.rings;
        Fair.reward(game, festivals(game), Fair.ROBOTNIK + Fair.UNDENIABLE);
        assertEquals(rings + Fair.FIRST_PURSE, game.rings);
        Fair.reward(game, festivals(game), Fair.ROBOTNIK + Fair.UNDENIABLE);
        assertEquals(rings + Fair.FIRST_PURSE + Fair.REPEAT_PURSE, game.rings);
    }

    // ------------------------------------------------------------------ Scrap Brain Night

    @Test
    void theHauntedMazeIsAPerfectMazeEachYear() {
        for (int seed : new int[] {1, 7, 1234, 99_999}) {
            Maze maze = new Maze(seed);
            int cells = Maze.COLUMNS * Maze.ROWS;
            assertEquals(cells, maze.reachable(), "every cell can be reached");
            assertEquals(cells - 1, maze.passages(), "and by one way only");
            for (int c : maze.treasures) {
                assertEquals(3, Integer.bitCount(maze.walls[c]), "treasure waits in dead ends");
            }
        }
        assertEquals(java.util.Arrays.toString(new Maze(5).walls), java.util.Arrays.toString(new Maze(5).walls));
        assertNotEquals(java.util.Arrays.toString(new Maze(5).walls), java.util.Arrays.toString(new Maze(6).walls));
    }

    @Test
    void reachingMechaSonicPaysAndTheFirstTimeGivesTheDeathEggRecord() {
        Game game = game("sonic");
        int rings = game.rings;
        Maze.reward(game, festivals(game), 10, 2, true, 3000);
        assertEquals(rings + 10 * Maze.RINGS_EACH + Maze.FINISH_PURSE, game.rings);
        assertEquals(1, game.inventory.total("record_death_egg"));
        assertEquals(2, game.inventory.total("scrap"));
        rings = game.rings;
        Maze.reward(game, festivals(game), 4, 0, false, 5400);
        assertEquals(rings + 4 * Maze.RINGS_EACH, game.rings, "lost in the dark: the rings found");
    }

    // ------------------------------------------------------------------ the Ice Cap Festival

    @Test
    void theSnowboardRunStartsAndEndsOnTheFlatAndScoresItsWay() {
        int[] course = Snowboard.course(1);
        assertEquals(60, course[0]);
        assertEquals(60, course[course.length - 1]);
        assertNotEquals(java.util.Arrays.toString(course), java.util.Arrays.toString(Snowboard.course(2)));
        assertEquals(10 * Snowboard.RING_POINTS + 2 * Snowboard.TRICK_POINTS + 10 * Snowboard.TIME_POINTS,
                Snowboard.score(Snowboard.PAR_TICKS - 600, 10, 2), "rings, tricks, and seconds under par");
        assertEquals(0, Snowboard.score(Snowboard.PAR_TICKS + 600, 0, 0), "no points for being slow");
        assertFalse(Snowboard.beatsFrost(Snowboard.FROST_RECORD));
        assertTrue(Snowboard.beatsFrost(Snowboard.FROST_RECORD + 1));
    }

    @Test
    void theSnowboardCourseRunsOnlyOverOpenAirBlocks() {
        Set<Integer> open = Set.of(60, 2, 37, 40, 41, 45, 42, 46, 1, 7);
        for (int year = 1; year <= 30; year++) {
            for (int block : Snowboard.course(year)) {
                assertTrue(open.contains(block), "year " + year + " lays block " + block + ", which has cave walls");
            }
        }
    }

    @Test
    void theIceCapContestFallsBackToTheSnowboardWithoutFishing() {
        Festivals festivals = new Festivals();
        assertNull(festivals.fishingContest, "fishing is not installed by the festivals themselves");
        Game game = game("sonic");
        Snowboard.reward(game, festivals(game), 1000, 10, true);
        assertEquals(1, game.inventory.total("record_icecap_s3"));
        assertTrue(game.flags.contains("record.s3k.10b"));
    }

    @Test
    void theIceCapFishingContestScoresAgainstFrostsCatchAndKeepsItsOwnBest() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        int record = FishingContest.FROST_CATCH;
        int rings = game.rings;
        Snowboard.fishingReward(game, festivals, record + 1, record);
        assertEquals(rings + (record + 1) * Snowboard.RINGS_EACH, game.rings, "each point pays like a ring on the run");
        assertEquals(1, festivals.place(FestivalBook.ICE_CAP, 1), "out-fishing Frost wins the festival");
        assertEquals(1, game.inventory.total("record_icecap_s3"));
        assertTrue(festivals.trophies().contains(FestivalBook.ICE_CAP));
        assertEquals(0, festivals.best(FestivalBook.ICE_CAP), "the run's best is not a catch");
        assertEquals(record + 1, festivals.best(Snowboard.FISHING_BEST));
        game.calendar.set(2, Calendar.WINTER, 8, 900);
        rings = game.rings;
        Snowboard.fishingReward(game, festivals, record, record);
        assertEquals(2, festivals.place(FestivalBook.ICE_CAP, 2), "a tie leaves the record with Frost");
        assertEquals(rings + record * Snowboard.RINGS_EACH, game.rings);
        Snowboard.reward(game, festivals, 900, 0, false);
        assertEquals("RUN 900, CATCH " + (record + 1), festivals.bestLine(FestivalBook.ICE_CAP));
        assertEquals(record + 1, festivals.best(Snowboard.FISHING_BEST), "a lower catch keeps the best");
    }

    // ------------------------------------------------------------------ trophies and records

    @Test
    void onlyTheSixContestsPutATrophyOnTheShelf() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        for (Festival f : festivals.book.all()) {
            festivals.takePrize("trophy." + f.id);
        }
        assertEquals(6, festivals.trophies().size(), "two tiers of three");
        assertFalse(festivals.trophies().contains(FestivalBook.FLICKIES), "the migration has no winner");
        assertFalse(festivals.trophies().contains(FestivalBook.FEAST), "nor the gifts");
        assertFalse(Festivals.hasTrophy(FestivalBook.FLICKIES));
        assertTrue(Festivals.hasTrophy(FestivalBook.RACE));
    }

    @Test
    void theRecordsPageSaysEachYearsResultAndBest() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        assertEquals("-", festivals.resultLine(FestivalBook.RING_HUNT, 1), "not joined yet");
        assertEquals("", festivals.bestLine(FestivalBook.RING_HUNT), "no best before the first hunt");
        RingHunt.reward(game, festivals, 20, 25);
        assertEquals("2ND", festivals.resultLine(FestivalBook.RING_HUNT, 1));
        assertEquals("20 RINGS", festivals.bestLine(FestivalBook.RING_HUNT));
        Race.reward(game, festivals, 1, 1830);
        assertEquals("WON", festivals.resultLine(FestivalBook.RACE, 1));
        assertEquals("30.5 SECONDS", festivals.bestLine(FestivalBook.RACE));
        FlickyNight.reward(game, festivals, 3);
        assertEquals("JOINED", festivals.resultLine(FestivalBook.FLICKIES, 1), "a night without winners");
        assertEquals("3 FLOCKS WAVED BACK", festivals.bestLine(FestivalBook.FLICKIES));
        Maze.reward(game, festivals, 4, 0, true, 3720);
        assertEquals("OUT IN 1:02", festivals.bestLine(FestivalBook.SCRAP_BRAIN));
        assertEquals("-", festivals.resultLine(FestivalBook.RING_HUNT, 2), "each year starts afresh");
    }

    // ------------------------------------------------------------------ the Star Light Feast

    @Test
    void aSecretGiftIsNamedAsAFriendWouldSayIt() {
        assertEquals("A CHILI DOG", Feast.some("CHILI DOG"));
        assertEquals("AN EMERALD MELON", Feast.some("EMERALD MELON"));
        assertEquals("RING RADISH SEEDS", Feast.some("RING RADISH SEEDS"), "a packet of seeds takes no article");
        assertEquals("A BUBBLE BASS", Feast.some("BUBBLE BASS"), "a single fish ending in SS keeps its article");
    }

    @Test
    void theSecretFriendIsDrawnOnWinter18AndTheLetterArrives() {
        Game game = game("sonic");
        People people = people(game);
        Festivals festivals = festivals(game);
        at(game, Calendar.WINTER, 17, 600);
        assertNull(festivals.secretFriend(game));
        game.sleep(false);
        assertEquals(18, game.calendar.day());
        String friend = festivals.secretFriend(game);
        assertNotNull(friend);
        VillagerDef v = people.cast.get(friend);
        assertFalse(v.isPet());
        assertTrue(people.present(v, game));
        people.morning(game);
        assertTrue(people.mailbox().contains(Festivals.SECRET_FLAG + friend), "the committee's letter comes by Flicky");
        String giver = festivals.secretGiver(game);
        assertNotNull(giver);
        assertNotEquals(friend, giver, "someone else brings the farmer's present");
    }

    @Test
    void aLovedSecretGiftCountsThreeTimesOver() {
        Game game = game("sonic");
        People people = people(game);
        VillagerDef hazel = people.cast.get("hazel");
        String loved = hazel.lovedItems().stream().filter(catalog::hasItem).findFirst().orElseThrow();
        game.inventory.add(game.item(loved), 1);
        Feast.reward(game, festivals(game), "hazel", game.item(loved), "tails");
        assertEquals(People.points(starpost.people.Taste.LOVE) * Feast.GIFT_FACTOR + Feast.EVERYONE,
                people.bond("hazel").points);
        assertEquals(0, game.inventory.total(loved), "the gift is given");
        assertEquals(1, game.inventory.total("buzz_waterer"), "Tails's present: a Buzz Bomber waterer");
        assertEquals(game.maxMomentum, game.momentum, "everyone eats");
    }

    // ------------------------------------------------------------------ the Signpost Board

    @Test
    void eachMorningANeighbourPinsUpARequestForSomethingObtainable() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        for (int day = 0; day < 20; day++) {
            festivals.morning(game);
            for (Request r : festivals.board.posted()) {
                assertNotEquals("robotnik", r.type == Request.SPECIAL ? "" : r.villager, "Robotnik only places orders");
                if (r.delivery()) {
                    assertTrue(catalog.hasItem(r.item), r.item);
                    if (game.item(r.item).kind() == Kind.FISH) {
                        assertTrue(game.item(r.item).price() > 0, r.item + " is a catch, not junk");
                    }
                    if (r.type == Request.DELIVER && game.item(r.item).kind() == Kind.CROP) {
                        assertTrue(catalog.crop(r.item).grows(game.calendar.season()), r.item + " grows now");
                    }
                }
                assertTrue(r.rings > 0 && r.count > 0);
            }
            assertTrue(festivals.board.posted().size() <= Board.MAX_POSTED);
            game.sleep(false);
        }
        assertTrue(festivals.board.posted().size() > 0);
    }

    @Test
    void mondaysBringRobotniksSpecialOrderOnceRobomartIsOpen() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        festivals.morning(game);   // Spring 1, a Monday, before Robomart opens
        assertTrue(festivals.board.posted().stream().noneMatch(r -> r.type == Request.SPECIAL));
        while (game.calendar.dayNumber() < 7) {
            game.sleep(false);
        }
        assertEquals(0, game.calendar.weekday(), "Spring 8 is a Monday");
        festivals.morning(game);
        Request special = festivals.board.posted().stream().filter(r -> r.type == Request.SPECIAL).findFirst()
                .orElseThrow();
        assertEquals("robotnik", special.villager);
        assertEquals("ring_radish", special.item);
        assertTrue(Board.specialHeadline(special, game).endsWith("NO QUESTIONS."));
        assertTrue(Board.specialHeadline(special, game).startsWith("500 "), "always a hundred times too many");
    }

    @Test
    void aDeliveryIsFinishedByTalkingToWhoeverAsked() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        People people = people(game);
        Request r = new Request(1, "dandel", Request.DELIVER, "ring_radish", 3, 200, 60, 0, 1);
        festivals.board.posted.add(r);
        assertTrue(festivals.board.accept(r, game));
        assertNull(people.errand("dandel", game), "nothing to hand over yet");
        game.inventory.add(game.item("ring_radish"), 5);
        assertNull(people.errand("pud", game), "only Dandel asked");
        int rings = game.rings;
        assertNotNull(people.errand("dandel", game));
        assertEquals(2, game.inventory.total("ring_radish"), "three handed over");
        assertEquals(rings + 200, game.rings);
        assertEquals(60, people.bond("dandel").points);
        assertTrue(festivals.board.active().isEmpty());
        assertEquals(1, festivals.board.completed());
        assertNull(people.errand("dandel", game), "done once");
    }

    @Test
    void robotniksOrdersPayHandsomelyWithARobomartCatch() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        People people = people(game);
        people.add("dandel", 500);
        Request r = new Request(1, "robotnik", Request.SPECIAL, "egg_plant", 20, 4800, 100, 0, 6);
        festivals.board.posted.add(r);
        festivals.board.accept(r, game);
        game.inventory.add(game.item("egg_plant"), 20);
        int rings = game.rings;
        assertNotNull(people.errand("robotnik", game));
        assertEquals(rings + 4800, game.rings);
        assertEquals(Board.SPECIAL_COLA, game.inventory.total("robo_cola"), "the balance in Robo Cola");
        assertEquals(500 - Board.SPECIAL_DANDEL_PENALTY, people.bond("dandel").points, "Dandel saw the Egg Truck");
        assertTrue(game.flags.contains("egg_supplier"));
        assertEquals(1, festivals.board.specialOrders());
    }

    @Test
    void poppingJobsFinishThemselvesAndOnlyThreeCanBeTakenOn() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        Request pop = new Request(1, "pud", Request.POP, null, 3, 150, 60, 0, 1);
        festivals.board.posted.add(pop);
        game.xp(Skills.BOPPING, 40);
        festivals.board.accept(pop, game);
        assertEquals(0, festivals.board.progress(pop, game), "counted from when it was taken on");
        game.xp(Skills.BOPPING, 2 * Request.XP_PER_POP);
        assertTrue(festivals.board.checkPops(game, people(game)).isEmpty(), "two of three");
        int rings = game.rings;
        game.xp(Skills.BOPPING, Request.XP_PER_POP);
        assertEquals(1, festivals.board.checkPops(game, people(game)).size());
        assertEquals(rings + 150, game.rings);
        for (int i = 2; i <= 5; i++) {
            Request r = new Request(i, "pud", Request.POP, null, 9, 10, 10, 0, 9);
            festivals.board.posted.add(r);
            boolean took = festivals.board.accept(r, game);
            assertEquals(i <= 4, took, "three at a time");
        }
    }

    @Test
    void requestsComeDownWhenTheirTimeRunsOut() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        Request r = new Request(1, "pud", Request.DELIVER, "ring_radish", 1, 50, 60, 0, 1);
        festivals.board.posted.add(r);
        festivals.board.accept(r, game);
        game.sleep(false);
        festivals.morning(game);
        assertEquals(1, festivals.board.active().size(), "day 1 is still in time");
        game.sleep(false);
        List<String> notices = festivals.morning(game);
        assertTrue(festivals.board.active().isEmpty(), "gone on day 2");
        assertTrue(notices.get(0).startsWith("REQUEST RAN OUT"));
    }

    // ------------------------------------------------------------------ saving

    private Game filled() {
        Game game = game("sonic");
        Festivals festivals = festivals(game);
        RingHunt.reward(game, festivals, 25, 20);
        Race.reward(game, festivals, 2, 2000);
        festivals.recordTime(FestivalBook.SCRAP_BRAIN, 3333);
        festivals.recordBest(Snowboard.FISHING_BEST, 47);
        festivals.debugSecret("hazel", 1);
        festivals.owe("chili_dog", 2);
        festivals.morning(game);
        Request r = new Request(festivals.board.nextId++, "pud", Request.POP, null, 4, 80, 60, 0, 2);
        festivals.board.posted.add(r);
        festivals.board.accept(r, game);
        return game;
    }

    private static List<SaveSection> sections() {
        return List.of(new People(), new Skills(), new Festivals());
    }

    @Test
    void theFestivalsSectionSurvivesASaveAndLoad() {
        Game game = filled();
        String text = SaveCodec.encode(game);
        Game loaded = SaveCodec.decode(catalog, text, sections());
        assertNotNull(loaded);
        assertEquals(text, SaveCodec.encode(loaded), "a loaded save writes back identically");
        Festivals f = festivals(loaded);
        assertEquals(1, f.place(FestivalBook.RING_HUNT, 1));
        assertEquals(25, f.best(FestivalBook.RING_HUNT));
        assertEquals(3333, f.bestTime(FestivalBook.SCRAP_BRAIN));
        assertEquals(47, f.best(Snowboard.FISHING_BEST), "the Ice Cap catch keeps its own best");
        assertTrue(f.prizeTaken("trophy." + FestivalBook.RING_HUNT));
        assertEquals("hazel", f.secretFriend(loaded));
        assertEquals(2, f.owed().get("chili_dog"));
        assertEquals(festivals(game).board.posted().size(), f.board.posted().size());
        assertEquals(1, f.board.active().size());
        assertEquals("pud", f.board.active().get(0).villager);
    }

    @Test
    void aDamagedFestivalsSectionIsRejectedOrCleaned() {
        String text = SaveCodec.encode(filled());
        assertNull(SaveCodec.decode(catalog, text.replace("s.festivals.version=1", "s.festivals.version=9"), sections()),
                "an unknown version is refused");
        assertNull(SaveCodec.decode(catalog, text.replaceFirst("(s\\.festivals\\.req\\.\\d+=)[^\\n]*", "$1pud,1,x"),
                sections()), "a damaged request is refused");
        assertNull(SaveCodec.decode(catalog, text.replace("s.festivals.boardday=", "s.festivals.boardday=x"),
                sections()), "a malformed number is refused");
        // Unknown festivals and items are dropped, numbers clamped.
        String odd = text + "s.festivals.place.martian_ball@1=1\ns.festivals.owed.moon_rock=3\n"
                + "s.festivals.place.ring_hunt@2=99\ns.festivals.best.martian_ball:fishing=5\n"
                + "s.festivals.best.ice_cap:=5\n";
        Game loaded = SaveCodec.decode(catalog, odd, sections());
        assertNotNull(loaded);
        Festivals f = festivals(loaded);
        assertEquals(-1, f.place("martian_ball", 1));
        assertFalse(f.owed().containsKey("moon_rock"));
        assertEquals(8, f.place(FestivalBook.RING_HUNT, 2), "places are clamped");
        assertEquals(0, f.best("martian_ball:fishing"), "a contest at an unknown festival is dropped");
        assertEquals(0, f.best("ice_cap:"), "and a contest without a name");
        assertEquals(0, SaveCodec.decode(catalog, SaveCodec.encode(game("sonic")), sections())
                .section(Festivals.class).board.completed(), "a fresh game loads empty");
    }
}
