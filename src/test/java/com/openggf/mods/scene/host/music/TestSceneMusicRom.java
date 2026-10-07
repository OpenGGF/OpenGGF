package com.openggf.mods.scene.host.music;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.SceneMusicPart;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSceneMusicRom {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void failedSpeakerFreezesAudibleTimeAndCannotTurnThePerformanceIntoVirtualPlayback() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        var device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int i = 0; i < 10; i++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(150_000_000L);
            assertEquals(2_400, player.samplePosition());
            audio.replaceFailedPresentationSink(new IllegalStateException("lost speaker"));
            assertTrue(player.paused());
            assertEquals(1, player.underrunCount());
            for (int i = 0; i < 20; i++) audio.presentFrame(PresentationMode.FORWARD);
            now.set(500_000_000L);
            assertEquals(2_400, player.samplePosition(), "produced-ahead packets were never heard");
            assertEquals(2_400, player.samplePositionAt(now.get()));
            player.resume();
            assertTrue(player.paused(), "failed output cannot resume the stale song cursor");
            var retry = music.start(song, 1, 0, false, 0);
            assertTrue(retry.paused(), "retry remains interrupted until a sink is restored");
            assertEquals(0, retry.samplePosition());
            assertEquals(1, retry.underrunCount());
        } finally { audio.destroy(); }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void historicalInputUsesSurroundingAudibleObservationsAndNeverRunsBeyondTheFiniteSong() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        OpenAlPcmSink.Device device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int index = 0; index < 10; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(150_000_000);
            assertEquals(2_400, player.samplePosition());
            now.set(166_000_000);
            assertEquals(2_400, player.samplePosition());
            consumed.set(2_496); now.set(168_000_000);
            assertEquals(2_496, player.samplePosition());
            assertEquals(2_400, player.samplePositionAt(155_000_000),
                    "a late input inside the observed stall must not borrow samples played afterward");
            assertEquals(2_400, player.samplePositionAt(165_000_000));
            assertEquals(2_448, player.samplePositionAt(167_000_000),
                    "a running interval retains sample-rate timestamp precision");
            assertEquals(2_496, player.samplePositionAt(170_000_000),
                    "a future timestamp must not invent an unconsumed sample");
            for (int index = 10; index < 160; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(128_000); now.set(3_000_000_000L);
            assertEquals(song.lengthSamples(), player.samplePosition());
            assertEquals(song.lengthSamples(), player.samplePositionAt(now.get()),
                    "post-song silence must not extend the timestamp coordinate beyond finite EOF");
            assertEquals(song.lengthSamples(), player.samplePositionAt(now.get() + 1_000_000));

            player = music.start(song, 1, 0, false, 800);
            assertEquals(-800, player.samplePositionAt(now.get()));
            for (int index = 0; index < 2; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(480); now.set(3_010_000_000L);
            assertEquals(-320, player.samplePosition());
            consumed.set(960); now.set(3_020_000_000L);
            assertEquals(160, player.samplePosition());
            assertEquals(-80, player.samplePositionAt(3_015_000_000L),
                    "historical mapping retains signed positions across the lead-in boundary");
        } finally { audio.destroy(); }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void replacingOrClosingPreparationReleasesRomPcmWhileOpaqueMetadataRemainsUsable()
            throws IOException, ReflectiveOperationException {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom)) {
            var first = music.prepare("s1", 0x81, 120);
            var firstNotes = first.notes();
            var stopped = music.start(first, 1, 0, false, 0);
            assertNotNull(retainedField(stopped, "masked"));
            assertSame(first, music.prepare("s1", 0x81, 120), "cached retries keep the current preparation");
            assertFalse(stopped.finished());
            var playing = music.start(first, 8, 0, false, 0);
            assertTrue(stopped.finished());
            assertNull(retainedField(stopped, "masked"), "a stopped handle cannot retain its previous arrangement");
            var second = music.prepare("s1", 0x81, 121);
            assertTrue(playing.finished(), "replacing preparation stops its active performance");
            assertNull(retainedField(playing, "masked"));
            assertRetired(first);
            assertEquals(48_000, first.sampleRate());
            assertEquals(96_000, first.lengthSamples());
            assertSame(firstNotes, first.notes(), "retired note metadata remains useful for chart inspection");
            assertThrows(IllegalArgumentException.class, () -> music.start(first, 1, 0, false, 0));
            var last = music.start(second, 1, 0, false, 0);
            music.close();
            assertTrue(last.finished());
            assertNull(retainedField(last, "masked"));
            assertRetired(second);
            assertEquals(96_800, second.lengthSamples());
            assertFalse(second.notes().isEmpty());
        } finally { audio.destroy(); }
    }

    private static void assertRetired(Object prepared) throws ReflectiveOperationException {
        for (String field : List.of("full", "masked", "profile", "data", "dac", "parts"))
            assertNull(retainedField(prepared, field), "retired preparation retains " + field);
    }

    private static Object retainedField(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void timestampsResumeFromThePausedAudibleSampleInsteadOfExtrapolatingThroughThePause() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        AtomicLong starvation = new AtomicLong();
        OpenAlPcmSink.Device device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        when(device.underrunCount()).thenAnswer(ignored -> starvation.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int index = 0; index < 10; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(50_000_000);
            assertEquals(2_400, player.samplePosition());
            player.pause(); now.set(1_050_000_000);
            assertEquals(2_400, player.samplePosition());
            player.resume(); now.set(1_066_000_000); consumed.set(3_168);
            assertEquals(2_448, player.samplePositionAt(1_051_000_000),
                    "a tap 1ms after resume belongs 48 samples beyond the paused position");
            audio.pause();
            assertTrue(player.paused(), "focus pause must freeze the scene source too");
            now.set(2_066_000_000); audio.resume();
            assertFalse(player.paused());
            now.set(2_082_000_000); consumed.set(3_936);
            assertEquals(3_216, player.samplePositionAt(2_067_000_000),
                    "focus resume must restart the input timestamp anchor");
            for (int event = 0; event < 100; event++) {
                now.addAndGet(1_000);
                assertEquals(3_216, player.samplePositionAt(2_067_000_000),
                        "a large input batch must retain its prior audible-clock anchor");
            }
            player.pause(); audio.pause(); audio.resume();
            assertTrue(player.paused(), "regaining focus must preserve a manual performance pause");
            player.resume();
            assertFalse(player.paused());
            assertEquals(0, player.underrunCount());
            starvation.set(1);
            assertEquals(1, player.underrunCount(),
                    "speaker starvation must be observed even with unconsumed software packet remainder");
            assertEquals(1, player.underrunCount(), "the same stopped transition is counted only once");
        } finally { audio.destroy(); }
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillFiniteCaptureAndRestart() throws IOException { verify("s1", 0x81, new Sonic1AudioProfile()); }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void chemicalPlantFiniteCaptureAndRestart() throws IOException { verify("s2", 0x8C, new Sonic2AudioProfile()); }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void angelIslandFiniteCaptureAndRestart() throws IOException { verify("s3k", 0x01, new Sonic3kAudioProfile()); }

    private void verify(String game, int id, GameAudioProfile profile) throws IOException {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation(game, profile, config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> actualRom);
             var capture = audio.beginLiveCaptureAudio(60)) {
            var song = music.prepare(game, id, 120);
            assertEquals(96_000, song.lengthSamples());
            assertSame(song, music.prepare(game, id, 120));
            assertFalse(song.notes().isEmpty());
            assertTrue(song.notes().stream().allMatch(note -> note.onsetSamples() + note.durationSamples() <= song.lengthSamples()));
            assertThrows(UnsupportedOperationException.class, () -> song.notes().clear());
            int fm = game.equals("s2") ? 0 : 1;
            int psg = game.equals("s2") ? 4 : 0;
            SceneMusicPlayer player = music.start(song, fm, psg, false, 800);
            assertEquals(-800, player.samplePosition());
            short[] packet = new short[1600];
            audio.presentFrame(PresentationMode.FORWARD);
            capture.drainPresentationFrame(packet);
            assertArrayEquals(new short[1600], packet);
            assertEquals(0, player.samplePosition());
            short[] first = new short[48_000 * 2];
            for (int frame = 0; frame < 60; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
                System.arraycopy(packet, 0, first, frame * 1600, 1600);
            }
            assertTrue(acVariance(first) > 1, "captured song must vary, not merely contain DC");
            player.pause();
            long paused = player.samplePosition();
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(paused, player.samplePosition());
            player.resume();
            player = music.start(song, fm, psg, false, 0);
            short[] restarted = new short[first.length];
            for (int frame = 0; frame < 60; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
                System.arraycopy(packet, 0, restarted, frame * 1600, 1600);
            }
            assertArrayEquals(first, restarted);
            assertEquals(48_000, player.samplePosition());
            player.setPartAudible(false);
            short[] missedBacking = captureFrames(audio, capture, 60);
            assertTrue(acVariance(missedBacking) > 1, "backing must continue across musical rests when the performer misses");
            assertEquals(0, missedBacking[missedBacking.length - 2]);
            assertEquals(0, missedBacking[missedBacking.length - 1], "finite PCM must end at zero after its 5ms taper");
            assertTrue(player.finished());
            player.stop();
            if (game.equals("s2")) {
                player = music.start(song, fm, psg, false, 0);
                player.setPartAudible(false);
                short[] missed = new short[first.length];
                for (int frame = 0; frame < 60; frame++) {
                    audio.presentFrame(PresentationMode.FORWARD);
                    capture.drainPresentationFrame(packet);
                    System.arraycopy(packet, 0, missed, frame * 1600, 1600);
                }
                assertFalse(Arrays.equals(first, missed), "PSG3 hi-hat must mute its physical noise output");
                assertTrue(acVariance(missed) > 1, "FM and DAC backing must continue after a Synth miss");
            }
            if (game.equals("s1")) {
                player = music.start(song, 1, 0, false, 0);
                player.setPartAudible(false);
                short[] fixedPart = captureFrames(audio, capture, 120);
                player = music.start(song, List.of(new SceneMusicPart(0, 1, 0, false),
                        new SceneMusicPart(48_000, 8, 0, false)), 0);
                player.setPartAudible(false);
                short[] changingPart = captureFrames(audio, capture, 120);
                assertArrayEquals(Arrays.copyOf(fixedPart, 96_000), Arrays.copyOf(changingPart, 96_000),
                        "the opening must use the same selected FM part before its section boundary");
                assertFalse(Arrays.equals(Arrays.copyOfRange(fixedPart, 96_000, fixedPart.length),
                        Arrays.copyOfRange(changingPart, 96_000, changingPart.length)),
                        "muting follows the new musical source after the section boundary");
                assertThrows(IllegalArgumentException.class, () -> music.start(song,
                        List.of(new SceneMusicPart(1, 1, 0, false)), 0));
                assertThrows(IllegalArgumentException.class, () -> music.start(song,
                        List.of(new SceneMusicPart(0, 1, 0, false), new SceneMusicPart(0, 8, 0, false)), 0));
            }
        } finally { audio.destroy(); }
    }

    private static short[] captureFrames(AudioManager audio, com.openggf.audio.LiveCaptureAudioHandle capture, int frames) {
        short[] result = new short[frames * 1600];
        short[] packet = new short[1600];
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            capture.drainPresentationFrame(packet);
            System.arraycopy(packet, 0, result, frame * 1600, 1600);
        }
        return result;
    }

    private static double acVariance(short[] samples) {
        double mean = Arrays.stream(toInts(samples)).average().orElse(0);
        double sum = 0;
        for (short sample : samples) sum += (sample - mean) * (sample - mean);
        return sum / samples.length;
    }
    private static int[] toInts(short[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }
}
