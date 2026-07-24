package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.smps.DacData;

import java.lang.reflect.Field;
import java.util.Map;

/** Test-source bridge for package-private audio diagnostics. */
public final class AudioManagerTestDiagnostics {
    private AudioManagerTestDiagnostics() {
    }

    public static AudioPresentationParityProbe.Snapshot shadowParitySnapshot(
            AudioManager audio) {
        return audio.shadowParitySnapshot();
    }

    public static LiveCaptureAudioHandle attachPresentationCapture(
            AudioManager audio, int frameRate) {
        return audio.attachShadowCaptureForTesting(frameRate);
    }

    public static AudioPresentationProducer.TransactionFingerprint
            producerFingerprint(AudioManager audio) {
        return audio.releaseStateForTesting().producer();
    }

    /**
     * Pre-decodes a fallback WAV SFX asset into the presentation source
     * factory's cache so {@code playSfx(name)} resolves a sample voice without
     * a packaged classpath asset.
     */
    public static void registerFallbackSfxAsset(
            AudioManager audio, String assetId, byte[] unsigned8Mono,
            int sourceSampleRate) {
        audio.shadowFactoryForTesting().registerUnsigned8Mono(
                assetId, unsigned8Mono, sourceSampleRate);
    }

    /**
     * Materializes the queued presentation commands at the owner boundary — the
     * same drain {@code presentFrame(FORWARD)} performs before it renders — then
     * primes the admitted SMPS composite voice so a stub (data-free) asset
     * still synthesizes audible FM/PSG/DAC output.
     *
     * @return the primed composite voice, or {@code null} when no SMPS music
     *         voice was admitted
     */
    public static SmpsCompositeVoice admitAndPrimeSmpsMusic(AudioManager audio) {
        AudioPresentationCommandQueue commands =
                field(audio, "shadowCommands", AudioPresentationCommandQueue.class);
        AudioVoiceRegistry registry =
                field(audio, "shadowRegistry", AudioVoiceRegistry.class);
        commands.applyPending(registry::apply);
        for (int index = 0; index < registry.orderedVoiceCount(); index++) {
            if (registry.orderedVoiceAt(index)
                    instanceof SmpsCompositeVoice composite) {
                primeSynth(composite.driver());
                return composite;
            }
        }
        return null;
    }

    /** Mirrors the source-parity fixture's synthesis priming. */
    private static void primeSynth(SmpsDriver driver) {
        driver.setDacData(new DacData(
                Map.of(1, new byte[] {0, 24, 64, 127}),
                Map.of(0x81, new DacData.DacEntry(1, 4)), 295));
        driver.setDacInterpolate(true);
        driver.writeFm(driver, 0, 0x22, 0x0B);
        driver.writeFm(driver, 0, 0x2B, 0x80);
        driver.setInstrument(driver, 0, new byte[] {
                0x32, 0x71, 0x0D, 0x33, 0x01, 0x5F, 0x5F, 0x5F,
                0x5F, 0x14, 0x0E, 0x0E, 0x0E, 0x08, 0x08, 0x08,
                0x08, 0x0F, 0x0F, 0x0F, 0x0F, 0x1B, 0x16, 0x1F,
                0x00
        });
        driver.writeFm(driver, 0, 0xA4, 0x22);
        driver.writeFm(driver, 0, 0xA0, 0x69);
        driver.writeFm(driver, 0, 0xB4, 0xC7);
        driver.writeFm(driver, 0, 0x28, 0xF0);
        driver.playDac(driver, 0x81);
        driver.writePsg(driver, 0x84);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x92);
    }

    private static <T> T field(AudioManager audio, String name, Class<T> type) {
        try {
            Field field = AudioManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(audio));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "cannot read AudioManager." + name, failure);
        }
    }
}
