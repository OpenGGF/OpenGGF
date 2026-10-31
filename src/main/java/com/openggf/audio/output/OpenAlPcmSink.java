package com.openggf.audio.output;

import com.openggf.audio.presentation.AudioPresentationFrameView;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTSourceLatency;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Bounded final-PCM speaker sink. The device receives fixed-size stereo
 * buffers and never owns a mixer voice, decoder, or presentation cursor.
 */
public final class OpenAlPcmSink implements AudioPresentationSink {
    public static final int DEVICE_BUFFER_FRAMES = 1_024;
    private static final long WARNING_INTERVAL_NANOS = 1_000_000_000L;
    private static final int OPENAL_BUFFER_COUNT = 8;
    // Match the former streamer: one 21 ms buffer is not enough to absorb
    // ordinary game-loop scheduling jitter without stopping the AL source.
    private static final int DEVICE_QUEUE_TARGET = 3;
    // Keep one FIFO buffer beyond the device queue so regulation starts at its target.
    private static final int DEVICE_PRIME_FRAMES =
            DEVICE_BUFFER_FRAMES * (DEVICE_QUEUE_TARGET + 1);
    // A bounded ledger for source discontinuities still behind audible PCM.
    // Unusually delayed/unavailable cursors fail explicitly instead of growing it.
    private static final int SOURCE_GAP_CAPACITY = 64;

    public interface Device {
        int initialize();

        void enqueue(short[] stereoPcm, int stereoFrames, int sampleRate);

        /** Remaining source frames, excluding the consumed portion of the head buffer. */
        record QueueState(int queuedBuffers, long queuedSourceFrames, boolean playing) { }

        QueueState update();

        default boolean supportsPlaybackRate() { return false; }

        default void setPlaybackRate(double ratio) {
            throw new UnsupportedOperationException("Device cannot regulate playback rate");
        }

        /** Sample frames actually consumed since the most recent flush, or -1 if unavailable. */
        default long consumedStereoFrames() { return -1; }

        /** Transitions from playing to an empty stopped source since the most recent flush. */
        default long underrunCount() { return 0; }

        void flush();

        void pause();

        void resume();

        void close();
    }

    private final Device device;
    private final Consumer<Throwable> failureHandler;
    private final LongSupplier nanoTime;
    private final Consumer<String> warning;
    private final int sampleRate;
    private final SpeakerPacketFifo fifo;
    private final short[] packetScratch;
    private final short[] deviceScratch =
            new short[DEVICE_BUFFER_FRAMES * 2];

    private final QueueDepthServo servo = new QueueDepthServo();
    private long backlogStereoFrames;
    private boolean warned;
    private long lastWarningNanos;
    private boolean paused;
    private boolean failed;
    private boolean closed;
    private long deviceEnqueuedStereoFrames;
    private long consumedSourceGapFrames;
    private long lastConsumedStereoFrames;
    private final long[] sourceGapBoundaries = new long[SOURCE_GAP_CAPACITY];
    private final long[] sourceGapFrames = new long[SOURCE_GAP_CAPACITY];
    private int sourceGapHead, sourceGapCount;

    public OpenAlPcmSink(
            Device device,
            Consumer<Throwable> failureHandler,
            LongSupplier nanoTime,
            Consumer<String> warning) {
        this.device = Objects.requireNonNull(device, "device");
        this.failureHandler =
                Objects.requireNonNull(failureHandler, "failureHandler");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.warning = Objects.requireNonNull(warning, "warning");
        int initializedRate;
        try {
            initializedRate = device.initialize();
            if (device.supportsPlaybackRate()) device.setPlaybackRate(1.0);
        } catch (Throwable failure) {
            closeDeviceAfterFailure(failure);
            throw failure;
        }
        if (initializedRate <= 0) {
            IllegalStateException failure = new IllegalStateException(
                    "OpenAL device returned an invalid sample rate");
            closeDeviceAfterFailure(failure);
            throw failure;
        }
        sampleRate = initializedRate;
        fifo = new SpeakerPacketFifo(sampleRate);
        packetScratch = new short[Math.multiplyExact(sampleRate, 2)];
    }

    public static OpenAlPcmSink openDefault(
            Consumer<Throwable> failureHandler,
            Consumer<String> warning) {
        return new OpenAlPcmSink(new LwjglDevice(false), failureHandler,
                System::nanoTime, warning);
    }

    /**
     * Opens an optional concurrent speaker on its caller's thread without replacing
     * the process-wide gameplay context. The owner must perform all operations,
     * including close, on that thread. Unsupported devices fail before binding a context.
     */
    public static OpenAlPcmSink openIsolatedDefault(
            Consumer<Throwable> failureHandler,
            Consumer<String> warning) {
        return new OpenAlPcmSink(new LwjglDevice(true), failureHandler,
                System::nanoTime, warning);
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public void accept(AudioPresentationFrameView frame) {
        Objects.requireNonNull(frame, "frame");
        if (closed || failed || paused) {
            return;
        }
        int stereoFrames = frame.stereoFrames();
        if (stereoFrames > sampleRate) {
            fail(new IllegalArgumentException(
                    "presentation packet exceeds one second"));
            return;
        }
        frame.copyTo(packetScratch, 0);
        long droppedBefore = fifo.droppedStereoFrames();
        fifo.offer(packetScratch, stereoFrames);
        if (fifo.droppedStereoFrames() != droppedBefore) {
            // A drop removes the oldest not-yet-device PCM. Attach the source-time
            // gap after already queued device packets, rather than jumping the
            // audible cursor while those preceding packets are still sounding.
            if (!recordSourceGap(fifo.droppedStereoFrames() - droppedBefore)) return;
            warnOverrun();
        }
    }

    public void updateDevice() {
        if (closed || failed || paused) {
            return;
        }
        try {
            Device.QueueState queue = device.update();
            int deviceQueuedBuffers = queue.queuedBuffers();
            backlogStereoFrames = fifo.queuedStereoFrames() + queue.queuedSourceFrames();
            if (device.supportsPlaybackRate()) {
                double previous = servo.ratio;
                servo.update(nanoTime.getAsLong(), backlogStereoFrames,
                        queue.playing() && deviceQueuedBuffers > 0);
                if (previous != servo.ratio) device.setPlaybackRate(servo.ratio);
            }
            // Ordinary playback must retire the ledger even if no scene asks
            // for its audible cursor. An unavailable cursor retires nothing.
            if (sourceGapCount > 0) retireSourceGaps(device.consumedStereoFrames());
            int requiredFrames = deviceQueuedBuffers == 0
                    ? DEVICE_PRIME_FRAMES : DEVICE_BUFFER_FRAMES;
            while (deviceQueuedBuffers < DEVICE_QUEUE_TARGET
                    && fifo.queuedStereoFrames() >= requiredFrames) {
                int drained = fifo.drain(
                        deviceScratch, DEVICE_BUFFER_FRAMES);
                device.enqueue(deviceScratch, drained, sampleRate);
                deviceEnqueuedStereoFrames += drained;
                deviceQueuedBuffers++;
                requiredFrames = DEVICE_BUFFER_FRAMES;
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @Override
    public void onReverseBoundary() {
        if (closed || failed) {
            return;
        }
        fifo.flush();
        deviceEnqueuedStereoFrames = 0;
        lastConsumedStereoFrames = 0;
        clearSourceGaps();
        try {
            resetPlaybackRate();
            backlogStereoFrames = 0;
            device.flush();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public void pause() {
        if (!closed && !failed && !paused) {
            paused = true;
            try {
                resetPlaybackRate();
                device.pause();
            } catch (Throwable failure) {
                fail(failure);
            }
        }
    }

    public void resume() {
        if (!closed && !failed && paused) {
            paused = false;
            try {
                resetPlaybackRate();
                device.resume();
            } catch (Throwable failure) {
                fail(failure);
            }
        }
    }

    public boolean isRateRegulated() {
        return !closed && !failed && device.supportsPlaybackRate();
    }

    public double playbackRate() { return servo.ratio; }

    /** Last serviced combined FIFO and device backlog, in source frames. */
    public long backlogStereoFrames() { return backlogStereoFrames; }

    /** Number of excursions to either rate limit, retained across transport resets. */
    public long saturationCount() { return servo.saturations; }

    private void resetPlaybackRate() {
        servo.reset();
        if (device.supportsPlaybackRate()) device.setPlaybackRate(1.0);
    }

    public int queuedStereoFrames() {
        return fifo.queuedStereoFrames();
    }

    public long droppedStereoFrames() {
        return fifo.droppedStereoFrames();
    }

    public long consumedStereoFrames() {
        if (closed || failed) return -1;
        try {
            long consumed = device.consumedStereoFrames();
            if (consumed < 0) return consumed;
            retireSourceGaps(consumed);
            // Resetting pitch at pause can briefly move the latency estimate backward.
            // Retain the last audible observation within this flushed source coordinate.
            lastConsumedStereoFrames = Math.max(lastConsumedStereoFrames, consumed + consumedSourceGapFrames);
            return lastConsumedStereoFrames;
        } catch (Throwable failure) {
            fail(failure);
            return -1;
        }
    }

    private boolean recordSourceGap(long frames) {
        if (sourceGapCount > 0) {
            int last = (sourceGapHead + sourceGapCount - 1) % SOURCE_GAP_CAPACITY;
            if (sourceGapBoundaries[last] == deviceEnqueuedStereoFrames) {
                sourceGapFrames[last] += frames;
                return true;
            }
        }
        if (sourceGapCount == SOURCE_GAP_CAPACITY) {
            fail(new IllegalStateException("OpenAL cursor left 64 source-time gap boundaries unconsumed"));
            return false;
        }
        int next = (sourceGapHead + sourceGapCount) % SOURCE_GAP_CAPACITY;
        sourceGapBoundaries[next] = deviceEnqueuedStereoFrames;
        sourceGapFrames[next] = frames;
        sourceGapCount++;
        return true;
    }

    private void retireSourceGaps(long consumed) {
        if (consumed < 0) return;
        while (sourceGapCount > 0 && sourceGapBoundaries[sourceGapHead] <= consumed) {
            consumedSourceGapFrames += sourceGapFrames[sourceGapHead];
            sourceGapHead = (sourceGapHead + 1) % SOURCE_GAP_CAPACITY;
            sourceGapCount--;
        }
    }

    private void clearSourceGaps() {
        sourceGapHead = 0;
        sourceGapCount = 0;
        consumedSourceGapFrames = 0;
    }

    public long underrunCount() {
        if (closed || failed) return 0;
        try {
            return device.underrunCount();
        } catch (Throwable failure) {
            fail(failure);
            return 0;
        }
    }

    /** AL's mixer offset minus reported mixer-to-DAC latency, in the flushed source coordinate. */
    static long audibleSampleFrame(long completedFrames, long offsetFixed32,
                                   long latencyNanos, int sampleRate, double playbackRatio) {
        double offset = offsetFixed32 / 0x1.0p32;
        double latencyFrames = Math.max(0, latencyNanos) * (sampleRate / 1_000_000_000.0);
        // The slow slew bounds latency-window pitch error to a fraction of a frame;
        // the current ratio therefore suffices without a rate-history ring.
        return Math.max(0, (long) Math.floor(completedFrames + offset - latencyFrames * playbackRatio));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        fifo.flush();
        clearSourceGaps();
        try {
            resetPlaybackRate();
        } finally {
            device.close();
        }
    }

    private void warnOverrun() {
        long now = nanoTime.getAsLong();
        if (!warned || now - lastWarningNanos >= WARNING_INTERVAL_NANOS) {
            warned = true;
            lastWarningNanos = now;
            warning.accept("OpenAL speaker FIFO overrun; dropped oldest PCM");
        }
    }

    private void fail(Throwable failure) {
        if (failed || closed) {
            return;
        }
        failed = true;
        fifo.flush();
        try {
            close();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        failureHandler.accept(failure);
    }

    private void closeDeviceAfterFailure(Throwable primary) {
        try {
            device.close();
        } catch (Throwable cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    /** Host output policy only. Error is seconds of source PCM, never game time. */
    private final class QueueDepthServo {
        // One second rejects packet and host-pump jitter without hiding crystal drift.
        private static final double BACKLOG_FILTER_SECONDS = 1.0;
        // A ten-second critically damped response keeps pitch changes below musical transients.
        private static final double RESPONSE_SECONDS = 10.0;
        // The unfiltered backlog plant is an integrator, so these poles give critical damping.
        private static final double PROPORTIONAL_GAIN = 2.0 / RESPONSE_SECONDS;
        // The same response poles remove a constant producer/device frequency error.
        private static final double INTEGRAL_GAIN = 1.0 / (RESPONSE_SECONDS * RESPONSE_SECONDS);
        // Half a percent covers the display lock band plus ordinary device crystal error.
        private static final double MAX_RATE_TRIM = 0.005;
        // Traverse the full trim in one response time; 100 ms latency then costs at most 0.12 frames at 48 kHz.
        private static final double MAX_RATE_SLEW_PER_SECOND = MAX_RATE_TRIM / RESPONSE_SECONDS;

        private double ratio = 1.0;
        private double filteredError;
        private double integral;
        private long previousNanos;
        private boolean observing;
        private boolean saturated;
        private long saturations;

        private void reset() {
            ratio = 1.0;
            filteredError = 0;
            integral = 0;
            observing = false;
            saturated = false;
        }

        private void update(long now, long backlog, boolean playing) {
            if (!playing || backlog == 0) {
                reset();
                return;
            }
            double error = (backlog - DEVICE_PRIME_FRAMES) / (double) sampleRate;
            if (!observing) {
                previousNanos = now;
                filteredError = error;
                observing = true;
                return;
            }
            double seconds = (now - previousNanos) / 1_000_000_000.0;
            if (seconds <= 0) return;
            previousNanos = now;
            filteredError += -Math.expm1(-seconds / BACKLOG_FILTER_SECONDS) * (error - filteredError);
            double proportional = PROPORTIONAL_GAIN * filteredError;
            double candidateIntegral = integral + INTEGRAL_GAIN * filteredError * seconds;
            double requested = proportional + candidateIntegral;
            double slew = MAX_RATE_SLEW_PER_SECOND * seconds;
            double trim = Math.clamp(Math.clamp(requested, -MAX_RATE_TRIM, MAX_RATE_TRIM),
                    ratio - 1.0 - slew, ratio - 1.0 + slew);
            // Conditional integration prevents windup at both the clamp and slew boundaries.
            if ((requested - trim) * filteredError <= 0) integral = candidateIntegral;
            boolean atLimit = Math.abs(requested) >= MAX_RATE_TRIM;
            if (atLimit && !saturated) saturations++;
            saturated = atLimit;
            ratio = 1.0 + trim;
        }
    }

    private static final class LwjglDevice implements Device {
        private final boolean isolated;
        private long previousThreadContext;
        private ALCapabilities previousThreadCapabilities;
        private boolean threadContextBound;
        private long device;
        private long context;
        private int presentationSource = -1;
        private final int[] buffers = new int[OPENAL_BUFFER_COUNT];
        private final int[] freeBuffers = new int[OPENAL_BUFFER_COUNT];
        private int freeCount;
        private ShortBuffer uploadScratch;
        private boolean initialized;
        private boolean closed;
        private final int[] bufferStereoFrames = new int[OPENAL_BUFFER_COUNT];
        private long completedStereoFrames;
        private boolean sourceLatencySupported;
        private double playbackRatio = 1.0;
        private int outputSampleRate;
        private long underruns;
        private boolean started, stoppedAfterPlaying;

        private LwjglDevice(boolean isolated) { this.isolated = isolated; }

        @Override
        public int initialize() {
            if (initialized) {
                throw new IllegalStateException(
                        "OpenAL device is already initialized");
            }
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                throw new IllegalStateException(
                        "Could not open the default OpenAL device");
            }
            ALCCapabilities capabilities = isolated ? ALC.createCapabilities(device) : null;
            if (isolated && !capabilities.ALC_EXT_thread_local_context) {
                throw new IllegalStateException("Isolated OpenAL output requires ALC_EXT_thread_local_context");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer attributes = stack.ints(
                        ALC10.ALC_FREQUENCY, 48_000, 0);
                context = ALC10.alcCreateContext(device, attributes);
            }
            boolean madeCurrent = false;
            if (context != 0) {
                if (isolated) {
                    previousThreadContext = EXTThreadLocalContext.alcGetThreadContext();
                    if (previousThreadContext != 0) previousThreadCapabilities = AL.getCapabilities();
                    madeCurrent = EXTThreadLocalContext.alcSetThreadContext(context);
                    threadContextBound = madeCurrent;
                } else madeCurrent = ALC10.alcMakeContextCurrent(context);
            }
            if (!madeCurrent) {
                throw new IllegalStateException(
                        "Could not create the OpenAL context");
            }
            if (!isolated) capabilities = ALC.createCapabilities(device);
            // LWJGL detects a thread context and installs thread-local AL capabilities.
            AL.createCapabilities(capabilities);
            sourceLatencySupported = AL.getCapabilities().AL_SOFT_source_latency;
            presentationSource = AL10.alGenSources();
            for (int index = 0; index < buffers.length; index++) {
                buffers[index] = AL10.alGenBuffers();
                freeBuffers[index] = buffers[index];
            }
            freeCount = buffers.length;
            uploadScratch = MemoryUtil.memAllocShort(
                    DEVICE_BUFFER_FRAMES * 2);
            int negotiated = ALC10.alcGetInteger(
                    device, ALC10.ALC_FREQUENCY);
            initialized = true;
            outputSampleRate = negotiated > 0 ? negotiated : 48_000;
            return outputSampleRate;
        }

        @Override
        public void enqueue(
                short[] stereoPcm, int stereoFrames, int sampleRate) {
            reclaimProcessed();
            if (freeCount == 0) {
                throw new IllegalStateException(
                        "OpenAL presentation queue exhausted");
            }
            int buffer = freeBuffers[--freeCount];
            for (int index = 0; index < buffers.length; index++) {
                if (buffers[index] == buffer) bufferStereoFrames[index] = stereoFrames;
            }
            uploadScratch.clear();
            uploadScratch.put(stereoPcm, 0, stereoFrames * 2);
            uploadScratch.flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16,
                    uploadScratch, sampleRate);
            AL10.alSourceQueueBuffers(presentationSource, buffer);
            int state = AL10.alGetSourcei(
                    presentationSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                AL10.alSourcePlay(presentationSource);
            }
            started = true;
            stoppedAfterPlaying = false;
            checkError("enqueue final PCM");
        }

        @Override
        public QueueState update() {
            reclaimProcessed();
            int queued = AL10.alGetSourcei(presentationSource, AL10.AL_BUFFERS_QUEUED);
            int offset = AL10.alGetSourcei(presentationSource, AL11.AL_SAMPLE_OFFSET);
            boolean playing = AL10.alGetSourcei(presentationSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
            long frames = 0;
            for (int count : bufferStereoFrames) frames += count;
            checkError("update final PCM");
            return new QueueState(queued, playing ? Math.max(0, frames - offset) : 0, playing);
        }

        @Override
        public boolean supportsPlaybackRate() { return true; }

        @Override
        public void setPlaybackRate(double ratio) {
            AL10.alSourcef(presentationSource, AL10.AL_PITCH, (float) ratio);
            checkError("regulate final PCM rate");
            playbackRatio = ratio;
        }

        @Override
        public long consumedStereoFrames() {
            reclaimProcessed();
            if (sourceLatencySupported) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    long clock = stack.nmalloc(Long.BYTES, Long.BYTES * 2);
                    SOFTSourceLatency.nalGetSourcei64vSOFT(presentationSource,
                            SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT, clock);
                    checkError("read audible PCM cursor and device latency");
                    return audibleSampleFrame(completedStereoFrames, MemoryUtil.memGetLong(clock),
                            MemoryUtil.memGetLong(clock + Long.BYTES), outputSampleRate, playbackRatio);
                }
            }
            // OpenAL 1.1 fallback: device latency is then covered by the scene's
            // user calibration. Queue latency is still measured, never guessed.
            long offset = AL10.alGetSourcei(presentationSource, AL11.AL_SAMPLE_OFFSET);
            checkError("read audible PCM cursor");
            return completedStereoFrames + Math.max(0, offset);
        }

        @Override
        public long underrunCount() {
            reclaimProcessed();
            checkError("read PCM underrun transitions");
            return underruns;
        }

        @Override
        public void flush() {
            if (!initialized || presentationSource < 0) {
                return;
            }
            AL10.alSourceStop(presentationSource);
            int queued = AL10.alGetSourcei(
                    presentationSource, AL10.AL_BUFFERS_QUEUED);
            while (queued-- > 0) {
                AL10.alSourceUnqueueBuffers(presentationSource);
            }
            System.arraycopy(buffers, 0, freeBuffers, 0, buffers.length);
            freeCount = buffers.length;
            checkError("flush final PCM");
            completedStereoFrames = 0;
            Arrays.fill(bufferStereoFrames, 0);
            underruns = 0;
            started = false;
            stoppedAfterPlaying = false;
        }

        @Override
        public void pause() {
            if (initialized && presentationSource >= 0) {
                AL10.alSourcePause(presentationSource);
            }
        }

        @Override
        public void resume() {
            if (initialized && presentationSource >= 0
                    && AL10.alGetSourcei(presentationSource,
                    AL10.AL_BUFFERS_QUEUED) > 0) {
                AL10.alSourcePlay(presentationSource);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (presentationSource >= 0) {
                AL10.alSourceStop(presentationSource);
                AL10.alDeleteSources(presentationSource);
                presentationSource = -1;
            }
            Arrays.stream(buffers)
                    .filter(buffer -> buffer != 0)
                    .forEach(AL10::alDeleteBuffers);
            if (uploadScratch != null) {
                MemoryUtil.memFree(uploadScratch);
                uploadScratch = null;
            }
            if (context != 0) {
                if (isolated) {
                    if (threadContextBound) {
                        EXTThreadLocalContext.alcSetThreadContext(previousThreadContext);
                        AL.setCurrentThread(previousThreadCapabilities);
                        threadContextBound = false;
                    }
                } else ALC10.alcMakeContextCurrent(0);
                ALC10.alcDestroyContext(context);
                context = 0;
            }
            if (device != 0) {
                ALC10.alcCloseDevice(device);
                device = 0;
            }
            initialized = false;
        }

        private void reclaimProcessed() {
            if (!initialized || presentationSource < 0) {
                return;
            }
            int processed = AL10.alGetSourcei(
                    presentationSource, AL10.AL_BUFFERS_PROCESSED);
            while (processed-- > 0) {
                if (freeCount == freeBuffers.length) {
                    throw new IllegalStateException(
                            "OpenAL free-buffer accounting overflow");
                }
                int buffer = AL10.alSourceUnqueueBuffers(presentationSource);
                for (int index = 0; index < buffers.length; index++) {
                    if (buffers[index] == buffer) {
                        completedStereoFrames += bufferStereoFrames[index];
                        bufferStereoFrames[index] = 0;
                    }
                }
                freeBuffers[freeCount++] = buffer;
            }
            int state = AL10.alGetSourcei(presentationSource, AL10.AL_SOURCE_STATE);
            if (started && state == AL10.AL_STOPPED && !stoppedAfterPlaying) {
                underruns++;
                stoppedAfterPlaying = true;
            }
        }

        private static void checkError(String operation) {
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                throw new IllegalStateException(
                        "OpenAL error during " + operation + ": " + error);
            }
        }
    }
}
