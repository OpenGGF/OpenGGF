package com.openggf.mods.code;

import com.openggf.audio.AudioManager;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.mods.scene.SceneNoteEvent;
import com.openggf.mods.scene.host.music.SceneMusicFactory;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.SitarHeroS3kSongProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Complete ROM control-flow forms plus bounded real production preparation, independent of shared curation. */
class TestSitarHeroS3kSongCatalogue {
    @TempDir static Path work;
    static ExampleModHarness harness;
    static Class<?> catalogue;
    static List<?> songs;

    @BeforeAll static void compile() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        catalogue = harness.loader().loadClass("sitarhero.catalogue.Sonic3kCatalogue");
        songs = (List<?>) catalogue.getMethod("all").invoke(null);
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    @Test void inventoriesBothBanksWithExactAliasesAndExplicitRoleAbsence() throws Exception {
        assertEquals(50, songs.size());
        var expected = new HashSet<Integer>();
        for (int id = 1; id <= 0x26; id++) expected.add(id);
        expected.addAll(List.of(0x2D, 0x2F, 0x30, 0x32, 0x33, 0x109, 0x115, 0x11F, 0x125, 0x12E, 0x12F, 0x132));
        var actual = new HashSet<Integer>(); var ids = new HashSet<String>();
        for (Object song : songs) {
            assertTrue(actual.add(number(song, "musicId"))); assertTrue(ids.add(string(song, "id")));
            assertEquals("s3k", string(song, "game"));
            assertTrue(number(song, "durationFrames") <= 36_000);
            int duration = number(song, "loopFrames") == 0 ? number(song, "endFrames")
                    : Math.max(7200, number(song, "introFrames") + 2 * number(song, "loopFrames"));
            assertEquals(duration, number(song, "durationFrames"));
            for (String field : List.of("lead", "rhythm", "synth")) for (Object section : list(song, field))
                assertTrue(number(section, "firstBeat") < number(section, "lastBeat"));
        }
        assertEquals(expected, actual);
        for (String id : List.of("marble-garden-1", "marble-garden-2", "flying-battery-1", "flying-battery-1-s3",
                "flying-battery-2", "mushroom-hill-1", "mushroom-hill-2", "sandopolis-2", "death-egg-1", "death-egg-2", "s3k-miniboss", "s3-knuckles"))
            assertTrue(list(song(id), "synth").isEmpty(), id + " has no active PSG music");
        assertEquals(Set.of("s3k-title", "s3-title", "s3k-ending", "s3-ending", "s3k-credits"),
                new HashSet<>(songs.stream().filter(s -> uncheckedNumber(s, "loopFrames") == 0).map(s -> uncheckedString(s, "id")).toList()));
        assertThrows(UnsupportedOperationException.class, songs::clear);
    }

    @Test void retailSampleFamiliesKeepClapsDistinctFromKicksAndIgnoreSpeech() throws Exception {
        assertEquals(0, catalogue.getMethod("drumLane", int.class).invoke(null, 0xB2));
        assertEquals(0, catalogue.getMethod("drumLane", int.class).invoke(null, 0xB3));
        assertEquals(4, catalogue.getMethod("drumLane", int.class).invoke(null, 0xB4));
        assertEquals(-1, catalogue.getMethod("drumLane", int.class).invoke(null, 0xA5));
        assertEquals(-1, catalogue.getMethod("drumLane", int.class).invoke(null, 0xB6));
    }

    @Test void creditsClockRetainsQuarterBeatTempoBoundaryAndNaturalStop() throws Exception {
        List<?> anchors = (List<?>) catalogue.getMethod("tempoAnchors", String.class).invoke(null, "s3k-credits");
        assertEquals(4, catalogue.getMethod("tempoAnchorBeatDivisor", String.class).invoke(null, "s3k-credits"));
        assertEquals(List.of(0, 136, 521, 784, 912, 1056, 1598), anchors.stream().map(a -> uncheckedNumber(a, "beat")).toList());
        assertEquals(List.of(0, 866, 3495, 5124, 5943, 6835, 10247), anchors.stream().map(a -> uncheckedNumber(a, "serviceFrame")).toList());
        assertTrue(((List<?>) catalogue.getMethod("tempoAnchors", String.class).invoke(null, "angel-island-1")).isEmpty());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void everyDurationCrossesTwoCompleteNativeFormsOrTheActualNaturalStop() throws Exception {
        Rom rom = GameServices.rom().getRom();
        var loader = new Sonic3kSmpsLoader(rom); var dac = loader.loadDacData();
        for (Object song : songs) {
            String id = string(song, "id"); int music = number(song, "musicId"), duration = number(song, "durationFrames");
            var result = SitarHeroS3kSongProbe.inspectBank(loader, dac, music, 36_001);
            Object form = catalogue.getMethod("nativeForm", String.class).invoke(null, id);
            List<?> anchors = (List<?>) catalogue.getMethod("tempoAnchors", String.class).invoke(null, id);
            if (id.equals("s3k-credits")) {
                assertEquals(result.tempos().size() + 2, anchors.size());
                for (int i = 0; i < result.tempos().size(); i++) {
                    var tempo = result.tempos().get(i); Object anchor = anchors.get(i + 1);
                    assertEquals(tempo.frame(), number(anchor, "serviceFrame"));
                    assertEquals(tempo.units() * 4, number(anchor, "beat") * 24);
                }
                assertEquals(duration - 1, number(anchors.getLast(), "serviceFrame"));
            } else assertTrue(result.tempos().isEmpty(), id + " needs no omitted tempo changes");
            var percussion = result.parts().stream().filter(p -> p.kind().equals("DAC")).findFirst().orElseThrow();
            assertEquals(list(song, "firstDacUnits"), percussion.firstDacUnits(), id + " native inter-attack durations");
            assertEquals(!percussion.firstDacUnits().isEmpty(), song.getClass().getMethod("drums").invoke(song));
            if (number(song, "loopFrames") == 0) {
                assertEquals(duration, result.naturalEndFrame(), id + " naturally stops, no restart");
                assertEquals(number(form, "endUnits"), result.parts().stream().mapToInt(SitarHeroS3kSongProbe.Part::stopUnits).max().orElseThrow());
                assertTrue(result.parts().stream().allMatch(p -> p.stopFrame() >= 0));
            } else {
                assertEquals(0, result.naturalEndFrame());
                List<SitarHeroS3kSongProbe.Boundary> jumps = percussion.jumps();
                assertTrue(jumps.size() >= 2, id + " must finish two complete DAC arrangement loops");
                int loop = jumps.get(1).units() - jumps.getFirst().units();
                assertEquals(number(form, "loopUnits"), loop, id + " outer arrangement jump, not inner note/pattern loop");
                assertEquals(catalogue.getMethod("dacIntroUnits", String.class).invoke(null, id), jumps.getFirst().units() - loop, id + " DAC introduction");
                assertTrue(duration >= jumps.get(1).frame(), id + " reaches second native form boundary (exclusive PCM endpoint)");
                for (var part : result.parts()) {
                    var sources = new HashMap<Integer, List<SitarHeroS3kSongProbe.Boundary>>();
                    for (var jump : part.jumps()) sources.computeIfAbsent(jump.source(), ignored -> new ArrayList<>()).add(jump);
                    var fullForm = sources.values().stream().filter(rows -> rows.size() >= 2)
                            .max(Comparator.comparingInt(rows -> rows.get(1).units() - rows.getFirst().units()));
                    if (fullForm.isPresent()) assertTrue(duration >= fullForm.get().get(1).frame(),
                            id + " completes two native forms on " + part.kind() + part.channel());
                }
            }
            if (list(song, "synth").isEmpty())
                assertTrue(result.parts().stream().filter(p -> p.kind().equals("PSG")).allMatch(p -> p.attacks() == 0), id + " unsupported PSG is actually absent");
            for (String role : List.of("lead", "rhythm", "synth")) for (Object section : list(song, role)) {
                String kind = string(section, "kind"); int channel = number(section, "channel");
                var primary = result.parts().stream().filter(p -> p.kind().equals(kind) && p.channel() == channel).findFirst().orElseThrow();
                assertTrue(primary.attacks() > 0, id + "/" + role + " selects an active authentic part");
                if (kind.equals("PSG")) assertEquals(primary.noise(), section.getClass().getMethod("noise").invoke(section), id + " actual PSG noise mode");
                int harmony = number(section, "harmony");
                if (harmony >= 0) assertTrue(result.parts().stream().anyMatch(p -> p.kind().equals(kind) && p.channel() == harmony && p.attacks() > 0));
            }
        }
        // Both table selections use the same native bytes for these apparently separate enum variants.
        for (int music : List.of(0x0B, 0x0C, 0x0D, 0x0E, 0x26, 0x2D))
            assertEquals(loader.findMusicOffset(music), loader.findMusicOffset(0x100 | music));
        assertEquals(loader.findMusicOffset(0x18), loader.findMusicOffset(0x2E));
        for (int music : List.of(0x09, 0x15, 0x1F, 0x25, 0x2E, 0x2F, 0x32))
            assertNotEquals(loader.findMusicOffset(music), loader.findMusicOffset(0x100 | music), "included S3 variant is a separate ROM stream");
        // Final Boss is a relocated copy, not an exact pointer alias or a new arrangement.
        var boss = SitarHeroS3kSongProbe.inspectBank(loader, dac, 0x30, 36_001);
        var copy = SitarHeroS3kSongProbe.inspectBank(loader, dac, 0x130, 36_001);
        assertNotEquals(boss.romOffset(), copy.romOffset());
        assertEquals(boss.headerTempo(), copy.headerTempo()); assertEquals(boss.divider(), copy.divider());
        assertEquals(boss.tempos(), copy.tempos()); assertEquals(boss.naturalEndFrame(), copy.naturalEndFrame());
        assertEquals(boss.parts().size(), copy.parts().size());
        for (int i = 0; i < boss.parts().size(); i++) {
            var original = boss.parts().get(i); var relocated = copy.parts().get(i);
            assertEquals(original.kind(), relocated.kind()); assertEquals(original.channel(), relocated.channel());
            assertEquals(original.attacks(), relocated.attacks()); assertEquals(original.signature(), relocated.signature());
            assertEquals(original.stopFrame(), relocated.stopFrame()); assertEquals(original.stopUnits(), relocated.stopUnits());
            assertEquals(original.noise(), relocated.noise()); assertEquals(original.firstDacUnits(), relocated.firstDacUnits());
            assertEquals(original.jumps().size(), relocated.jumps().size());
            int delta = relocated.start() - original.start(); assertEquals(789, delta);
            for (int j = 0; j < original.jumps().size(); j++) {
                var left = original.jumps().get(j); var right = relocated.jumps().get(j);
                assertEquals(left.frame(), right.frame()); assertEquals(left.units(), right.units());
                assertEquals(left.returnDepth(), right.returnDepth());
                assertEquals(left.source() + delta, right.source()); assertEquals(left.target() + delta, right.target());
            }
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void bothProductionTablesExecuteExactlyTheNativeCompleteBankStreams() throws Exception {
        var loader = new Sonic3kSmpsLoader(GameServices.rom().getRom()); var dac = loader.loadDacData();
        var musicIds = new ArrayList<Integer>();
        for (int id = 1; id <= 0x33; id++) musicIds.add(id);
        for (int id = 1; id <= 0x32; id++) musicIds.add(0x100 | id);
        for (int music : musicIds) {
            var nativeBank = SitarHeroS3kSongProbe.inspectBank(loader, dac, music, 36_001);
            assertEquals(nativeBank, SitarHeroS3kSongProbe.inspect(loader, dac, music, 36_001),
                    "complete native attacks, source counts, jumps and stops for music " + Integer.toHexString(music));
            if (music == 0x132) {
                assertEquals(609, nativeBank.naturalEndFrame());
                assertEquals(48, nativeBank.parts().stream().filter(p -> p.kind().equals("FM") && p.channel() == 2).findFirst().orElseThrow().attacks());
                assertEquals(72, nativeBank.parts().stream().filter(p -> p.kind().equals("FM") && p.channel() == 3).findFirst().orElseThrow().attacks());
            }
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void realProductionPreparationHasTheSelectedMelodicNoiseAndSampleOwners() throws Exception {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60); config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s3k", new Sonic3kAudioProfile(), config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        Rom rom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> rom)) {
            // Six-second preparations stay within this branch's pre-expansion host cap.
            for (String id : List.of("angel-island-1", "marble-garden-1", "flying-battery-1", "s3-knuckles", "s3-title", "s3k-credits", "s3-ending")) {
                Object song = song(id); var prepared = music.prepare("s3k", number(song, "musicId"), 360);
                assertEquals(288_000, prepared.lengthSamples()); assertFalse(prepared.notes().isEmpty());
                for (String role : List.of("lead", "rhythm", "synth")) {
                    List<?> sections = list(song, role); if (sections.isEmpty()) continue;
                    Object first = sections.getFirst(); int channel = number(first, "channel"); String kind = string(first, "kind");
                    assertTrue(prepared.notes().stream().anyMatch(e -> e.kind().name().equals(kind) && e.channel() == channel), id + "/" + role);
                }
                assertTrue(prepared.notes().stream().anyMatch(e -> e.kind() == SceneNoteEvent.Kind.DAC));
                if (list(song, "synth").isEmpty()) assertTrue(prepared.notes().stream().noneMatch(e -> e.kind() == SceneNoteEvent.Kind.PSG));
            }
        } finally { audio.destroy(); }
    }

    static Object song(String id) { return songs.stream().filter(s -> uncheckedString(s, "id").equals(id)).findFirst().orElseThrow(); }
    static int number(Object object, String field) throws Exception { return ((Number) object.getClass().getMethod(field).invoke(object)).intValue(); }
    static String string(Object object, String field) throws Exception { return (String) object.getClass().getMethod(field).invoke(object); }
    static List<?> list(Object object, String field) throws Exception { return (List<?>) object.getClass().getMethod(field).invoke(object); }
    static int uncheckedNumber(Object object, String field) { try { return number(object, field); } catch (Exception e) { throw new IllegalStateException(e); } }
    static String uncheckedString(Object object, String field) { try { return string(object, field); } catch (Exception e) { throw new IllegalStateException(e); } }
}
