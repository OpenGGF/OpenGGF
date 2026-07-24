package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameSound;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.SegaPcmSpec;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.capture.CaptureException;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.CapturedFrame;
import com.openggf.capture.DrainPcmAudioTap;
import com.openggf.capture.VideoFrameGrabber;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.replay.TraceReplayDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline trace capture runs over the same unified presentation producer as
 * live recording.
 *
 * <p>The capture drivers own the headless outer-frame audio boundary:
 * {@code GameLoop.step()} presents nothing, each presented outer framebuffer
 * frame calls {@link HeadlessGameBoot#presentHeadlessOuterAudioFrame()} exactly
 * once, and the packet is drained exactly once afterwards (captured or
 * discarded during clip fast-forward). Nothing here installs a deterministic
 * runtime, replaces the producer, or opens an audio device.
 */
class TestTraceCaptureUnifiedAudio {

    private static final int SAMPLE_RATE = 48_000;
    private static final int FPS = 60;
    private static final int FRAME_SAMPLES = SAMPLE_RATE / FPS;
    private static final int PCM_ADDRESS = 0x40;

    private AudioManager audio;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        audio = AudioManager.getInstance();
        audio.endCaptureMode();
        audio.resetState();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        audio.endCaptureMode();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        TestEnvironment.resetAll();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void sessionStartAndFinishAttachAndDetachOneUnifiedHandle() throws Exception {
        audio.setBackend(headlessBackend());
        SentinelRuntime sentinel = installSentinelRuntime();
        int leasesBefore = leaseCount();     // also realizes the lazy producer
        Object producerBefore = producer(audio);
        assertNotNull(producerBefore, "the producer must already exist");

        RecordingCaptureRecorder recorder = recorder();
        TraceCaptureSession session = session(recorder);

        session.start(320, 224, SAMPLE_RATE);

        assertEquals(leasesBefore + 1, leaseCount(),
                "start attaches exactly one unified capture lease");
        // The recorder samples the live lease count as it opens, so opening
        // the recorder first would record leases=leasesBefore here.
        assertEquals(List.of("recorder-start(leases=" + (leasesBefore + 1) + ")"),
                recorder.events,
                "the lease is already attached when the recorder opens");
        assertSame(producerBefore, producer(audio),
                "start must not replace the authoritative producer");
        assertSame(sentinel, deterministicRuntime(audio),
                "start must not install a deterministic runtime");

        session.finish();

        assertEquals(leasesBefore, leaseCount(),
                "finish detaches exactly the compatibility lease");
        assertEquals(List.of("recorder-start(leases=" + (leasesBefore + 1) + ")",
                        "recorder-stop(leases=" + (leasesBefore + 1) + ")"),
                recorder.events,
                "recorder.stop() runs before endCaptureMode(), so the lease is"
                        + " still attached inside stop()");
        assertSame(producerBefore, producer(audio));
        assertSame(sentinel, deterministicRuntime(audio));
    }

    @Test
    void sessionFailureClosesCaptureHandleAndRecorder() throws Exception {
        audio.setBackend(headlessBackend());
        int leasesBefore = leaseCount();
        RecordingCaptureRecorder recorder = recorder();
        recorder.failOnStop = true;
        TraceCaptureSession session = session(recorder);
        session.start(320, 224, SAMPLE_RATE);

        assertThrows(CaptureException.class, session::finish);

        assertEquals(List.of("recorder-start(leases=" + (leasesBefore + 1) + ")",
                        "recorder-stop(leases=" + (leasesBefore + 1) + ")"),
                recorder.events,
                "the recorder stop attempt still runs first");
        assertEquals(leasesBefore, leaseCount(),
                "a failing recorder stop still closes the capture lease");
        assertThrows(IllegalStateException.class,
                () -> audio.drainCaptureFrame(new short[FRAME_SAMPLES * 2]),
                "the compatibility lease is gone after a failed finish");
    }

    @Test
    void eachCapturedFramebufferExplicitlyTicksOneHeadlessOuterAudioBoundary() {
        audio.setBackend(headlessBackend());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        InputHandler input = mock(InputHandler.class);
        when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        GameLoop loop = new GameLoop(input);
        loop.setGameMode(GameMode.LEGAL_DISCLAIMER);
        audio.beginCaptureMode(SAMPLE_RATE, FPS);

        loop.step();

        assertEquals(0, AudioManagerTestDiagnostics
                        .shadowParitySnapshot(audio).presentedFrames(),
                "a GameLoop.step() in this mode presents no audio (the"
                        + " per-mode proof for every GameMode lives in"
                        + " TestGameLoopAudioPresentationModes)");

        HeadlessGameBoot.presentHeadlessOuterAudioFrame();

        var parity = AudioManagerTestDiagnostics.shadowParitySnapshot(audio);
        assertEquals(1, parity.presentedFrames(),
                "each captured framebuffer ticks exactly one outer boundary");
        assertEquals(1, parity.forwardFrames());
        assertEquals(FRAME_SAMPLES,
                audio.drainCaptureFrame(new short[FRAME_SAMPLES * 2]));

        audio.endCaptureMode();
    }

    /**
     * The tool's per-outer-frame audio cadence, exercised through the helper
     * the capture loops delegate to.
     *
     * <p>Scope note: this drives
     * {@link TraceCaptureTool.HeadlessOuterAudioFrames} directly, because
     * {@code driveClip}/{@code driveAndCapture} need a GL context, a ROM, a
     * loaded trace, and ffmpeg. Loop-body <em>wiring</em> (which branch calls
     * which helper method) is therefore not asserted here. What makes the
     * cadence-multiplying regression non-silent is the helper's own
     * present/drain alternation guard, pinned at the end of this test: moving
     * the presentation into the per-simulation-step body throws on the first
     * fast-forward frame with more than one simulation step.
     */
    @Test
    void toolFastForwardDrainsOrDiscardsEveryPresentedPacketWithoutBacklog() {
        audio.setBackend(headlessBackend());
        audio.beginCaptureMode(SAMPLE_RATE, FPS);
        LiveCaptureAudioHandle lease = attachedLease();
        TraceCaptureTool.HeadlessOuterAudioFrames frames =
                new TraceCaptureTool.HeadlessOuterAudioFrames(
                        new DrainPcmAudioTap(audio));

        short[] captured = new short[16384];
        int presented = 0;
        for (int outerFrame = 0; outerFrame < 30; outerFrame++) {
            // Simulation-only steps enqueue commands but must not present.
            audio.stopAllSfx();
            audio.stopAllSfx();
            frames.presentOuterFrame();
            presented++;
            if (outerFrame < 20) {
                assertEquals(FRAME_SAMPLES, frames.discardPresented(),
                        "fast-forward discards exactly one presented packet");
            } else {
                assertEquals(FRAME_SAMPLES, frames.drainCaptured(captured),
                        "the capture window drains exactly one presented packet");
            }
            assertEquals(presented, AudioManagerTestDiagnostics
                            .shadowParitySnapshot(audio).presentedFrames(),
                    "simulation steps must not multiply audio cadence");
        }

        assertEquals(30, frames.presentedFrames());
        assertEquals(30, frames.drainedFrames());
        assertEquals((long) 30 * FRAME_SAMPLES, lease.totalStereoFrames(),
                "one clocked packet per presented outer frame, no backlog");

        // Presenting again before the packet is drained is exactly the shape a
        // per-simulation-step presentation would take. It must be rejected, not
        // absorbed, so the regression cannot ship as silent extra cadence.
        frames.presentOuterFrame();
        assertThrows(IllegalStateException.class, frames::presentOuterFrame,
                "a second presentation before the drain would multiply the"
                        + " capture audio cadence");
        assertEquals(31, frames.presentedFrames(),
                "the rejected presentation never reached the producer");
        assertEquals(31, AudioManagerTestDiagnostics
                        .shadowParitySnapshot(audio).presentedFrames(),
                "the rejected presentation never reached the producer");
        assertEquals(FRAME_SAMPLES, frames.discardPresented());
        assertThrows(IllegalStateException.class, frames::discardPresented,
                "a drain with nothing presented would emit a stale or silent"
                        + " packet into the recording");

        audio.endCaptureMode();
    }

    @Test
    void traceFramesContainFinalSmpsWavAndPcmPackets() throws Exception {
        audio.setBackend(headlessBackend());
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        AbstractSmpsData music = new AudioTestFixtures.StubSmpsData("music");
        music.setId(0x81);
        loader.musicResults.put(0x81, music);
        byte[] segaPcm = rampPcm(2_000);
        Rom rom = mock(Rom.class);
        when(rom.readBytes(PCM_ADDRESS, segaPcm.length)).thenReturn(segaPcm);
        audio.setAudioProfile(new TraceCaptureProfile(
                loader, new SegaPcmSpec(PCM_ADDRESS, segaPcm.length, 8_000)));
        audio.setRom(rom);
        AudioManagerTestDiagnostics.registerFallbackSfxAsset(
                audio, "sfx/jump.wav", rampPcm(1_000), SAMPLE_RATE);

        audio.beginCaptureMode(SAMPLE_RATE, FPS);
        DrainPcmAudioTap tap = new DrainPcmAudioTap(audio);
        LiveCaptureAudioHandle speaker =
                AudioManagerTestDiagnostics.attachPresentationCapture(audio, FPS);

        audio.playMusic(0x81);
        audio.playSfx("JUMP");
        audio.playSegaPcm();
        assertNotNull(AudioManagerTestDiagnostics.admitAndPrimeSmpsMusic(audio),
                "SMPS music must be admitted into the offline producer");

        AudioPresentationSnapshot presentation =
                audio.captureLogicalSnapshot().presentation();
        assertNotNull(presentation.activeMusic(), "SMPS music voice");
        assertNotNull(presentation.rawPcmVoiceId(), "raw PCM voice");
        assertTrue(hasSampleAsset(presentation, "sfx/jump.wav"),
                "fallback WAV SFX voice");

        HeadlessGameBoot.presentHeadlessOuterAudioFrame();
        short[] pcmBuffer = new short[16384];
        int sampleCount = tap.drain(pcmBuffer);
        CapturedFrame frame = new CapturedFrame(
                new byte[320 * 224 * 4], 320, 224, pcmBuffer, sampleCount, 0);

        assertEquals(FRAME_SAMPLES, sampleCount);
        assertFalse(allZero(frame.pcm(), sampleCount * 2),
                "trace frames carry the final mixed SMPS/WAV/PCM packet");
        short[] speakerPcm = new short[FRAME_SAMPLES * 2];
        assertEquals(FRAME_SAMPLES, speaker.drainPresentationFrame(speakerPcm));
        short[] capturedPcm = new short[FRAME_SAMPLES * 2];
        System.arraycopy(frame.pcm(), 0, capturedPcm, 0, capturedPcm.length);
        assertArrayEquals(speakerPcm, capturedPcm,
                "trace capture and speaker see one identical final packet");

        speaker.close();
        audio.endCaptureMode();
    }

    @Test
    void traceCaptureAndLiveCaptureCannotDestructivelyDrainEachOther()
            throws Exception {
        audio.setBackend(headlessBackend());
        byte[] segaPcm = rampPcm(4_000);
        Rom rom = mock(Rom.class);
        when(rom.readBytes(PCM_ADDRESS, segaPcm.length)).thenReturn(segaPcm);
        audio.setAudioProfile(new TraceCaptureProfile(
                null, new SegaPcmSpec(PCM_ADDRESS, segaPcm.length, SAMPLE_RATE)));
        audio.setRom(rom);

        audio.beginCaptureMode(SAMPLE_RATE, FPS);
        LiveCaptureAudioHandle live = audio.beginLiveCaptureAudio(FPS);
        DrainPcmAudioTap offline = new DrainPcmAudioTap(audio);
        audio.playSegaPcm();

        // Offline first, then live.
        HeadlessGameBoot.presentHeadlessOuterAudioFrame();
        short[] offlineFirst = new short[FRAME_SAMPLES * 2];
        short[] liveSecond = new short[FRAME_SAMPLES * 2];
        assertEquals(FRAME_SAMPLES, offline.drain(offlineFirst));
        assertEquals(FRAME_SAMPLES, live.drainPresentationFrame(liveSecond));
        assertFalse(allZero(offlineFirst, offlineFirst.length));
        assertArrayEquals(offlineFirst, liveSecond,
                "an offline drain must not consume the live view");

        // Live first, then offline.
        HeadlessGameBoot.presentHeadlessOuterAudioFrame();
        short[] liveFirst = new short[FRAME_SAMPLES * 2];
        short[] offlineSecond = new short[FRAME_SAMPLES * 2];
        assertEquals(FRAME_SAMPLES, live.drainPresentationFrame(liveFirst));
        assertEquals(FRAME_SAMPLES, offline.drain(offlineSecond));
        assertFalse(allZero(liveFirst, liveFirst.length));
        assertArrayEquals(liveFirst, offlineSecond,
                "a live drain must not consume the offline view");

        live.close();
        audio.endCaptureMode();
    }

    // --- fixtures ---------------------------------------------------------

    private TraceCaptureSession session(CaptureRecorder recorder) {
        return new TraceCaptureSession(
                mock(GameLoop.class), mock(TraceReplayDriver.class),
                new FixedGrabber(320, 224), new DrainPcmAudioTap(audio),
                recorder, FPS);
    }

    private RecordingCaptureRecorder recorder() {
        return new RecordingCaptureRecorder(this::leaseCount);
    }

    private HeadlessSmpsAudioBackend headlessBackend() {
        return new HeadlessSmpsAudioBackend(
                SonicConfigurationService.getInstance(),
                PerformanceProfiler.getInstance());
    }

    private LiveCaptureAudioHandle attachedLease() throws AssertionError {
        try {
            Field field = AudioManager.class.getDeclaredField(
                    "offlineCaptureHandle");
            field.setAccessible(true);
            return (LiveCaptureAudioHandle) field.get(audio);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private int leaseCount() {
        AudioPresentationProducer.TransactionFingerprint fingerprint =
                AudioManagerTestDiagnostics.producerFingerprint(audio);
        return fingerprint.captureCount();
    }

    private SentinelRuntime installSentinelRuntime() throws Exception {
        SentinelRuntime sentinel = new SentinelRuntime();
        java.lang.reflect.Method setter =
                AudioManager.class.getDeclaredMethod(
                        "setDeterministicAudioRuntime",
                        DeterministicAudioRuntime.class);
        setter.setAccessible(true);
        setter.invoke(audio, sentinel);
        return sentinel;
    }

    private static Object producer(AudioManager audio) throws Exception {
        Field field = AudioManager.class.getDeclaredField("shadowProducer");
        field.setAccessible(true);
        return field.get(audio);
    }

    private static DeterministicAudioRuntime deterministicRuntime(
            AudioManager audio) throws Exception {
        Field field =
                AudioManager.class.getDeclaredField("deterministicAudioRuntime");
        field.setAccessible(true);
        return (DeterministicAudioRuntime) field.get(audio);
    }

    private static boolean hasSampleAsset(
            AudioPresentationSnapshot presentation, String assetId) {
        for (PresentationVoiceSnapshot voice : presentation.voices()) {
            if (voice instanceof PresentationVoiceSnapshot.Sample sample
                    && assetId.equals(sample.assetId())) {
                return true;
            }
        }
        return false;
    }

    private static byte[] rampPcm(int length) {
        byte[] pcm = new byte[length];
        for (int index = 0; index < length; index++) {
            pcm[index] = (byte) (index % 251);
        }
        return pcm;
    }

    private static boolean allZero(short[] samples, int length) {
        for (int index = 0; index < length; index++) {
            if (samples[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private static final class SentinelRuntime
            implements DeterministicAudioRuntime {
        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            throw new AssertionError(
                    "offline capture must never advance a retired runtime");
        }
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

    /**
     * Records lifecycle ordering without opening an encoder. Each event
     * carries the capture-lease count observed <em>inside</em> the recorder
     * call, so "lease before recorder" is an observed ordering rather than an
     * inference from state read after {@code start()} returned.
     */
    private static final class RecordingCaptureRecorder extends CaptureRecorder {
        private final List<String> events = new ArrayList<>();
        private final IntSupplier leaseCount;
        private boolean failOnStop;

        private RecordingCaptureRecorder(IntSupplier leaseCount) {
            super(null, com.openggf.capture.BackpressurePolicy.BLOCK, 1,
                    Path.of("target"), "test", "stamp");
            this.leaseCount = leaseCount;
        }

        @Override
        public void start(int width, int height, int fps, int sampleRate) {
            events.add("recorder-start(leases=" + leaseCount.getAsInt() + ")");
        }

        @Override
        public void submit(CapturedFrame frame) {
            events.add("recorder-submit");
        }

        @Override
        public Path stop() throws CaptureException {
            events.add("recorder-stop(leases=" + leaseCount.getAsInt() + ")");
            if (failOnStop) {
                throw new CaptureException("injected recorder stop failure");
            }
            return outputFile();
        }
    }

    private record TraceCaptureProfile(SmpsLoader loader, SegaPcmSpec spec)
            implements GameAudioProfile {
        @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
        @Override public SmpsSequencerConfig getSequencerConfig() {
            return new SmpsSequencerConfig.Builder().build();
        }
        @Override public int getSpeedShoesOnCommandId() { return -1; }
        @Override public int getSpeedShoesOffCommandId() { return -1; }
        @Override public int getInvincibilityMusicId() { return -1; }
        @Override public int getExtraLifeMusicId() { return -1; }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
        @Override public SegaPcmSpec getSegaPcmSpec() { return spec; }
    }
}
