package com.openggf.tools.audio.completerun.s2;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
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
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(List.of(), projector.requests(),
                "silent presentation must publish no request transitions");
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

    @Test
    void rejectedMismatchedRestorePublishesNothingAndRetainsMailbox() {
        S2ProductionRequestProjector projector = new S2ProductionRequestProjector();
        Sonic2AudioProfile profile = new Sonic2AudioProfile(projector);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        audio.playSfx(0xB5);
        AudioLogicalSnapshot owned = audio.captureLogicalSnapshot();
        AudioLogicalSnapshot mismatched = new AudioLogicalSnapshot(
                owned.ringLeft(), owned.commandTimelineFrame(),
                owned.commandTimelineNextOrder(), owned.commandEntryCount(),
                owned.presentation(), owned.donorGameIds(), owned.donorBindings());

        audio.restoreLogicalSnapshot(mismatched);
        assertEquals(List.of(), projector.requests(),
                "failed restore must not publish request events");

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, projector.requests().size(),
                "failed restore must retain the prior live mailbox");
    }

    @Test
    void priorityRejectionProjectsObservedPriorityWithoutRoleOwnership() {
        S2ProductionRequestProjector projector = new S2ProductionRequestProjector();
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        service.addObserver(projector);

        service.submitSound(0xBF, sfx(0xBF));
        commit(service);
        service.submitSound(0xA1, sfx(0xA1));
        commit(service);

        CompleteRunAudioTrace.Decision rejected = projector.decisions().getLast();
        assertEquals(false, rejected.accepted());
        assertEquals("rejected_priority", rejected.reason());
        assertEquals(0x7F, rejected.priorityBefore());
        assertEquals(0x7F, rejected.priorityAfter());
        assertEquals(null, rejected.roleDecisions(),
                "production request events do not claim hardware ownership");
    }

    private static void commit(Sonic2SoundRequestService service) {
        var boundary = service.beginForwardBoundary();
        boundary.service(ignored -> { });
        boundary.commit();
    }

    private static AudioCommand.PlaySfx sfx(int id) {
        return new AudioCommand.PlaySfx(id, null,
                AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
    }
}
