package com.openggf.mods.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.AudioManager;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsNoteListener;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.level.LevelData;
import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.host.music.SceneMusicFactory;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** S1 book, independent shipped-ROM control-flow reference, and actual finite preparation. */
class TestSitarHeroS1SongCatalogue {
    private static final List<Integer> IDS = List.of(0x81, 0x83, 0x85, 0x82, 0x84, 0x86,
            0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x90, 0x91);
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static List<?> arrangements;
    private static Class<?> roleType;
    private static JsonNode reference;

    @BeforeAll
    static void compileExample() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        arrangements = (List<?>) harness.loader().loadClass("sitarhero.catalogue.Sonic1Catalogue")
                .getMethod("all").invoke(null);
        roleType = harness.loader().loadClass("sitarhero.model.Role");
    }

    @AfterAll
    static void closeExample() throws Exception { if (harness != null) harness.close(); }

    @Test
    void inventoryIncludesEverySubstantiveSongAndExplicitlyAbsentParts() throws Exception {
        assertEquals(IDS, arrangements.stream().map(a -> integerUnchecked(a, "musicId")).toList());
        Set<Object> ids = new HashSet<>();
        for (Object song : arrangements) {
            assertTrue(ids.add(value(song, "id")));
            assertEquals("s1", value(song, "game"));
            assertTrue(integer(song, "durationFrames") > 0);
            assertTrue(integer(song, "durationFrames") <= 36_000);
            assertTrue(supports(song, "SITAR"));
            assertTrue(supports(song, "HARP"));
        }
        assertFalse(supports(song(0x89), "BONGOS"), "Special Stage DAC stream immediately stops");
        assertEquals(List.of(), value(song(0x89), "firstDacUnits"));
        assertEquals(5, integer(((List<?>) value(song(0x89), "lead")).getFirst(), "channel"),
                "FM6 is real pitched Special Stage lead, not fabricated drums");
        for (int id : List.of(0x8D, 0x90)) {
            assertFalse(supports(song(id), "SYNTH"), "all PSG tracks stop for " + id);
            assertEquals(List.of(), value(song(id), "synth"));
        }
        Object titleNoise = ((List<?>) value(song(0x8A), "synth")).getFirst();
        assertTrue((boolean) value(titleNoise, "noise"), "Title PSG3 is a hi-hat, not a pitched synth melody");
        assertEquals(2, integer(titleNoise, "channel"));
        for (int id : List.of(0x87, 0x88, 0x8E, 0x8F, 0x92, 0x93))
            assertFalse(IDS.contains(id), "powerup, result, failure and reward cues are excluded");
    }

    @Test
    void durationAndStageBindingsPreserveWholeFormAndFiniteCadences() throws Exception {
        for (Object a : arrangements) {
            int loop = integer(a, "loopFrames");
            int duration = integer(a, "durationFrames");
            if (loop > 0) assertEquals(Math.max(7200, integer(a, "introFrames") + 2 * loop), duration);
            else assertEquals(integer(a, "endFrames"), duration);
        }
        assertEquals(8640, integer(song(0x86), "durationFrames"), "two complete Scrap Brain forms exceed120s");
        assertEquals(7601, integer(song(0x91), "durationFrames"), "Credits ends at native final track-stop service");
        assertEquals(1081, integer(song(0x8B), "durationFrames"), "Ending is not looped or padded to120s");
        assertEquals(541, integer(song(0x8A), "durationFrames"));
        assertEquals(561, integer(song(0x90), "durationFrames"));
        Map<Integer, Integer> zones = Map.of(0x81, 0, 0x83, 1, 0x85, 2, 0x82, 3, 0x84, 4, 0x86, 5, 0x8D, 6);
        var nativeStages = new Sonic1ZoneRegistry();
        for (var entry : zones.entrySet()) {
            assertEquals(entry.getValue(), integer(song(entry.getKey()), "zone"));
            assertEquals(0, integer(song(entry.getKey()), "act"));
            assertEquals(entry.getKey(), nativeStages.getMusicId(entry.getValue(), 0),
                    "stage picture's production zone registry matches this ROM song");
        }
        assertEquals(0, integer(song(0x8C), "zone"));
        assertEquals(2, integer(song(0x8C), "act"), "Boss concert uses the actual Green Hill Act3 boss stage");
        assertEquals(LevelData.S1_GREEN_HILL_3, nativeStages.getLevelDataForZone(0).get(2));
        for (int id : List.of(0x89, 0x8A, 0x8B, 0x90, 0x91)) {
            assertEquals(0, integer(song(id), "zone"), "explicit Green Hill concert backdrop for non-zone song");
            assertEquals(0, integer(song(id), "act"));
            assertEquals(LevelData.S1_GREEN_HILL_1, nativeStages.getLevelDataForZone(integer(song(id), "zone"))
                    .get(integer(song(id), "act")));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0x81, 0x83, 0x85, 0x82, 0x84, 0x86, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x90, 0x91})
    @RequiresRom(SonicGame.SONIC_1)
    void nativeDurationAndEveryAttackAgreeWithProductionProgression(int id) throws Exception {
        Object arrangement = song(id);
        JsonNode nativeSong = reference(id);
        try (Rom rom = new Rom()) {
            rom.open(RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath());
            var profile = new Sonic1AudioProfile();
            var loader = profile.createSmpsLoader(rom);
            var seq = new SmpsSequencer(loader.loadMusic(id), loader.loadDacData(), () -> { }, profile.getSequencerConfig());
            List<List<Attack>> actual = new ArrayList<>();
            for (int i = 0; i < seq.trackCount(); i++) actual.add(new ArrayList<>());
            int[] frame = {0};
            seq.setNoteListener(new SmpsNoteListener() {
                public void attack(SmpsSequencer.Track track) {
                    int index = seq.getTracks().indexOf(track);
                    actual.get(index).add(new Attack(frame[0], track.pos, track.note));
                }
                public void release(SmpsSequencer.Track track) { }
            });
            int[] stopped = new int[seq.trackCount()];
            java.util.Arrays.fill(stopped, -1);
            int frames = integer(arrangement, "durationFrames");
            for (; frame[0] < frames; frame[0]++) {
                seq.serviceOuterFrame();
                for (int i = 0; i < stopped.length; i++)
                    if (!seq.trackAt(i).active && stopped[i] < 0) stopped[i] = frame[0];
            }
            for (int i = 0; i < stopped.length; i++) {
                JsonNode nativeTrack = nativeSong.path("tracks").get(i);
                String identity = nativeTrack.path("kind").asText() + "/" + nativeTrack.path("channel").asInt();
                List<Attack> expected = new ArrayList<>();
                for (JsonNode event : nativeTrack.path("events")) {
                    if (event.path("frame").asInt() < frames)
                        expected.add(new Attack(event.path("frame").asInt(), event.path("offset").asInt(), event.path("pitch").asInt()));
                }
                for (int index = 0; index < Math.min(expected.size(), actual.get(i).size()); index++)
                    assertEquals(expected.get(index), actual.get(i).get(index), "0x" + Integer.toHexString(id)
                            + " " + identity + " first divergent attack index=" + index);
                assertEquals(expected.size(), actual.get(i).size(), "0x" + Integer.toHexString(id)
                        + " " + identity + " native attack count");
                if (nativeTrack.path("stop_frame").isInt())
                    assertEquals(nativeTrack.path("stop_frame").asInt(), stopped[i], "native stop for " + identity);
            }
            if (integer(arrangement, "loopFrames") == 0) {
                assertEquals(nativeSong.path("end_frame").asInt() + 1, frames);
                assertTrue(seq.getTracks().stream().noneMatch(t -> t.active));
            } else {
                assertTrue(seq.getTracks().stream().anyMatch(t -> t.active));
                JsonNode lead = nativeSong.path("tracks").get(1); // FM1 full-form spine, not percussion loops
                JsonNode first = lead.path("jumps").get(0);
                JsonNode second = lead.path("jumps").get(1);
                assertNotNull(second, "at least two full melodic forms were executed");
                assertTrue(second.path("frame").asInt() <= frames,
                        "the final packet finishes the last loop at its next service boundary");
                int period = second.path("frame").asInt() - first.path("frame").asInt();
                assertTrue(Math.abs(period - integer(arrangement, "loopFrames")) <= 1,
                        "whole native return period agrees with conservatively quantized catalogue duration");
            }
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void fullBankReferenceProvesInclusionFormAndDacQuarterGrouping() throws Exception {
        reference(0x81); // entire bank, not only included songs
        assertEquals(19, reference.size());
        for (Object a : arrangements) {
            JsonNode source = reference(integer(a, "musicId"));
            JsonNode dac = source.path("tracks").get(0);
            List<Integer> durations = new ArrayList<>();
            for (JsonNode n : dac.path("first_units")) durations.add(n.asInt());
            assertEquals(value(a, "firstDacUnits"), durations, "seven actual inter-attack SMPS durations");
            if (integer(a, "loopFrames") == 0)
                assertTrue(source.path("end_frame").isInt(), "finite song reaches native cfStopTrack");
        }
        JsonNode credits = reference(0x91);
        assertEquals(7600, credits.path("end_frame").asInt());
        assertEquals(List.of(15, 10, 7, 3, 4),
                java.util.stream.StreamSupport.stream(credits.path("tempo_changes").spliterator(), false)
                        .map(n -> n.path("tempo").asInt()).toList());
        assertEquals(1536, reference(0x81).path("tracks").get(1).path("jumps").get(0).path("loop_units").asInt(),
                "GHZ's eight-unit hi-hat and repeated DAC patterns are not whole-song boundaries");
    }

    @Test
    void creditsTempoAnchorsPreserveNativeSectionsAndFinalOwner() throws Exception {
        var provider = harness.loader().loadClass("sitarhero.catalogue.Sonic1Catalogue");
        List<?> anchors = (List<?>) provider.getMethod("tempoAnchors", String.class).invoke(null, "s1-credits");
        assertEquals(List.of(0, 84, 172, 204, 212, 224, 275),
                anchors.stream().map(a -> integerUnchecked(a, "beat")).toList());
        assertEquals(List.of(0, 2056, 4318, 5171, 5394, 5969, 7600),
                anchors.stream().map(a -> integerUnchecked(a, "serviceFrame")).toList());
        assertEquals(List.of(24, 12, 12, 12, 16, 24, 24),
                anchors.stream().map(a -> integerUnchecked(a, "unitsPerBeat")).toList());
        for (Object a : arrangements) if (integer(a, "musicId") != 0x91)
            assertEquals(List.of(), provider.getMethod("tempoAnchors", String.class).invoke(null, value(a, "id")));
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void actualShortPreparationHasEverySelectedRoleAndNoInventedPart() throws Exception {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var profile = new Sonic1AudioProfile();
        var audio = AudioManager.createStandalonePresentation("s1", profile, config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        try (Rom rom = new Rom()) {
            rom.open(RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath());
            try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
                for (Object a : arrangements) {
                    // Host's long-song cap is owned by the integrating parent. This
                    // actual production preparation verifies source ownership immediately.
                    // Ending's real melodic PSG only enters at service600.
                    int opening = integer(a, "musicId") == 0x8B ? 1081 : 480;
                    int frames = Math.min(opening, integer(a, "durationFrames"));
                    var prepared = music.prepare("s1", integer(a, "musicId"), frames);
                    assertEquals(frames * 800L, prepared.lengthSamples());
                    for (String role : List.of("SITAR", "HARP", "SYNTH")) {
                        String field = switch (role) { case "SITAR" -> "lead"; case "HARP" -> "rhythm"; default -> "synth"; };
                        List<?> sections = (List<?>) value(a, field);
                        if (sections.isEmpty()) continue;
                        // The opening ownership handoff can contain an intended rest.
                        // Every declared part must enter in this real opening window.
                        assertTrue(sections.stream().anyMatch(section -> prepared.notes().stream().anyMatch(event -> {
                            try { return matches(section, event); }
                            catch (Exception failure) { throw new IllegalStateException(failure); }
                        })), value(a, "id") + "/" + role + " has actual ROM attacks");
                    }
                    boolean drums = supports(a, "BONGOS");
                    assertEquals(drums, prepared.notes().stream().anyMatch(e -> e.kind() == SceneNoteEvent.Kind.DAC));
                    if (!supports(a, "SYNTH"))
                        assertFalse(prepared.notes().stream().anyMatch(e -> e.kind() == SceneNoteEvent.Kind.PSG));
                    assertTrue(prepared.notes().stream().allMatch(e -> e.onsetSamples() >= 0
                            && e.onsetSamples() + e.durationSamples() <= prepared.lengthSamples()));
                }
            }
        } finally { audio.destroy(); }
    }

    private record Attack(int frame, int offset, int pitch) { }

    private static synchronized JsonNode reference(int id) throws Exception {
        if (reference == null) {
            Path output = work.resolve("native-forms.json");
            Path errors = work.resolve("native-forms-errors.txt");
            Process process = new ProcessBuilder("python3", "tools/audio/s1_song_forms.py", "--rom",
                    RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath(), "--frames", "9001", "--events")
                    .redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "bounded native reference survey");
            assertEquals(0, process.exitValue(), java.nio.file.Files.readString(errors));
            reference = new ObjectMapper().readTree(output.toFile());
        }
        String hex = Integer.toHexString(id);
        for (JsonNode song : reference) if (hex.equals(song.path("id").asText())) return song;
        throw new IllegalArgumentException(hex);
    }

    private static boolean matches(Object section, SceneNoteEvent event) throws Exception {
        return event.kind().name().equals(value(section, "kind")) && integer(section, "channel") == event.channel();
    }
    private static Object song(int id) {
        return arrangements.stream().filter(a -> integerUnchecked(a, "musicId") == id).findFirst().orElseThrow();
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean supports(Object a, String role) throws Exception {
        Object r = Enum.valueOf((Class) roleType, role);
        return (boolean) a.getClass().getMethod("supports", roleType).invoke(a, r);
    }
    private static Object value(Object a, String field) throws Exception { return a.getClass().getMethod(field).invoke(a); }
    private static int integer(Object a, String field) throws Exception { return ((Number) value(a, field)).intValue(); }
    private static int integerUnchecked(Object a, String field) {
        try { return integer(a, field); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
