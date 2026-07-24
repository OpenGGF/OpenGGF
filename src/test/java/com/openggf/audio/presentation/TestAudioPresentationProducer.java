package com.openggf.audio.presentation;

import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestAudioPresentationProducer {
    @Test
    void sixtyNtscPacketsAt48000ContainExactly48000StereoFrames() {
        RecordingSink sink = new RecordingSink(48_000, 801);
        AudioPresentationProducer producer = emptyProducer(48_000, 60, sink);

        for (int frame = 0; frame < 60; frame++) {
            producer.present(frame, PresentationMode.FORWARD);
        }

        assertEquals(48_000, sink.totalStereoFrames);
        assertEquals(60, sink.acceptCount);
    }

    @Test
    void fiftyPalPacketsAt48000ContainExactly48000StereoFrames() {
        RecordingSink sink = new RecordingSink(48_000, 960);
        AudioPresentationProducer producer = emptyProducer(48_000, 50, sink);

        for (int frame = 0; frame < 50; frame++) {
            producer.present(frame, PresentationMode.FORWARD);
        }

        assertEquals(48_000, sink.totalStereoFrames);
        assertEquals(50, sink.acceptCount);
    }

    @Test
    void forwardAppliesCommandsMixesAppendsHistoryAndBroadcastsOnce() {
        Fixture fixture = fixture(8, 2, constantStereo("forward", 8, (short) 123, (short) -321));
        fixture.submitTone();
        fixture.producer.setHistoryArmed(true);

        fixture.producer.present(7, PresentationMode.FORWARD);

        assertEquals(1, fixture.sink.acceptCount);
        assertEquals(7, fixture.sink.lastPresentationFrame);
        assertEquals(PresentationMode.FORWARD, fixture.sink.lastMode);
        assertArrayEquals(repeatStereo(4, (short) 123, (short) -321), fixture.sink.lastPacket(4));
        assertEquals(4L << 32, sampleCursor(fixture.registry));

        fixture.producer.beginReverse(1.0);
        fixture.producer.present(8, PresentationMode.REVERSE);
        assertArrayEquals(
                repeatStereo(4, (short) 123, (short) -321),
                fixture.sink.lastPacket(4),
                "forward final PCM must be available from producer-owned history");
    }

    @Test
    void silentBroadcastsFreshClockSizedZerosWithoutAdvancingVoiceOrHistory() {
        Fixture fixture = fixture(8, 2, constantStereo("silent", 8, (short) 100, (short) 200));
        fixture.submitTone();

        fixture.producer.present(0, PresentationMode.SILENT);

        assertEquals(1, fixture.sink.acceptCount);
        assertEquals(PresentationMode.SILENT, fixture.sink.lastMode);
        assertArrayEquals(new short[8], fixture.sink.lastPacket(4));
        assertEquals(0L, sampleCursor(fixture.registry));

        fixture.producer.beginReverse(1.0);
        fixture.producer.present(1, PresentationMode.REVERSE);
        assertArrayEquals(new short[8], fixture.sink.lastPacket(4),
                "silent presentation must not enter forward history");
        assertEquals(0L, sampleCursor(fixture.registry));
    }

    @Test
    void reverseBroadcastsHistoryWithoutAdvancingVoiceOrAppendingHistory() {
        Fixture fixture = fixture(4, 2, rampStereo("reverse", 8));
        fixture.submitTone();
        fixture.producer.setHistoryArmed(true);
        fixture.producer.present(0, PresentationMode.FORWARD);
        long cursorAfterForward = sampleCursor(fixture.registry);

        fixture.producer.beginReverse(1.0);
        fixture.producer.present(1, PresentationMode.REVERSE);

        assertEquals(cursorAfterForward, sampleCursor(fixture.registry));
        assertEquals(PresentationMode.REVERSE, fixture.sink.lastMode);
        assertArrayEquals(new short[] {1, 101, 0, 100}, fixture.sink.lastPacket(2));
        assertEquals(2, fixture.sink.acceptCount);
    }

    @Test
    void sinkAndTwoCaptureHandlesReceiveEqualCopiesOfOneProducerPacket() {
        Fixture fixture = fixture(8, 2, rampStereo("copies", 8));
        LiveCaptureAudioHandle first = fixture.producer.attachCapture(2);
        LiveCaptureAudioHandle second = fixture.producer.attachCapture(2);
        fixture.submitTone();

        fixture.producer.present(0, PresentationMode.FORWARD);
        short[] firstPacket = new short[first.maxStereoFramesPerPacket() * 2];
        short[] secondPacket = new short[second.maxStereoFramesPerPacket() * 2];
        int firstFrames = first.drainPresentationFrame(firstPacket);
        int secondFrames = second.drainPresentationFrame(secondPacket);

        assertEquals(4, firstFrames);
        assertEquals(firstFrames, secondFrames);
        assertArrayEquals(fixture.sink.lastPacket(firstFrames),
                Arrays.copyOf(firstPacket, firstFrames * 2));
        assertArrayEquals(firstPacket, secondPacket);
    }

    @Test
    void captureDrainNeverAdvancesProducerAndSecondDrainReturnsFreshSilence() {
        Fixture fixture = fixture(4, 2, rampStereo("drain", 8));
        LiveCaptureAudioHandle capture = fixture.producer.attachCapture(2);
        fixture.submitTone();
        fixture.producer.present(0, PresentationMode.FORWARD);
        long voiceCursor = sampleCursor(fixture.registry);
        int sinkCount = fixture.sink.acceptCount;

        short[] first = new short[capture.maxStereoFramesPerPacket() * 2];
        short[] second = new short[capture.maxStereoFramesPerPacket() * 2];
        assertEquals(2, capture.drainPresentationFrame(first));
        assertEquals(2, capture.drainPresentationFrame(second));

        assertArrayEquals(new short[] {0, 100, 1, 101}, first);
        assertArrayEquals(new short[4], second);
        assertEquals(voiceCursor, sampleCursor(fixture.registry));
        assertEquals(sinkCount, fixture.sink.acceptCount);
    }

    @Test
    void captureClockSnapshotPreservesFractionalPhaseExactly() {
        AudioPresentationProducer producer =
                emptyProducer(5, 2, new NoDeviceAudioSink(5));
        LiveCaptureAudioHandle capture = producer.attachCapture(2);
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];

        producer.present(0, PresentationMode.SILENT);
        assertEquals(2, capture.drainPresentationFrame(packet));
        assertEquals(2, capture.totalStereoFrames());
        assertEquals(new AudioFrameClock.Snapshot(5, 2, 2, 1),
                capture.clockSnapshot());

        producer.present(1, PresentationMode.SILENT);
        assertEquals(3, capture.drainPresentationFrame(packet));
        assertEquals(5, capture.totalStereoFrames());
        assertEquals(new AudioFrameClock.Snapshot(5, 2, 5, 0),
                capture.clockSnapshot());
    }

    @Test
    void lateCaptureAttachAlignsWithFractionalForwardPackets() {
        Fixture fixture = fixture(5, 2, rampStereo("late-forward", 16));
        fixture.submitTone();
        fixture.producer.present(0, PresentationMode.FORWARD);
        assertArrayEquals(new short[] {0, 100, 1, 101},
                fixture.sink.lastPacket(2));

        LiveCaptureAudioHandle capture = fixture.producer.attachCapture(2);
        assertEquals(new AudioFrameClock.Snapshot(5, 2, 0, 1),
                capture.clockSnapshot());
        short[] captured =
                new short[capture.maxStereoFramesPerPacket() * 2];

        fixture.producer.present(1, PresentationMode.FORWARD);
        assertEquals(3, capture.drainPresentationFrame(captured));
        assertArrayEquals(fixture.sink.lastPacket(3), captured);
        assertEquals(new AudioFrameClock.Snapshot(5, 2, 3, 0),
                capture.clockSnapshot());

        fixture.producer.present(2, PresentationMode.FORWARD);
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(fixture.sink.lastPacket(2),
                Arrays.copyOf(captured, 4));
        assertEquals(new AudioFrameClock.Snapshot(5, 2, 5, 1),
                capture.clockSnapshot());
    }

    @Test
    void everyPresentedFrameReusesTheSameSynchronousViewObject() {
        IdentitySink sink = new IdentitySink(48_000);
        AudioPresentationProducer producer = emptyProducer(48_000, 60, sink);

        producer.present(0, PresentationMode.FORWARD);
        producer.present(1, PresentationMode.SILENT);
        producer.present(2, PresentationMode.FORWARD);

        assertEquals(3, sink.acceptCount);
        assertSame(sink.first, sink.second);
        assertSame(sink.first, sink.third);
        assertEquals(2, sink.lastObservedPresentationFrame,
                "accept must complete synchronously inside present");
    }

    @Test
    void retainedConsumersMustCopyBecauseTheViewMutatesOnTheNextPresent() {
        RetainingSink sink = new RetainingSink(4);
        Fixture fixture = fixture(4, 2, rampStereo("mutable-view", 8), sink);
        fixture.submitTone();

        fixture.producer.present(0, PresentationMode.FORWARD);
        AudioPresentationFrameView retained = sink.retained;
        short firstSample = retained.sampleAt(0, 1);
        fixture.producer.present(1, PresentationMode.SILENT);

        assertEquals(100, firstSample);
        assertEquals(0, retained.sampleAt(0, 0));
        assertEquals(PresentationMode.SILENT, retained.mode());
    }

    @Test
    void warmedProducerAllocatesNoFramePacketOrConsumerArray() {
        AudioPresentationProducer producer =
                emptyProducer(48_000, 60, new NoDeviceAudioSink(48_000));
        producer.setHistoryArmed(true);
        producer.attachCapture(60);
        producer.attachCapture(60);
        for (int frame = 0; frame < 2_000; frame++) {
            producer.present(frame, PresentationMode.FORWARD);
        }

        java.lang.management.ThreadMXBean rawBean = ManagementFactory.getThreadMXBean();
        if (!(rawBean instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int frame = 0; frame < 2_000; frame++) {
            producer.present(frame, PresentationMode.FORWARD);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(0, allocated,
                "warmed present must reuse its frame view, PCM, and consumer storage");
    }

    @Test
    void frameViewExposesSampleAtAndCopyToButNoRawSampleArray() {
        List<String> publicShortArrayMethods = new ArrayList<>();
        for (Method method : AudioPresentationFrameView.class.getMethods()) {
            if (method.getReturnType() == short[].class) {
                publicShortArrayMethods.add(method.getName());
            }
        }

        assertTrue(Arrays.stream(AudioPresentationFrameView.class.getMethods())
                .anyMatch(method -> method.getName().equals("sampleAt")));
        assertTrue(Arrays.stream(AudioPresentationFrameView.class.getMethods())
                .anyMatch(method -> method.getName().equals("copyTo")));
        assertTrue(publicShortArrayMethods.isEmpty(),
                "the synchronous view must not expose its mutable backing array");
    }

    @Test
    void sampleAtCopyToSpeakerAndTwoCaptureCopiesAreBitEqual() {
        InspectingSink sink = new InspectingSink(8);
        Fixture fixture = fixture(8, 2, rampStereo("bit-equal", 8), sink);
        LiveCaptureAudioHandle first = fixture.producer.attachCapture(2);
        LiveCaptureAudioHandle second = fixture.producer.attachCapture(2);
        fixture.submitTone();

        fixture.producer.present(0, PresentationMode.FORWARD);
        short[] firstPcm = new short[8];
        short[] secondPcm = new short[8];
        int frames = first.drainPresentationFrame(firstPcm);
        assertEquals(frames, second.drainPresentationFrame(secondPcm));

        assertArrayEquals(sink.copied, Arrays.copyOf(firstPcm, frames * 2));
        assertArrayEquals(firstPcm, secondPcm);
        assertArrayEquals(sink.readBySampleAt, sink.copied);
    }

    @Test
    void silentAppliesStructuralSampleAndScalarCommandsInOriginalOrder() {
        DecodedPcm sampleA = constantStereo(
                "sample-A", 8, (short) 100, (short) 200);
        DecodedPcm musicB = constantStereo(
                "music-B", 8, (short) 300, (short) 400);
        List<String> resolutionOrder = new ArrayList<>();
        MultiPcmFixture fixture = multiPcmFixture(
                8, 2, sampleA, musicB, resolutionOrder);
        submit(fixture, StartSampleSfx.fromVoice(
                SampleBackedVoice.oneShot(
                        1, 1, sampleA, 8, 1.0f, 1.0f)));
        submit(fixture, new SetSpeedMultiplier(2));
        submit(fixture, new ReplaceMusic(MusicVoiceEntry.fromVoice(
                2, AudioSourceDescriptor.baseMusic(7),
                SampleBackedVoice.loopingMusic(2, musicB, 8, 1.0f))));
        submit(fixture, new StopAllSfx());

        fixture.producer.present(0, PresentationMode.SILENT);

        assertEquals(List.of("sample-A", "music-B"), resolutionOrder);
        assertEquals(List.of(
                        "StartSampleSfx",
                        "SetSpeedMultiplier",
                        "ReplaceMusic",
                        "StopAllSfx"),
                fixture.applicationOrder);
        AudioPresentationSnapshot snapshot = fixture.registry.snapshot();
        assertEquals(2, snapshot.speedMultiplier());
        assertEquals(2L, snapshot.activeMusic().voiceId());
        assertEquals(1, fixture.registry.orderedVoiceCount());
        assertEquals(2, fixture.registry.orderedVoiceAt(0).voiceId());
        assertArrayEquals(new short[8], fixture.sink.lastPacket(4));
    }

    @Test
    void silentStartThenStopLeavesNoVoiceWithoutRenderingEitherCommand() {
        DecodedPcm sampleA = constantStereo(
                "sample-A", 8, (short) 100, (short) 200);
        List<String> resolutionOrder = new ArrayList<>();
        MultiPcmFixture fixture = multiPcmFixture(
                8, 2, sampleA, sampleA, resolutionOrder);
        submit(fixture, StartSampleSfx.fromVoice(
                SampleBackedVoice.oneShot(
                        1, 1, sampleA, 8, 1.0f, 1.0f)));
        submit(fixture, new StopAllSfx());

        fixture.producer.present(0, PresentationMode.SILENT);

        assertEquals(List.of("sample-A"), resolutionOrder);
        assertEquals(0, fixture.registry.orderedVoiceCount());
        assertArrayEquals(new short[8], fixture.sink.lastPacket(4));
    }

    @Test
    void silentSpeedChangeThenMusicReplacementPreservesDependencyOrder() {
        DecodedPcm sampleA = constantStereo(
                "unused-A", 8, (short) 100, (short) 200);
        DecodedPcm musicB = constantStereo(
                "music-B", 8, (short) 300, (short) 400);
        List<String> resolutionOrder = new ArrayList<>();
        MultiPcmFixture fixture = multiPcmFixture(
                8, 2, sampleA, musicB, resolutionOrder);
        submit(fixture, new SetSpeedMultiplier(2));
        submit(fixture, new ReplaceMusic(MusicVoiceEntry.fromVoice(
                2, AudioSourceDescriptor.baseMusic(7),
                SampleBackedVoice.loopingMusic(2, musicB, 8, 1.0f))));

        fixture.producer.present(0, PresentationMode.SILENT);

        AudioPresentationSnapshot snapshot = fixture.registry.snapshot();
        assertEquals(List.of("music-B"), resolutionOrder);
        assertEquals(2, snapshot.speedMultiplier());
        assertEquals(2L, snapshot.activeMusic().voiceId());
        PresentationVoiceSnapshot.Sample musicSnapshot =
                (PresentationVoiceSnapshot.Sample)
                        fixture.registry.orderedVoiceAt(0).snapshot();
        assertEquals(0, musicSnapshot.sourcePositionQ32(),
                "silent command application must not render replacement music");
    }

    @Test
    void offOwnerCaptureCloseFailureDoesNotPoisonTheAttachedHandle()
            throws InterruptedException {
        Fixture fixture = fixture(4, 2, rampStereo("owner-close", 8));
        LiveCaptureAudioHandle capture = fixture.producer.attachCapture(2);
        fixture.submitTone();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try {
                capture.close();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        closer.start();
        closer.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        fixture.producer.present(0, PresentationMode.FORWARD);
        short[] packet = new short[4];

        assertEquals(2, capture.drainPresentationFrame(packet),
                "a rejected off-owner close must leave the handle attached and usable");
        assertArrayEquals(new short[] {0, 100, 1, 101}, packet);
        capture.close();
    }

    private static AudioPresentationProducer emptyProducer(
            int sampleRate, int frameRate, AudioPresentationSink sink) {
        int maxFrames = (sampleRate + frameRate - 1) / frameRate;
        AudioVoiceRegistry registry = new AudioVoiceRegistry();
        return new AudioPresentationProducer(
                sampleRate, frameRate, sampleRate, 4, registry,
                new AudioPresentationCommandQueue(registry::isRendering),
                new AudioPresentationMixer(maxFrames, registry::onVoiceFailure),
                sink);
    }

    private static Fixture fixture(
            int sampleRate, int frameRate, DecodedPcm pcm) {
        return fixture(sampleRate, frameRate, pcm,
                new RecordingSink(sampleRate,
                        (sampleRate + frameRate - 1) / frameRate));
    }

    private static Fixture fixture(
            int sampleRate, int frameRate, DecodedPcm pcm,
            AudioPresentationSink sink) {
        AudioPresentationDependencyResolver resolver =
                new AudioPresentationDependencyResolver() {
                    @Override
                    public DecodedPcm resolvePcm(String assetId) {
                        assertEquals(pcm.assetId(), assetId);
                        return pcm;
                    }

                    @Override
                    public SmpsCompositeVoice recreateSmps(
                            PresentationVoiceSnapshot.Smps snapshot) {
                        throw new AssertionError("no SMPS voice expected");
                    }
                };
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                noSmps(), resolver,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()),
                warning -> {
                    throw new AssertionError(warning);
                });
        int maxFrames = (sampleRate + frameRate - 1) / frameRate;
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        AudioPresentationProducer producer = new AudioPresentationProducer(
                sampleRate, frameRate, sampleRate, 2, registry, commands,
                new AudioPresentationMixer(maxFrames, registry::onVoiceFailure),
                sink);
        return new Fixture(registry, commands, producer,
                sink instanceof RecordingSink recording ? recording : null, pcm);
    }

    private static SmpsSfxInstantiation noSmps() {
        return new SmpsSfxInstantiation() {
            @Override
            public com.openggf.audio.smps.SmpsSequencer instantiateCached(
                    ResolvedSmpsSfxSource source,
                    com.openggf.audio.driver.SmpsDriver currentOwner) {
                throw new AssertionError("no SMPS SFX expected");
            }

            @Override
            public SmpsCompositeVoice instantiateStandaloneCached(
                    ResolvedSmpsSfxSource source) {
                throw new AssertionError("no SMPS SFX expected");
            }
        };
    }

    private static MultiPcmFixture multiPcmFixture(
            int sampleRate,
            int frameRate,
            DecodedPcm first,
            DecodedPcm second,
            List<String> resolutionOrder) {
        AudioPresentationDependencyResolver resolver =
                new AudioPresentationDependencyResolver() {
                    @Override
                    public DecodedPcm resolvePcm(String assetId) {
                        resolutionOrder.add(assetId);
                        if (assetId.equals(first.assetId())) {
                            return first;
                        }
                        if (assetId.equals(second.assetId())) {
                            return second;
                        }
                        throw new AssertionError("unexpected PCM " + assetId);
                    }

                    @Override
                    public SmpsCompositeVoice recreateSmps(
                            PresentationVoiceSnapshot.Smps snapshot) {
                        throw new AssertionError("no SMPS voice expected");
                    }
                };
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                noSmps(), resolver,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()),
                warning -> {
                    throw new AssertionError(warning);
                });
        AudioPresentationCommandQueue commands =
                new AudioPresentationCommandQueue(registry::isRendering);
        RecordingSink sink = new RecordingSink(
                sampleRate, (sampleRate + frameRate - 1) / frameRate);
        List<String> applicationOrder = new ArrayList<>();
        AudioPresentationProducer producer = new AudioPresentationProducer(
                sampleRate, frameRate, sampleRate, 2, registry, commands,
                new AudioPresentationMixer(
                        (sampleRate + frameRate - 1) / frameRate,
                        registry::onVoiceFailure),
                sink,
                command -> {
                    applicationOrder.add(command.getClass().getSimpleName());
                    registry.apply(command);
                });
        return new MultiPcmFixture(
                registry, commands, producer, sink, applicationOrder);
    }

    private static void submit(
            MultiPcmFixture fixture, AudioPresentationCommand command) {
        fixture.commands.submit(
                command, () -> true, fixture.registry::apply);
    }

    private static long sampleCursor(AudioVoiceRegistry registry) {
        PresentationVoiceSnapshot.Sample snapshot =
                (PresentationVoiceSnapshot.Sample) registry.orderedVoiceAt(0).snapshot();
        return snapshot.sourcePositionQ32();
    }

    private static DecodedPcm constantStereo(
            String id, int frames, short left, short right) {
        return new DecodedPcm(id, 2, 8, repeatStereo(frames, left, right));
    }

    private static DecodedPcm rampStereo(String id, int frames) {
        short[] samples = new short[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            samples[frame * 2] = (short) frame;
            samples[frame * 2 + 1] = (short) (frame + 100);
        }
        return new DecodedPcm(id, 2, 4, samples);
    }

    private static short[] repeatStereo(
            int frames, short left, short right) {
        short[] samples = new short[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            samples[frame * 2] = left;
            samples[frame * 2 + 1] = right;
        }
        return samples;
    }

    private record Fixture(
            AudioVoiceRegistry registry,
            AudioPresentationCommandQueue commands,
            AudioPresentationProducer producer,
            RecordingSink sink,
            DecodedPcm pcm) {
        private void submitTone() {
            SampleBackedVoice voice = SampleBackedVoice.oneShot(
                    1, 1, pcm, pcm.sampleRate(), 1.0f, 1.0f);
            commands.submit(StartSampleSfx.fromVoice(voice), () -> true, registry::apply);
        }
    }

    private record MultiPcmFixture(
            AudioVoiceRegistry registry,
            AudioPresentationCommandQueue commands,
            AudioPresentationProducer producer,
            RecordingSink sink,
            List<String> applicationOrder) {
    }

    private static class RecordingSink implements AudioPresentationSink {
        private final int sampleRate;
        private final short[] copy;
        private int copiedFrames;
        private int acceptCount;
        private long totalStereoFrames;
        private long lastPresentationFrame;
        private PresentationMode lastMode;

        private RecordingSink(int sampleRate, int maxFrames) {
            this.sampleRate = sampleRate;
            copy = new short[maxFrames * 2];
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public void accept(AudioPresentationFrameView frame) {
            acceptCount++;
            totalStereoFrames += frame.stereoFrames();
            lastPresentationFrame = frame.presentationFrame();
            lastMode = frame.mode();
            copiedFrames = frame.stereoFrames();
            frame.copyTo(copy, 0);
        }

        private short[] lastPacket(int frames) {
            assertEquals(frames, copiedFrames);
            return Arrays.copyOf(copy, frames * 2);
        }

        @Override
        public void onReverseBoundary() {
        }

        @Override
        public void close() {
        }
    }

    private static final class IdentitySink implements AudioPresentationSink {
        private final int sampleRate;
        private AudioPresentationFrameView first;
        private AudioPresentationFrameView second;
        private AudioPresentationFrameView third;
        private int acceptCount;
        private long lastObservedPresentationFrame;

        private IdentitySink(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public void accept(AudioPresentationFrameView frame) {
            if (acceptCount == 0) {
                first = frame;
            } else if (acceptCount == 1) {
                second = frame;
            } else {
                third = frame;
            }
            acceptCount++;
            lastObservedPresentationFrame = frame.presentationFrame();
        }

        @Override public void onReverseBoundary() {}
        @Override public void close() {}
    }

    private static final class RetainingSink implements AudioPresentationSink {
        private final int sampleRate;
        private AudioPresentationFrameView retained;

        private RetainingSink(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        @Override public int sampleRate() { return sampleRate; }
        @Override public void accept(AudioPresentationFrameView frame) { retained = frame; }
        @Override public void onReverseBoundary() {}
        @Override public void close() {}
    }

    private static final class InspectingSink implements AudioPresentationSink {
        private final int sampleRate;
        private short[] copied;
        private short[] readBySampleAt;

        private InspectingSink(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        @Override public int sampleRate() { return sampleRate; }

        @Override
        public void accept(AudioPresentationFrameView frame) {
            copied = new short[frame.stereoFrames() * 2];
            readBySampleAt = new short[copied.length];
            frame.copyTo(copied, 0);
            for (int stereoFrame = 0; stereoFrame < frame.stereoFrames(); stereoFrame++) {
                readBySampleAt[stereoFrame * 2] = frame.sampleAt(stereoFrame, 0);
                readBySampleAt[stereoFrame * 2 + 1] = frame.sampleAt(stereoFrame, 1);
            }
        }

        @Override public void onReverseBoundary() {}
        @Override public void close() {}
    }
}
