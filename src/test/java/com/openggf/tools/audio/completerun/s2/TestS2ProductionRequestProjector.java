package com.openggf.tools.audio.completerun.s2;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS2ProductionRequestProjector {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void realProductionIngressProjectsTransferredRingAndResolvedDecision() {
        S2ProductionRequestProjector projector = new S2ProductionRequestProjector();
        Sonic2AudioProfile profile = new Sonic2AudioProfile(projector);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        audio.playSfx(0xB5);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(List.of(new CompleteRunAudioTrace.Request(
                2, CompleteRunAudioTrace.OwnerClass.SFX,
                "sfx.native.b5", 0xB5, "sound_queue", 0)),
                projector.requests());
        assertEquals(List.of(new CompleteRunAudioTrace.Decision(
                2, 0xCE, "sfx.native.ce", true, "accepted",
                0, 0x70, List.of(CompleteRunAudioTrace.HardwareRole.values()),
                null)), projector.decisions());
    }
}
