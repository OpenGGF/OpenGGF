package com.openggf.capture;

import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration witnesses, not independent-render acceptance tests. Drive the existing
 * production capture and audio boundaries at deliberately different call rates.
 * Only pixels and the encoder endpoint are substitutes; no Engine scheduler,
 * gameplay, ROM synthesis, wall-clock sleep or GL context is involved.
 *
 * The mismatch assertions describe the current render-call/CFR contract. Replace
 * them when the shared output-time owner is selected; do not preserve that loss
 * as the desired independent-render behavior. These witnesses also apply with
 * the separately reviewed PCM FIFO: repacketization cannot add output pictures.
 */
class TestLiveCaptureCadenceBoundary {
    private static final long SECOND = 1_000_000_000L;
    private static final int SAMPLE_RATE = 1001; // expose rational packet phase

    @ParameterizedTest
    @CsvSource({"50,30", "50,50", "50,60", "50,120", "50,144",
            "60,30", "60,50", "60,60", "60,120", "60,144"})
    void renderCallsStillOwnCfrDurationDespiteFixedRegionProduction(int regionHz, int renderHz)
            throws Exception {
        long[] pictures = new long[renderHz];
        for (int index = 0; index < renderHz; index++) {
            pictures[index] = ((index + 1L) * SECOND + renderHz - 1) / renderHz;
        }
        assertSchedule(regionHz, pictures);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 60})
    void irregularAndStalledPresentationsDoNotAcquireMissingPictures(int regionHz) throws Exception {
        assertSchedule(regionHz, new long[] {1_000_000, 18_000_000, 23_000_000,
                50_000_000, 55_000_000, 320_000_000, 321_000_000, SECOND});
        assertSchedule(regionHz, new long[] {20_000_000, 40_000_000,
                740_000_000, 760_000_000, SECOND});
    }

    private static void assertSchedule(int regionHz, long[] deadlines) throws Exception {
        try (Harness h = new Harness(regionHz, true)) {
            h.start();
            List<Integer> expectedPictures = new ArrayList<>();
            int ticks = 0;
            for (long deadline : deadlines) {
                // Independent test admission, not a replacement production scheduler.
                int due = (int) (deadline * regionHz / SECOND);
                while (ticks < due) h.produce(++ticks, PresentationMode.FORWARD);
                h.picture = ticks;
                expectedPictures.add(ticks);
                int sourceBefore = h.sourceSamples;
                h.present();
                assertEquals(sourceBefore, h.sourceSamples, "capture must never synthesize");
            }
            h.stop();
            List<CapturedFrame> frames = h.encoder().frames;
            assertEquals(regionHz, ticks);
            assertEquals(SAMPLE_RATE, h.speakerSamples.size());
            assertEquals(expectedPictures, frames.stream().map(Harness::pictureOf).toList(),
                    "the selected picture is the supplied final viewport, including repeats");
            assertEquals(deadlines.length, frames.size(), "stop flushes only requested pictures");
            assertOutputClock(frames, regionHz, 0);
            assertEquals(regionHz, h.encoder().fps);
            assertEquals(SAMPLE_RATE, h.encoder().sampleRate);
            List<String> command = FfmpegEncoder.phase1Command("ffmpeg", Path.of("out.mkv"),
                    1, 1, h.encoder().fps, 1);
            assertEquals(Integer.toString(regionHz), command.get(command.indexOf("-r") + 1));
            if (deadlines.length == regionHz) {
                assertEquals(h.speakerSamples, pcm(frames), "stock aligned PCM remains exact");
            } else {
                assertNotEquals(SAMPLE_RATE, frames.stream().mapToInt(CapturedFrame::sampleCount).sum(),
                        "CFR output duration follows capture calls, not the one elapsed second");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pauseStepAndReversePicturesKeepMonotonicOutputAndExactPublishedPcm(boolean deferred)
            throws Exception {
        try (Harness h = new Harness(60, deferred)) {
            h.start();
            h.produce(1, PresentationMode.FORWARD);
            h.picture = 1;
            h.present();
            h.produce(2, PresentationMode.FORWARD);
            h.picture = 2;
            h.present();
            int forwardSamples = h.sourceSamples;
            h.produce(2, PresentationMode.SILENT); // existing user/focus/modal pause policy
            h.present();
            h.produce(3, PresentationMode.SILENT); // existing frame-step audio policy
            h.picture = 3;
            h.present();
            h.producer.beginReverse(1.0);
            h.produce(2, PresentationMode.REVERSE);
            h.picture = 2; // selected restored viewport; capture never restores gameplay
            h.present();
            h.stop();
            assertEquals(forwardSamples, h.sourceSamples, "silence/reverse/stop do not synthesize");
            List<CapturedFrame> frames = h.encoder().frames;
            assertEquals(List.of(1, 2, 2, 3, 2), frames.stream().map(Harness::pictureOf).toList());
            assertOutputClock(frames, 60, 0);
            assertEquals(h.speakerSamples, pcm(frames), "forward, silence and reverse keep audible order");
            assertTrue(pcm(frames.subList(2, 4)).stream().allMatch(value -> value == 0));
            assertTrue(pcm(frames.subList(4, 5)).stream().anyMatch(value -> value != 0));
        }
    }

    @Test
    void stopFlushesPendingPictureAndRestartUsesNewVideoOriginWithCurrentAudioPhase() throws Exception {
        try (Harness h = new Harness(60, true)) {
            h.start();
            h.produce(1, PresentationMode.FORWARD);
            h.picture = 11;
            h.present();
            assertTrue(h.encoder().frames.isEmpty(), "the first PBO picture is pending");
            h.picture = 99; // a later framebuffer is not a capture request
            h.stop();
            assertEquals(List.of(11), h.encoder().frames.stream().map(Harness::pictureOf).toList());
            assertOutputClock(h.encoder().frames, 60, 0);

            h.start();
            h.produce(2, PresentationMode.FORWARD);
            h.picture = 22;
            h.present();
            h.stop();
            assertEquals(2, h.encoders.size());
            assertEquals(List.of(22), h.encoder().frames.stream().map(Harness::pictureOf).toList());
            assertOutputClock(h.encoder().frames, 60, SAMPLE_RATE % 60);
            assertEquals(h.speakerSamples, pcm(h.encoders.stream().flatMap(e -> e.frames.stream()).toList()));
        }
    }

    private static void assertOutputClock(List<CapturedFrame> frames, int fps, int remainder) {
        for (int index = 0; index < frames.size(); index++) {
            CapturedFrame frame = frames.get(index);
            assertEquals(index, frame.frameIndex(), "video ordinal is independent of the picture's tick");
            assertEquals((SAMPLE_RATE + remainder) / fps, frame.sampleCount());
            remainder = (SAMPLE_RATE + remainder) % fps;
        }
    }

    private static List<Short> pcm(List<CapturedFrame> frames) {
        List<Short> samples = new ArrayList<>();
        for (CapturedFrame frame : frames) {
            for (int index = 0; index < frame.sampleCount(); index++) {
                assertEquals(-frame.pcm()[index * 2], frame.pcm()[index * 2 + 1]);
                samples.add(frame.pcm()[index * 2]);
            }
        }
        return samples;
    }

    private static final class Harness implements AutoCloseable {
        final int fps;
        final List<Short> speakerSamples = new ArrayList<>();
        final List<CollectingEncoder> encoders = new ArrayList<>();
        final AudioPresentationProducer producer;
        final java.util.concurrent.ExecutorService finalizer = Executors.newSingleThreadExecutor();
        final LiveCaptureController controller;
        final LiveCapturePresentationCoordinator presentation;
        final CaptureViewport viewport = new CaptureViewport(0, 0, 1, 1);
        int picture;
        int sourceSamples;

        Harness(int fps, boolean deferred) {
            this.fps = fps;
            AudioVoiceRegistry voices = new AudioVoiceRegistry();
            producer = new AudioPresentationProducer(SAMPLE_RATE, fps, SAMPLE_RATE, 0, voices,
                    new AudioPresentationCommandQueue(voices::isRendering),
                    new AudioPresentationMixer((SAMPLE_RATE + fps - 1) / fps, voices::onVoiceFailure),
                    new AudioPresentationSink() {
                        public int sampleRate() { return SAMPLE_RATE; }
                        public void accept(AudioPresentationFrameView frame) {
                            for (int index = 0; index < frame.stereoFrames(); index++) {
                                speakerSamples.add(frame.sampleAt(index, 0));
                                assertEquals(-frame.sampleAt(index, 0), frame.sampleAt(index, 1));
                            }
                        }
                        public void onReverseBoundary() { }
                        public void close() { }
                    });
            producer.setHistoryArmed(true);
            producer.setScenePcmSource((target, frames) -> {
                for (int index = 0; index < frames; index++) {
                    target[index * 2] = (short) ++sourceSamples;
                    target[index * 2 + 1] = (short) -sourceSamples;
                }
            });
            controller = new LiveCaptureController(new LiveCaptureController.Dependencies(
                    producer::attachCapture, () -> SAMPLE_RATE,
                    ignored -> new VideoFrameGrabber() {
                        final ArrayDeque<byte[]> pending = new ArrayDeque<>();
                        public int width() { return 1; }
                        public int height() { return 1; }
                        public boolean deferredReadback() { return deferred; }
                        public byte[] grab() { return ByteBuffer.allocate(4).putInt(picture).array(); }
                        public void beginReadback() { pending.add(grab()); }
                        public void grabInto(byte[] target) {
                            byte[] pixels = deferred ? pending.remove() : grab();
                            System.arraycopy(pixels, 0, target, 0, pixels.length);
                        }
                    },
                    (ignored, rate) -> {
                        CollectingEncoder encoder = new CollectingEncoder();
                        encoders.add(encoder);
                        return new CaptureRecorder(encoder, BackpressurePolicy.BLOCK, 2,
                                Path.of("target"), "cadence-boundary", "test");
                    }, finalizer, Duration.ofSeconds(5)));
            presentation = new LiveCapturePresentationCoordinator(controller);
        }

        void start() {
            controller.start(viewport, fps);
            assertEquals(LiveCaptureController.State.ACTIVE, controller.state());
        }
        void produce(int tick, PresentationMode mode) { producer.present(tick, mode); }
        void present() { presentation.present(viewport, () -> { }, () -> { }); }
        void stop() throws Exception {
            controller.requestStop(LiveCaptureController.StopReason.USER);
            finalizer.submit(() -> { }).get(5, TimeUnit.SECONDS); // ordered completion, no polling
            assertEquals(LiveCaptureController.State.INACTIVE, controller.state());
            assertNull(controller.lastFailure());
        }
        CollectingEncoder encoder() { return encoders.getLast(); }
        static int pictureOf(CapturedFrame frame) { return ByteBuffer.wrap(frame.rgba()).getInt(); }
        public void close() {
            controller.close();
            producer.close();
        }
    }

    private static final class CollectingEncoder implements CaptureEncoder {
        final List<CapturedFrame> frames = new ArrayList<>();
        int fps;
        int sampleRate;
        public void open(Path output, int width, int height, int fps, int sampleRate) {
            this.fps = fps;
            this.sampleRate = sampleRate;
        }
        public void encode(CapturedFrame frame) {
            frames.add(new CapturedFrame(frame.rgba(), frame.width(), frame.height(),
                    Arrays.copyOf(frame.pcm(), frame.sampleCount() * 2), frame.sampleCount(), frame.frameIndex()));
        }
        public Path finish() { return Path.of("target", "cadence-boundary-test.mkv"); }
        public void abort() { }
    }
}
