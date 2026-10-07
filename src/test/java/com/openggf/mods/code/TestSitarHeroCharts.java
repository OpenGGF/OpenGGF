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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Actual example charts/metadata; ROM tests establish all twelve chart sources and timing invariants. */
class TestSitarHeroCharts {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static Class<?> roleType;
    private static Class<?> songType;
    private static Class<?> curatorType;

    @BeforeAll
    static void compileExample() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        roleType = harness.loader().loadClass("sitarhero.model.Role");
        songType = harness.loader().loadClass("sitarhero.model.SongSpec");
        curatorType = harness.loader().loadClass("sitarhero.chart.ChartCurator");
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
            assertEquals(Integer.bitCount(subset), songs.size(), "one accepted song per ROM");
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
        Object song = songs().getFirst(); // GHZ
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

    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillAllFourCuratedRoles() throws Exception { verifyRom("s1", new Sonic1AudioProfile()); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void chemicalPlantAllFourCuratedRoles() throws Exception { verifyRom("s2", new Sonic2AudioProfile()); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void angelIslandAllFourCuratedRoles() throws Exception { verifyRom("s3k", new Sonic3kAudioProfile()); }

    private void verifyRom(String game, GameAudioProfile profile) throws Exception {
        Object song = songs().stream().filter(value -> {
            try { return game.equals(value.getClass().getMethod("game").invoke(value)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }).findFirst().orElseThrow();
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation(game, profile, config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
            ScenePreparedMusic prepared = music.prepare(game, integer(song, "musicId"), integer(song, "durationFrames"));
            Set<Long> realAttacks = new HashSet<>();
            prepared.notes().forEach(event -> realAttacks.add(event.onsetSamples()));
            for (Object role : roleType.getEnumConstants()) {
                Object chart = curate(song, role, prepared);
                List<?> notes = notes(chart);
                @SuppressWarnings("unchecked")
                List<SceneMusicPart> parts = (List<SceneMusicPart>) curatorType.getMethod("audioParts", songType,
                        roleType, ScenePreparedMusic.class).invoke(null, song, role, prepared);
                assertTrue(notes.size() > 20 && notes.size() < 900, game + "/" + role + " playable chart count " + notes.size());
                assertEquals(prepared.lengthSamples(), number(chart, "length"));
                long beat = number(chart, "samplesPerBeat");
                long previous = -beat;
                int lanesUsed = 0;
                boolean drums = (boolean) roleType.getMethod("drums").invoke(role);
                for (Object note : notes) {
                    long onset = number(note, "onset");
                    assertTrue(realAttacks.contains(onset), "chart gems must come from real stream attacks");
                    SceneMusicPart part = parts.getFirst();
                    for (SceneMusicPart candidate : parts) if (candidate.onsetSamples() <= onset) part = candidate;
                    SceneMusicPart selected = part;
                    assertTrue(prepared.notes().stream().anyMatch(event -> event.onsetSamples() == onset
                            && switch (event.kind()) {
                                case FM -> (selected.fmMask() & (1 << event.channel())) != 0;
                                case PSG -> (selected.psgMask() & (1 << event.channel())) != 0;
                                case DAC -> selected.dacMuted();
                            }), "every chart attack must belong to the selected audible part");
                    assertTrue(onset - previous >= beat / (drums ? 4 : 2), "authored density cap");
                    assertTrue(number(note, "end") <= prepared.lengthSamples());
                    int lanes = integer(note, "lanes");
                    assertTrue(lanes > 0 && (lanes & ~31) == 0);
                    if (drums) {
                        assertEquals(onset, number(note, "end"));
                        assertFalse(flag(note, "hopo"));
                    }
                    lanesUsed |= lanes;
                    previous = onset;
                }
                if (drums) assertTrue((lanesUsed & 16) != 0 && (lanesUsed & 1) != 0, "actual kick and snare families");
                if (drums && game.equals("s3k"))
                    assertEquals(31, lanesUsed, "AIZ's native tom and metal-hit families cover all four pads plus kick");
                assertTrue(notes.stream().anyMatch(note -> {
                    try { return integer(note, "phrase") >= 0; } catch (Exception failure) { throw new IllegalStateException(failure); }
                }), "authored Star Power phrases present");
            }
        } finally { audio.destroy(); }
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
