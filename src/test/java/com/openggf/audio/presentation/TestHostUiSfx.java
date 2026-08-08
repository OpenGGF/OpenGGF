package com.openggf.audio.presentation;

import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the bootstrap UI fallback is real PCM, not a silent command. */
class TestHostUiSfx {

    @Test
    void fallbackCuesResolveToDistinctNonSilentPcm() throws Exception {
        AudioPresentationSourceFactory factory = new AudioPresentationSourceFactory(
                () -> true,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));

        SampleBackedVoice navigate = factory.fallbackSfx(1, "UI_NAVIGATE", 0, 1.0f);
        SampleBackedVoice confirm = factory.fallbackSfx(2, "UI_CONFIRM", 0, 1.0f);

        String navigateAsset = ((PresentationVoiceSnapshot.Sample) navigate.snapshot()).assetId();
        String confirmAsset = ((PresentationVoiceSnapshot.Sample) confirm.snapshot()).assetId();
        assertEquals("host/ui/ui_navigate", navigateAsset);
        assertEquals("host/ui/ui_confirm", confirmAsset);
        assertNotNull(factory.resolvePcm(navigateAsset));
        assertNotNull(factory.resolvePcm(confirmAsset));
        assertFalse(Arrays.equals(
                factory.resolvePcm(navigateAsset).copySamples(),
                factory.resolvePcm(confirmAsset).copySamples()));

        long[] mixed = new long[2 * 512];
        navigate.mixInto(mixed, 512);
        assertTrue(Arrays.stream(mixed).anyMatch(sample -> sample != 0));
    }
}
