package com.openggf.audio.runtime;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class StreamBackedDeterministicAudioRuntime implements DeterministicAudioRuntime {
    private final AudioFrameClock frameClock;
    private final AudioOutputFifo outputFifo;
    private final List<AudioTimelineEntry> pendingCommands = new ArrayList<>();
    private final PcmHistoryRing pcmHistory;
    private final int reverseReleaseCrossfadeFrames;
    private AudioStream musicStream;
    private AudioStream sfxStream;
    private Consumer<AudioCommand> commandHandler = command -> {};
    private PcmHistoryRing.ReverseCursor reverseCursor;
    private ReverseResynthesizer reverseResynthesizer;
    private boolean hasLastReverseFrame;
    private boolean reverseFrameOutputThisSession;
    private short lastReverseLeft;
    private short lastReverseRight;
    private int releaseCrossfadeRemaining;
    private short[] musicScratch = new short[0];
    private short[] sfxScratch = new short[0];

    public StreamBackedDeterministicAudioRuntime(AudioFrameClock frameClock, AudioOutputFifo outputFifo) {
        this(frameClock, outputFifo, null);
    }

    public StreamBackedDeterministicAudioRuntime(
            AudioFrameClock frameClock,
            AudioOutputFifo outputFifo,
            PcmHistoryRing pcmHistory) {
        this(frameClock, outputFifo, pcmHistory, 0);
    }

    public StreamBackedDeterministicAudioRuntime(
            AudioFrameClock frameClock,
            AudioOutputFifo outputFifo,
            PcmHistoryRing pcmHistory,
            int reverseReleaseCrossfadeFrames) {
        if (reverseReleaseCrossfadeFrames < 0) {
            throw new IllegalArgumentException("reverseReleaseCrossfadeFrames must be non-negative");
        }
        this.frameClock = Objects.requireNonNull(frameClock, "frameClock");
        this.outputFifo = Objects.requireNonNull(outputFifo, "outputFifo");
        this.pcmHistory = pcmHistory;
        this.reverseReleaseCrossfadeFrames = reverseReleaseCrossfadeFrames;
    }

    @Override
    public boolean consumesSubmittedCommands() {
        return true;
    }

    @Override
    public void setCommandHandler(Consumer<AudioCommand> commandHandler) {
        this.commandHandler = commandHandler != null ? commandHandler : command -> {};
    }

    @Override
    public void submit(AudioTimelineEntry entry) {
        pendingCommands.add(Objects.requireNonNull(entry, "entry"));
    }

    @Override
    public void discardSubmittedCommandsAfter(long frame) {
        pendingCommands.removeIf(entry -> entry.frame() > frame);
    }

    @Override
    public void clearSubmittedCommands() {
        pendingCommands.clear();
    }

    @Override
    public void setMusicStream(AudioStream musicStream) {
        this.musicStream = musicStream;
    }

    @Override
    public void setSfxStream(AudioStream sfxStream) {
        this.sfxStream = sfxStream;
    }

    @Override
    public void advanceFrame(long frame, FrameAudioMode mode) {
        if (mode == FrameAudioMode.PRESENTATION_ONLY_REVERSE) {
            return;
        }
        consumeCommands(frame);
        int samples = frameClock.samplesForNextFrame() * 2;
        ensureScratch(samples);
        Arrays.fill(musicScratch, 0, samples, (short) 0);
        Arrays.fill(sfxScratch, 0, samples, (short) 0);

        if (musicStream != null) {
            musicStream.read(musicScratch);
            if (musicStream.isComplete()) {
                musicStream = null;
            }
        }
        if (sfxStream != null) {
            sfxStream.read(sfxScratch);
            if (sfxStream.isComplete()) {
                sfxStream = null;
            }
        }

        if (sfxStream != null || hasNonZeroSamples(sfxScratch, samples)) {
            mixSfxIntoMusic(samples);
        }
        if (mode == FrameAudioMode.NORMAL) {
            if (pcmHistory != null) {
                pcmHistory.write(musicScratch, samples / 2);
            }
            outputFifo.write(musicScratch, samples / 2);
        }
    }

    @Override
    public boolean providesPresentationPcm() {
        return true;
    }

    @Override
    public int drainPcm(short[] target, int frames) {
        if (reverseCursor != null) {
            // Loop: extend the cursor's readable window with the resynth as
            // much as the ring's physical-slot budget allows, then read a
            // chunk, then loop. Each readPrevious frees physical slots so the
            // next ensureHeadroom call can extend further. Without the loop,
            // a single ensureHeadroom call from a full-ring cursor would
            // refuse to extend (no slots free), and readPrevious could only
            // satisfy at most one ring-capacity worth of frames in a single
            // drainPcm call — which is wrong when the caller's request
            // exceeds the ring capacity.
            int totalRead = 0;
            while (totalRead < frames) {
                int remaining = frames - totalRead;
                if (reverseResynthesizer != null) {
                    reverseResynthesizer.ensureHeadroom(reverseCursor, remaining);
                }
                long unread = reverseCursor.nextReadableFrame()
                        - reverseCursor.oldestReadableFrame() + 1;
                if (unread <= 0) {
                    break; // history exhausted past the start-of-history floor
                }
                int chunk = (int) Math.min(unread, (long) remaining);
                short[] chunkBuf = new short[chunk * 2];
                int got = reverseCursor.readPrevious(chunkBuf, chunk);
                if (got <= 0) {
                    break;
                }
                System.arraycopy(chunkBuf, 0, target, totalRead * 2, got * 2);
                totalRead += got;
                if (got < chunk) {
                    // readPrevious stopped early; nothing more available.
                    break;
                }
            }
            // Pad the unfilled tail with silence so the caller's buffer is
            // fully written even when history runs out.
            if (totalRead < frames) {
                java.util.Arrays.fill(target, totalRead * 2, frames * 2, (short) 0);
            }
            rememberLastReverseFrame(target, totalRead);
            return totalRead;
        }
        int read = outputFifo.drain(target, frames);
        applyReleaseCrossfade(target, read);
        return read;
    }

    @Override
    public void flushPresentationFifo() {
        outputFifo.flush();
    }

    @Override
    public void beginReversePresentation() {
        reverseCursor = pcmHistory != null ? pcmHistory.createReverseCursor() : null;
        cancelReleaseCrossfade();
    }

    @Override
    public void endReversePresentation() {
        if (pcmHistory != null) {
            pcmHistory.commitReverseCursor(reverseCursor);
        }
        reverseCursor = null;
        if (hasLastReverseFrame && reverseFrameOutputThisSession && reverseReleaseCrossfadeFrames > 0) {
            releaseCrossfadeRemaining = reverseReleaseCrossfadeFrames;
        }
        reverseFrameOutputThisSession = false;
    }

    @Override
    public boolean hasActivePresentation() {
        if (musicStream != null || sfxStream != null) {
            return true;
        }
        if (outputFifo.availableFrames() > 0) {
            return true;
        }
        if (reverseCursor != null) {
            return true;
        }
        return releaseCrossfadeRemaining > 0;
    }

    @Override
    public AudioFrameClock.Snapshot captureClockSnapshot() {
        return frameClock.captureSnapshot();
    }

    @Override
    public void restoreClockSnapshot(AudioFrameClock.Snapshot snapshot) {
        if (snapshot != null) {
            frameClock.restoreSnapshot(snapshot);
        }
    }

    @Override
    public AudioStream musicStreamForReverseResynth() {
        return musicStream;
    }

    @Override
    public AudioStream sfxStreamForReverseResynth() {
        return sfxStream;
    }

    @Override
    public int samplesForNextFrameForReverseResynth() {
        return frameClock.samplesForNextFrame();
    }

    public PcmHistoryRing pcmHistoryRingForReverseResynth() {
        return pcmHistory;
    }

    public int sampleRateForReverseResynth() {
        return frameClock.sampleRate();
    }

    public void setReverseResynthesizer(ReverseResynthesizer resynthesizer) {
        this.reverseResynthesizer = resynthesizer;
    }

    // Test seam: surface the active reverse cursor so integration tests can
    // assert the cursor floor moves downward after a drainPcm/ensureHeadroom
    // cycle. Non-test callers should not reach into the cursor directly.
    public PcmHistoryRing.ReverseCursor getActiveReverseCursorForTest() {
        return reverseCursor;
    }

    @Override
    public void clearPcmHistory() {
        reverseCursor = null;
        cancelReleaseCrossfade();
        if (pcmHistory != null) {
            pcmHistory.clear();
        }
    }

    public long totalSamplesProduced() {
        return frameClock.totalSamplesProduced();
    }

    private void consumeCommands(long frame) {
        // Drain everything queued at or before `frame`. The filter mirrors the
        // remove predicate below, so any command whose frame number is less
        // than or equal to the current advance frame is dispatched before it
        // is swept from the queue. The == form was too tight: when a tick
        // skips an audio frame (e.g., a freeze during act transition, or
        // playMusic recorded just before beginGameplayAudioFrame increments
        // the counter), the queued command's frame can be strictly less than
        // the next advance frame and would be silently dropped.
        List<AudioTimelineEntry> entries = pendingCommands.stream()
                .filter(entry -> entry.frame() <= frame)
                .sorted(Comparator.comparingLong(AudioTimelineEntry::frame)
                        .thenComparingInt(AudioTimelineEntry::order))
                .toList();
        for (AudioTimelineEntry entry : entries) {
            commandHandler.accept(entry.command());
        }
        pendingCommands.removeIf(entry -> entry.frame() <= frame);
    }

    private void rememberLastReverseFrame(short[] target, int readFrames) {
        if (readFrames <= 0) {
            return;
        }
        int index = (readFrames - 1) * 2;
        lastReverseLeft = target[index];
        lastReverseRight = target[index + 1];
        hasLastReverseFrame = true;
        reverseFrameOutputThisSession = true;
    }

    private void applyReleaseCrossfade(short[] target, int frames) {
        if (releaseCrossfadeRemaining <= 0) {
            return;
        }
        int total = reverseReleaseCrossfadeFrames;
        for (int frame = 0; frame < frames && releaseCrossfadeRemaining > 0; frame++) {
            int elapsed = total - releaseCrossfadeRemaining + 1;
            int index = frame * 2;
            target[index] = crossfade(lastReverseLeft, target[index], elapsed, total);
            target[index + 1] = crossfade(lastReverseRight, target[index + 1], elapsed, total);
            releaseCrossfadeRemaining--;
        }
        if (releaseCrossfadeRemaining == 0) {
            hasLastReverseFrame = false;
        }
    }

    private void cancelReleaseCrossfade() {
        hasLastReverseFrame = false;
        reverseFrameOutputThisSession = false;
        releaseCrossfadeRemaining = 0;
    }

    private static short crossfade(short from, short to, int elapsed, int total) {
        int mixed = (from * (total - elapsed) + to * elapsed) / total;
        return (short) mixed;
    }

    private void ensureScratch(int samples) {
        if (musicScratch.length != samples) {
            musicScratch = new short[samples];
            sfxScratch = new short[samples];
        }
    }

    private void mixSfxIntoMusic(int samples) {
        for (int i = 0; i < samples; i++) {
            int mixed = musicScratch[i] + sfxScratch[i];
            if (mixed > Short.MAX_VALUE) {
                mixed = Short.MAX_VALUE;
            } else if (mixed < Short.MIN_VALUE) {
                mixed = Short.MIN_VALUE;
            }
            musicScratch[i] = (short) mixed;
        }
    }

    private static boolean hasNonZeroSamples(short[] samples, int length) {
        for (int i = 0; i < length; i++) {
            if (samples[i] != 0) {
                return true;
            }
        }
        return false;
    }
}
