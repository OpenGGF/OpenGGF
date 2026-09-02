package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.audio.session.SmpsDriverSession;
import com.openggf.audio.session.SmpsSessionTestSupport;
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
    void sourceDiagnosticReentryStartsOnlyAfterCompositeReceiptIsSealed() {
        AudioManager audio = install(ignored -> { });
        AtomicBoolean reentered = new AtomicBoolean();
        AtomicInteger visibleTimelineEntries = new AtomicInteger(-1);
        audio.setAdmissionObserver(ignored -> {
            if (reentered.compareAndSet(false, true)) {
                visibleTimelineEntries.set(
                        audio.commandTimeline().entryCount());
                assertEquals(0,
                        requestSnapshot(audio).pipeline().queue0()
                                .requestByte());
                audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
            }
        });

        assertTrue(audio.playSfx(0xA0));
        audio.presentFrame(PresentationMode.FORWARD);

        assertTrue(reentered.get());
        assertEquals(1, visibleTimelineEntries.get(),
                "source diagnostics publish only after the first receipt");
        assertEquals(1, audio.commandTimeline().entryCount(),
                "reentrant work must not enter the sealed boundary");

        assertEquals(1, audio.commandTimeline().entryCount(),
                "observer reentry cannot mutate the already sealed receipt");
    }

    @Test
    void preSealFailureRestoresRomBackedSessionRequestAndTimelineForRetry()
            throws Exception {
        AudioManager audio = install(ignored -> { });
        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        audio.playMusic(Sonic2Music.EXTRA_LIFE.id);
        var beforeAttempt = audio.captureLogicalSnapshot();
        int beforeTimeline = audio.commandTimeline().entryCount();
        SmpsDriverSession session = shadowSession(audio);
        AtomicInteger failures = new AtomicInteger();
        SmpsSessionTestSupport.setPhysicalWriteInterceptor(
                session, ignored -> {
                    if (failures.getAndIncrement() == 0) {
                        throw new IllegalStateException("seeded pre-seal failure");
                    }
                });

        assertThrows(IllegalStateException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));
        var afterFailure = audio.captureLogicalSnapshot();
        assertEquals(logicalFingerprint(
                        beforeAttempt.presentation().smpsLogical()),
                logicalFingerprint(
                        afterFailure.presentation().smpsLogical()));
        assertEquals(beforeAttempt.presentation().smpsSession()
                        .selectedDacSource(),
                afterFailure.presentation().smpsSession()
                        .selectedDacSource());
        assertEquals(beforeAttempt.forwardServiceSnapshot(),
                afterFailure.forwardServiceSnapshot());
        assertEquals(beforeTimeline, audio.commandTimeline().entryCount());

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(beforeTimeline + 1,
                audio.commandTimeline().entryCount());
        assertTrue(requestSnapshot(audio).pipeline().oneUpPlaying());
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

    private static SmpsDriverSession shadowSession(AudioManager audio)
            throws Exception {
        var field = AudioManager.class.getDeclaredField("shadowSmpsSession");
        field.setAccessible(true);
        return (SmpsDriverSession) field.get(audio);
    }

    private static List<String> logicalFingerprint(
            com.openggf.audio.rewind.SmpsDriverSnapshot snapshot) {
        return snapshot.sequencers().stream().map(entry ->
                entry.source() + ":" + entry.sfx() + ":"
                        + entry.snapshot().tracks().stream().map(track ->
                        track.pos() + "/" + track.type() + "/"
                                + track.channelId() + "/" + track.duration()
                                + "/" + track.note() + "/" + track.active()
                                + "/" + track.overridden()).toList())
                .toList();
    }

}
