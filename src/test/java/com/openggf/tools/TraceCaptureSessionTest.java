package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.capture.BackpressurePolicy;
import com.openggf.capture.CaptureException;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.CapturedFrame;
import com.openggf.capture.DrainPcmAudioTap;
import com.openggf.capture.VideoFrameGrabber;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.replay.TraceGhostHook;
import com.openggf.trace.replay.TraceReplayDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link TraceCaptureSession} lifecycle: its public API is unchanged, and its
 * audio side is one non-consuming lease on the unified presentation producer,
 * taken before the recorder opens and released after the recorder stops.
 */
class TraceCaptureSessionTest {

    private static final int SAMPLE_RATE = 48_000;
    private static final int FPS = 60;

    private AudioManager audio;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        audio = AudioManager.getInstance();
        audio.endCaptureMode();
        audio.resetState();
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        audio.setBackend(new HeadlessSmpsAudioBackend(
                config, PerformanceProfiler.getInstance()));
    }

    @AfterEach
    void tearDown() {
        audio.endCaptureMode();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void startRejectsDimensionsThatDoNotMatchTheGrabber() {
        RecordingCaptureRecorder recorder = new RecordingCaptureRecorder();
        TraceCaptureSession session = session(recorder);

        CaptureException failure = assertThrows(CaptureException.class,
                () -> session.start(640, 448, SAMPLE_RATE));

        assertTrue(failure.getMessage().contains("do not match grabber"),
                failure.getMessage());
        assertEquals(List.of(), recorder.events,
                "a rejected start never opens the recorder");
        assertThrows(IllegalStateException.class,
                () -> audio.drainCaptureFrame(new short[2048]),
                "a rejected start never takes a capture lease");
    }

    @Test
    void stepAndCaptureRequiresStart() {
        TraceCaptureSession session = session(new RecordingCaptureRecorder());

        CaptureException failure =
                assertThrows(CaptureException.class, session::stepAndCapture);

        assertTrue(failure.getMessage().contains("start()"),
                failure.getMessage());
    }

    @Test
    void startTakesTheCaptureLeaseBeforeOpeningTheRecorder() throws Exception {
        int leasesBefore = leaseCount();
        RecordingCaptureRecorder recorder = new RecordingCaptureRecorder();
        TraceCaptureSession session = session(recorder);

        session.start(320, 224, SAMPLE_RATE);

        assertEquals(leasesBefore + 1, leaseCount());
        assertEquals(List.of("recorder-start"), recorder.events);
        assertNotNull(TraceGhostHook.active(),
                "the ghost hook is installed for the capture run");
        assertEquals(SAMPLE_RATE / FPS,
                audio.drainCaptureFrame(new short[2048]),
                "the lease is live and clocked at the session frame rate");

        session.finish();
    }

    @Test
    void finishStopsTheRecorderThenReleasesTheLeaseAndGhostHook()
            throws Exception {
        int leasesBefore = leaseCount();
        RecordingCaptureRecorder recorder = new RecordingCaptureRecorder();
        TraceCaptureSession session = session(recorder);
        session.start(320, 224, SAMPLE_RATE);

        Path output = session.finish();

        assertEquals(recorder.outputFile(), output);
        assertEquals(List.of("recorder-start", "recorder-stop"), recorder.events);
        assertEquals(leasesBefore, leaseCount());
        assertNull(TraceGhostHook.active(),
                "finish clears the ghost hook it installed");
        assertThrows(IllegalStateException.class,
                () -> audio.drainCaptureFrame(new short[2048]));
    }

    @Test
    void finishReleasesTheLeaseEvenWhenTheRecorderStopFails() {
        int leasesBefore = leaseCount();
        RecordingCaptureRecorder recorder = new RecordingCaptureRecorder();
        recorder.failOnStop = true;
        TraceCaptureSession session = session(recorder);
        assertDoesNotThrowStart(session);

        assertThrows(CaptureException.class, session::finish);

        assertEquals(leasesBefore, leaseCount(),
                "a failing recorder stop must still release the lease");
        assertNull(TraceGhostHook.active());
    }

    private void assertDoesNotThrowStart(TraceCaptureSession session) {
        try {
            session.start(320, 224, SAMPLE_RATE);
        } catch (CaptureException failure) {
            throw new AssertionError(failure);
        }
    }

    private TraceCaptureSession session(CaptureRecorder recorder) {
        return new TraceCaptureSession(
                mock(GameLoop.class), mock(TraceReplayDriver.class),
                new FixedGrabber(320, 224), new DrainPcmAudioTap(audio),
                recorder, FPS);
    }

    private int leaseCount() {
        return AudioManagerTestDiagnostics.producerFingerprint(audio)
                .captureCount();
    }

    private static final class FixedGrabber implements VideoFrameGrabber {
        private final int width;
        private final int height;

        private FixedGrabber(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public byte[] grab() { return new byte[width * height * 4]; }
    }

    /** Records lifecycle ordering without opening an encoder. */
    private static final class RecordingCaptureRecorder extends CaptureRecorder {
        private final List<String> events = new ArrayList<>();
        private boolean failOnStop;

        private RecordingCaptureRecorder() {
            super(null, BackpressurePolicy.BLOCK, 1,
                    Path.of("target"), "session-test", "stamp");
        }

        @Override
        public void start(int width, int height, int fps, int sampleRate) {
            events.add("recorder-start");
        }

        @Override
        public void submit(CapturedFrame frame) {
            events.add("recorder-submit");
        }

        @Override
        public Path stop() throws CaptureException {
            events.add("recorder-stop");
            if (failOnStop) {
                throw new CaptureException("injected recorder stop failure");
            }
            return outputFile();
        }
    }
}
