package com.openggf.audio.runtime;

import com.openggf.audio.AudioBenchmarkMemoryProbe;
import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the deterministic command queue's stable ordering, atomic callback
 * dispatch, cursor-safe truncation/compaction, and allocation-free frame path.
 * Also covers the grow-only scratch buffers, whose stale tail must never be
 * consumed — neither by the mix/output stages (bounded by the frame's sample
 * count) nor by the streams themselves (length-bounded
 * {@link AudioStream#read(short[], int)}).
 */
class TestStreamBackedDeterministicAudioRuntimeCommands {

    @Test
    void framesWithoutPendingCommandsNeverInvokeTheCommandHandler() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        List<AudioCommand> handled = new ArrayList<>();
        runtime.setCommandHandler(handled::add);

        for (long frame = 1; frame <= 5; frame++) {
            runtime.advanceFrame(frame, FrameAudioMode.NORMAL);
        }

        assertTrue(handled.isEmpty());
    }

    @Test
    void pendingCommandsDispatchInOrderExactlyOnceAtTheirFrame() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        List<String> handled = new ArrayList<>();
        runtime.setCommandHandler(command -> handled.add(((AudioCommand.PlaySfx) command).sfxName()));

        runtime.submit(entry(5, 1, "B"));
        runtime.submit(entry(5, 0, "A"));
        runtime.submit(entry(7, 0, "C"));

        runtime.advanceFrame(4, FrameAudioMode.NORMAL);
        assertEquals(List.of(), handled);

        runtime.advanceFrame(5, FrameAudioMode.NORMAL);
        assertEquals(List.of("A", "B"), handled);

        runtime.advanceFrame(5, FrameAudioMode.NORMAL);
        assertEquals(List.of("A", "B"), handled, "Frame-5 commands were consumed and must not replay.");

        runtime.advanceFrame(7, FrameAudioMode.NORMAL);
        assertEquals(List.of("A", "B", "C"), handled);
    }

    @Test
    void commandsSubmittedOutOfOrderDispatchTheCurrentFrameByFrameThenOrder() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);

        runtime.submit(entry(8, 2, "C"));
        runtime.submit(entry(9, 0, "later"));
        runtime.submit(entry(8, 0, "A"));
        runtime.submit(entry(8, 1, "B"));

        runtime.advanceFrame(8, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "B", "C"), handled);
    }

    @Test
    void commandsOlderThanTheCurrentFrameAreDiscardedWithoutDispatch() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);
        runtime.submit(entry(3, 0, "stale"));
        runtime.submit(entry(5, 0, "current"));
        runtime.submit(entry(6, 0, "future"));

        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(6, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("current", "future"), handled);
    }

    @Test
    void discardAfterRemovesOnlyTheLaterPendingSuffix() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);
        runtime.submit(entry(1, 0, "consumed"));
        runtime.submit(entry(2, 0, "retained"));
        runtime.submit(entry(3, 0, "discarded-a"));
        runtime.submit(entry(3, 1, "discarded-b"));

        runtime.advanceFrame(1, FrameAudioMode.SILENT_STEP);
        runtime.discardSubmittedCommandsAfter(2);
        runtime.advanceFrame(2, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(3, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("consumed", "retained"), handled);
    }

    @Test
    void commandsWithEqualFrameAndOrderRetainSubmissionOrder() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);
        runtime.submit(entry(4, 7, "first"));
        runtime.submit(entry(4, 7, "second"));
        runtime.submit(entry(4, 7, "third"));

        runtime.advanceFrame(4, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("first", "second", "third"), handled);
    }

    @Test
    void clearAfterPrefixConsumptionRemovesEveryRemainingCommand() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);
        runtime.submit(entry(1, 0, "consumed"));
        runtime.submit(entry(2, 0, "cleared-a"));
        runtime.submit(entry(3, 0, "cleared-b"));

        runtime.advanceFrame(1, FrameAudioMode.SILENT_STEP);
        runtime.clearSubmittedCommands();
        runtime.advanceFrame(2, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(3, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("consumed"), handled);
    }

    @Test
    void commandCallbackCannotSubmitAndTheFullFrameBatchCanBeRetried() {
        assertReentrantQueueMutationRejectedAndFrameRetried(
                runtime -> runtime.submit(entry(6, 0, "reentrant")));
    }

    @Test
    void commandCallbackCannotClearAndTheFullFrameBatchCanBeRetried() {
        assertReentrantQueueMutationRejectedAndFrameRetried(
                StreamBackedDeterministicAudioRuntime::clearSubmittedCommands);
    }

    @Test
    void commandCallbackCannotDiscardAndTheFullFrameBatchCanBeRetried() {
        assertReentrantQueueMutationRejectedAndFrameRetried(
                runtime -> runtime.discardSubmittedCommandsAfter(5));
    }

    @Test
    void commandCallbackCannotAdvanceAnotherFrameAndAllBatchesCanBeRetried() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        runtime.submit(entry(5, 0, "A"));
        runtime.submit(entry(5, 1, "B"));
        runtime.submit(entry(6, 0, "C"));
        runtime.setCommandHandler(command -> runtime.advanceFrame(6, FrameAudioMode.SILENT_STEP));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP));
        assertEquals("Audio frame advancement cannot be re-entered from a command handler",
                failure.getMessage());

        List<String> retried = new ArrayList<>();
        runtime.setCommandHandler(command -> retried.add(((AudioCommand.PlaySfx) command).sfxName()));
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(6, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "B", "C"), retried);
    }

    @Test
    void handlerFailureLeavesTheFullFrameBatchAvailableForRetry() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        List<String> handled = new ArrayList<>();
        runtime.submit(entry(5, 0, "A"));
        runtime.submit(entry(5, 1, "B"));
        runtime.setCommandHandler(command -> {
            String name = ((AudioCommand.PlaySfx) command).sfxName();
            handled.add(name);
            if (name.equals("A")) {
                throw new IllegalArgumentException("handler failed");
            }
        });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP));
        assertEquals("handler failed", failure.getMessage());

        runtime.setCommandHandler(command -> handled.add(((AudioCommand.PlaySfx) command).sfxName()));
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "A", "B"), handled);
    }

    @Test
    void submissionCompactsConsumedPrefixWithoutChangingStableDispatchOrTruncation() {
        List<String> handled = new ArrayList<>();
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHandledNames(handled);
        for (int frame = 1; frame <= 64; frame++) {
            runtime.submit(entry(frame, 0, "consumed-" + frame));
        }
        runtime.submit(entry(100, 1, "B"));
        runtime.submit(entry(100, 1, "C"));
        runtime.submit(entry(101, 0, "discarded"));
        for (int frame = 1; frame <= 64; frame++) {
            runtime.advanceFrame(frame, FrameAudioMode.SILENT_STEP);
        }
        handled.clear();

        runtime.submit(entry(100, 0, "A")); // Compacts the 64-entry consumed prefix first.
        runtime.discardSubmittedCommandsAfter(100);
        runtime.advanceFrame(100, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(100, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(101, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "B", "C"), handled);
    }

    @Test
    void consumingOneCommandAcrossEachOfOneThousandSilentStepFramesAllocatesLessThanSixteenKibibytes() {
        warmCommandConsumptionPath();
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        int[] handled = {0};
        runtime.setCommandHandler(command -> handled[0]++);
        runtime.advanceFrame(0, FrameAudioMode.SILENT_STEP); // Grow scratch buffers outside the measurement.
        for (int frame = 1; frame <= 1_000; frame++) {
            runtime.submit(entry(frame, 0, "command-" + frame));
        }
        AudioBenchmarkMemoryProbe probe = AudioBenchmarkMemoryProbe.create();
        assumeTrue(probe.snapshot().allocatedBytesSupported(),
                "Thread allocation accounting is unavailable on this JVM");

        AudioBenchmarkMemoryProbe.RunResult result = probe.measureTimedRun(() -> {
            for (int frame = 1; frame <= 1_000; frame++) {
                runtime.advanceFrame(frame, FrameAudioMode.SILENT_STEP);
            }
        });

        assertEquals(1_000, handled[0]);
        assertTrue(result.allocatedBytes() < 16 * 1_024,
                () -> "Expected in-place command consumption below 16 KiB, allocated "
                        + result.allocatedBytes() + " bytes");
    }

    @Test
    void shrinkingFrameAfterGrowthBoundsStreamConsumptionAndIgnoresStaleScratchTail() {
        // AudioFrameClock(5, 2) alternates 2- and 3-sample frames, so the third
        // frame (2 samples = 4 shorts) runs with a scratch buffer grown to 6
        // shorts whose tail still holds frame-2 data.
        AudioOutputFifo fifo = new AudioOutputFifo(64);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(5, 2), fifo);
        CountingStream music = new CountingStream(new short[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}, false);
        // SFX covers exactly frames 1-2 (10 shorts) and then completes; its
        // nonzero frame-2 samples linger in the scratch tail during frame 3.
        CountingStream sfx = new CountingStream(new short[] {
                10, 20, 30, 40, 50, 60, 70, 80, 90, 100}, true);
        runtime.setMusicStream(music);
        runtime.setSfxStream(sfx);

        runtime.advanceFrame(1, FrameAudioMode.NORMAL); // 4 shorts
        runtime.advanceFrame(2, FrameAudioMode.NORMAL); // 6 shorts, sfx completes
        runtime.advanceFrame(3, FrameAudioMode.NORMAL); // 4 shorts, smaller than scratch

        assertEquals(14, music.samplesRead,
                "The grown scratch buffer must not over-consume the music stream on the smaller frame.");
        assertEquals(10, sfx.samplesRead);

        short[] target = new short[14];
        assertEquals(7, runtime.drainPcm(target, 7));
        assertArrayEquals(new short[] {
                        11, 22, 33, 44,            // frame 1: music + sfx
                        55, 66, 77, 88, 99, 110,   // frame 2: music + sfx
                        11, 12, 13, 14},           // frame 3: music only — no stale sfx tail mixed
                target);
    }

    private static AudioTimelineEntry entry(long frame, int order, String name) {
        return new AudioTimelineEntry(frame, order,
                new AudioCommand.PlaySfx(-1, name, AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
    }

    private static StreamBackedDeterministicAudioRuntime runtimeWithHandledNames(List<String> handled) {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        runtime.setCommandHandler(command -> handled.add(((AudioCommand.PlaySfx) command).sfxName()));
        return runtime;
    }

    private static void assertReentrantQueueMutationRejectedAndFrameRetried(
            Consumer<StreamBackedDeterministicAudioRuntime> mutation) {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        runtime.submit(entry(5, 0, "A"));
        runtime.submit(entry(5, 1, "B"));
        runtime.setCommandHandler(command -> mutation.accept(runtime));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP));
        assertEquals("Command queue cannot be mutated while commands are being dispatched",
                failure.getMessage());

        List<String> retried = new ArrayList<>();
        runtime.setCommandHandler(command -> retried.add(((AudioCommand.PlaySfx) command).sfxName()));
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "B"), retried);
    }

    private static void warmCommandConsumptionPath() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(64));
        for (int frame = 1; frame <= 10_000; frame++) {
            runtime.submit(entry(frame, 0, "warmup"));
        }
        for (int frame = 1; frame <= 10_000; frame++) {
            runtime.advanceFrame(frame, FrameAudioMode.SILENT_STEP);
        }
    }

    private static final class CountingStream implements AudioStream {
        private final short[] samples;
        private final boolean completeWhenDrained;
        private int cursor;
        private int samplesRead;

        private CountingStream(short[] samples, boolean completeWhenDrained) {
            this.samples = samples;
            this.completeWhenDrained = completeWhenDrained;
        }

        @Override
        public int read(short[] buffer) {
            int count = Math.min(buffer.length, samples.length - cursor);
            System.arraycopy(samples, cursor, buffer, 0, count);
            cursor += count;
            samplesRead += count;
            return count;
        }

        @Override
        public boolean isComplete() {
            return completeWhenDrained && cursor >= samples.length;
        }
    }
}
