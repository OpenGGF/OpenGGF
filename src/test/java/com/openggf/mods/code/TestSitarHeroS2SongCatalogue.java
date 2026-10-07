package com.openggf.mods.code;

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
import com.openggf.game.sonic2.Sonic2ZoneRegistry;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.host.music.SceneMusicFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestSitarHeroS2SongCatalogue {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static List<?> arrangements;
    private static final int[] IDS = {0x81, 0x8C, 0x86, 0x83, 0x94, 0x84, 0x8F, 0x82,
            0x8E, 0x90, 0x87, 0x91, 0x85, 0x80, 0x9B, 0x89, 0x88, 0x8D, 0x8B, 0x92, 0x8A, 0xBD};
    record Actual(String kind, int channel, int frame, int offset) { }

    @BeforeAll
    static void compileExample() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        arrangements = (List<?>) harness.loader().loadClass("sitarhero.catalogue.Sonic2Catalogue")
                .getMethod("all").invoke(null);
    }
    @AfterAll
    static void closeExample() throws Exception { if (harness != null) harness.close(); }

    @Test
    void completeInventoryUsesCompatibleStagesAndHonestRolePresence() throws Exception {
        assertEquals(22, arrangements.size());
        Set<Integer> actualIds = new HashSet<>();
        Set<String> labels = new HashSet<>();
        Set<String> ids = new HashSet<>();
        var registry = new Sonic2ZoneRegistry();
        for (Object a : arrangements) {
            assertTrue(actualIds.add(integer(a, "musicId")));
            assertTrue(labels.add(string(a, "label")));
            assertTrue(ids.add(string(a, "id")));
            assertEquals("s2", string(a, "game"));
            int zone = integer(a, "zone"), act = integer(a, "act");
            assertTrue(zone >= 0 && zone < 11);
            assertTrue(act >= 0 && act < registry.getActCount(zone));
            if (arrangements.indexOf(a) < 11)
                assertEquals(integer(a, "musicId"), registry.getMusicId(zone, act), "Zone picture must match the source song");
            assertEquals(7, list(a, "firstDacUnits").size(), "All included S2 songs have a real DAC stream");
            if (integer(a, "loopFrames") > 0) {
                assertEquals(7200, integer(a, "durationFrames"));
                assertTrue(integer(a, "durationFrames") >= integer(a, "introFrames") + 2 * integer(a, "loopFrames"));
            } else {
                assertTrue(Set.of("ending-s2", "credits-s2").contains(string(a, "id")));
                assertEquals(integer(a, "endFrames"), integer(a, "durationFrames"));
            }
            for (Object role : harness.loader().loadClass("sitarhero.model.Role").getEnumConstants()) {
                boolean supported = (boolean) a.getClass().getMethod("supports", role.getClass()).invoke(a, role);
                boolean missing = role.toString().equals("SYNTH") && Set.of("hill-top", "wing-fortress").contains(string(a, "id"));
                assertEquals(!missing, supported, string(a, "id") + "/" + role);
            }
        }
        assertEquals(java.util.Arrays.stream(IDS).boxed().collect(java.util.stream.Collectors.toSet()), actualIds);
        // Deliberately included: unused HPZ and the substantial 2P menu/results song.
        assertTrue(actualIds.containsAll(Set.of(0x9B, 0x92)));
        // Excluded: power-up music and brief jingles/cues, including the 9-second Title fanfare.
        assertTrue(java.util.Collections.disjoint(actualIds, Set.of(0x93, 0x99, 0x98, 0x96, 0x97, 0xB8, 0x9C, 0xBA, 0xDC)));
    }

    @Test
    void nativeControlFlowAndServiceProgressionMatchEveryProductionSong() throws Exception {
        String path = System.getProperty("sonic2.rom.path");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "Requires absolute S2 REV01 ROM path");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(path));
            Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
            var dac = loader.loadDacData();
            for (int id : IDS) {
                var data = loader.loadMusic(id);
                assertNotNull(data, "music %02X".formatted(id));
                var reference = SitarHeroS2NativeProgram.read(data, 16_500);
                int[] frames = reference.serviceFrames(16_500);
                List<Actual> actual = new ArrayList<>();
                int[] frame = {0};
                var sequencer = new SmpsSequencer(data, dac, () -> { }, Sonic2SmpsSequencerConfig.CONFIG);
                sequencer.setNoteListener(new SmpsNoteListener() {
                    public void attack(SmpsSequencer.Track t) {
                        actual.add(new Actual(t.type.name(), t.channelId, frame[0], t.pos));
                    }
                    public void release(SmpsSequencer.Track t) { }
                });
                int end = reference.tracks().stream().mapToInt(SitarHeroS2NativeProgram.Track::stopUnit).max().orElse(-1);
                int limit = end > 0 ? frames[end] + 2 : 7_201;
                for (; frame[0] < limit; frame[0]++) sequencer.serviceOuterFrame();
                for (var track : reference.tracks()) {
                    List<Actual> expected = track.attacks().stream().filter(a -> frames[a.unit()] < limit)
                            .map(a -> new Actual(track.kind(), track.channel(), frames[a.unit()], a.offset())).toList();
                    List<Actual> observed = actual.stream().filter(a -> a.kind().equals(track.kind()) && a.channel() == track.channel()).toList();
                    String owner = "ROM %02X %s%d full progression".formatted(id, track.kind(), track.channel());
                    assertEquals(expected.size(), observed.size(), owner + " attack count");
                    for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), observed.get(i), owner + " attack " + i);
                }
                Object arrangement = arrangements.stream().filter(a -> {
                    try { return integer(a, "musicId") == id; }
                    catch (Exception e) { throw new IllegalStateException(e); }
                }).findFirst().orElseThrow();
                for (Object section : list(arrangement, "synth")) {
                    int channel = integer(section, "channel");
                    var psg = reference.tracks().stream().filter(t -> t.kind().equals("PSG") && t.channel() == channel).findFirst().orElseThrow();
                    boolean noise = flag(section, "noise");
                    assertFalse(psg.attacks().isEmpty());
                    assertTrue(psg.attacks().stream().allMatch(a -> a.noise() == noise), "Actual PSG waveform ownership %02X".formatted(id));
                }
                var drum = reference.tracks().getFirst();
                List<Integer> firstSeven = new ArrayList<>();
                for (int i = 0; i < 7; i++) firstSeven.add((drum.attacks().get(i + 1).unit() - drum.attacks().get(i).unit()) / reference.divider());
                assertEquals(firstSeven, list(arrangement, "firstDacUnits"), "Native DAC attack durations %02X".formatted(id));
                if (end > 0) {
                    assertTrue(sequencer.getTracks().stream().noneMatch(t -> t.active), "natural stop %02X".formatted(id));
                    assertEquals(frames[end] + 1, integer(arrangement, "endFrames"), "Must reach last native stop, including delayed PSG tails");
                } else {
                    int bodyUnits = integer(arrangement, "loopBeats") * integer(arrangement, "unitsPerBeat") * reference.divider();
                    // Full form must be a top-level melodic/harmonic back-edge, not
                    // a tiny DAC/PSG ostinato or the first repeated phrase.
                    assertTrue(reference.tracks().stream().filter(t -> t.kind().equals("FM"))
                            .flatMap(t -> t.jumps().stream()).anyMatch(j -> j.unit() - j.firstVisit() == bodyUnits), "Whole native melodic form %02X".formatted(id));
                    assertEquals((bodyUnits * 256 + reference.tempo() - 1) / reference.tempo(), integer(arrangement, "loopFrames"));
                    int introFrames = integer(arrangement, "introFrames");
                    assertTrue(reference.tracks().stream().filter(t -> t.kind().equals("FM"))
                            .flatMap(t -> t.jumps().stream()).anyMatch(j -> introFrames == 0 ? j.firstVisit() == 0
                                    : frames[j.firstVisit()] == introFrames), "Native intro boundary %02X".formatted(id));
                    assertTrue(integer(arrangement, "durationFrames") >= introFrames + 2 * integer(arrangement, "loopFrames"));
                }
                if (id == 0xBD) assertEquals(java.util.Map.of(864, 0xEA, 4128, 0xCD, 4704, 0xC5, 6144, 0xC0), reference.tempoChanges());
                if (id == 0x88) {
                    // FM1's seven-count bass grouping and FM5's 28-count ostinato
                    // are internally repetitive. Their musical events repeat at
                    // the entire melody's 2304-unit boundary, without an LCM-sized song.
                    for (int channel : List.of(0, 4)) {
                        var t = reference.tracks().stream().filter(v -> v.kind().equals("FM") && v.channel() == channel).findFirst().orElseThrow();
                        var first = t.attacks().stream().filter(v -> v.unit() >= 672 && v.unit() < 2976)
                                .map(v -> List.of(v.unit() - 672, v.note())).toList();
                        var second = t.attacks().stream().filter(v -> v.unit() >= 2976 && v.unit() < 5280)
                                .map(v -> List.of(v.unit() - 2976, v.note())).toList();
                        assertEquals(first, second, "Complete Special Stage backing progression");
                    }
                }
            }
        }
    }

    @Test
    void shortProductionPreparationCorroboratesAllSelectedRolesAndAbsentPsg() throws Exception {
        String path = System.getProperty("sonic2.rom.path");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "Requires absolute S2 REV01 ROM path");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(path));
            var config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.FPS, 60);
            config.setConfigValue(SonicConfiguration.REGION, "NTSC");
            var audio = AudioManager.createStandalonePresentation("s2", new Sonic2AudioProfile(), config,
                    PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                    new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
            try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
                for (Object a : arrangements) {
                    var prepared = music.prepare("s2", integer(a, "musicId"), 2400);
                    assertEquals(1_920_000L, prepared.lengthSamples());
                    for (String role : List.of("lead", "rhythm", "synth")) {
                        List<?> sections = list(a, role);
                        if (sections.isEmpty()) {
                            assertTrue(prepared.notes().stream().noneMatch(e -> e.kind() == SceneNoteEvent.Kind.PSG));
                        } else for (Object s : sections) {
                            if (integer(s, "firstBeat") > 36) continue; // later Credits tempo boundaries covered by full native walk
                            var kind = SceneNoteEvent.Kind.valueOf(string(s, "kind"));
                            int channel = integer(s, "channel");
                            List<SceneNoteEvent> notes = prepared.notes().stream().filter(e -> e.kind() == kind && e.channel() == channel).toList();
                            assertFalse(notes.isEmpty(), string(a, "id") + "/" + role + " has a real production part");
                            int harmony = integer(s, "harmony");
                            if (harmony >= 0) assertTrue(prepared.notes().stream().anyMatch(e -> e.kind() == kind && e.channel() == harmony),
                                    string(a, "id") + "/" + role + " harmony has a real production part");
                            if (kind == SceneNoteEvent.Kind.PSG && flag(s, "noise"))
                                assertEquals(2, channel, "Noise belongs to real PSG3, never a pitched PSG surrogate");
                        }
                    }
                    assertTrue(prepared.notes().stream().anyMatch(e -> e.kind() == SceneNoteEvent.Kind.DAC));
                }
            } finally { audio.destroy(); }
        }
    }

    private static int integer(Object a, String field) throws Exception { return ((Number) a.getClass().getMethod(field).invoke(a)).intValue(); }
    private static String string(Object a, String field) throws Exception { return (String) a.getClass().getMethod(field).invoke(a); }
    private static List<?> list(Object a, String field) throws Exception { return (List<?>) a.getClass().getMethod(field).invoke(a); }
    private static boolean flag(Object a, String field) throws Exception { return (boolean) a.getClass().getMethod(field).invoke(a); }
}
