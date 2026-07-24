package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1SmpsConstants;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsConstants;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestSegaPcmCommandRouting {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void segaPcmSpecsMatchDisassembly() {
        assertEquals(new SegaPcmSpec(0x079688, 0x6978, 16_500), new Sonic1AudioProfile().getSegaPcmSpec());
        assertEquals(new SegaPcmSpec(0x0F1E8C, 0x6174, 16_500), new Sonic2AudioProfile().getSegaPcmSpec());
        assertEquals(new SegaPcmSpec(0x0F8000, 0x5E2F, 0x3862), new Sonic3kAudioProfile().getSegaPcmSpec());
        assertEquals(0xFA, Sonic2SmpsConstants.CMD_SEGA);
        assertEquals(0xFE, Sonic3kSmpsConstants.CMD_STOP_SEGA);
        assertEquals(0xFF, Sonic3kSmpsConstants.CMD_SEGA);
        assertEquals(0xE1, Sonic1SmpsConstants.CMD_SEGA);
    }

    @Test
    void pcmStreamUsesYmDacOutputScale() {
        PcmSampleStream stream = new PcmSampleStream(new byte[] {0, (byte) 0x80, (byte) 0xFF}, 48_000, 48_000);
        short[] buffer = new short[6];

        assertEquals(6, stream.read(buffer));

        assertArrayEquals(new short[] {-8192, -8192, 0, 0, 8128, 8128}, buffer);
    }

    @Test
    void rawSampleVoiceMatchesLegacyYmDacOutputScale() {
        SampleBackedVoice voice = SampleBackedVoice.unsigned8Mono(1, 0, "sega",
                new byte[] {0, (byte) 0x80, (byte) 0xFF}, 48_000, 48_000, 0.25f);
        long[] accumulation = new long[6];

        voice.mixInto(accumulation, 3);

        assertArrayEquals(new long[] {-8192, -8192, 0, 0, 8128, 8128}, accumulation);
    }

    @Test
    void presentationFactorySegaPcmMatchesLegacyYmDacOutputScale() {
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true,
                        new SmpsCoordFlagHandlerOwner(
                                new SmpsCoordFlagRuntimeState()));
        DecodedPcm pcm = factory.registerUnsigned8Mono(
                "sega/factory",
                new byte[] {0, (byte) 0x80, (byte) 0xFF},
                48_000);
        SampleBackedVoice voice = factory.segaPcm(1, pcm);
        long[] accumulation = new long[6];

        voice.mixInto(accumulation, 3);

        assertArrayEquals(
                new long[] {-8192, -8192, 0, 0, 8128, 8128},
                accumulation);
        assertEquals("sega/factory",
                ((PresentationVoiceSnapshot.Sample) voice.snapshot())
                        .assetId());
    }

    @Test
    void rawSampleVoiceRestoresAfterRemovalUsingCachedAsset() {
        DecodedPcmCache cache = new DecodedPcmCache();
        DecodedPcm pcm = cache.registerUnsigned8Mono("sega",
                new byte[] {0, (byte) 0x80, (byte) 0xFF}, 48_000);
        SampleBackedVoice removedVoice = SampleBackedVoice.oneShot(1, 0, pcm, 48_000, 1.0f, 0.25f);
        removedVoice.mixInto(new long[2], 1);
        PresentationVoiceSnapshot.Sample snapshot = (PresentationVoiceSnapshot.Sample) removedVoice.snapshot();
        SampleBackedVoice restored = SampleBackedVoice.oneShot(1, 0, cache.get("sega"), 48_000, 1.0f, 0.25f);
        restored.restore(snapshot);
        long[] accumulation = new long[4];

        restored.mixInto(accumulation, 2);

        assertArrayEquals(new long[] {0, 0, 8128, 8128}, accumulation);
    }

    @Test
    void s3kSegaCommandRoutesToRawPcmAndStopCommandStopsOnlyPcm() throws Exception {
        Rom rom = GameServices.rom().getRom();
        RecordingBackend backend = new RecordingBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        audio.setAudioProfile(new Sonic3kAudioProfile());
        audio.setRom(rom);
        audio.setDeterministicAudioRuntime(new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(48_000, 60), new AudioOutputFifo(48_000)));

        audio.playMusic(Sonic3kSmpsConstants.CMD_SEGA);
        audio.playMusic(Sonic3kSmpsConstants.CMD_STOP_SEGA);

        assertEquals(1, backend.pcmPlayCalls);
        assertEquals(Sonic3kSmpsConstants.SEGA_SOUND_SIZE, backend.lastPcmLength);
        assertEquals(Sonic3kSmpsConstants.SEGA_SOUND_SAMPLE_RATE, backend.lastSampleRate);
        assertEquals(1, backend.pcmStopCalls);
        assertEquals(0, backend.musicPlayCalls);
    }

    private static final class RecordingBackend extends NullAudioBackend {
        int musicPlayCalls;
        int pcmPlayCalls;
        int pcmStopCalls;
        int lastPcmLength;
        int lastSampleRate;

        @Override
        public void playMusic(int musicId) {
            musicPlayCalls++;
        }

        @Override
        public void playSmps(AbstractSmpsData data, DacData dacData) {
            musicPlayCalls++;
        }

        @Override
        public void playPcmSample(byte[] pcm, int sourceSampleRate) {
            pcmPlayCalls++;
            lastPcmLength = pcm.length;
            lastSampleRate = sourceSampleRate;
        }

        @Override
        public void stopPcmSample() {
            pcmStopCalls++;
        }
    }
}
