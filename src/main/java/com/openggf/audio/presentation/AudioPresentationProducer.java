package com.openggf.audio.presentation;

import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.PcmHistoryRing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Sole outer-frame owner of final PCM cadence, history, rewind, and taps.
 */
public final class AudioPresentationProducer {
    private static final int CHANNELS = 2;
    private static final int MAX_CAPTURE_HANDLES = 32;

    private final Thread ownerThread;
    private final int sampleRate;
    private final int maxStereoFrames;
    private final int crossfadeFrames;
    private final AudioVoiceRegistry registry;
    private final AudioPresentationCommandQueue commands;
    private final AudioPresentationMixer mixer;
    private final AudioFrameClock clock;
    private final PcmHistoryRing history;
    private final short[] silence;
    private final short[] reversePcm;
    private final AudioPresentationFrameView frameView;
    private final CaptureHandle[] captures =
            new CaptureHandle[MAX_CAPTURE_HANDLES];
    private final Consumer<AudioPresentationCommand> commandApplier;
    private final IdentityTokenRegistry diagnosticIdentityTokens =
            new IdentityTokenRegistry();

    private AudioPresentationSink sink;
    private PcmHistoryRing.ReverseCursor reverseCursor;
    private AudioPresentationSnapshot selectedRestore;
    private AudioPresentationDependencyResolver selectedRestoreResolver;
    private AudioVoiceRegistry.PreparedSnapshotRestore
            preparedSelectedRestore;
    private int captureCount;
    private int releaseCrossfadeRemaining;
    private short lastReverseLeft;
    private short lastReverseRight;
    private boolean historyArmed;
    private boolean presenting;
    private boolean reverseActive;
    private boolean reverseFrameOutput;
    private boolean hasLastReverseFrame;
    private boolean closed;

    public AudioPresentationProducer(
            int sampleRate,
            int frameRate,
            int historyFrames,
            int crossfadeFrames,
            AudioVoiceRegistry registry,
            AudioPresentationCommandQueue commands,
            AudioPresentationMixer mixer,
            AudioPresentationSink sink) {
        this(sampleRate, frameRate, historyFrames, crossfadeFrames, registry,
                commands, mixer, sink, null);
    }

    AudioPresentationProducer(
            int sampleRate,
            int frameRate,
            int historyFrames,
            int crossfadeFrames,
            AudioVoiceRegistry registry,
            AudioPresentationCommandQueue commands,
            AudioPresentationMixer mixer,
            AudioPresentationSink sink,
            Consumer<AudioPresentationCommand> commandApplier) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive");
        }
        if (historyFrames <= 0) {
            throw new IllegalArgumentException("historyFrames must be positive");
        }
        if (crossfadeFrames < 0) {
            throw new IllegalArgumentException(
                    "crossfadeFrames must be non-negative");
        }
        ownerThread = Thread.currentThread();
        this.sampleRate = sampleRate;
        maxStereoFrames = (sampleRate + frameRate - 1) / frameRate;
        this.crossfadeFrames = crossfadeFrames;
        this.registry = Objects.requireNonNull(registry, "registry");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.mixer = Objects.requireNonNull(mixer, "mixer");
        if (mixer.maxStereoFrames() < maxStereoFrames) {
            throw new IllegalArgumentException(
                    "mixer capacity is smaller than the producer packet");
        }
        this.sink = requireCompatibleSink(sink);
        clock = new AudioFrameClock(sampleRate, frameRate);
        history = new PcmHistoryRing(historyFrames);
        silence = new short[maxStereoFrames * CHANNELS];
        reversePcm = new short[maxStereoFrames * CHANNELS];
        frameView = new AudioPresentationFrameView(silence);
        this.commandApplier =
                commandApplier != null ? commandApplier : registry::apply;
    }

    public void present(long commandFrame, PresentationMode mode) {
        assertOwnerThread();
        assertOpen();
        Objects.requireNonNull(mode, "mode");
        if (presenting) {
            throw new IllegalStateException(
                    "audio presentation cannot be re-entered");
        }
        presenting = true;
        try {
            int stereoFrames = clock.samplesForNextFrame();
            short[] pcm;
            if (mode == PresentationMode.FORWARD) {
                commands.applyPending(commandApplier);
                registry.beginRendering();
                try {
                    pcm = mixer.mix(registry, stereoFrames);
                } finally {
                    registry.endRendering();
                }
                applyReleaseCrossfade(pcm, stereoFrames);
                if (historyArmed) {
                    history.write(pcm, stereoFrames);
                }
            } else if (mode == PresentationMode.SILENT) {
                commands.applyPending(commandApplier);
                Arrays.fill(silence, 0, stereoFrames * CHANNELS, (short) 0);
                pcm = silence;
            } else {
                Arrays.fill(reversePcm, 0, stereoFrames * CHANNELS, (short) 0);
                if (reverseCursor != null) {
                    reverseCursor.readPrevious(reversePcm, stereoFrames);
                }
                if (reverseActive && stereoFrames > 0) {
                    rememberLastReverseFrame(reversePcm, stereoFrames);
                }
                pcm = reversePcm;
            }

            frameView.update(pcm, stereoFrames, commandFrame, mode);
            sink.accept(frameView);
            for (int index = 0; index < captureCount; index++) {
                CaptureHandle capture = captures[index];
                if (capture != null) {
                    capture.onPresentationFrame(frameView);
                }
            }
        } finally {
            presenting = false;
        }
    }

    public LiveCaptureAudioHandle attachCapture(int frameRate) {
        assertOwnerBoundary();
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive");
        }
        if (captureCount == captures.length) {
            throw new IllegalStateException(
                    "audio presentation capture capacity exhausted");
        }
        CaptureHandle capture =
                new CaptureHandle(frameRate, clock.captureSnapshot());
        captures[captureCount++] = capture;
        return capture;
    }

    public void beginReverse(double rate) {
        assertOwnerBoundary();
        registry.discardPreparedRestore(preparedSelectedRestore);
        preparedSelectedRestore = null;
        selectedRestore = null;
        selectedRestoreResolver = null;
        cancelReleaseCrossfade();
        reverseCursor = history.createReverseCursor();
        reverseCursor.setRate(rate);
        reverseActive = true;
        sink.onReverseBoundary();
    }

    public void setReverseRate(double rate) {
        assertOwnerBoundary();
        if (reverseCursor != null) {
            reverseCursor.setRate(rate);
        }
    }

    public void endReverse() {
        assertOwnerBoundary();
        if (!reverseActive) {
            return;
        }
        if (selectedRestore != null) {
            if (preparedSelectedRestore == null) {
                preparedSelectedRestore = registry.prepareSnapshotRestore(
                        selectedRestore, selectedRestoreResolver);
            }
            registry.commitPreparedRestore(preparedSelectedRestore);
        }
        registry.stopTransientVoices();
        history.commitReverseCursor(reverseCursor);
        reverseCursor = null;
        reverseActive = false;
        selectedRestore = null;
        selectedRestoreResolver = null;
        preparedSelectedRestore = null;
        if (hasLastReverseFrame && reverseFrameOutput
                && crossfadeFrames > 0) {
            releaseCrossfadeRemaining = crossfadeFrames;
        }
        reverseFrameOutput = false;
        sink.onReverseBoundary();
    }

    public void clearHistory() {
        assertOwnerBoundary();
        history.clear();
        selectedRestore = null;
        selectedRestoreResolver = null;
        registry.discardPreparedRestore(preparedSelectedRestore);
        preparedSelectedRestore = null;
        cancelReleaseCrossfade();
    }

    public void setHistoryArmed(boolean armed) {
        assertOwnerBoundary();
        historyArmed = armed;
    }

    public AudioPresentationSnapshot snapshot() {
        assertOwnerBoundary();
        return registry.snapshot();
    }

    /**
     * Read-only identity/state fingerprint used by transactional-release
     * diagnostics. Mutable runtime objects are represented by opaque,
     * collision-free reference-identity tokens so taking the fingerprint
     * cannot perturb presentation state or expose those objects to callers.
     */
    public TransactionFingerprint transactionFingerprint() {
        assertOwnerBoundary();
        IdentityFingerprint[] voiceIdentities =
                new IdentityFingerprint[registry.orderedVoiceCount()];
        for (int index = 0; index < registry.orderedVoiceCount(); index++) {
            voiceIdentities[index] = identityFingerprint(
                    registry.orderedVoiceAt(index));
        }
        return new TransactionFingerprint(
                clock.captureSnapshot(),
                history.diagnosticSnapshot(),
                List.of(voiceIdentities),
                reverseCursor != null ? reverseCursor.state() : null,
                identityFingerprint(selectedRestore),
                identityFingerprint(selectedRestoreResolver),
                identityFingerprint(preparedSelectedRestore),
                releaseCrossfadeRemaining,
                lastReverseLeft,
                lastReverseRight,
                historyArmed,
                reverseActive,
                reverseFrameOutput,
                hasLastReverseFrame,
                captureCount);
    }

    /**
     * Immutable diagnostic fingerprint for atomic rewind-release verification.
     * Runtime voices, resolvers, and prepared tokens are represented by
     * opaque reference-identity fingerprints; no live mutable object is
     * directly accessible through this snapshot.
     */
    public record TransactionFingerprint(
            AudioFrameClock.Snapshot clock,
            PcmHistoryRing.DiagnosticSnapshot history,
            List<IdentityFingerprint> voiceIdentities,
            PcmHistoryRing.CursorState reverseCursor,
            IdentityFingerprint selectedRestoreIdentity,
            IdentityFingerprint selectedRestoreResolverIdentity,
            IdentityFingerprint preparedSelectedRestoreIdentity,
            int releaseCrossfadeRemaining,
            short lastReverseLeft,
            short lastReverseRight,
            boolean historyArmed,
            boolean reverseActive,
            boolean reverseFrameOutput,
            boolean hasLastReverseFrame,
            int captureCount) {
        public TransactionFingerprint {
            voiceIdentities = List.copyOf(voiceIdentities);
        }
    }

    /**
     * Opaque, collision-free reference identity used only in diagnostics.
     */
    public record IdentityFingerprint(long token) {
    }

    private IdentityFingerprint identityFingerprint(Object reference) {
        return new IdentityFingerprint(
                diagnosticIdentityTokens.tokenFor(reference));
    }

    /**
     * Assigns stable, collision-free diagnostic ids without keeping runtime
     * voices, snapshots, or resolvers alive.
     */
    private static final class IdentityTokenRegistry {
        private final List<TokenEntry> entries = new ArrayList<>();
        private long nextToken = 1;

        private long tokenFor(Object reference) {
            if (reference == null) {
                return 0;
            }
            for (int index = entries.size() - 1; index >= 0; index--) {
                TokenEntry entry = entries.get(index);
                Object existing = entry.reference().get();
                if (existing == null) {
                    entries.remove(index);
                } else if (existing == reference) {
                    return entry.token();
                }
            }
            long token = nextToken++;
            if (token == 0) {
                throw new IllegalStateException(
                        "diagnostic identity token space exhausted");
            }
            entries.add(new TokenEntry(new WeakReference<>(reference), token));
            return token;
        }

        private record TokenEntry(
                WeakReference<Object> reference, long token) {
        }
    }

    public void restore(
            AudioPresentationSnapshot snapshot,
            AudioPresentationDependencyResolver resolver,
            boolean preservePresentation) {
        assertOwnerBoundary();
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        if (preservePresentation && reverseActive) {
            registry.discardPreparedRestore(preparedSelectedRestore);
            preparedSelectedRestore = null;
            selectedRestore = snapshot;
            selectedRestoreResolver = resolver;
            return;
        }
        registry.restore(snapshot, resolver);
        if (!preservePresentation) {
            history.clear();
            reverseCursor = null;
            reverseActive = false;
            selectedRestore = null;
            selectedRestoreResolver = null;
            registry.discardPreparedRestore(preparedSelectedRestore);
            preparedSelectedRestore = null;
            cancelReleaseCrossfade();
        }
    }

    public void prepareSelectedRestore() {
        assertOwnerBoundary();
        if (selectedRestore == null || preparedSelectedRestore != null) {
            return;
        }
        preparedSelectedRestore = registry.prepareSnapshotRestore(
                selectedRestore, selectedRestoreResolver);
    }

    public void replaceSink(AudioPresentationSink sink) {
        assertOwnerBoundary();
        AudioPresentationSink replacement = requireCompatibleSink(sink);
        if (replacement == this.sink) {
            return;
        }
        AudioPresentationSink previous = this.sink;
        this.sink = replacement;
        previous.close();
    }

    public void close() {
        assertOwnerThread();
        if (closed) {
            return;
        }
        closed = true;
        for (int index = 0; index < captureCount; index++) {
            CaptureHandle capture = captures[index];
            if (capture != null) {
                capture.closed = true;
                captures[index] = null;
            }
        }
        captureCount = 0;
        try {
            registry.clear();
        } finally {
            try {
                history.clear();
            } finally {
                sink.close();
            }
        }
    }

    private AudioPresentationSink requireCompatibleSink(
            AudioPresentationSink sink) {
        Objects.requireNonNull(sink, "sink");
        if (sink.sampleRate() != sampleRate) {
            throw new IllegalArgumentException(
                    "sink sample rate does not match producer sample rate");
        }
        return sink;
    }

    private void rememberLastReverseFrame(short[] pcm, int readFrames) {
        if (readFrames <= 0) {
            return;
        }
        int sample = (readFrames - 1) * CHANNELS;
        lastReverseLeft = pcm[sample];
        lastReverseRight = pcm[sample + 1];
        hasLastReverseFrame = true;
        reverseFrameOutput = true;
    }

    private void applyReleaseCrossfade(short[] pcm, int stereoFrames) {
        if (releaseCrossfadeRemaining <= 0) {
            return;
        }
        for (int frame = 0;
                frame < stereoFrames && releaseCrossfadeRemaining > 0;
                frame++) {
            int elapsed =
                    crossfadeFrames - releaseCrossfadeRemaining + 1;
            int sample = frame * CHANNELS;
            pcm[sample] = crossfade(
                    lastReverseLeft, pcm[sample], elapsed, crossfadeFrames);
            pcm[sample + 1] = crossfade(
                    lastReverseRight, pcm[sample + 1],
                    elapsed, crossfadeFrames);
            releaseCrossfadeRemaining--;
        }
        if (releaseCrossfadeRemaining == 0) {
            hasLastReverseFrame = false;
        }
    }

    private void cancelReleaseCrossfade() {
        hasLastReverseFrame = false;
        reverseFrameOutput = false;
        releaseCrossfadeRemaining = 0;
    }

    private static short crossfade(
            short from, short to, int elapsed, int total) {
        long mixed = (long) from * (total - elapsed)
                + (long) to * elapsed;
        return (short) (mixed / total);
    }

    private void detach(CaptureHandle capture) {
        assertOwnerThread();
        for (int index = 0; index < captureCount; index++) {
            if (captures[index] != capture) {
                continue;
            }
            int remaining = captureCount - index - 1;
            if (remaining > 0) {
                System.arraycopy(
                        captures, index + 1, captures, index, remaining);
            }
            captures[--captureCount] = null;
            return;
        }
    }

    private void assertOwnerBoundary() {
        assertOwnerThread();
        assertOpen();
        if (presenting) {
            throw new IllegalStateException(
                    "audio presentation boundary is active");
        }
    }

    private void assertOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "audio presentation producer accessed off owner thread");
        }
    }

    private void assertOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "audio presentation producer is closed");
        }
    }

    private final class CaptureHandle
            implements LiveCaptureAudioHandle, AudioPresentationListener {
        private final int captureFrameRate;
        private final int captureMaxStereoFrames;
        private final AudioFrameClock captureClock;
        private final short[] pending;
        private int pendingStereoFrames;
        private boolean fresh;
        private boolean closed;

        private CaptureHandle(
                int captureFrameRate,
                AudioFrameClock.Snapshot producerClockSnapshot) {
            this.captureFrameRate = captureFrameRate;
            captureMaxStereoFrames =
                    (sampleRate + captureFrameRate - 1) / captureFrameRate;
            captureClock = new AudioFrameClock(
                    sampleRate, captureFrameRate);
            captureClock.restoreSnapshot(new AudioFrameClock.Snapshot(
                    producerClockSnapshot.sampleRate(),
                    producerClockSnapshot.frameRate(),
                    0,
                    producerClockSnapshot.remainder()));
            pending = new short[
                    Math.max(maxStereoFrames, captureMaxStereoFrames)
                            * CHANNELS];
        }

        @Override
        public void onPresentationFrame(AudioPresentationFrameView frame) {
            if (closed) {
                return;
            }
            frame.copyTo(pending, 0);
            pendingStereoFrames = frame.stereoFrames();
            fresh = true;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int frameRate() {
            return captureFrameRate;
        }

        @Override
        public int maxStereoFramesPerPacket() {
            return captureMaxStereoFrames;
        }

        @Override
        public int drainPresentationFrame(short[] target) {
            Objects.requireNonNull(target, "target");
            if (closed) {
                throw new IllegalStateException(
                        "audio presentation capture is closed");
            }
            int requiredCapacity =
                    Math.multiplyExact(captureMaxStereoFrames, CHANNELS);
            if (target.length < requiredCapacity) {
                throw new IllegalArgumentException(
                        "target is too small for the maximum presentation packet");
            }
            int requestedFrames = captureClock.samplesForNextFrame();
            int copiedFrames = fresh
                    ? Math.min(requestedFrames, pendingStereoFrames)
                    : 0;
            if (copiedFrames > 0) {
                System.arraycopy(
                        pending, 0, target, 0, copiedFrames * CHANNELS);
            }
            Arrays.fill(
                    target,
                    copiedFrames * CHANNELS,
                    requestedFrames * CHANNELS,
                    (short) 0);
            fresh = false;
            pendingStereoFrames = 0;
            return requestedFrames;
        }

        @Override
        public long totalStereoFrames() {
            return captureClock.totalSamplesProduced();
        }

        @Override
        public AudioFrameClock.Snapshot clockSnapshot() {
            return captureClock.captureSnapshot();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            assertOwnerThread();
            closed = true;
            fresh = false;
            pendingStereoFrames = 0;
            detach(this);
        }
    }
}
