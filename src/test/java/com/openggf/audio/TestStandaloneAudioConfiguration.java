package com.openggf.audio;

import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Standalone presentation must honor its supplied config without bootstrapping ambient owners. */
class TestStandaloneAudioConfiguration {
    @Test
    void constructionAndPresentationNeverReadAmbientConfiguration(@TempDir Path directory) {
        EngineContext original = EngineServices.current();
        EngineContext ambient = mock(EngineContext.class);
        when(ambient.configuration()).thenThrow(new AssertionError("Ambient engine config was consulted"));
        EngineServices.configure(ambient);
        AudioManager audio = null;
        try {
            var config = SonicConfigurationService.createStandalone(directory);
            config.resetToDefaults();
            audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                    null, new NoDeviceAudioSink(48_000),
                    new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
            assertEquals(48_000, audio.outputSampleRate());
            AudioManager standalone = audio;
            assertDoesNotThrow(() -> audioFrame(standalone));
            verify(ambient, never()).configuration();
        } finally {
            try {
                if (audio != null) audio.destroy();
            } finally {
                EngineServices.configure(original);
            }
        }
    }

    private static void audioFrame(AudioManager audio) {
        audio.presentFrame(PresentationMode.FORWARD);
    }
}
