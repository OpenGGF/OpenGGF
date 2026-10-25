package com.openggf.audio.rewind;

import com.openggf.audio.presentation.AudioPresentationForwardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestAudioTimelineNativePause {
    private record ForwardSnapshot(int request) implements AudioPresentationForwardService.Snapshot { }

    private static AudioCommand command(int id) {
        return new AudioCommand.SetSpeedMultiplier(id);
    }

    @Test
    void mixedSameFrameEventsHaveOneOrderWithoutChangingPublicProjection() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(12);
        AudioTimelineEntry first = timeline.record(command(1));
        ForwardSnapshot paused = new ForwardSnapshot(1);
        AudioTimelineInternalAccess.prepareNativePause(timeline, true, paused).commit();
        AudioTimelineEntry second = timeline.record(command(2));
        ForwardSnapshot running = new ForwardSnapshot(2);
        AudioTimelineInternalAccess.prepareNativePause(timeline, false, running).commit();

        assertEquals(List.of(first, second), timeline.entries());
        assertEquals(2, timeline.entryCount());
        assertEquals(0, first.order());
        assertEquals(1, second.order());
        assertEquals(2, timeline.nextOrder());
        assertEquals(2, timeline.entriesOnFrame(12));
        assertEquals(first, timeline.entryAt(0));
        assertEquals(second, timeline.entryAt(1));

        var events = AudioTimelineInternalAccess.eventsFrom(timeline, 0, 12);
        assertEquals(4, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(List.of(0, 1, 2, 3), events.stream().map(
                AudioTimelineInternalAccess.Event::cursor).toList());
        assertEquals(List.of(0, 1, 1, 2), events.stream().map(
                AudioTimelineInternalAccess.Event::publicEntryIndex).toList());
        assertEquals(command(1), events.get(0).command());
        assertNull(events.get(0).nativePause());
        assertNull(events.get(0).forwardSnapshot());
        assertNull(events.get(1).command());
        assertEquals(Boolean.TRUE, events.get(1).nativePause());
        assertSame(paused, events.get(1).forwardSnapshot());
        assertEquals(command(2), events.get(2).command());
        assertEquals(Boolean.FALSE, events.get(3).nativePause());
        assertSame(running, events.get(3).forwardSnapshot());
        assertThrows(UnsupportedOperationException.class, events::clear);
    }

    @Test
    void preparingNativePauseDoesNotCommitOrAdvanceAnyCursor() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(7);
        var abandoned = AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1));
        var committed = AudioTimelineInternalAccess.prepareNativePause(
                timeline, false, new ForwardSnapshot(2));

        assertEquals(0, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(0, timeline.entryCount());
        assertTrue(AudioTimelineInternalAccess.eventsFrom(timeline, 0, 7).isEmpty());
        committed.commit();
        committed.commit();

        assertEquals(1, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(0, timeline.entryCount());
        assertEquals(0, timeline.nextOrder());
        assertEquals(Boolean.FALSE, AudioTimelineInternalAccess.eventsFrom(
                timeline, 0, 7).getFirst().nativePause());
        assertNotNull(abandoned); // Deliberately never committed: rollback leaves no history.
    }

    @Test
    void preparedPublicCommandsKeepTheirOrdersAcrossNativeCommit() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(5);
        timeline.record(command(1));
        var batch = timeline.prepareAppend(List.of(command(2), command(3)));
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1), 2).commit();
        batch.commit();

        assertEquals(List.of(0, 1, 2), timeline.entries().stream().map(
                AudioTimelineEntry::order).toList());
        assertEquals(3, timeline.nextOrder());
        assertEquals(4, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(List.of(command(1), command(2), command(3)),
                timeline.entries().stream().map(AudioTimelineEntry::command).toList());
    }

    @Test
    void discardAndRebranchTruncatesBothKindsWithoutLeavingNativeTail() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(10);
        timeline.record(command(1));
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1)).commit();
        timeline.beginFrame(11);
        timeline.record(command(2));
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, false, new ForwardSnapshot(2)).commit();

        timeline.discardAfter(10);
        assertEquals(1, timeline.entryCount());
        assertEquals(2, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(10, timeline.currentFrame());
        assertEquals(1, timeline.nextOrder());
        timeline.beginFrame(11);
        ForwardSnapshot replacement = new ForwardSnapshot(3);
        AudioTimelineInternalAccess.prepareNativePause(timeline, true, replacement).commit();
        AudioTimelineEntry replacementCommand = timeline.record(command(3));

        var events = AudioTimelineInternalAccess.eventsFrom(timeline, 2, 11);
        assertEquals(2, events.size());
        assertSame(replacement, events.getFirst().forwardSnapshot());
        assertEquals(command(3), events.getLast().command());
        assertEquals(1, events.getLast().publicEntryIndex());
        assertEquals(0, replacementCommand.order());
    }

    @Test
    void publicPruneCannotDiscardNativeEventsSharingTheSnapshotPublicAnchor() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(4);
        timeline.record(command(1));
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1)).commit();
        int beforeResume = AudioTimelineInternalAccess.cursor(timeline);
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, false, new ForwardSnapshot(2)).commit();
        timeline.record(command(2));

        timeline.pruneBefore(1);
        assertEquals(1, timeline.firstRetainedEntryIndex());
        assertEquals(2, timeline.entryCount());
        assertEquals(List.of(command(2)), timeline.entries().stream().map(
                AudioTimelineEntry::command).toList());
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.entryAt(0));
        assertEquals(1, AudioTimelineInternalAccess.firstRetainedCursor(timeline));
        assertEquals(List.of(Boolean.TRUE, Boolean.FALSE),
                AudioTimelineInternalAccess.eventsFrom(timeline, 0, 4).stream()
                        .filter(event -> event.nativePause() != null)
                        .map(AudioTimelineInternalAccess.Event::nativePause).toList());

        // The retained keyframe, rather than its ambiguous public anchor, supplies this floor.
        AudioTimelineInternalAccess.pruneBefore(timeline, beforeResume);
        assertEquals(2, AudioTimelineInternalAccess.firstRetainedCursor(timeline));
        var events = AudioTimelineInternalAccess.eventsFrom(timeline, 0, 4);
        assertEquals(Boolean.FALSE, events.getFirst().nativePause());
        assertEquals(command(2), events.getLast().command());
        assertEquals(command(2), timeline.entryAt(1).command());
    }

    @Test
    void exactInternalPrunePreservesAbsolutePublicAndEventIndices() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(1);
        timeline.record(command(1));
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1)).commit();
        timeline.beginFrame(2);
        timeline.record(command(2));

        AudioTimelineInternalAccess.pruneBefore(timeline, 2);
        AudioTimelineInternalAccess.pruneBefore(timeline, 1);
        assertEquals(2, AudioTimelineInternalAccess.firstRetainedCursor(timeline));
        assertEquals(3, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(1, timeline.firstRetainedEntryIndex());
        assertEquals(2, timeline.entryCount());
        assertEquals(command(2), timeline.entryAt(1).command());

        AudioTimelineInternalAccess.pruneBefore(timeline, 99);
        assertEquals(3, AudioTimelineInternalAccess.firstRetainedCursor(timeline));
        assertEquals(2, timeline.firstRetainedEntryIndex());
        assertTrue(timeline.entries().isEmpty());
        timeline.record(command(3));
        assertEquals(command(3), timeline.entryAt(2).command());
        assertEquals(3, AudioTimelineInternalAccess.eventsFrom(
                timeline, 0, 2).getFirst().cursor());
    }

    @Test
    void eventRangeRespectsFrameLimitAndNativeSnapshotIsRequired() {
        AudioCommandTimeline timeline = new AudioCommandTimeline();
        timeline.beginFrame(1);
        timeline.record(command(1));
        timeline.beginFrame(2);
        AudioTimelineInternalAccess.prepareNativePause(
                timeline, true, new ForwardSnapshot(1)).commit();

        assertEquals(1, AudioTimelineInternalAccess.eventsFrom(timeline, 0, 1).size());
        assertEquals(1, AudioTimelineInternalAccess.eventsFrom(timeline, 1, 2).size());
        assertTrue(AudioTimelineInternalAccess.eventsFrom(timeline, 2, 2).isEmpty());
        assertThrows(NullPointerException.class, () ->
                AudioTimelineInternalAccess.prepareNativePause(timeline, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                AudioTimelineInternalAccess.prepareNativePause(
                        timeline, true, new ForwardSnapshot(2), -1));

        timeline.clear();
        assertEquals(0, timeline.entryCount());
        assertEquals(0, AudioTimelineInternalAccess.cursor(timeline));
        assertEquals(0, AudioTimelineInternalAccess.firstRetainedCursor(timeline));
    }
}
