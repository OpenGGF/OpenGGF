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
    private static final Comparator<AudioTimelineEntry> COMMAND_ORDER =
            Comparator.comparingLong(AudioTimelineEntry::frame)
                    .thenComparingInt(AudioTimelineEntry::order);
    private static final int MINIMUM_COMMAND_COMPACTION_PREFIX = 64;
    private static final String COMMAND_QUEUE_MUTATION_DURING_DISPATCH_MESSAGE =
            "Command queue cannot be mutated while commands are being dispatched";
    private static final String FRAME_ADVANCEMENT_DURING_DISPATCH_MESSAGE =
            "Audio frame advancement cannot be re-entered from a command handler";

    private final AudioFrameClock frameClock;
    private final AudioOutputFifo outputFifo;
    private final List<AudioTimelineEntry> pendingCommands = new ArrayList<>();
    private final PcmHistoryRing pcmHistory;
    private final int reverseReleaseCrossfadeFrames;
    private AudioStream musicStream;
    private AudioStream sfxStream;
    private Consumer<AudioCommand> commandHandler = command -> {};
    private PcmHistoryRing.ReverseCursor reverseCursor;
    private double pendingReverseRate = 1.0;
    private boolean hasLastReverseFrame;
    private boolean reverseFrameOutputThisSession;
    private short lastReverseLeft;
    private short lastReverseRight;
    private int releaseCrossfadeRemaining;
    private short[] musicScratch = new short[0];
    private short[] sfxScratch = new short[0];
    private int lastProducedFrames;
    private int firstPendingCommand;
    private boolean dispatchingCommands;
    private LiveAudioCaptureTap presentationAudioCaptureTap;
    private boolean reversePresentationActive;

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
        ensureCommandQueueMutable();
        AudioTimelineEntry requiredEntry = Objects.requireNonNull(entry, "entry");
        compactConsumedCommandPrefixIfNeeded();
        int insertionIndex = findCommandInsertionIndex(requiredEntry);
        pendingCommands.add(insertionIndex, requiredEntry);
    }

    @Override
    public void discardSubmittedCommandsAfter(long frame) {
        ensureCommandQueueMutable();
        int firstDiscardedCommand = findFirstCommandAfter(frame);
        if (firstDiscardedCommand < pendingCommands.size()) {
            pendingCommands.subList(firstDiscardedCommand, pendingCommands.size()).clear();
        }
        clearCommandStorageIfExhausted();
    }

    @Override
    public void clearSubmittedCommands() {
        ensureCommandQueueMutable();
        pendingCommands.clear();
        firstPendingCommand = 0;
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
        if (dispatchingCommands) {
            throw new IllegalStateException(FRAME_ADVANCEMENT_DURING_DISPATCH_MESSAGE);
        }
        if (mode == FrameAudioMode.PRESENTATION_ONLY_REVERSE) {
            return;
        }
        consumeCommands(frame);
        int samples = frameClock.samplesForNextFrame() * 2;
        ensureScratch(samples);
        Arrays.fill(musicScratch, 0, samples, (short) 0);
        Arrays.fill(sfxScratch, 0, samples, (short) 0);

        if (musicStream != null) {
            musicStream.read(musicScratch, samples);
            if (musicStream.isComplete()) {
                musicStream = null;
            }
        }
        if (sfxStream != null) {
            sfxStream.read(sfxScratch, samples);
            if (sfxStream.isComplete()) {
                sfxStream = null;
            }
        }

        if (sfxStream != null || hasNonZeroSamples(sfxScratch, samples)) {
            mixSfxIntoMusic(samples);
        }
        if (mode == FrameAudioMode.NORMAL) {
            if (presentationAudioCaptureTap != null) {
                presentationAudioCaptureTap.acceptForwardPcm(musicScratch, samples / 2);
            }
            if (pcmHistory != null) {
                pcmHistory.write(musicScratch, samples / 2);
            }
            outputFifo.write(musicScratch, samples / 2);
            lastProducedFrames = samples / 2;
        } else if (mode == FrameAudioMode.SILENT_STEP && presentationAudioCaptureTap != null) {
            presentationAudioCaptureTap.clearForwardPcm();
        }
    }

    @Override
    public boolean providesPresentationPcm() {
        return true;
    }

    /** Stereo-frame count produced by the most recent NORMAL {@link #advanceFrame}. */
    public int lastProducedFrames() {
        return lastProducedFrames;
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

    public PresentationAudioCapture openPresentationAudioCapture(int sampleRate, int frameRate) {
        if (presentationAudioCaptureTap != null) {
            throw new IllegalStateException("A presentation audio capture is already attached");
        }
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(sampleRate, frameRate);
        if (reversePresentationActive) {
            tap.beginReversePresentation(reverseCursor != null ? reverseCursor.fork() : null);
        }
        presentationAudioCaptureTap = tap;
        return new PresentationAudioCaptureLease(tap);
    }

    @Override
    public void flushPresentationFifo() {
        outputFifo.flush();
    }

    @Override
    public void beginReversePresentation() {
        reversePresentationActive = true;
        reverseCursor = pcmHistory != null ? pcmHistory.createReverseCursor() : null;
        if (reverseCursor != null) {
            reverseCursor.setRate(pendingReverseRate);
        }
        if (presentationAudioCaptureTap != null) {
            presentationAudioCaptureTap.beginReversePresentation(
                    reverseCursor != null ? reverseCursor.fork() : null);
        }
        cancelReleaseCrossfade();
    }

    @Override
    public void setReversePlaybackRate(double rate) {
        pendingReverseRate = (Double.isNaN(rate) || rate <= 0.0) ? 1.0 : rate;
        if (reverseCursor != null) {
            reverseCursor.setRate(pendingReverseRate);
        }
        if (presentationAudioCaptureTap != null) {
            presentationAudioCaptureTap.setReversePlaybackRate(pendingReverseRate);
        }
    }

    @Override
    public void endReversePresentation() {
        if (pcmHistory != null) {
            pcmHistory.commitReverseCursor(reverseCursor);
        }
        reverseCursor = null;
        reversePresentationActive = false;
        if (presentationAudioCaptureTap != null) {
            presentationAudioCaptureTap.endReversePresentation();
        }
        if (hasLastReverseFrame && reverseFrameOutputThisSession && reverseReleaseCrossfadeFrames > 0) {
            releaseCrossfadeRemaining = reverseReleaseCrossfadeFrames;
        }
        reverseFrameOutputThisSession = false;
        pendingReverseRate = 1.0;
    }

    @Override
    public void clearPcmHistory() {
        reverseCursor = null;
        reversePresentationActive = false;
        cancelReleaseCrossfade();
        if (pcmHistory != null) {
            pcmHistory.clear();
        }
        if (presentationAudioCaptureTap != null) {
            presentationAudioCaptureTap.clearPcmHistory();
        }
    }

    public long totalSamplesProduced() {
        return frameClock.totalSamplesProduced();
    }

    private void consumeCommands(long frame) {
        int commandCount = pendingCommands.size();
        if (firstPendingCommand >= commandCount) {
            return;
        }

        int commandIndex = firstPendingCommand;
        while (commandIndex < commandCount
                && pendingCommands.get(commandIndex).frame() < frame) {
            commandIndex++;
        }
        int firstCommandForFrame = commandIndex;
        while (commandIndex < commandCount
                && pendingCommands.get(commandIndex).frame() == frame) {
            commandIndex++;
        }
        int afterCommandForFrame = commandIndex;
        if (firstCommandForFrame < afterCommandForFrame) {
            dispatchingCommands = true;
            try {
                for (int index = firstCommandForFrame; index < afterCommandForFrame; index++) {
                    commandHandler.accept(pendingCommands.get(index).command());
                }
            } finally {
                dispatchingCommands = false;
            }
        }
        firstPendingCommand = afterCommandForFrame;
        clearCommandStorageIfExhausted();
    }

    private void ensureCommandQueueMutable() {
        if (dispatchingCommands) {
            throw new IllegalStateException(COMMAND_QUEUE_MUTATION_DURING_DISPATCH_MESSAGE);
        }
    }

    private int findCommandInsertionIndex(AudioTimelineEntry entry) {
        int low = firstPendingCommand;
        int high = pendingCommands.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (COMMAND_ORDER.compare(pendingCommands.get(middle), entry) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private int findFirstCommandAfter(long frame) {
        int low = firstPendingCommand;
        int high = pendingCommands.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (pendingCommands.get(middle).frame() <= frame) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void compactConsumedCommandPrefixIfNeeded() {
        if (firstPendingCommand < MINIMUM_COMMAND_COMPACTION_PREFIX
                || firstPendingCommand < pendingCommands.size() - firstPendingCommand) {
            return;
        }
        pendingCommands.subList(0, firstPendingCommand).clear();
        firstPendingCommand = 0;
    }

    private void clearCommandStorageIfExhausted() {
        if (firstPendingCommand < pendingCommands.size()) {
            return;
        }
        pendingCommands.clear();
        firstPendingCommand = 0;
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

    /**
     * Grows the scratch buffers when needed without shrinking. Only the first
     * {@code samples} elements of each buffer are valid for the current frame:
     * {@link #advanceFrame} zero-fills, reads, mixes, and writes strictly within
     * that bound, and the stream reads are length-bounded so a larger buffer
     * never over-consumes stream state.
     */
    private void ensureScratch(int samples) {
        if (musicScratch.length < samples) {
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

    private final class PresentationAudioCaptureLease implements PresentationAudioCapture {
        private final LiveAudioCaptureTap tap;
        private boolean closed;

        private PresentationAudioCaptureLease(LiveAudioCaptureTap tap) {
            this.tap = tap;
        }

        @Override
        public int sampleRate() {
            return tap.sampleRate();
        }

        @Override
        public int frameRate() {
            return tap.frameRate();
        }

        @Override
        public int maxStereoFramesPerPacket() {
            return tap.maxStereoFramesPerPacket();
        }

        @Override
        public int drainPresentationFrame(short[] target) {
            if (closed) {
                throw new IllegalStateException("Presentation audio capture is closed");
            }
            return tap.drainPresentationFrame(target);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (presentationAudioCaptureTap == tap) {
                presentationAudioCaptureTap = null;
            }
        }
    }
}
