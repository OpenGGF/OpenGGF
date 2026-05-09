package com.openggf.audio.runtime;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;

public final class StreamBackedDeterministicAudioRuntime implements DeterministicAudioRuntime {
    private final AudioFrameClock frameClock;
    private final AudioOutputFifo outputFifo;
    private final PriorityQueue<PendingAudioCommand> pendingCommands = new PriorityQueue<>(
            Comparator.comparingLong((PendingAudioCommand pending) -> pending.entry().frame())
                    .thenComparingInt(pending -> pending.entry().order())
                    .thenComparingLong(PendingAudioCommand::sequence));
    private final PcmHistoryRing pcmHistory;
    private final int reverseReleaseCrossfadeFrames;
    private long nextSubmissionSequence;
    private AudioStream musicStream;
    private AudioStream sfxStream;
    private Consumer<AudioCommand> commandHandler = command -> {};
    private PcmHistoryRing.ReverseCursor reverseCursor;
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
        pendingCommands.add(new PendingAudioCommand(
                Objects.requireNonNull(entry, "entry"),
                nextSubmissionSequence++));
    }

    @Override
    public void discardSubmittedCommandsAfter(long frame) {
        pendingCommands.removeIf(pending -> pending.entry().frame() > frame);
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
            int read = reverseCursor.readPrevious(target, frames);
            rememberLastReverseFrame(target, read);
            return read;
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
        while (!pendingCommands.isEmpty() && pendingCommands.peek().entry().frame() < frame) {
            pendingCommands.poll();
        }
        long consumeThroughSequence = nextSubmissionSequence - 1;
        while (!pendingCommands.isEmpty()) {
            PendingAudioCommand pending = pendingCommands.peek();
            AudioTimelineEntry entry = pending.entry();
            if (entry.frame() != frame || pending.sequence() > consumeThroughSequence) {
                break;
            }
            pendingCommands.poll();
            commandHandler.accept(entry.command());
        }
    }

    private record PendingAudioCommand(AudioTimelineEntry entry, long sequence) {
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
