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

/** Actual example charts/metadata; ROM tests establish all 184 supported song/role/difficulty charts and timing invariants. */
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
            assertEquals(Integer.bitCount(subset) * 4, songs.size(), "four authored songs per ROM");
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
    void catalogueHasExactlyTwelveStableSourcesAndDiscoverableSilentParts() throws Exception {
        List<String> ids = List.of("green-hill", "marble", "spring-yard", "labyrinth", "chemical-plant",
                "emerald-hill", "aquatic-ruin", "casino-night", "angel-island-1", "hydrocity-1",
                "marble-garden-1", "flying-battery-1");
        int[] music = {0x81, 0x83, 0x85, 0x82, 0x8C, 0x81, 0x86, 0x83, 1, 3, 5, 9};
        int[] zones = {0, 1, 2, 3, 1, 0, 2, 3, 0, 1, 2, 4};
        int[] durations = {3168, 3600, 3600, 3600, 3717, 3600, 3600, 3600, 3932, 3600, 3600, 3600};
        assertEquals(12, songs().size());
        for (int index = 0; index < ids.size(); index++) {
            Object song = song(ids.get(index));
            assertEquals(song, songs().get(index), "authored per-game tour order");
            assertEquals(music[index], integer(song, "musicId"));
            assertEquals(zones[index], integer(song, "zone"), "engine progression index for backdrop");
            assertEquals(0, integer(song, "act"));
            assertEquals(durations[index], integer(song, "durationFrames"));
            assertEquals(durations[index] == 3600, flag(song, "excerpt"));
            List<?> roles = (List<?>) songType.getMethod("availableRoles").invoke(song);
            assertEquals(index >= 10 ? List.of(role("SITAR"), role("BONGOS"), role("HARP"))
                    : List.of(roleType.getEnumConstants()), roles);
        }
        // The public seven-argument constructor stays available for existing callers.
        assertNotNull(songType.getConstructor(String.class, String.class, String.class, int.class,
                int.class, int.class, int.class).newInstance("green-hill", "Green Hill", "s1", 0x81, 3168, 0, 0));
        assertEquals(0b100, roleType.getMethod("psgMask", songType).invoke(role("SYNTH"), song("chemical-plant")), "authentic PSG3 noise");
        assertEquals(0b001, roleType.getMethod("psgMask", songType).invoke(role("SYNTH"), song("spring-yard")), "stopped PSG2 is not assigned");
        assertEquals(0b00110, roleType.getMethod("fmMask", songType).invoke(role("SITAR"), song("angel-island-1")), "AIZ FM1 is bass");
        assertEquals(0b00110, roleType.getMethod("fmMask", songType).invoke(role("SITAR"), song("emerald-hill")), "EHZ melody is FM2/3");
        assertEquals(0b11110, roleType.getMethod("fmMask", songType).invoke(role("SITAR"), song("marble-garden-1")), "MGZ intro and delayed main melody");
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
    void excerptsMeasureTempoFromTheirOwnDacProgression() throws Exception {
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
        assertEquals(1, drumFamily(song("emerald-hill"), 0x8E), "S2 floor tom");
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
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
            for (Object song : songs()) {
                if (!game.equals(songType.getMethod("game").invoke(song))) continue;
                String id = (String) songType.getMethod("id").invoke(song);
                ScenePreparedMusic prepared = music.prepare(game, integer(song, "musicId"), integer(song, "durationFrames"));
                assertEquals((long) integer(song, "durationFrames") * 800, prepared.lengthSamples());
                for (SceneNoteEvent event : prepared.notes()) {
                    if (event.kind() == SceneNoteEvent.Kind.DAC)
                        assertTrue(drumFamily(song, event.pitch()) >= 0, id + " authored mapping covers its actual DAC bank: " + event.pitch());
                }
                Map<Long,List<SceneNoteEvent>> attacks = new TreeMap<>();
                prepared.notes().forEach(event -> attacks.computeIfAbsent(event.onsetSamples(), ignored -> new ArrayList<>()).add(event));
                List<?> available = (List<?>) songType.getMethod("availableRoles").invoke(song);
                for (Object role : roleType.getEnumConstants()) {
                    if (!available.contains(role)) {
                        assertTrue(prepared.notes().stream().noneMatch(event -> event.kind() == SceneNoteEvent.Kind.PSG),
                                id + " has truly silent PSG streams");
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
            assertTrue(notes.size() > 15 && notes.size() < 1000, context + " meaningful finite chart: " + notes.size());
            assertTrue(notes.size() >= previousCount, context + " increasing difficulty cannot reduce density");
            previousCount = notes.size();
            assertEquals(prepared.lengthSamples(), number(chart, "length"));
            long beat = number(chart, "samplesPerBeat");
            if (List.of("green-hill", "chemical-plant", "angel-island-1").contains(id)) {
                int totalBeats = id.equals("green-hill") ? 132 : 144;
                assertEquals(Math.round(prepared.lengthSamples() / (double) totalBeats), beat, "original ROM cycle clock is stable");
            }
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
                assertTrue(onset - previous >= Math.max(beat / division, prepared.sampleRate() / (drums ? 12L : 10L)), context + " ergonomic density cap");
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
                assertTrue(phrase >= -1 && phrase < 8);
                if (phrase >= 0) phrases.merge(phrase, 1, Integer::sum);
                int ticks = integer(note, "sustainTicks");
                assertEquals(end > onset ? (int) ((end - onset) * 25 / beat) : 0, ticks);
                lanesUsed |= lanes;
                previous = onset;
            }
            assertFalse(phrases.isEmpty(), context + " authored Star Power phrases");
            if (drums && difficulty.toString().equals("EASY")) assertTrue(Integer.bitCount(lanesUsed) <= 3, "EASY simplifies the native kit to three strike families");
            if (drums && id.equals("angel-island-1") && laneCount == 5) assertEquals(31, lanesUsed, "AIZ native tom and metal families remain available");
        }
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
        return new ScenePreparedMusic() {
            public int sampleRate() { return 48_000; }
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
