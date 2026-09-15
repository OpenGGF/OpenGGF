package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void externalHostClockSurvivesSeekBranchResetAndInternalSteps() {
        audio.beginCommandTimelineFrame(100);
        audio.resetRingSound();
        RewindController controller = new RewindController(new RewindRegistry(),
                new InMemoryKeyframeStore(), new FakeInputSource(20), in -> {
                    audio.playSfx(com.openggf.audio.GameSound.RING);
                    return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
                }, 5, audio);
        controller.step();
        assertEquals(101,audio.commandTimeline().currentFrame());
        assertEquals(false,audio.captureLogicalSnapshot().ringLeft());
        audio.beginGameplayAudioFrame(120);audio.resetRingSound();controller.recordExternalStep();
        assertEquals(120,audio.commandTimeline().currentFrame(),"external recording must not rewrite the host clock");
        audio.resetRingSound(); // The same out-of-tick callback as level-load InitAudio.
        audio.beginGameplayAudioFrame(150);audio.playSfx(com.openggf.audio.GameSound.RING);controller.recordExternalStep();
        controller.seekTo(2);
        assertEquals(120,audio.commandTimeline().currentFrame());
        assertTrue(audio.captureLogicalSnapshot().ringLeft(),"seek must include frame 120 reset but exclude frame 150 ring");
        assertTrue(audio.commandTimeline().entries().stream().allMatch(entry->entry.frame()<=120));
        controller.step();
        assertEquals(121,audio.commandTimeline().currentFrame(),"internal branch continues from the selected host coordinate");
        assertEquals(false,audio.captureLogicalSnapshot().ringLeft());
        audio.beginGameplayAudioFrame(170);audio.resetRingSound();controller.recordExternalStep();
        controller.resetToFrameZero();
        assertEquals(0,controller.currentFrame());assertEquals(170,audio.commandTimeline().currentFrame());
        controller.step();assertEquals(171,audio.commandTimeline().currentFrame());
        assertEquals(false,audio.captureLogicalSnapshot().ringLeft());
        controller.seekTo(0);assertEquals(170,audio.commandTimeline().currentFrame());
        assertTrue(audio.captureLogicalSnapshot().ringLeft(),"new frame-zero audio base is the loaded level's state");
    }

    @Test
    void pruningExternalClockMapRetainsCommandsBetweenAudioCheckpoints() throws Exception {
        audio.beginCommandTimelineFrame(1000);
        RewindController controller = new RewindController(new RewindRegistry(),
                new InMemoryKeyframeStore(), new FakeInputSource(200), in ->
                    com.openggf.LevelFrameResult.GAMEPLAY_FRAME,60,audio);
        controller.setGameplayCheckpointInterval(10);
        for(int frame=1;frame<=130;frame++) {
            audio.beginGameplayAudioFrame(1000+frame*3);
            if(frame==65)audio.playSfx(com.openggf.audio.GameSound.RING);
            if(frame==80)audio.resetRingSound();
            controller.recordExternalStep();
        }
        assertEquals(70,controller.pruneHistoryToRetainFrames(60));
        var field=RewindController.class.getDeclaredField("audioTimelineFrames");field.setAccessible(true);
        assertEquals(61,((java.util.Map<?,?>)field.get(controller)).size(),"coordinate map is pruned with gameplay history");
        controller.seekTo(70);
        assertEquals(1210,audio.commandTimeline().currentFrame());
        assertEquals(false,audio.captureLogicalSnapshot().ringLeft(),"logical keyframe lookup and audio cutoff use their own clocks");
    }

    @Test
    void pruningBetweenAudioCheckpointsPreservesInterveningCommandForSeek() {
        RewindController controller = new RewindController(new RewindRegistry(),
                new InMemoryKeyframeStore(), new FakeInputSource(200), in -> {
                    if (in.frameIndex() == 65) audio.playSfx(com.openggf.audio.GameSound.RING);
                    if (in.frameIndex() == 80) audio.resetRingSound();
                    return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
                }, 60, audio);
        controller.setGameplayCheckpointInterval(10);
        for (int frame = 1; frame <= 130; frame++) controller.step();
        assertTrue(audio.captureLogicalSnapshot().ringLeft());
        assertEquals(70, controller.pruneHistoryToRetainFrames(60));
        controller.seekTo(70);
        assertEquals(false, audio.captureLogicalSnapshot().ringLeft(),
                "frame 65 ring command must replay from retained audio base 60");
        assertEquals(70, controller.currentFrame());
    }

    @Test
    void seekToSuppressesAudioDuringInternalReplay() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = in -> {
            audio.playSfx("STEP");
            return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
        };
        RewindController controller = new RewindController(registry, keyframes, inputs, stepper, 5, audio);

        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        assertEquals(8, audio.commandTimeline().entryCount());
        backend.clear();

        controller.seekTo(3);

        assertEquals(3, audio.commandTimeline().entryCount(),
                "seek must discard commands after the restored frame and emit no replay commands");
        assertEquals(3, controller.currentFrame());
    }

    @Test
    void stepBackwardSuppressesAudioDuringSegmentExpansion() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = in -> {
            audio.playSfx("STEP");
            return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
        };
        RewindController controller = new RewindController(registry, keyframes, inputs, stepper, 5, audio);

        for (int i = 0; i < 8; i++) {
            controller.step();
        }
        backend.clear();

        assertTrue(controller.stepBackward());

        assertEquals(7, audio.commandTimeline().entryCount(),
                "segment expansion must not append presentation commands");
        assertEquals(7, controller.currentFrame());
    }

    @Test
    void failedSegmentExpansionClosesAudioReplayScope() {
        RewindRegistry registry = new RewindRegistry();
        AtomicInteger failExpansion = new AtomicInteger();
        RewindController controller = new RewindController(
                registry, new InMemoryKeyframeStore(), new FakeInputSource(20), in -> {
                    if (failExpansion.get() != 0 && in.frameIndex() == 6) {
                        throw new RuntimeException("failed expansion");
                    }
                    return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
                }, 5, audio);
        for (int i = 0; i < 7; i++) controller.step();
        failExpansion.set(1);
        backend.clear();

        assertThrows(RuntimeException.class, controller::stepBackward);
        audio.playSfx("LIVE");

        assertEquals(1, audio.commandTimeline().entryCount(),
                "failed rewind must close suppression before returning");
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
                    return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
                },
                5,
                audio);

        assertTrue(controller.recordExternalStep());
        audio.playSfx("LIVE");

        assertEquals(1, audio.commandTimeline().entryCount(),
                "external live frame audio remains in the presentation timeline");
        assertEquals(0, steps.get(), "recordExternalStep must not invoke the stepper");
    }

    @Test
    void seekToRestoresAudioLogicalStateWithoutPresentationReplay() {
        RewindRegistry registry = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = in -> {
            if (in.frameIndex() == 1 || in.frameIndex() == 3) {
                audio.playSfx(com.openggf.audio.GameSound.RING);
            }
            if (in.frameIndex() == 2 || in.frameIndex() == 4) {
                audio.resetRingSound();
            }
            return com.openggf.LevelFrameResult.GAMEPLAY_FRAME;
        };
        RewindController controller = new RewindController(registry, keyframes, inputs, stepper, 2, audio);

        for (int i = 0; i < 5; i++) {
            controller.step();
        }
        assertTrue(audio.captureLogicalSnapshot().ringLeft(), "frame 4 reset leaves live state at true");
        backend.clear();

        controller.seekTo(3);

        assertEquals(3, audio.commandTimeline().entryCount(),
                "logical rewind replay must truncate to the restored presentation timeline");
        assertEquals(3, controller.currentFrame());
        assertEquals(false, audio.captureLogicalSnapshot().ringLeft(),
                "frame 3 ring command must be reflected in restored logical state");
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
