package com.openggf.audio;

import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioReplayReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAudioKeyframeReplay {
    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void replaysTimelineCommandsAfterNearestKeyframeWithoutRecordingDuplicates() {
        AudioKeyframeStore store = new AudioKeyframeStore();

        audio.beginCommandTimelineFrame(1);
        audio.playSfx("before");
        store.capture(1, audio);
        audio.beginCommandTimelineFrame(2);
        audio.playSfx("after");
        audio.beginCommandTimelineFrame(3);
        audio.fadeOutMusic(4, 1);
        int recordedEntries = audio.commandTimeline().entries().size();

        backend.clear();
        int replayed = store.replayTo(audio, 3, AudioReplayReason.SEEK);

        assertEquals(2, replayed);
        assertEquals(recordedEntries, audio.commandTimeline().entries().size());
        assertEquals("playSfxPitch:after:1.0", backend.calls.get(0));
        assertEquals("fadeOutMusic:4:1", backend.calls.get(1));
    }

    @Test
    void replaysCommandsAfterMidFrameKeyframeUsingCapturedEntryCount() {
        AudioKeyframeStore store = new AudioKeyframeStore();

        audio.beginCommandTimelineFrame(4);
        audio.playSfx("before");
        store.capture(4, audio);
        audio.playSfx("same-frame-after");

        backend.clear();
        int replayed = store.replayTo(audio, 4, AudioReplayReason.SEEK);

        assertEquals(1, replayed);
        assertEquals(2, audio.commandTimeline().entries().size());
        assertEquals("playSfxPitch:same-frame-after:1.0", backend.calls.get(0));
    }
}
