package com.openggf.game.sonic2.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline.SourceSlot.MUSIC0;
import static com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline.SourceSlot.MUSIC1;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2ScreenStopMailbox {
    // REV01 MusID_Stop. Keep the expected byte independent of the caller.
    private static final int STOP = 0xFD;
    private static final Sonic2Music COMPRESSED = Sonic2Music.EMERALD_HILL;
    private static final Sonic2Music NEXT = Sonic2Music.GAME_OVER;

    private final AudioManager audio = AudioManager.getInstance();
    private final List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
    private final TitleScreenManager title = new TitleScreenManager();

    @BeforeEach
    void setUp() throws Exception {
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile(events::add));
        audio.setRom(RomManager.getInstance().getRom());
    }

    @AfterEach
    void tearDown() {
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void segaStopWaitsForAnEarlierCompressedPlayToFinishLoading() {
        startCompressedLoad();

        title.initialize();
        present();
        assertNotNull(state().smpsLogical().pendingService(), "Saxman must still own the driver");
        assertEquals(List.of(COMPRESSED.nativeId), dispatched());
        assertEquals(STOP, mailbox().pipeline().music0().requestByte());

        presentFrames(16);

        assertAdmittedStop();
        assertEquals(List.of(COMPRESSED.nativeId, STOP), dispatched());
        assertNull(state().activeMusic());
        assertTrue(state().smpsLogical().sequencers().isEmpty());
    }

    @Test
    void aPlayAfterTheDeferredSegaStopCanStartNormally() {
        startCompressedLoad();
        title.initialize();
        presentFrames(16);
        assertAdmittedStop();

        audio.playMusic(NEXT.id);
        present();

        assertEquals(List.of(COMPRESSED.nativeId, STOP, NEXT.nativeId), dispatched());
        assertEquals(NEXT.id, state().activeMusic().musicId());
    }

    @Test
    void segaStopInMusic1CanBeLostToTheShippedFourthSfxTransfer() {
        // sndDriverInput (s2.asm:1270-1332) treats Music1 as an SFX source
        // on the fourth iteration when VoiceTblPtr's low byte is clear.
        audio.playMusic(NEXT.id);
        title.initialize();
        assertEquals(STOP, mailbox().pipeline().music1().requestByte());

        present();

        assertTrue(events.stream().anyMatch(event ->
                event instanceof Sonic2SoundRequestService.Submission submission
                        && submission.rawRequestId() == STOP && submission.sourceMailbox() == MUSIC1));
        assertTrue(events.stream().anyMatch(event ->
                event instanceof Sonic2SoundRequestService.Transfer transfer
                        && transfer.rawRequestId() == STOP && transfer.sourceMailbox() == MUSIC1
                        && transfer.physicalSlot() == 3 && transfer.slot3Alias()));
        assertEquals(List.of(NEXT.nativeId), dispatched());
        assertEquals(NEXT.id, state().activeMusic().musicId());
    }

    @Test
    void segaAndTitleEntryBothDispatchStopWithoutACompressedLoad() {
        audio.playMusic(NEXT.id);
        present();
        assertNull(state().smpsLogical().pendingService());
        assertEquals(NEXT.id, state().activeMusic().musicId());

        title.initialize();
        present();

        assertAdmittedStop();
        assertNull(state().activeMusic());

        InputHandler input = new InputHandler();
        for (int frame = 0; frame < 600 && title.getState() != TitleScreenProvider.State.INTRO_TEXT_FADE_IN; frame++) {
            title.update(input);
            present();
        }
        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_IN, title.getState());
        assertEquals(List.of(NEXT.nativeId, STOP, 0xFA, STOP), dispatched());
        assertNull(state().activeMusic());
    }

    private void startCompressedLoad() {
        audio.playMusic(COMPRESSED.id);
        present();
        assertNotNull(state().smpsLogical().pendingService(), "EHZ must use the ROM-backed Saxman plan");
        assertEquals(List.of(COMPRESSED.nativeId), dispatched());
    }

    private void assertAdmittedStop() {
        assertTrue(events.stream().anyMatch(event ->
                event instanceof Sonic2SoundRequestService.Submission submission
                        && submission.rawRequestId() == STOP && submission.sourceMailbox() == MUSIC0));
        // Normal Music0 -> QueueToPlay is consumed in this same presentation.
        // The service's Transfer receipts cover only the physical SFX slots.
        assertEquals(0, mailbox().pipeline().music0().requestByte());
        assertTrue(dispatched().contains(STOP));
        assertTrue(audio.commandTimeline().entries().stream().map(AudioTimelineEntry::command)
                .anyMatch(new AudioCommand.RetainGlobalStop(STOP)::equals));
    }

    private List<Integer> dispatched() {
        return events.stream().filter(Sonic2SoundRequestService.Dispatch.class::isInstance)
                .map(Sonic2SoundRequestService.Dispatch.class::cast)
                .map(Sonic2SoundRequestService.Dispatch::selectedRequestId).toList();
    }

    private Sonic2SoundRequestService.Snapshot mailbox() {
        return assertInstanceOf(Sonic2SoundRequestService.Snapshot.class,
                audio.captureLogicalSnapshot().forwardServiceSnapshot());
    }

    private AudioPresentationSnapshot state() {
        return audio.captureLogicalSnapshot().presentation();
    }

    private void presentFrames(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            present();
        }
    }

    private void present() {
        audio.presentFrame(PresentationMode.FORWARD);
    }
}
