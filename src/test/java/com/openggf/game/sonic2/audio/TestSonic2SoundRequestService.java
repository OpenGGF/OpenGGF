package com.openggf.game.sonic2.audio;

import com.openggf.audio.rewind.AudioCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2SoundRequestService {

    @Test
    void sealedReceiptPublishesItsDiagnosticsAtMostOnce() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        service.addObserver(observed::add);
        service.submitSound(0xF1, sfx(0xF1));

        var boundary = service.beginForwardBoundary();
        boundary.service(command -> {
            throw new AssertionError("F1-F7 cannot produce playback");
        });
        boundary.prepareCommit();
        var receipt = boundary.commit();
        boundary.publishDiagnostics(receipt);
        int firstPublication = observed.size();
        boundary.publishDiagnostics(receipt);

        assertTrue(firstPublication > 0);
        assertEquals(firstPublication, observed.size());
    }

    @Test
    void preparedBoundaryCannotBePreparedTwice() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        service.submitSound(0xF1, sfx(0xF1));
        var boundary = service.beginForwardBoundary();
        boundary.service(ignored -> { });
        boundary.prepareCommit();

        assertThrows(IllegalStateException.class, boundary::prepareCommit);
        boundary.rollback();
    }

    @Test
    void servicesOneRomOrderedBoundaryAndPublishesNothingBeforeSeal() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        service.addObserver(observed::add);

        service.submitMusic(0x82, music(0x82));
        service.submitMusic(0x94, music(0x94));
        service.submitSound(0xA0, sfx(0xA0));

        List<AudioCommand> commands = new ArrayList<>();
        var transaction = service.beginForwardBoundary();
        transaction.service(commands::add);

        assertTrue(observed.isEmpty(), "uncommitted events must remain invisible");
        assertEquals(List.of(music(0x82)), commands);
        transaction.rollback();
        assertTrue(observed.isEmpty(),
                "rollback must not publish staged request events");
    }

    @Test
    void rollbackRestoresMailboxAndPublishesNothing() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        service.addObserver(observed::add);
        service.submitSound(0xB5, sfx(0xB5));
        Sonic2SoundRequestService.Snapshot before = service.snapshot();

        var failed = service.beginForwardBoundary();
        List<AudioCommand> discarded = new ArrayList<>();
        failed.service(discarded::add);
        failed.rollback();

        assertEquals(before, service.snapshot());
        assertTrue(observed.isEmpty());

    }

    private static AudioCommand.PlayMusic music(int id) {
        return new AudioCommand.PlayMusic(id, AudioCommand.MusicRoute.BASE_SMPS, false, null);
    }

    private static AudioCommand.PlaySfx sfx(int id) {
        return new AudioCommand.PlaySfx(id, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
    }
}
