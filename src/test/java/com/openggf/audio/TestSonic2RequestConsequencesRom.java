package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void nullMusicLoadRollsBackCompletePairAndRetriesExactly() {
        assertRejectedMusicIsAtomic(false);
    }

    @Test
    void throwingMusicSourceRollsBackCompletePairAndRetriesExactly() {
        assertRejectedMusicIsAtomic(true);
    }

    private void assertRejectedMusicIsAtomic(boolean throwFromLoader) {
        List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
        RejectingMusicProfile profile = new RejectingMusicProfile(
                events::add, throwFromLoader);
        AudioManager audio = install(events::add, profile);
        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        profile.rejectMusic.set(true);
        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        var requestBefore = requestSnapshot(audio);
        var driverBefore = audio.shadowSmpsDriverSnapshotForTesting();
        int timelineBefore = audio.commandTimeline().entryCount();
        int diagnosticsBefore = events.size();

        assertThrows(IllegalStateException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));

        assertEquals(requestBefore, requestSnapshot(audio));
        var driverAfter = audio.shadowSmpsDriverSnapshotForTesting();
        assertEquals(driverBefore.sequencers().size(),
                driverAfter.sequencers().size());
        assertEquals(driverBefore.sequencers().getFirst().source(),
                driverAfter.sequencers().getFirst().source());
        assertEquals(driverBefore.sequencers().getFirst().snapshot()
                        .sfxPriority(),
                driverAfter.sequencers().getFirst().snapshot()
                        .sfxPriority());
        assertEquals(timelineBefore, audio.commandTimeline().entryCount());
        assertEquals(diagnosticsBefore, events.size());

        profile.rejectMusic.set(false);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(timelineBefore + 1,
                audio.commandTimeline().entryCount());
        assertTrue(requestSnapshot(audio).pipeline().oneUpPlaying());
        assertFalse(audio.shadowSmpsDriverSnapshotForTesting().sequencers()
                .stream().anyMatch(entry -> entry.snapshot().sfx()));
    }

    private AudioManager install(
            java.util.function.Consumer<Sonic2SoundRequestService.Event>
                    observer) {
        return install(observer, new Sonic2AudioProfile(observer));
    }

    private AudioManager install(
            java.util.function.Consumer<Sonic2SoundRequestService.Event>
                    observer,
            Sonic2AudioProfile profile) {
        File file = RomTestUtils.ensureSonic2RomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return audio;
    }

    private static final class RejectingMusicProfile
            extends Sonic2AudioProfile {
        private final AtomicBoolean rejectMusic = new AtomicBoolean();
        private final boolean throwFromLoader;

        private RejectingMusicProfile(
                java.util.function.Consumer<Sonic2SoundRequestService.Event>
                        observer,
                boolean throwFromLoader) {
            super(observer);
            this.throwFromLoader = throwFromLoader;
        }

        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            SmpsLoader delegate = super.createSmpsLoader(rom);
            return new SmpsLoader() {
                @Override
                public AbstractSmpsData loadMusic(int musicId) {
                    if (rejectMusic.get()) {
                        if (throwFromLoader) {
                            throw new IllegalStateException(
                                    "seeded source rejection");
                        }
                        return null;
                    }
                    return delegate.loadMusic(musicId);
                }

                @Override
                public AbstractSmpsData loadSfx(int sfxId) {
                    return delegate.loadSfx(sfxId);
                }

                @Override
                public AbstractSmpsData loadSfx(String sfxName) {
                    return delegate.loadSfx(sfxName);
                }

                @Override
                public DacData loadDacData() {
                    return delegate.loadDacData();
                }

                @Override
                public int findMusicOffset(int musicId) {
                    return delegate.findMusicOffset(musicId);
                }
            };
        }
    }

    private static Sonic2SoundRequestService.Snapshot requestSnapshot(
            AudioManager audio) {
        return (Sonic2SoundRequestService.Snapshot) audio
                .captureLogicalSnapshot().forwardServiceSnapshot();
    }
}
