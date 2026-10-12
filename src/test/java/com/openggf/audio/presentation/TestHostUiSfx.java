package com.openggf.audio.presentation;

import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the bootstrap UI fallback is real PCM, not a silent command. */
class TestHostUiSfx {

    @Test
    void cuesEaseToSilenceAtTheirEndsAndBetweenNotesWithMixingHeadroom() {
        for (String cue : new String[]{"UI_NAVIGATE", "UI_CONFIRM", "UI_CANCEL", "UI_ERROR"}) {
            DecodedPcm pcm = HostUiSfx.forCue(cue);
            short[] samples = pcm.copySamples();
            assertEquals(0, samples[0], cue);
            assertEquals(0, samples[samples.length - 1], cue);
            // An 8 ms attack and 18 ms release should be effectively silent at the edges.
            for (int i = 0; i < 16; i++) {
                assertTrue(Math.abs(samples[i]) <= 32, cue + " attack");
                assertTrue(Math.abs(samples[samples.length - 1 - i]) <= 32, cue + " release");
            }
            for (short sample : samples) {
                assertTrue(Math.abs(sample) <= 6_000, cue + " leaves headroom for overlapping feedback");
            }
        }
        assertSilentJoin("UI_CONFIRM", 55);
        assertSilentJoin("UI_ERROR", 75);
    }

    private static void assertSilentJoin(String cue, int firstNoteMillis) {
        DecodedPcm pcm = HostUiSfx.forCue(cue);
        int join = pcm.sampleRate() * firstNoteMillis / 1_000;
        assertEquals(0, pcm.sample(join - 1, 0), cue + " first note ends in silence");
        assertEquals(0, pcm.sample(join, 0), cue + " second note starts in silence");
    }

    @Test
    void fallbackCuesResolveToDistinctNonSilentPcm() throws Exception {
        AudioPresentationSourceFactory factory = new AudioPresentationSourceFactory(
                () -> true,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));

        SampleBackedVoice navigate = factory.fallbackSfx(1, "UI_NAVIGATE", 0, 1.0f);
        SampleBackedVoice confirm = factory.fallbackSfx(2, "UI_CONFIRM", 0, 1.0f);
        SampleBackedVoice error = factory.fallbackSfx(3, "UI_ERROR", 0, 1.0f);

        SampleBackedVoice cancel = factory.fallbackSfx(4, "UI_CANCEL", 0, 1.0f);

        String navigateAsset = ((PresentationVoiceSnapshot.Sample) navigate.snapshot()).assetId();
        String confirmAsset = ((PresentationVoiceSnapshot.Sample) confirm.snapshot()).assetId();
        String errorAsset = ((PresentationVoiceSnapshot.Sample) error.snapshot()).assetId();
        String cancelAsset = ((PresentationVoiceSnapshot.Sample) cancel.snapshot()).assetId();
        assertEquals("host/ui/ui_cancel", cancelAsset);
        DecodedPcm cancelPcm = factory.resolvePcm(cancelAsset);
        assertNotNull(cancelPcm);
        assertSame(cancelPcm, factory.resolvePcm(cancelAsset));
        assertEquals("host/ui/ui_navigate", navigateAsset);
        assertEquals("host/ui/ui_confirm", confirmAsset);
        assertEquals("host/ui/ui_error", errorAsset);
        DecodedPcm navigatePcm = factory.resolvePcm(navigateAsset);
        DecodedPcm confirmPcm = factory.resolvePcm(confirmAsset);
        DecodedPcm errorPcm = factory.resolvePcm(errorAsset);
        assertNotNull(navigatePcm);
        assertNotNull(confirmPcm);
        assertNotNull(errorPcm);
        assertSame(navigatePcm, factory.resolvePcm(navigateAsset));
        assertSame(confirmPcm, factory.resolvePcm(confirmAsset));
        assertSame(errorPcm, factory.resolvePcm(errorAsset));
        assertFalse(Arrays.equals(
                navigatePcm.copySamples(), confirmPcm.copySamples()));
        assertFalse(Arrays.equals(
                confirmPcm.copySamples(), errorPcm.copySamples()));

        for (DecodedPcm other : new DecodedPcm[]{navigatePcm, confirmPcm, errorPcm}) {
            assertFalse(Arrays.equals(cancelPcm.copySamples(), other.copySamples()));
        }

        for (SampleBackedVoice voice : new SampleBackedVoice[]{navigate, confirm, error, cancel}) {
            long[] mixed = new long[2 * 512];
            voice.mixInto(mixed, 512);
            assertTrue(Arrays.stream(mixed).anyMatch(sample -> sample != 0),
                    "host cue must mix to nonzero PCM: " + voice.snapshot());
        }
    }
}
