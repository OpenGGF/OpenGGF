package com.openggf.game.rewind;

import com.openggf.LevelFrameResult;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.FadeManagerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindFadeRestoreSafety {

    @Test
    void auditPreservesPendingLiveCompletionWithoutApplyingHistory() {
        Fixture fixture = new Fixture(4);
        fixture.controller.setDeterminismAuditor(new RewindDeterminismAuditor());
        fixture.recordThrough(1);
        fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
        fixture.recordThrough(3);
        fixture.stepper.step(input(4));
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertTrue(fixture.controller.recordExternalStep());

        FadeManagerSnapshot afterAudit = fixture.fade.capture();
        fixture.finishFade();
        assertAll(
                () -> assertEquals(liveFade, afterAudit, "audit must preserve the pending live fade"),
                () -> assertEquals(4, fixture.controller.currentFrame()),
                () -> assertEquals(4, fixture.counter.value),
                () -> assertTrue(fixture.restoredFrames.isEmpty(),
                        "a live callback cannot be restored as an audit rollback point"),
                () -> assertEquals(1, fixture.completions.get(),
                        "the original completion must still run exactly once after the attempted audit"));
    }

    @Test
    void auditDoesNotApplyAnUnsafePreviousKeyframe() {
        Fixture fixture = new Fixture(4);
        fixture.beforeStep = in -> {
            if (in.frameIndex() == 3) {
                fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 3);
            }
        };
        fixture.recordThrough(4);
        assertTrue(fadeAt(fixture, 4).isPoisoned());
        List<String> reports = new ArrayList<>();
        fixture.controller.setDeterminismAuditor(new RewindDeterminismAuditor(reports::add));
        fixture.recordThrough(7);
        fixture.stepper.step(input(8));
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertTrue(fixture.controller.recordExternalStep());

        assertAll(
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertEquals(8, fixture.controller.currentFrame()),
                () -> assertEquals(8, fixture.counter.value),
                () -> assertTrue(fixture.restoredFrames.isEmpty(),
                        "an unsafe audit floor must be refused before publishing a historical restore"),
                () -> assertTrue(reports.isEmpty(), "an ineligible segment is not a determinism failure"),
                () -> assertEquals(1, fixture.completions.get()));
    }

    @Test
    void seekRejectsAnUnsafeKeyframeWithoutMutatingLiveStateOrHistory() {
        assertUnsafeFloorSeek(4);
    }

    @Test
    void seekRejectsReplayFromAnUnsafeFloorWithoutMutatingLiveStateOrHistory() {
        assertUnsafeFloorSeek(5);
    }

    private static void assertUnsafeFloorSeek(int targetFrame) {
        Fixture fixture = new Fixture(4);
        fixture.beforeStep = in -> {
            if (in.frameIndex() == 3) {
                fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
            }
        };
        fixture.forwardThrough(12);
        assertTrue(fadeAt(fixture, 4).isPoisoned());
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> fixture.controller.seekTo(targetFrame)),
                () -> assertEquals(12, fixture.controller.currentFrame()),
                () -> assertEquals(12, fixture.counter.value),
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertTrue(fixture.restoredFrames.isEmpty(),
                        "unsafe floors must be refused before any registry restore"),
                () -> assertEquals(12, fixture.keyframes.latestAtOrBefore(99).orElseThrow().frame()),
                () -> assertEquals(1, fixture.completions.get()));
    }

    @Test
    void seekRejectsPendingLiveCompletionWithoutLosingItsCallback() {
        Fixture fixture = new Fixture(10);
        fixture.forwardThrough(1);
        fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
        fixture.forwardThrough(3);
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> fixture.controller.seekTo(0)),
                () -> assertEquals(3, fixture.controller.currentFrame()),
                () -> assertEquals(3, fixture.counter.value),
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertTrue(fixture.restoredFrames.isEmpty()),
                () -> {
                    fixture.finishFade();
                    assertEquals(1, fixture.completions.get(), "refused seek must preserve the live callback");
                });
    }

    @Test
    void seekRejectsPendingReplayTargetAndRollsBackToSafeLiveState() {
        Fixture fixture = new Fixture(10);
        fixture.beforeStep = in -> {
            if (in.frameIndex() == 2) {
                fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
            }
        };
        fixture.forwardThrough(20);
        FadeManagerSnapshot liveFade = fixture.fade.capture();
        assertEquals(1, fixture.completions.get());

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> fixture.controller.seekTo(3)),
                () -> assertEquals(20, fixture.controller.currentFrame()),
                () -> assertEquals(20, fixture.counter.value),
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertEquals(20, fixture.keyframes.latestAtOrBefore(99).orElseThrow().frame()),
                () -> {
                    fixture.controller.step();
                    fixture.finishFade();
                    assertEquals(21, fixture.controller.currentFrame());
                    assertEquals(1, fixture.completions.get(),
                            "a rejected replay must discard its newly created pending callback");
                });
    }

    @Test
    void seekCanReplayThroughAFadeToASafeTarget() {
        Fixture fixture = new Fixture(10);
        fixture.beforeStep = in -> {
            if (in.frameIndex() == 2) {
                fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
            }
        };
        fixture.forwardThrough(20);

        assertDoesNotThrow(() -> fixture.controller.seekTo(9));

        assertEquals(9, fixture.controller.currentFrame());
        assertEquals(9, fixture.counter.value);
        assertFalse(fixture.fade.hasPendingCompletion(),
                "a callback recreated by replay may complete before the committed target");
    }

    @Test
    void stepBackwardPreservesPendingLiveCompletionOnRefusal() {
        Fixture fixture = new Fixture(10);
        fixture.forwardThrough(1);
        fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 6);
        fixture.forwardThrough(3);
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertAll(
                () -> assertFalse(fixture.controller.stepBackward()),
                () -> assertEquals(3, fixture.controller.currentFrame()),
                () -> assertEquals(3, fixture.counter.value),
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertTrue(fixture.restoredFrames.isEmpty()),
                () -> {
                    fixture.finishFade();
                    assertEquals(1, fixture.completions.get(), "refused rewind must preserve the live callback");
                });
    }

    @Test
    void stepBackwardRejectsAnUnsafeFloorBeforeReplayingPastIt() {
        Fixture fixture = new Fixture(5);
        fixture.beforeStep = in -> {
            if (in.frameIndex() == 4) {
                fixture.fade.startFadeToBlack(fixture.completions::incrementAndGet, 0, 3);
            }
        };
        fixture.forwardThrough(8);
        assertTrue(fadeAt(fixture, 5).isPoisoned());
        FadeManagerSnapshot liveFade = fixture.fade.capture();

        assertAll(
                () -> assertFalse(fixture.controller.stepBackward()),
                () -> assertEquals(8, fixture.controller.currentFrame()),
                () -> assertEquals(8, fixture.counter.value),
                () -> assertEquals(liveFade, fixture.fade.capture()),
                () -> assertTrue(fixture.restoredFrames.isEmpty(),
                        "replaying from an unsafe floor already loses its historical callback"),
                () -> assertEquals(1, fixture.completions.get()));
    }

    private static FadeManagerSnapshot fadeAt(Fixture fixture, int frame) {
        return (FadeManagerSnapshot) fixture.keyframes.latestAtOrBefore(frame)
                .orElseThrow().snapshot().get("fademanager");
    }

    private static Bk2FrameInput input(int frame) {
        return new Bk2FrameInput(frame, 0, 0, false, "");
    }

    private static final class CapturedCounter implements RewindSnapshottable<Integer> {
        int value;

        @Override public String key() { return "counter"; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer snapshot) { value = snapshot; }
    }

    private static final class Fixture {
        final FadeManager fade = new FadeManager();
        final CapturedCounter counter = new CapturedCounter();
        final AtomicInteger completions = new AtomicInteger();
        final List<Integer> restoredFrames = new ArrayList<>();
        final InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        final RewindController controller;
        final EngineStepper stepper;
        Consumer<Bk2FrameInput> beforeStep = in -> { };

        Fixture(int interval) {
            RewindRegistry registry = new RewindRegistry();
            registry.register(counter);
            registry.register(fade);
            registry.registerPostRestoreCallback("observed-frame", () -> restoredFrames.add(counter.value));
            stepper = in -> {
                beforeStep.accept(in);
                counter.value = in.frameIndex();
                fade.update();
                return LevelFrameResult.GAMEPLAY_FRAME;
            };
            InputSource inputs = new InputSource() {
                @Override public int frameCount() { return 100; }
                @Override public Bk2FrameInput read(int frame) { return input(frame); }
            };
            controller = new RewindController(registry, keyframes, inputs, stepper, interval);
        }

        void forwardThrough(int frame) {
            while (controller.currentFrame() < frame) controller.step();
        }

        void recordThrough(int frame) {
            while (controller.currentFrame() < frame) {
                stepper.step(input(controller.currentFrame() + 1));
                assertTrue(controller.recordExternalStep());
            }
        }

        void finishFade() {
            for (int frame = 0; frame < 100; frame++) fade.update();
        }
    }
}
