package com.openggf.audio;

import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Compare real ROM note progression AND final PCM with a sped-up normal recording. */
@RequiresRom(SonicGame.SONIC_1)
class TestAcceleratedSmpsPlayback {

    @ParameterizedTest
    @CsvSource({"NTSC,1.5", "NTSC,2.0", "NTSC,3.375", "NTSC,32.0",
            "PAL,1.5", "PAL,2.0", "PAL,3.375", "PAL,32.0"})
    void acceleratedSongMatchesNormalSourceTimeAndDecimatedPcm(String region, double rate) {
        try (var normal = new Playback(region); var fast = new Playback(region)) {
            int frames = 16;
            short[] source = normal.render((int) (frames * rate));
            fast.audio.setForwardPlaybackRate(rate);
            short[] actual = fast.render(frames);
            assertEquals(normal.music(), fast.music(),
                    "note positions and durations must advance at the playback rate, not wall time");
            short[] expected = new short[actual.length];
            for (int i = 0; i < expected.length / 2; i++) {
                int picked = (int) Math.floor(i * rate) * 2;
                expected[i * 2] = source[picked];
                expected[i * 2 + 1] = source[picked + 1];
            }
            assertArrayEquals(expected, actual,
                    "driver services must interleave with synthesis, including all source tail samples");
        }
    }

    @Test
    void fractionalSourcePhaseRestoresAndSilentFramesDoNotAdvanceIt() {
        try (var playback = new Playback("NTSC")) {
            playback.audio.setForwardPlaybackRate(1.5);
            playback.render(1);
            var saved = playback.audio.captureLogicalSnapshot();
            assertEquals(0.5, saved.presentation().forwardTiming().phase());
            short[] expected = playback.render(4);
            var expectedMusic = playback.music();
            playback.audio.restoreLogicalSnapshot(saved);
            assertEquals(saved.presentation().forwardTiming(),
                    playback.audio.captureLogicalSnapshot().presentation().forwardTiming());
            playback.audio.presentFrame(PresentationMode.SILENT);
            playback.drain();
            assertEquals(saved.presentation().forwardTiming(),
                    playback.audio.captureLogicalSnapshot().presentation().forwardTiming());
            assertArrayEquals(expected, playback.render(4));
            assertEquals(expectedMusic, playback.music());
        }
    }

    @Test
    void changingSpeedAndReturningToNormalPreserveTheSourceTimeline() {
        try (var normal = new Playback("NTSC"); var changing = new Playback("NTSC")) {
            short[] source = normal.render(6);
            double sourceStart = 0;
            for (double rate : new double[] {1.5, 2.25, 1.25, 1.0}) {
                changing.audio.setForwardPlaybackRate(rate);
                short[] actual = changing.render(1);
                short[] expected = new short[actual.length];
                for (int i = 0; i < actual.length / 2; i++) {
                    int picked = (int) (sourceStart + i * rate) * 2;
                    expected[i * 2] = source[picked];
                    expected[i * 2 + 1] = source[picked + 1];
                }
                assertArrayEquals(expected, actual);
                sourceStart += changing.samplesPerPacket * rate;
            }
            assertEquals(normal.music(), changing.music());
        }
    }

    @Test
    void failedAcceleratedServiceRollsBackSourcePhaseAndRetriesTheSamePcm() throws Exception {
        try (var reference = new Playback("NTSC"); var failing = new Playback("NTSC")) {
            for (var playback : new Playback[] {reference, failing}) {
                playback.audio.setForwardPlaybackRate(1.5);
                playback.render(1);
                playback.audio.setForwardPlaybackRate(32);
            }
            var before = failing.audio.captureLogicalSnapshot().presentation().forwardTiming();
            var musicBefore = failing.music();
            var field = AudioManager.class.getDeclaredField("shadowSmpsSession");
            field.setAccessible(true);
            var session = (com.openggf.audio.session.SmpsDriverSession) field.get(failing.audio);
            com.openggf.audio.session.SmpsSessionTestSupport.setPhysicalWriteInterceptor(session,
                    ignored -> { throw new IllegalStateException("source service failure"); });
            assertThrows(IllegalStateException.class,
                    () -> failing.audio.presentFrame(PresentationMode.FORWARD));
            assertEquals(before, failing.audio.captureLogicalSnapshot().presentation().forwardTiming());
            assertEquals(musicBefore, failing.music());
            com.openggf.audio.session.SmpsSessionTestSupport.setPhysicalWriteInterceptor(session, ignored -> { });
            assertArrayEquals(reference.render(1), failing.render(1));
            assertEquals(reference.music(), failing.music());
        }
    }

    @Test
    void failureAfterCommitDoesNotRollBackTheCommittedSourceClock() throws Exception {
        try (var reference = new Playback("NTSC"); var failing = new Playback("NTSC")) {
            for (var playback : new Playback[] {reference, failing}) {
                playback.audio.setForwardPlaybackRate(1.5);
                playback.render(1);
                playback.audio.setForwardPlaybackRate(2.25);
            }
            var field = AudioManager.class.getDeclaredField("shadowProducer");
            field.setAccessible(true);
            var producer = field.get(failing.audio);
            var serviceField = producer.getClass().getDeclaredField("forwardService");
            serviceField.setAccessible(true);
            var service = mock(com.openggf.audio.presentation.AudioPresentationForwardService.class);
            var boundary = mock(com.openggf.audio.presentation.AudioPresentationForwardService.ForwardBoundary.class);
            var receipt = mock(com.openggf.audio.presentation.AudioPresentationForwardService.CommittedReceipt.class);
            when(service.beginForwardBoundary()).thenReturn(boundary);
            when(boundary.commit()).thenReturn(receipt);
            doThrow(new IllegalStateException("post-commit diagnostic failure"))
                    .when(boundary).publishDiagnostics(receipt);
            serviceField.set(producer, service);
            reference.render(1);
            assertThrows(IllegalStateException.class,
                    () -> failing.audio.presentFrame(PresentationMode.FORWARD));
            assertEquals(reference.audio.captureLogicalSnapshot().presentation().forwardTiming(),
                    failing.audio.captureLogicalSnapshot().presentation().forwardTiming());
            assertEquals(reference.music(), failing.music());
            verify(boundary, never()).rollback();
        }
    }

    private static final class Playback implements AutoCloseable {
        final Rom rom = new Rom();
        final AudioManager audio;
        final LiveCaptureAudioHandle capture;
        final int samplesPerPacket;
        Playback(String region) {
            int fps = region.equals("PAL") ? 50 : 60;
            samplesPerPacket = 48_000 / fps;
            assertTrue(rom.open(RomTestUtils.ensureSonic1RomAvailable().getAbsolutePath()));
            var config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.FPS, fps);
            config.setConfigValue(SonicConfiguration.REGION, region);
            audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(),
                    config, PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                    new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
            audio.setRom(rom);
            audio.playMusic(Sonic1Music.GHZ.id);
            capture = AudioManagerTestDiagnostics.attachPresentationCapture(audio, fps);
        }
        short[] render(int frames) {
            short[] result = new short[frames * samplesPerPacket * 2];
            for (int frame = 0; frame < frames; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                System.arraycopy(drain(), 0, result, frame * samplesPerPacket * 2, samplesPerPacket * 2);
            }
            return result;
        }
        short[] drain() {
            short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
            assertEquals(samplesPerPacket, capture.drainPresentationFrame(packet));
            return packet;
        }
        Object music() {
            var state = audio.captureLogicalSnapshot().presentation().smpsLogical().sequencers()
                    .stream().filter(entry -> !entry.sfx()).findFirst().orElseThrow().snapshot();
            // Track snapshots defensively copy arrays; compare their values.
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(state);
        }
        @Override public void close() { capture.close(); audio.destroy(); rom.close(); }
    }
}
