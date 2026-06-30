package com.openggf.audio.runtime;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the steady-state fast paths added to
 * {@link StreamBackedDeterministicAudioRuntime}: the empty-pending-command
 * early return in {@code consumeCommands} and the grow-only scratch buffers,
 * whose stale tail must never be consumed — neither by the mix/output stages
 * (bounded by the frame's sample count) nor by the streams themselves
 * (length-bounded {@link AudioStream#read(short[], int)}).
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
