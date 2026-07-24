package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestShadowAudioPresentationRouting {
    private final AudioManager audio = AudioManager.getInstance();

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void sixtyPresentedFramesTickShadowExactlySixtyTimes() {
        audio.setBackend(new NullAudioBackend());
        for (int frame = 0; frame < 60; frame++) {
            audio.presentShadowFrame(PresentationMode.FORWARD);
        }
        var snapshot = audio.shadowParitySnapshotForTesting();
        assertEquals(60, snapshot.presentedFrames());
        assertEquals(60, snapshot.forwardFrames());
        assertEquals(0, snapshot.silentFrames());
        assertEquals(0, snapshot.reverseFrames());
    }

    @Test
    void everyLegacyControlHasOneSameOrderShadowCommand() {
        audio.setBackend(new NullAudioBackend());
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(2);
        audio.changeMusicTempo(3);
        audio.stopAllSfx();
        audio.stopMusic();
        audio.restoreMusic();
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertEquals(6,
                audio.shadowParitySnapshotForTesting().commandCount());
    }

    @Test
    void legacyBackendRemainsAudibleOwnerAcrossShadowTicks() {
        NullAudioBackend backend = new NullAudioBackend();
        audio.setBackend(backend);
        audio.presentShadowFrame(PresentationMode.FORWARD);
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertSame(backend, audio.getBackend());
    }

    @Test
    void muteAndSoloQueriesUseShadowState() {
        audio.setBackend(new NullAudioBackend());
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertEquals(true, audio.isMuted(ChannelType.FM, 2));
        assertEquals(true, audio.isSoloed(ChannelType.PSG, 1));
    }
}
