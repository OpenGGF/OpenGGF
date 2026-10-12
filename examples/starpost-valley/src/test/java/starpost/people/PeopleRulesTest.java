package starpost.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;

/** The neighbours' rules without a ROM: friendship, gifts, schedules, events, the translator, the post, saves. */
class PeopleRulesTest {
    private final Catalog catalog = new Catalog();

    private Game game(People people, String farmer) {
        Game game = Game.fresh(catalog, 42, farmer);
        game.sections.add(people);
        return game;
    }

    /** Moves the calendar to a date and time without running the night. */
    private static void at(Game game, int season, int day, int hhmm) {
        game.calendar.set(game.calendar.year(), season, day, Schedule.minutes(hhmm));
    }

    @Test
    void talkingOnceADayAddsTwentyAndHeartsAre250Points() {
        People people = new People();
        Game game = game(people, "sonic");
        People.Talk first = people.talk("dandel", game);
        assertTrue(first.firstToday());
        assertEquals(People.TALK_POINTS, first.points());
        assertTrue(first.line().isFirst(), "the first conversation is the first-meeting line");
        People.Talk again = people.talk("dandel", game);
        assertFalse(again.firstToday());
        assertEquals(0, again.points(), "talking twice in a day earns nothing more");
        assertEquals(20, people.bond("dandel").points);
        people.add("dandel", 230);
        assertEquals(1, people.hearts("dandel"), "250 points is a heart");
    }

    @Test
    void villagersWithoutAPartnerArcStopAtEightHearts() {
        People people = new People();
        people.add("dandel", 99_999);
        people.add("tails", 99_999);
        assertEquals(8, people.hearts("dandel"));
        assertEquals(10, people.hearts("tails"), "Tails can become a Partner");
        people.add("dandel", -99_999);
        assertEquals(0, people.bond("dandel").points, "never below zero");
        long partners = people.cast.all().stream().filter(VillagerDef::canPartner).count();
        assertTrue(partners >= 2, "at least two Partners (Tails and Clementine)");
    }

    @Test
    void giftsScoreByTasteAndItemTastesOverrideKinds() {
        People people = new People();
        Game game = game(people, "sonic");
        VillagerDef dandel = people.cast.get("dandel");
        assertSame(Taste.LOVE, dandel.taste(catalog.item("ring_radish")));
        assertSame(Taste.DISLIKE, dandel.taste(catalog.item("egg_plant")), "an item entry beats the CROP kind's LIKE");
        assertSame(Taste.LIKE, dandel.taste(catalog.item("emerald_melon")), "the CROP kind");
        assertSame(Taste.HATE, dandel.taste(catalog.item("scrap")));
        assertSame(Taste.NEUTRAL, dandel.taste(catalog.item("palm_wood")));
        game.inventory.add(catalog.item("ring_radish"), 3);
        People.Gift gift = people.gift("dandel", catalog.item("ring_radish"), game);
        assertEquals(People.GiftStatus.GIVEN, gift.status());
        assertEquals(80, gift.points());
        assertEquals(2, game.inventory.total("ring_radish"), "the gift is taken from the monitors");
        assertTrue(people.bond("dandel").knownLoves.contains("ring_radish"), "a loved gift is discovered");
        assertEquals(People.GiftStatus.NOT_A_GIFT, people.canGift("dandel", catalog.item("water_shield"), game),
                "tools are not gifts");
        for (Taste taste : Taste.values()) {
            assertNotNull(People.points(taste));
        }
        assertEquals(-40, People.points(Taste.HATE));
    }

    @Test
    void oneGiftADayTwoAWeekAndBirthdaysCountEightTimesOutsideTheLimit() {
        People people = new People();
        Game game = game(people, "sonic");
        game.inventory.add(catalog.item("palm_wood"), 20);
        at(game, Calendar.SPRING, 1, 900);      // Monday
        assertEquals(People.GiftStatus.GIVEN, people.gift("rusty", catalog.item("palm_wood"), game).status());
        assertEquals(People.GiftStatus.ALREADY_TODAY, people.gift("rusty", catalog.item("palm_wood"), game).status());
        game.sleep(false);                       // Tuesday
        assertEquals(People.GiftStatus.GIVEN, people.gift("rusty", catalog.item("palm_wood"), game).status());
        game.sleep(false);                       // Wednesday
        assertEquals(People.GiftStatus.WEEK_FULL, people.gift("rusty", catalog.item("palm_wood"), game).status());
        // Rusty's birthday is Spring 26: a gift then is eight times, and allowed even with the week full.
        at(game, Calendar.SPRING, 26, 900);     // a Friday
        people.bond("rusty").giftsThisWeek = 2;
        int before = people.bond("rusty").points;
        People.Gift birthday = people.gift("rusty", catalog.item("palm_wood"), game);
        assertEquals(People.GiftStatus.GIVEN, birthday.status());
        assertEquals(45 * People.BIRTHDAY_FACTOR, people.bond("rusty").points - before, "liked, times eight");
        assertEquals(2, people.bond("rusty").giftsThisWeek, "birthday gifts don't use up the week");
        // Monday resets the week.
        at(game, Calendar.SPRING, 28, 900);     // Sunday
        game.sleep(false);
        assertEquals(Calendar.SUMMER, game.calendar.season());
        assertEquals(0, game.calendar.weekday(), "Summer 1 is a Monday");
        assertEquals(0, people.bond("rusty").giftsThisWeek);
    }

    @Test
    void friendshipFadesWithoutContactButNotForPartners() {
        People people = new People();
        Game game = game(people, "sonic");
        people.setHearts("dandel", 3);
        people.setHearts("tails", 10);
        people.bond("tails").partner = true;
        people.setHearts("pud", 3);
        people.talk("pud", game);
        int pud = people.bond("pud").points;
        game.sleep(false);
        assertEquals(750 - People.DECAY, people.bond("dandel").points, "no word all day");
        assertEquals(2500, people.bond("tails").points, "Partners don't fade");
        assertEquals(pud, people.bond("pud").points, "talked today");
        assertFalse(people.bond("pud").talkedToday, "a new day");
        assertEquals(0, people.bond("barnaby").points, "strangers stay at zero");
    }

    @Test
    void everyScheduleResolvesEveryTenMinutesOfEveryKindOfDay() {
        People people = new People();
        Set<String> flags = new HashSet<>();
        for (VillagerDef v : people.cast.all()) {
            assertFalse(v.schedule().plans().isEmpty(), v.id + " has a schedule");
            for (int season = 0; season < 4; season++) {
                for (int weekday = 0; weekday < 7; weekday++) {
                    for (boolean rain : new boolean[] {false, true}) {
                        for (int minute = Calendar.DAY_START; minute <= Calendar.DAY_END; minute += 10) {
                            Spot spot = v.schedule().resolve(season, weekday, rain, minute, flags);
                            assertNotNull(spot, v.id + " somewhere at " + minute);
                            assertTrue(Anchors.known(spot), v.id + " at a known spot: " + spot);
                        }
                    }
                }
            }
        }
        VillagerDef tails = people.cast.get("tails");
        assertEquals(Spot.indoors("workshop"), tails.schedule().resolve(0, 0, false, Schedule.minutes(700), flags));
        assertEquals("workshop_yard", tails.schedule().resolve(0, 0, false, Schedule.minutes(1000), flags).anchor());
        assertTrue(tails.schedule().resolve(0, 1, false, Schedule.minutes(1000), flags).farm(),
                "Tuesday mornings Tails visits the farm");
        assertTrue(tails.schedule().resolve(0, 1, true, Schedule.minutes(1000), flags).inside(),
                "rain keeps him indoors");
        VillagerDef rusty = people.cast.get("rusty");
        assertTrue(rusty.schedule().resolve(0, 6, false, Schedule.minutes(1000), flags).inside());
        flags.add("rusty_day_off");
        assertEquals("waterfall", rusty.schedule().resolve(0, 6, false, Schedule.minutes(1000), flags).anchor(),
                "a story flag changes a plan");
    }

    @Test
    void heroesAreAbsentWhenTheyFarmAndKnucklesArrivesInSummer() {
        People people = new People();
        Game tailsFarm = game(people, "tails");
        assertFalse(people.present(people.cast.get("tails"), tailsFarm));
        assertTrue(people.present(people.cast.get("sonic"), tailsFarm));
        assertFalse(people.present(people.cast.get("knuckles"), tailsFarm), "Knuckles arrives in Summer");
        at(tailsFarm, Calendar.SUMMER, 1, 600);
        assertTrue(people.present(people.cast.get("knuckles"), tailsFarm));
        Game sonicFarm = game(new People(), "sonic");
        assertFalse(people.present(people.cast.get("sonic"), sonicFarm));
    }

    @Test
    void heartEventsTriggerInTheirPlaceAndTimeOnlyOnce() {
        People people = new People();
        Game game = game(people, "sonic");
        at(game, Calendar.SPRING, 3, 1000);
        int workshop = Anchors.valleyX("workshop");
        assertNull(people.dueEvent(game, false, workshop, Anchors::valleyX), "not enough hearts yet");
        people.setHearts("tails", 2);
        HeartEvent event = people.dueEvent(game, false, workshop, Anchors::valleyX);
        assertNotNull(event);
        assertEquals("tails_2", event.id);
        assertNull(people.dueEvent(game, false, workshop + 800, Anchors::valleyX), "too far from the workshop");
        assertNull(people.dueEvent(game, true, workshop, Anchors::valleyX), "not on the farm");
        game.raining = true;
        assertNull(people.dueEvent(game, false, workshop, Anchors::valleyX), "a dry-day event");
        game.raining = false;
        at(game, Calendar.SPRING, 3, 2000);
        assertNull(people.dueEvent(game, false, workshop, Anchors::valleyX), "outside its hours");
        at(game, Calendar.SPRING, 3, 1000);
        people.begin(event, game);
        assertNull(people.dueEvent(game, false, workshop, Anchors::valleyX), "fires once");
        people.setHearts("tails", 4);
        assertNull(people.dueEvent(game, false, workshop, Anchors::valleyX), "the 4-heart event is on the farm");
        assertEquals("tails_4", people.dueEvent(game, true, 400, Anchors::valleyX).id);
        // A Partner event makes a Partner when it ends.
        HeartEvent partner = people.cast.event("tails_10");
        assertTrue(partner.isPartner());
        people.finish(partner);
        assertTrue(people.bond("tails").partner);
    }

    @Test
    void theTranslatorTurnsAnimalPicturesIntoWords() {
        People people = new People();
        Game game = game(people, "sonic");
        VillagerDef dandel = people.cast.get("dandel");
        VillagerDef tails = people.cast.get("tails");
        assertTrue(people.speaksInPictures(dandel, game));
        assertFalse(people.speaksInPictures(tails, game), "Tails always speaks in words");
        // Tails's 2-heart event sets the flag; Sonic's does when Tails farms.
        boolean sets = people.cast.event("tails_2").steps().stream()
                .anyMatch(s -> s.op() == Step.Op.FLAG && People.TRANSLATOR.equals(s.text()));
        assertTrue(sets, "tails_2 gives the Chirp Translator");
        boolean sonicSets = people.cast.event("sonic_2t").steps().stream()
                .anyMatch(s -> s.op() == Step.Op.FLAG && People.TRANSLATOR.equals(s.text()));
        assertTrue(sonicSets && "tails".equals(people.cast.event("sonic_2t").farmerOnly()));
        game.flags.add(People.TRANSLATOR);
        assertFalse(people.speaksInPictures(dandel, game));
        // Every animal line has pictures to show before that.
        for (VillagerDef v : people.cast.all()) {
            if (!v.isAnimal()) {
                continue;
            }
            for (Line line : v.lines()) {
                String[] pictures = Pictures.of(line, catalog, people.cast);
                assertTrue(pictures.length > 0 && pictures.length <= Pictures.MAX, v.id + ": " + line.text());
            }
        }
        String[] read = Pictures.fromText("ROBOTNIK STOLE MY RING RADISH SEEDS!", catalog, people.cast);
        assertEquals("robotnik", read[0]);
        assertEquals("item:ring_radish_seeds", read[1], "the longest item name wins");
        assertEquals("!", read[read.length - 1]);
    }

    @Test
    void picturesUseKnownGlyphsAndFaces() {
        People people = new People();
        Set<String> special = Set.of("farmer", "robotnik", "ring", "badnik", "flicky", "food", "?", "!", "...", "zzz");
        for (VillagerDef v : people.cast.all()) {
            for (Line line : allLines(v)) {
                String[] pics = line.pictures();
                if (pics == null) {
                    continue;
                }
                for (String token : pics) {
                    boolean ok = special.contains(token) || Glyphs.rows(token) != null || token.startsWith("item:")
                            || token.startsWith("who:") && people.cast.get(token.substring(4)) != null;
                    assertTrue(ok, v.id + " uses an unknown picture " + token);
                }
            }
        }
    }

    private static List<Line> allLines(VillagerDef v) {
        List<Line> out = new java.util.ArrayList<>(v.lines());
        for (Taste taste : Taste.values()) {
            out.addAll(v.giftLines(taste));
        }
        out.addAll(v.birthdayGiftLines());
        out.addAll(v.againLines());
        out.addAll(v.thanksLines());
        return out;
    }

    @Test
    void theCastHasItsLinesEventsAndPartners() {
        People people = new People();
        Cast cast = people.cast;
        assertTrue(cast.all().size() >= 14, "about fourteen neighbours");
        for (VillagerDef v : cast.all()) {
            assertTrue(v.lines().size() >= 13, v.id + " has daily lines: " + v.lines().size());
            for (Taste taste : Taste.values()) {
                assertFalse(v.giftLines(taste).isEmpty(), v.id + " reacts to a " + taste + " gift");
            }
            assertFalse(v.againLines().isEmpty(), v.id + " has something to say twice");
            for (Line thanks : v.thanksLines()) {
                assertTrue(Speech.wrap(thanks.text().replace("{FARMER}", "KNUCKLES"), LetterScreen.LINE_CHARS).size()
                        <= LetterScreen.MAX_LINES, v.id + " thank-you note fits");
            }
            for (Line line : allLines(v)) {
                for (String word : line.text().split("[ |]")) {
                    assertTrue(word.length() <= Speech.TEXT_CHARS, v.id + " word fits a line: " + word);
                }
            }
        }
        for (String id : List.of("tails", "robotnik", "pip", "dandel", "clementine", "knuckles")) {
            for (int hearts : new int[] {2, 4, 6, 8}) {
                assertNotNull(cast.event(id + "_" + hearts), id + " has a " + hearts + "-heart event");
            }
        }
        for (String id : List.of("rusty", "pud", "barnaby", "frost", "hazel", "elder_totem", "sonic")) {
            for (int hearts : new int[] {2, 4}) {
                boolean found = cast.events().stream().anyMatch(e -> e.villager.equals(id) && e.hearts == hearts);
                assertTrue(found, id + " has a " + hearts + "-heart event");
            }
        }
        assertTrue(cast.event("tails_10").isPartner());
        assertTrue(cast.event("clementine_10").isPartner());
        Set<String> who = new HashSet<>(Set.of("farmer", "narrator"));
        cast.all().forEach(v -> who.add(v.id));
        for (HeartEvent event : cast.events()) {
            assertNotNull(cast.get(event.villager), event.id);
            assertEquals(cast.get(event.villager).id, event.villager);
            for (Step step : event.steps()) {
                assertTrue(step.who() == null || who.contains(step.who()), event.id + " step by " + step.who());
                if (step.op() == Step.Op.GIVE) {
                    assertTrue(catalog.hasItem(step.text()), event.id + " gives a real item: " + step.text());
                }
            }
            assertTrue(event.anchor() == null || Anchors.valleyX(event.anchor()) >= 0, event.id + " anchor");
        }
        for (Letter letter : cast.letters()) {
            assertTrue(Speech.wrap(letter.text().replace("{FARMER}", "KNUCKLES"), LetterScreen.LINE_CHARS).size()
                    <= LetterScreen.MAX_LINES, letter.id + " fits on its sheet");
            assertTrue(letter.from.equals("post") || cast.get(letter.from) != null, letter.id);
            assertTrue(letter.item() == null || catalog.hasItem(letter.item()), letter.id + " encloses a real item");
        }
    }

    @Test
    void dailyLinesVaryAndSpecialLinesWin() {
        People people = new People();
        Game game = game(people, "sonic");
        Set<String> said = new HashSet<>();
        for (int day = 0; day < 28; day++) {
            said.add(people.talk("tails", game).line().text());
            game.sleep(false);
        }
        assertTrue(said.size() >= 8, "a month of Tails says many different things: " + said.size());
        // The day before the Ring Hunt, Tails talks about it.
        at(game, Calendar.SPRING, 12, 1000);
        assertTrue(people.talk("tails", game).line().text().contains("RING HUNT"));
        // Farmer-specific lines never reach the wrong farmer.
        People others = new People();
        Game knuckles = game(others, "knuckles");
        for (int day = 0; day < 40; day++) {
            String text = others.talk("tails", knuckles).line().text();
            assertFalse(text.startsWith("SONIC!") || text.startsWith("SONIC,"), text);
            knuckles.sleep(false);
        }
        assertEquals("HELLO KNUCKLES OF STARPOST", People.words("HELLO {FARMER} OF {FARM}", knuckles));
    }

    @Test
    void theMorningPostBringsLettersOnceAndPartnersHelp() {
        People people = new People();
        Game game = game(people, "sonic");
        people.morning(game);
        assertTrue(people.mailbox().contains("welcome_tails"));
        assertTrue(people.mailbox().contains("welcome_pip"));
        assertFalse(people.mailbox().contains("welcome_sonic"), "Sonic farms");
        people.morning(game);
        assertEquals(1, people.mailbox().stream().filter("welcome_tails"::equals).count(), "idempotent");
        while (people.nextLetter() != null) {
            people.read(people.nextLetter());
        }
        // A birthday reminder the day before, for a neighbour you've met.
        people.talk("dandel", game);
        at(game, Calendar.SPRING, 14, 600);
        people.morning(game);
        assertTrue(people.mailbox().contains("birthday:dandel"));
        // Partners' perks.
        people.setHearts("clementine", 10);
        people.bond("clementine").partner = true;
        at(game, Calendar.SPRING, 15, 600);
        int dogs = game.inventory.total("chili_dog");
        List<String> notices = people.morning(game);
        assertEquals(dogs + 1, game.inventory.total("chili_dog"));
        assertTrue(notices.stream().anyMatch(n -> n.contains("CLEMENTINE")));
        assertTrue(people.morning(game).isEmpty(), "once a morning");
        // Knuckles's arrival news comes on Summer 1.
        at(game, Calendar.SUMMER, 1, 600);
        people.morning(game);
        assertTrue(people.mailbox().contains("knuckles_arrives"));
    }

    @Test
    void theSectionRoundTripsThroughTheSave() {
        People people = new People();
        Game game = game(people, "tails");
        people.talk("dandel", game);
        game.inventory.add(catalog.item("ring_radish"), 1);
        people.gift("dandel", catalog.item("ring_radish"), game);
        people.setHearts("clementine", 10);
        people.bond("clementine").partner = true;
        people.begin(people.cast.event("pip_2"), game);
        people.morning(game);
        game.flags.add(People.TRANSLATOR);
        String text = SaveCodec.encode(game);
        assertTrue(text.contains("s.people.bond.dandel="));
        Game loaded = SaveCodec.decode(catalog, text, List.<SaveSection>of(new People()));
        assertNotNull(loaded);
        assertEquals(text, SaveCodec.encode(loaded), "a loaded save writes back identically");
        People back = loaded.section(People.class);
        assertEquals(people.bond("dandel").points, back.bond("dandel").points);
        assertTrue(back.bond("clementine").partner);
        assertTrue(back.seen("pip_2"));
        assertEquals(people.mailbox(), back.mailbox());
        assertTrue(back.bond("dandel").knownLoves.contains("ring_radish"));
        assertTrue(back.translator(loaded));
    }

    @Test
    void aDamagedSectionIsRejectedOrClamped() {
        People people = new People();
        Map<String, String> keys = new TreeMap<>();
        people.setHearts("dandel", 3);
        people.save(keys);
        People fresh = new People();
        Map<String, String> wrongVersion = new TreeMap<>(keys);
        wrongVersion.put("version", "9");
        assertThrows(RuntimeException.class, () -> fresh.load(wrongVersion, catalog));
        Map<String, String> garbled = new TreeMap<>(keys);
        garbled.put("bond.dandel", "lots,1,0,0,0,0");
        assertThrows(RuntimeException.class, () -> fresh.load(garbled, catalog));
        Map<String, String> wrongFlag = new TreeMap<>(keys);
        wrongFlag.put("bond.dandel", "100,7,0,0,0,0");
        assertThrows(RuntimeException.class, () -> fresh.load(wrongFlag, catalog));
        Map<String, String> wild = new TreeMap<>(keys);
        wild.put("bond.dandel", "999999,1,0,0,50,1");
        wild.put("bond.nobody", "100,1,0,0,0,0");
        wild.put("seen.no_such_event", "3");
        wild.put("mail", "welcome_pip,no_such_letter,thanks:dandel,thanks:nobody");
        fresh.load(wild, catalog);
        assertEquals(8 * People.POINTS_PER_HEART, fresh.bond("dandel").points, "clamped to the cap");
        assertEquals(People.GIFTS_PER_WEEK, fresh.bond("dandel").giftsThisWeek);
        assertFalse(fresh.bond("dandel").partner, "Dandel has no Partner arc");
        assertFalse(fresh.seen("no_such_event"));
        assertEquals(List.of("welcome_pip", "thanks:dandel"), fresh.mailbox());
        // Through the codec: a damaged section makes the whole file unreadable, like the core's checks.
        Game game = game(new People(), "sonic");
        String text = SaveCodec.encode(game).replace("s.people.version=1", "s.people.version=x");
        assertNull(SaveCodec.decode(catalog, text, List.<SaveSection>of(new People())));
        // A save from before the section existed loads as a fresh valley.
        Game old = game(new People(), "sonic");
        old.sections.clear();
        Game loaded = SaveCodec.decode(catalog, SaveCodec.encode(old), List.<SaveSection>of(new People()));
        assertNotNull(loaded);
        assertEquals(0, loaded.section(People.class).bond("tails").points);
    }
}
