package com.openggf.tools.audio.playback;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBoundedAudioPlaybackTrace {

    @Test
    void recordsOrderedMarkersChipWritesAndImmutablePcm() {
        BoundedAudioPlaybackTrace trace = new BoundedAudioPlaybackTrace(4, 2);
        trace.mark("pickup");
        trace.onYm2612Write(1, 0x49, 5);
        trace.onPsgWrite(0x9F);
        short[] pcm = {100, -200, 300, -400};
        trace.recordPcm(pcm, 2);

        AudioPlaybackTraceSnapshot snapshot = trace.snapshot();
        pcm[0] = 999;

        assertEquals(List.of(
                new AudioPlaybackTraceEvent.Marker("pickup"),
                new AudioPlaybackTraceEvent.Ym2612Write(1, 0x49, 5),
                new AudioPlaybackTraceEvent.PsgWrite(0x9F)), snapshot.events());
        assertArrayEquals(new short[] {100, -200, 300, -400}, snapshot.pcm());
        assertEquals(300, snapshot.pcmSummary().leftPeak());
        assertEquals(400, snapshot.pcmSummary().rightPeak());
        assertTrue(snapshot.pcmSummary().leftRms() > 223.60
                && snapshot.pcmSummary().leftRms() < 223.61);
        assertTrue(snapshot.pcmSummary().rightRms() > 316.22
                && snapshot.pcmSummary().rightRms() < 316.23);

        short[] copy = snapshot.pcm();
        copy[0] = 777;
        assertEquals(100, snapshot.pcm()[0]);
    }

    @Test
    void rejectsEventAndPcmOverflowBeforePublishingPartialData() {
        BoundedAudioPlaybackTrace eventBound =
                new BoundedAudioPlaybackTrace(1, 2);
        eventBound.mark("only");
        assertThrows(IllegalStateException.class,
                () -> eventBound.onPsgWrite(0x90));
        assertEquals(1, eventBound.snapshot().events().size());

        BoundedAudioPlaybackTrace pcmBound =
                new BoundedAudioPlaybackTrace(2, 1);
        assertThrows(IllegalStateException.class,
                () -> pcmBound.recordPcm(new short[] {1, 2, 3, 4}, 2));
        assertArrayEquals(new short[0], pcmBound.snapshot().pcm());
    }

    @Test
    void comparatorReportsTheFirstExactDivergence() {
        BoundedAudioPlaybackTrace expected =
                new BoundedAudioPlaybackTrace(4, 2);
        expected.mark("pickup");
        expected.onYm2612Write(1, 0x49, 5);
        expected.recordPcm(new short[] {100, -100}, 1);

        BoundedAudioPlaybackTrace actual =
                new BoundedAudioPlaybackTrace(4, 2);
        actual.mark("pickup");
        actual.onYm2612Write(1, 0x49, 10);
        actual.recordPcm(new short[] {100, -100}, 1);

        AudioPlaybackTraceComparator.Result result =
                AudioPlaybackTraceComparator.compare(
                        expected.snapshot(), actual.snapshot());

        assertEquals(1, result.firstEventMismatch());
        assertEquals(-1, result.firstPcmMismatch());
        assertTrue(result.description().contains("Ym2612Write"));
    }

    @Test
    void markerSegmentsExposeOnlyTheirFollowingEvents() {
        BoundedAudioPlaybackTrace trace = new BoundedAudioPlaybackTrace(8, 1);
        trace.mark("first");
        trace.onPsgWrite(0x90);
        trace.mark("second");
        trace.onYm2612Write(1, 0x49, 5);

        AudioPlaybackTraceSnapshot snapshot = trace.snapshot();

        assertEquals(List.of(new AudioPlaybackTraceEvent.PsgWrite(0x90)),
                snapshot.eventsAfter("first"));
        assertEquals(List.of(
                        new AudioPlaybackTraceEvent.Ym2612Write(1, 0x49, 5)),
                snapshot.eventsAfter("second"));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot.eventsAfter("absent"));
    }
}
