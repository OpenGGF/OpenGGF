package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindControllerAudioSuppression {
    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void seekToSuppressesAudioDuringInternalReplay() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = in -> audio.playSfx("STEP");
        RewindController controller = new RewindController(registry, keyframes, inputs, stepper, 5, audio);

        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        assertEquals(8, backend.totalCalls());
        backend.clear();

        controller.seekTo(3);

        assertEquals(0, backend.totalCalls(), "seek replay must not emit live audio");
        assertEquals(3, controller.currentFrame());
    }

    @Test
    void stepBackwardSuppressesAudioDuringSegmentExpansion() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = in -> audio.playSfx("STEP");
        RewindController controller = new RewindController(registry, keyframes, inputs, stepper, 5, audio);

        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.clear();

        assertTrue(controller.stepBackward());

        assertEquals(0, backend.totalCalls(), "segment expansion must not emit live audio");
        assertEquals(7, controller.currentFrame());
    }

    @Test
    void recordExternalStepDoesNotEnterAudioSuppression() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        AtomicInteger steps = new AtomicInteger();
        RewindController controller = new RewindController(
                registry,
                keyframes,
                inputs,
                in -> {
                    steps.incrementAndGet();
                    audio.playSfx("STEP");
                },
                5,
                audio);

        assertTrue(controller.recordExternalStep());
        audio.playSfx("LIVE");

        assertEquals(1, backend.totalCalls(), "external live frame audio remains audible");
        assertEquals(0, steps.get(), "recordExternalStep must not invoke the stepper");
    }

    private static final class FakeInputSource implements InputSource {
        private final int frames;

        FakeInputSource(int frames) {
            this.frames = frames;
        }

        @Override public int frameCount() { return frames; }
        @Override public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }
}
