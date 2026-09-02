package com.openggf.game.sonic2.audio;

import com.openggf.audio.rewind.AudioCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2SoundRequestService {

    @Test
    void servicesOneRomOrderedBoundaryAndPublishesEventsOnlyOnCommit() {
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
        transaction.commit();

        assertEquals(List.of(
                new Sonic2SoundRequestService.Submission(1,
                        Sonic2SoundRequestPipeline.SourceSlot.MUSIC0, 0x82),
                new Sonic2SoundRequestService.Submission(2,
                        Sonic2SoundRequestPipeline.SourceSlot.MUSIC1, 0x94),
                new Sonic2SoundRequestService.Submission(3,
                        Sonic2SoundRequestPipeline.SourceSlot.SFX0, 0xA0),
                new Sonic2SoundRequestService.Transfer(4,
                        Sonic2SoundRequestPipeline.SourceSlot.MUSIC1, 3,
                        0x94, true),
                new Sonic2SoundRequestService.Transfer(5,
                        Sonic2SoundRequestPipeline.SourceSlot.SFX0, 0,
                        0xA0, false),
                new Sonic2SoundRequestService.Dispatch(6, 0x82, 0x82,
                        Sonic2SoundRequestPipeline.DispatchKind.NOT_YET_DISPATCHED)),
                observed);
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

        List<AudioCommand> replayed = new ArrayList<>();
        var retry = service.beginForwardBoundary();
        retry.service(replayed::add);
        retry.commit();
        assertEquals(List.of(sfx(0xCE)), replayed,
                "raw B5 must resolve once inside the ROM-owned pipeline");
    }

    @Test
    void lowerPriorityAndUndefinedOverreadProduceExactNoPlaybackDecisions() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        service.addObserver(observed::add);

        service.submitSound(0xBF, sfx(0xBF));
        commit(service);
        service.submitSound(0xA1, sfx(0xA1));
        commit(service);
        assertTrue(observed.stream().anyMatch(event -> event instanceof Sonic2SoundRequestService.Decision decision
                && decision.reason() == Sonic2SoundRequestService.DecisionReason.REJECTED_PRIORITY
                && decision.priorityBefore() == 0x7F
                && decision.priorityAfter() == 0x7F));

        Sonic2SoundRequestService undefined = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> undefinedEvents = new ArrayList<>();
        undefined.addObserver(undefinedEvents::add);
        undefined.submitSound(0xF1, sfx(0xF1));
        List<AudioCommand> commands = commit(undefined);
        assertTrue(commands.isEmpty(), "F1-F7 dispatch is an arbitration-only no-op");
        assertTrue(undefinedEvents.stream().anyMatch(event -> event instanceof Sonic2SoundRequestService.Dispatch dispatch
                && dispatch.kind() == Sonic2SoundRequestPipeline.DispatchKind.IGNORED_UNDEFINED_ID));
    }

    @Test
    void stopSfxCommandClearsPriorityBeforeTheNextRequest() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        List<Sonic2SoundRequestService.Event> observed = new ArrayList<>();
        service.addObserver(observed::add);

        service.submitSound(0xBF, sfx(0xBF));
        commit(service);
        service.submitSound(0xF8, new AudioCommand.StopAllSfx());
        assertEquals(List.of(new AudioCommand.StopAllSfx()), commit(service));
        service.submitSound(0xA1, sfx(0xA1));
        commit(service);

        assertTrue(observed.stream().anyMatch(event ->
                event instanceof Sonic2SoundRequestService.Decision decision
                        && decision.rawRequestId() == 0xA1
                        && decision.reason()
                        == Sonic2SoundRequestService.DecisionReason.ACCEPTED_PRIORITY
                        && decision.priorityBefore() == 0));
    }

    private static List<AudioCommand> commit(Sonic2SoundRequestService service) {
        List<AudioCommand> commands = new ArrayList<>();
        var transaction = service.beginForwardBoundary();
        transaction.service(commands::add);
        transaction.commit();
        return commands;
    }

    private static AudioCommand.PlayMusic music(int id) {
        return new AudioCommand.PlayMusic(id, AudioCommand.MusicRoute.BASE_SMPS, false, null);
    }

    private static AudioCommand.PlaySfx sfx(int id) {
        return new AudioCommand.PlaySfx(id, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
    }
}
