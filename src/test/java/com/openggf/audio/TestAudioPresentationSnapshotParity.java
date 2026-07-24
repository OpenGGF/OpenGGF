package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationSnapshotParity {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void logicalSnapshotCapturesAndRestoresPresentationFlagsAtSameBoundary() {
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.playSfx(GameSound.RING);
        audio.presentShadowFrame(PresentationMode.SILENT);

        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        AudioPresentationSnapshot expected = selected.presentation();
        assertTrue(expected.speedShoesEnabled());
        assertEquals(3, expected.speedMultiplier());
        assertFalse(expected.ringLeft());

        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(false);
        audio.setSpeedMultiplier(1);
        audio.resetRingSound();
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertNotEquals(expected, audio.captureLogicalSnapshot().presentation());

        audio.restoreLogicalSnapshot(selected);

        assertEquals(expected, audio.captureLogicalSnapshot().presentation());
        assertEquals(selected.backend(),
                audio.captureLogicalSnapshot().backend());
    }

    @Test
    void heldReverseDefersPresentationRestoreUntilRelease() {
        audio.toggleMute(ChannelType.FM, 0);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();

        audio.toggleMute(ChannelType.FM, 1);
        audio.presentShadowFrame(PresentationMode.SILENT);
        AudioPresentationSnapshot disturbed =
                audio.captureLogicalSnapshot().presentation();

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        assertEquals(disturbed,
                audio.captureLogicalSnapshot().presentation(),
                "held reverse must preserve the active history/cursor state");

        audio.endReverseAudioPresentation();
        assertEquals(selected.presentation(),
                audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void dualSnapshotsRestoreIndependentEqualCoordFlagCounters() {
        LWJGLAudioBackend backend =
                new LWJGLAudioBackend(SonicConfigurationService.getInstance());
        audio.setBackend(backend);
        audio.captureLogicalSnapshot();
        backend.legacyCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();
        backend.legacyCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(99);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(55);
        audio.restoreLogicalSnapshot(snapshot);

        assertEquals(7, backend.legacyCoordFlagHandlersForTesting().state()
                .spindashRevCounter());
        assertEquals(7, audio.presentationCoordFlagHandlersForTesting().state()
                .spindashRevCounter());
        assertFalse(backend.legacyCoordFlagHandlersForTesting().state()
                == audio.presentationCoordFlagHandlersForTesting().state());
    }
}
