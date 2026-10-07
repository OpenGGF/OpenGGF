package com.openggf.mods.code;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.ScenePreparedMusic;
import com.openggf.mods.scene.SceneMusicPart;
import com.openggf.mods.scene.host.music.SceneMusicFactory;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/** Actual example charts/metadata; ROM tests establish every supported full song/role/difficulty chart and timing invariants. */
class TestSitarHeroCharts {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static Class<?> roleType;
    private static Class<?> songType;
    private static Class<?> curatorType;
    private static Class<?> difficultyType;

    @BeforeAll
    static void compileExample() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        roleType = harness.loader().loadClass("sitarhero.model.Role");
        songType = harness.loader().loadClass("sitarhero.model.SongSpec");
        curatorType = harness.loader().loadClass("sitarhero.chart.ChartCurator");
        difficultyType = harness.loader().loadClass("sitarhero.model.Difficulty");
    }

    @AfterAll
    static void closeExample() throws Exception { if (harness != null) harness.close(); }

    @Test
    void allSevenRomSubsetsCombineSongsAndCosmeticPerformers() throws Exception {
        Class<?> catalog = harness.loader().loadClass("sitarhero.model.SongCatalog");
        Class<?> roster = harness.loader().loadClass("sitarhero.model.Roster");
        for (int subset = 1; subset < 8; subset++) {
            List<String> games = new ArrayList<>();
            if ((subset & 1) != 0) games.add("s1");
            if ((subset & 2) != 0) games.add("s2");
            if ((subset & 4) != 0) games.add("s3k");
            List<?> songs = (List<?>) catalog.getMethod("available", List.class).invoke(null, games);
            assertEquals(songs().stream().filter(song -> {
                try { return games.contains(songType.getMethod("game").invoke(song)); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }).count(), songs.size(), "all authored songs from exactly the loaded ROMs");
            for (Object song : songs) assertTrue(games.contains(songType.getMethod("game").invoke(song)));
            int expected = 2 + ((subset & 6) != 0 ? 1 : 0) + ((subset & 2) != 0 ? 1 : 0)
                    + ((subset & 4) != 0 ? 3 : 0);
            List<?> performers = (List<?>) roster.getMethod("availablePerformers", List.class).invoke(null, games);
            assertEquals(expected, performers.size(), "roster for " + games);
            for (Object performer : performers) {
                String donor = (String) roster.getMethod("game", List.class).invoke(performer, games);
                assertTrue(games.contains(donor));
                String id = (String) roster.getMethod("id").invoke(performer);
                if (id.equals("sonic") || id.equals("robotnik"))
                    assertEquals(games.getLast(), donor, "common character prefers S3K then S2 then S1");
                for (Object song : songs) assertNotNull(song, "performer has no song/game compatibility gate");
            }
        }
    }

    @Test
    void chartCurationKeepsRealAttacksThinsDensityAndUsesActualHarmonyChords() throws Exception {
        Object song = song("green-hill");
        List<SceneNoteEvent> events = new ArrayList<>();
        for (int i = 0; i < 8; i++) events.add(new SceneNoteEvent(0, SceneNoteEvent.Kind.DAC, 5,
                i % 2 == 0 ? 0x81 : 0x82, 100 + i, i * 8_000L, 8_000));
        long beat = 16_000;
        events.add(new SceneNoteEvent(4, SceneNoteEvent.Kind.FM, 3, 180, 200, beat * 40, beat * 3));
        events.add(new SceneNoteEvent(5, SceneNoteEvent.Kind.FM, 4, 192, 210, beat * 40, beat * 3));
        events.add(new SceneNoteEvent(4, SceneNoteEvent.Kind.FM, 3, 184, 202, beat * 40 + 1_000, 2_000));
        events.add(new SceneNoteEvent(4, SceneNoteEvent.Kind.FM, 3, 189, 204, beat * 42, beat));
        ScenePreparedMusic prepared = prepared(events, beat * 52);
        Object chart = curate(song, role("HARP"), prepared);
        List<?> notes = notes(chart);
        assertEquals(2, notes.size(), "rapid ornament is culled by authored difficulty density");
        assertEquals(2, Integer.bitCount(integer(notes.getFirst(), "lanes")), "only genuine concurrent harmony is chorded");
        assertEquals(beat * 40, number(notes.getFirst(), "onset"));
        assertTrue(number(notes.getFirst(), "end") < number(notes.getLast(), "onset"));
        assertEquals(beat, number(chart, "samplesPerBeat"), "quarter is derived from known DAC units and ROM onsets");
        assertFalse(flag(notes.getFirst(), "hopo"), "rhythm accompaniment chords require a strum");
    }

    @Test
    void fullCatalogueKeepsStableSourcesNaturalEndingsAndAuthenticPartAbsence() throws Exception {
        assertEquals(79, songs().size());
        assertEquals(11, songs().stream().filter(song -> gameOf(song).equals("s1")).count());
        assertEquals(22, songs().stream().filter(song -> gameOf(song).equals("s2")).count());
        assertEquals(46, songs().stream().filter(song -> gameOf(song).equals("s3k")).count());
        assertThrows(IllegalArgumentException.class, () -> song("s1-title"), "short title cue is outside full song library");
        assertThrows(IllegalArgumentException.class, () -> song("s1-continue"));
        assertThrows(IllegalArgumentException.class, () -> song("s3-title"));
        assertThrows(IllegalArgumentException.class, () -> song("s3k-title"));
        assertThrows(IllegalArgumentException.class, () -> song("s3-knuckles"));
        assertThrows(IllegalArgumentException.class, () -> song("s3k-knuckles"));
        assertEquals(7200, integer(song("green-hill"), "durationFrames"));
        assertEquals(8640, integer(song("scrap-brain"), "durationFrames"), "two complete 72-second loops");
        assertEquals(7601, integer(song("s1-credits"), "durationFrames"), "native late PSG tail");
        assertEquals(1081, integer(song("s1-ending"), "durationFrames"), "natural ending is not padded");
        assertEquals(9527, integer(song("credits-s2"), "durationFrames"));
        assertEquals(609, integer(song("s3-ending"), "durationFrames"), "earlier bank calls finish naturally");
        assertEquals(10248, integer(song("s3k-credits"), "durationFrames"));
        assertEquals(8763, integer(song("s3-credits"), "durationFrames"), "Sonic 3 Credits has a native outer loop");
        for (Object song : songs()) {
            String id = (String) songType.getMethod("id").invoke(song);
            assertFalse(flag(song, "excerpt"), id);
            assertTrue(integer(song, "durationFrames") <= 36_000);
            List<?> roles = (List<?>) songType.getMethod("availableRoles").invoke(song);
            assertTrue(roles.contains(role("SITAR")) && roles.contains(role("HARP")), id);
        }
        assertFalse(((List<?>) songType.getMethod("availableRoles").invoke(song("s1-special-stage"))).contains(role("BONGOS")));
        assertFalse(((List<?>) songType.getMethod("availableRoles").invoke(song("final-zone"))).contains(role("SYNTH")));
        assertEquals(1 << 5 | 1, roleType.getMethod("fmMask", songType).invoke(role("SITAR"), song("s1-special-stage")), "pitched FM6 stays melodic");
        assertNotNull(songType.getConstructor(String.class, String.class, String.class, int.class,
                int.class, int.class, int.class).newInstance("custom", "Custom", "s1", 0x81, 7200, 0, 0));
        assertEquals(0b100, roleType.getMethod("psgMask", songType).invoke(role("SYNTH"), song("chemical-plant")));
        assertEquals(0b001, roleType.getMethod("psgMask", songType).invoke(role("SYNTH"), song("spring-yard")));
    }

    private static String gameOf(Object song) {
        try { return (String) songType.getMethod("game").invoke(song); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test
    void nativeCreditsTempoAnchorsMapExactServiceBoundaries() throws Exception {
        for (String id : List.of("s1-credits", "credits-s2", "s3k-credits")) {
            Object song = song(id);
            ScenePreparedMusic prepared = prepared(List.of(), (long) integer(song, "durationFrames") * 800);
            Object clock = clock(song, prepared);
            var catalog = harness.loader().loadClass("sitarhero.model.SongCatalog");
            for (Object anchor : (List<?>) catalog.getMethod("tempoAnchors", String.class).invoke(null, id)) {
                double beat = ((Number) anchor.getClass().getMethod("beat").invoke(anchor)).doubleValue();
                long sample = ((Number) anchor.getClass().getMethod("serviceFrame").invoke(anchor)).longValue() * 800;
                assertEquals(sample, clockSample(clock, beat), id + " native tempo handoff");
                assertEquals(beat, clockBeat(clock, sample), 1e-9);
            }
        }
    }

    @Test
    void fractionalPickupsAndLoopsKeepNativeServiceBoundariesAcrossRepeats() throws Exception {
        // Expected quarters come from progressed native duration units, not rounded cover counts.
        Object[][] forms = {
                {"s3k-data-select", 56, 3584, 1.75, 112.0},
                {"launch-base-1", 590, 3411, 481.0 / 24, 116.0},
                {"s3k-final-boss", 964, 2527, 26.5, 69.5},
                {"desert-palace", 0, 2667, 0.0, 2302.0 / 24}
        };
        for (Object[] form : forms) {
            Object song = song((String) form[0]);
            Object clock = clock(song, prepared(List.of(), (long) integer(song, "durationFrames") * 800));
            int introFrames = (int) form[1], loopFrames = (int) form[2];
            double introBeats = (double) form[3], loopBeats = (double) form[4];
            assertEquals(introFrames * 800L, clockSample(clock, introBeats), form[0] + " pickup");
            for (int repeat = 1; repeat <= 3; repeat++) {
                long sample = (introFrames + repeat * loopFrames) * 800L;
                double beat = introBeats + repeat * loopBeats;
                assertEquals(sample, clockSample(clock, beat), form[0] + " loop " + repeat);
                assertEquals(beat, clockBeat(clock, sample), 1e-9);
            }
        }
        assertEquals(0, drumFamily(song("angel-island-1"), 0xB2), "echoed clap is a pad, not a kick");
        assertEquals(-1, drumFamily(song("angel-island-1"), 0xB6), "spoken bass hey is not a drum strike");
    }

    @Test
    void quarterAnchorsUseTheIntegerConsumedSampleBoundaryAtNonDivisibleRates() throws Exception {
        Object song = song("s3k-data-select");
        ScenePreparedMusic prepared = prepared(List.of(), (long) integer(song, "durationFrames") * 8_000 / 60, 8_000);
        Object clock = clock(song, prepared);
        for (int repeat = 0; repeat <= 3; repeat++) {
            long sample = (56L + repeat * 3584) * 8_000 / 60;
            double beat = 1.75 + repeat * 112;
            assertEquals(sample, clockSample(clock, beat), "native packet boundary " + repeat);
            assertEquals(beat, clockBeat(clock, sample), 1e-9);
        }
        var credits = harness.loader().loadClass("sitarhero.model.SongCatalog");
        song = song("s3k-credits");
        clock = clock(song, prepared(List.of(), (long) integer(song, "durationFrames") * 8_000 / 60, 8_000));
        for (Object anchor : (List<?>) credits.getMethod("tempoAnchors", String.class).invoke(null, "s3k-credits")) {
            double beat = ((Number) anchor.getClass().getMethod("beat").invoke(anchor)).doubleValue();
            long sample = ((Number) anchor.getClass().getMethod("serviceFrame").invoke(anchor)).longValue() * 8_000 / 60;
            assertEquals(sample, clockSample(clock, beat), "native medley packet boundary");
        }
    }

    @Test
    void difficultiesReduceFretsAndDensityWithoutMovingSourceAttacks() throws Exception {
        String[] labels = {"Easy", "Medium", "Hard", "Expert"};
        int[] frets = {3, 4, 5, 5}, melodic = {1, 2, 3, 4}, drums = {2, 4, 6, 8};
        Object song = song("green-hill");
        long beat = 32_000;
        List<SceneNoteEvent> events = new ArrayList<>();
        for (int i = 0; i < 8; i++) events.add(new SceneNoteEvent(0, SceneNoteEvent.Kind.DAC, 5,
                0x81, i, i * beat / 2, beat / 2));
        for (int i = 0; i < 64; i++) {
            long onset = beat * 48 + i * beat / 8;
            events.add(new SceneNoteEvent(4, SceneNoteEvent.Kind.FM, 3, 170 + i % 5, 100 + i, onset, beat));
            events.add(new SceneNoteEvent(5, SceneNoteEvent.Kind.FM, 4, 182 + i % 5, 200 + i, onset, beat));
        }
        ScenePreparedMusic prepared = prepared(events, beat * 64);
        int previousCount = 0;
        for (int i = 0; i < difficultyType.getEnumConstants().length; i++) {
            Object difficulty = difficultyType.getEnumConstants()[i];
            assertEquals(labels[i], difficultyType.getMethod("label").invoke(difficulty));
            assertEquals(frets[i], integer(difficulty, "lanes"));
            assertEquals(melodic[i], difficultyType.getMethod("densityDivision", boolean.class).invoke(difficulty, false));
            assertEquals(drums[i], difficultyType.getMethod("densityDivision", boolean.class).invoke(difficulty, true));
            Object chart = curate(song, role("HARP"), prepared, difficulty);
            assertTrue(notes(chart).size() > previousCount, "denser genuine attacks at each level");
            previousCount = notes(chart).size();
            for (Object note : notes(chart)) {
                assertEquals(0, (number(note, "onset") - beat * 48) % (beat / 8), "source onset remains intact");
                assertTrue(integer(note, "lanes") < (1 << frets[i]));
                if (i == 0) {
                    assertEquals(1, Integer.bitCount(integer(note, "lanes")));
                    assertFalse(flag(note, "hopo"));
                }
            }
        }
    }

    @Test
    void shortenedPreparationsCanMeasureAnIndependentDacClock() throws Exception {
        Object song = song("marble");
        int[] units = {3, 3, 12, 12, 12, 12, 12};
        List<SceneNoteEvent> events = new ArrayList<>();
        long onset = 0;
        for (int index = 0; index < 8; index++) {
            events.add(new SceneNoteEvent(0, SceneNoteEvent.Kind.DAC, 5, 0x81, index, onset, 800));
            if (index < units.length) onset += units[index] * 800L;
        }
        Object chart = curate(song, role("BONGOS"), prepared(events, 3600L * 800));
        assertEquals(12 * 800L, number(chart, "samplesPerBeat"), "60-second excerpt is not assumed to be a whole 144-beat loop");
    }

    @Test
    void sectionAudioOwnershipMatchesLeadAndPsgSelections() throws Exception {
        Object song = song("angel-island-1");
        long beat = 24_000;
        int[] units = {12, 6, 6, 12, 6, 6, 12};
        List<SceneNoteEvent> events = new ArrayList<>();
        long onset = 0;
        for (int i = 0; i < 8; i++) {
            events.add(new SceneNoteEvent(0, SceneNoteEvent.Kind.DAC, 5, 0x86, i, onset, 1000));
            if (i < units.length) onset += units[i] * 1000L;
        }
        events.add(new SceneNoteEvent(2, SceneNoteEvent.Kind.FM, 1, 160, 100, beat * 64 - 100, 100));
        events.add(new SceneNoteEvent(3, SceneNoteEvent.Kind.FM, 2, 180, 101, beat * 64 + 100, 100));
        events.add(new SceneNoteEvent(2, SceneNoteEvent.Kind.FM, 1, 175, 102, beat * 96 + 100, 100));
        ScenePreparedMusic prepared = prepared(events, beat * 110);
        List<SceneMusicPart> parts = audioParts(song, role("SITAR"), prepared);
        assertEquals(List.of(2, 4, 6), parts.stream().map(SceneMusicPart::fmMask).toList());
        assertEquals(2, partAt(parts, beat * 64 - 100).fmMask(), "ownership cannot snap earlier into the previous attack");
        assertEquals(4, partAt(parts, beat * 64 + 100).fmMask());
        Object greenHill = song("green-hill");
        List<SceneMusicPart> psgParts = audioParts(greenHill, role("SYNTH"), prepared);
        assertEquals(2, psgParts.getFirst().psgMask(), "GHZ intro PSG2");
        assertEquals(3, psgParts.getLast().psgMask(), "GHZ main PSG1/2");
    }

    @Test
    void nativeDacBanksKeepTheirDistinctIdentities() throws Exception {
        assertEquals(4, drumFamily(song("green-hill"), 0x81));
        assertEquals(0, drumFamily(song("green-hill"), 0x82));
        assertEquals(-1, drumFamily(song("green-hill"), 0x86), "S1 has no S3 kick");
        assertEquals(2, drumFamily(song("emerald-hill"), 0x8C), "S2 mid tom");
        assertEquals(2, drumFamily(song("emerald-hill"), 0x8E), "S2 floor tom");
        assertEquals(0, drumFamily(song("aquatic-ruin"), 0x83), "S2 clap");
        assertEquals(2, drumFamily(song("emerald-hill"), 0x86), "S2 high tom");
        assertEquals(4, drumFamily(song("angel-island-1"), 0x86), "S3 kick");
        assertEquals(3, drumFamily(song("angel-island-1"), 0x8C), "S3 high metal hit");
        assertEquals(0, drumFamily(song("flying-battery-1"), 0x8F), "S3 clap");
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1CatalogueAllRolesAndDifficulties() throws Exception { verifyRom("s1", new Sonic1AudioProfile()); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2CatalogueAllRolesAndDifficulties() throws Exception { verifyRom("s2", new Sonic2AudioProfile()); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kCatalogueAllAvailableRolesAndDifficulties() throws Exception { verifyRom("s3k", new Sonic3kAudioProfile()); }

    private void verifyRom(String game, GameAudioProfile profile) throws Exception {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation(game, profile, config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(8_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
            for (Object song : songs()) {
                if (!game.equals(songType.getMethod("game").invoke(song))) continue;
                String id = (String) songType.getMethod("id").invoke(song);
                ScenePreparedMusic prepared = music.prepare(game, integer(song, "musicId"), integer(song, "durationFrames"));
                assertEquals((long) integer(song, "durationFrames") * prepared.sampleRate() / 60, prepared.lengthSamples());
                for (SceneNoteEvent event : prepared.notes()) {
                    if (event.kind() == SceneNoteEvent.Kind.DAC) {
                        boolean nonPercussion = game.equals("s3k") && List.of(0xA5, 0xA9, 0xAA,
                                0xB6, 0xBA, 0xBB, 0xBE).contains(event.pitch());
                        assertEquals(!nonPercussion, drumFamily(song, event.pitch()) >= 0,
                                id + " native percussion/speech identity: " + event.pitch());
                    }
                }
                Map<Long,List<SceneNoteEvent>> attacks = new TreeMap<>();
                prepared.notes().forEach(event -> attacks.computeIfAbsent(event.onsetSamples(), ignored -> new ArrayList<>()).add(event));
                List<?> available = (List<?>) songType.getMethod("availableRoles").invoke(song);
                for (Object role : roleType.getEnumConstants()) {
                    if (!available.contains(role)) {
                        boolean drums = (boolean) roleType.getMethod("drums").invoke(role);
                        assertTrue(prepared.notes().stream().noneMatch(event -> event.kind() == (drums ? SceneNoteEvent.Kind.DAC : SceneNoteEvent.Kind.PSG)),
                                id + " unavailable role has no native attacks");
                        assertEquals(0, roleType.getMethod("psgMask", songType).invoke(role, song));
                        for (Object difficulty : difficultyType.getEnumConstants()) {
                            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                                    () -> curate(song, role, prepared, difficulty));
                            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
                        }
                        continue;
                    }
                    verifyPart(song, role, prepared, attacks);
                }
            }
        } finally { audio.destroy(); }
    }

    private static void verifyPart(Object song, Object role, ScenePreparedMusic prepared,
                                   Map<Long,List<SceneNoteEvent>> attacks) throws Exception {
        String id = (String) songType.getMethod("id").invoke(song);
        boolean drums = (boolean) roleType.getMethod("drums").invoke(role);
        List<SceneMusicPart> parts = audioParts(song, role, prepared);
        assertEquals(0, parts.getFirst().onsetSamples());
        long lastPart = -1;
        int fm = 0, psg = 0;
        for (SceneMusicPart part : parts) {
            assertTrue(part.onsetSamples() > lastPart && part.onsetSamples() < prepared.lengthSamples());
            assertEquals(drums, part.dacMuted());
            fm |= part.fmMask(); psg |= part.psgMask();
            lastPart = part.onsetSamples();
        }
        assertEquals(fm, roleType.getMethod("fmMask", songType).invoke(role, song));
        assertEquals(psg, roleType.getMethod("psgMask", songType).invoke(role, song));
        int previousCount = 0;
        for (Object difficulty : difficultyType.getEnumConstants()) {
            String context = id + "/" + role + "/" + difficulty;
            int laneCount = integer(difficulty, "lanes");
            int division = (int) difficultyType.getMethod("densityDivision", boolean.class).invoke(difficulty, drums);
            Object chart = curate(song, role, prepared, difficulty);
            assertEquals(chart, curate(song, role, prepared, difficulty), context + " deterministic");
            if (difficulty.toString().equals("MEDIUM")) assertEquals(chart, curate(song, role, prepared), "legacy overload defaults to MEDIUM");
            List<?> notes = notes(chart);
            assertTrue(!notes.isEmpty() && notes.size() <= prepared.lengthSamples() * 12 / prepared.sampleRate() + 1, context + " meaningful finite chart: " + notes.size());
            assertTrue(notes.size() >= previousCount, context + " increasing difficulty cannot reduce density");
            previousCount = notes.size();
            assertEquals(prepared.lengthSamples(), number(chart, "length"));
            long beat = number(chart, "samplesPerBeat");
            Object clock = clock(song, prepared);
            long previous = -beat;
            int lanesUsed = 0;
            Map<Integer,Integer> phrases = new TreeMap<>();
            for (int index = 0; index < notes.size(); index++) {
                Object note = notes.get(index);
                long onset = number(note, "onset"), end = number(note, "end");
                assertTrue(attacks.containsKey(onset), context + " every onset comes from the ROM");
                SceneMusicPart selected = partAt(parts, onset);
                List<SceneNoteEvent> realPart = attacks.get(onset).stream().filter(event -> belongs(event, selected)).toList();
                assertFalse(realPart.isEmpty(), context + " every attack belongs to the selected audible part");
                assertTrue(onset - previous >= Math.max(clockLength(clock, onset) / division, prepared.sampleRate() / (drums ? 12L : 10L)), context + " ergonomic density cap");
                assertTrue(end >= onset && end <= prepared.lengthSamples());
                if (index + 1 < notes.size()) assertTrue(end < number(notes.get(index + 1), "onset"), context + " tails do not overlap the next hit");
                int lanes = integer(note, "lanes");
                assertTrue(lanes > 0 && lanes < (1 << (drums ? 5 : laneCount)), context + " lane range");
                if (Integer.bitCount(lanes) > 1) {
                    assertTrue(realPart.stream().map(SceneNoteEvent::pitch).distinct().count() >= Integer.bitCount(lanes),
                            context + " chords require distinct source pitches attacking simultaneously");
                    assertFalse(flag(note, "hopo"));
                }
                if (drums) {
                    assertEquals(onset, end);
                    assertFalse(flag(note, "hopo"));
                    boolean knownSample = false;
                    for (SceneNoteEvent event : realPart) knownSample |= drumFamily(song, event.pitch()) >= 0;
                    assertTrue(knownSample, "native DAC bank identity");
                }
                if (difficulty.toString().equals("EASY")) {
                    assertEquals(1, Integer.bitCount(lanes), context + " EASY uses single notes");
                    assertFalse(flag(note, "hopo"));
                }
                int phrase = integer(note, "phrase");
                assertTrue(phrase >= -1 && phrase <= (int) (clockBeat(clock, prepared.lengthSamples()) / 32));
                if (phrase >= 0) phrases.merge(phrase, 1, Integer::sum);
                int ticks = integer(note, "sustainTicks");
                assertEquals(end > onset ? (int) ((clockBeat(clock, end) - clockBeat(clock, onset)) * 25) : 0, ticks);
                lanesUsed |= lanes;
                previous = onset;
            }
            if (clockBeat(clock, prepared.lengthSamples()) > 64)
                assertFalse(phrases.isEmpty(), context + " authored Star Power phrases continue through full songs");
            if (drums && difficulty.toString().equals("EASY")) assertTrue(Integer.bitCount(lanesUsed) <= 3, "EASY simplifies the native kit to three strike families");
            if (drums && id.equals("angel-island-1") && laneCount == 5) assertEquals(31, lanesUsed, "AIZ native tom and metal families remain available");
        }
    }

    private static Object clock(Object song, ScenePreparedMusic prepared) throws Exception {
        var type = harness.loader().loadClass("sitarhero.chart.MusicalClock");
        var method = type.getDeclaredMethod("of", songType, ScenePreparedMusic.class); method.setAccessible(true);
        return method.invoke(null, song, prepared);
    }
    private static double clockBeat(Object clock, long sample) throws Exception {
        var method = clock.getClass().getDeclaredMethod("beatAt", long.class); method.setAccessible(true);
        return (double) method.invoke(clock, sample);
    }
    private static long clockSample(Object clock, double beat) throws Exception {
        var method = clock.getClass().getDeclaredMethod("sampleAt", double.class); method.setAccessible(true);
        return (long) method.invoke(clock, beat);
    }
    private static long clockLength(Object clock, long sample) throws Exception {
        var method = clock.getClass().getDeclaredMethod("beatLengthAt", long.class); method.setAccessible(true);
        return (long) method.invoke(clock, sample);
    }

    private static boolean belongs(SceneNoteEvent event, SceneMusicPart part) {
        return switch (event.kind()) {
            case FM -> (part.fmMask() & (1 << event.channel())) != 0;
            case PSG -> (part.psgMask() & (1 << event.channel())) != 0;
            case DAC -> part.dacMuted();
        };
    }

    private static SceneMusicPart partAt(List<SceneMusicPart> parts, long onset) {
        SceneMusicPart result = parts.getFirst();
        for (SceneMusicPart part : parts) if (part.onsetSamples() <= onset) result = part;
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<SceneMusicPart> audioParts(Object song, Object role, ScenePreparedMusic prepared) throws Exception {
        return (List<SceneMusicPart>) curatorType.getMethod("audioParts", songType, roleType, ScenePreparedMusic.class).invoke(null, song, role, prepared);
    }

    private static int drumFamily(Object song, int note) throws Exception {
        var method = harness.loader().loadClass("sitarhero.chart.CurationProfile").getDeclaredMethod("drumLane", songType, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, song, note);
    }

    private static ScenePreparedMusic prepared(List<SceneNoteEvent> events, long length) {
        return prepared(events, length, 48_000);
    }

    private static ScenePreparedMusic prepared(List<SceneNoteEvent> events, long length, int rate) {
        return new ScenePreparedMusic() {
            public int sampleRate() { return rate; }
            public long lengthSamples() { return length; }
            public List<SceneNoteEvent> notes() { return List.copyOf(events); }
        };
    }

    private static List<?> songs() throws Exception {
        return (List<?>) harness.loader().loadClass("sitarhero.model.SongCatalog").getMethod("all").invoke(null);
    }

    private static Object song(String id) throws Exception {
        for (Object song : songs()) if (id.equals(songType.getMethod("id").invoke(song))) return song;
        throw new IllegalArgumentException(id);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object difficulty(String name) { return Enum.valueOf((Class) difficultyType, name); }
    private static Object curate(Object song, Object role, ScenePreparedMusic prepared, Object difficulty) throws Exception {
        return curatorType.getMethod("curate", songType, roleType, ScenePreparedMusic.class, difficultyType).invoke(null, song, role, prepared, difficulty);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object role(String name) { return Enum.valueOf((Class) roleType, name); }
    private static Object curate(Object song, Object role, ScenePreparedMusic prepared) throws Exception {
        return curatorType.getMethod("curate", songType, roleType, ScenePreparedMusic.class).invoke(null, song, role, prepared);
    }
    private static List<?> notes(Object chart) throws Exception { return (List<?>) chart.getClass().getMethod("notes").invoke(chart); }
    private static long number(Object value, String field) throws Exception { return ((Number) value.getClass().getMethod(field).invoke(value)).longValue(); }
    private static int integer(Object value, String field) throws Exception { return ((Number) value.getClass().getMethod(field).invoke(value)).intValue(); }
    private static boolean flag(Object value, String field) throws Exception { return (boolean) value.getClass().getMethod(field).invoke(value); }
}
