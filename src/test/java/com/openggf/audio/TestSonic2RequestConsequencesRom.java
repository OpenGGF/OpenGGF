package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2RequestConsequencesRom {
    private Rom rom;

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void oneUpStopsSfxBeforeSaveAndRetainsShippedPriorityBug() {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        AudioManager audio = install(events::add);

        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        Sonic2SoundRequestService.Snapshot beforeOneUp = requestSnapshot(audio);
        assertEquals(0x7F, beforeOneUp.pipeline().sfxPriorityValue());

        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        audio.presentFrame(PresentationMode.FORWARD);

        Sonic2SoundRequestService.Snapshot afterOneUp = requestSnapshot(audio);
        assertTrue(afterOneUp.pipeline().oneUpPlaying(), events.toString());
        assertEquals(0, afterOneUp.pipeline().sfxPriorityValue());
        assertEquals(0x7F,
                afterOneUp.pipeline().savedOneUpSfxPriorityValue(),
                "shipped FixDriverBugs=0 saves the old priority for restore");
        assertFalse(audio.shadowSmpsDriverSnapshotForTesting().sequencers()
                        .stream().anyMatch(entry -> entry.snapshot().sfx()),
                "pre-one-up SFX must not survive in the saved driver region");
    }

    @Test
    void diagnosticFailureCannotRetryOrDuplicateACommittedRequest() {
        AtomicInteger callbacks = new AtomicInteger();
        AudioManager audio = install(event -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("seeded observer failure");
        });

        assertTrue(audio.playSfx(0xA0));
        audio.presentFrame(PresentationMode.FORWARD);
        int committedCallbacks = callbacks.get();
        assertTrue(committedCallbacks > 0);
        assertEquals(1, audio.commandTimeline().entryCount());

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(committedCallbacks, callbacks.get());
        assertEquals(1, audio.commandTimeline().entryCount());
    }

    private AudioManager install(
            java.util.function.Consumer<Sonic2SoundRequestService.Event>
                    observer) {
        File file = RomTestUtils.ensureSonic2RomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        Sonic2AudioProfile profile = new Sonic2AudioProfile(observer);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return audio;
    }

    private static Sonic2SoundRequestService.Snapshot requestSnapshot(
            AudioManager audio) {
        return (Sonic2SoundRequestService.Snapshot) audio
                .captureLogicalSnapshot().forwardServiceSnapshot();
    }
}
