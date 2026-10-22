package com.openggf.stageplay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.TestStageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayTimeline {
    @Test
    void sceneSwitchesCoverTheirMidpointAndNeverInterpolateAcrossLocations() {
        var incoming = new SetSource("s2", 1, 0, "", "");
        var document = scene().withActors(List.of(actor(List.of(
                key(0, 0, 1, -1, Interpolation.LINEAR), key(100, 1000, 1, -1, Interpolation.LINEAR),
                key(120, 1200, 1, -1, Interpolation.HOLD)))))
                .withCamera(List.of(new CameraKey(0, 0, 0, 1, Interpolation.LINEAR),
                        new CameraKey(100, 500, 300, 2, Interpolation.LINEAR),
                        new CameraKey(120, 600, 300, 3, Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("arrival", 100, incoming, Transition.FADE_WHITE, 20)));
        var before = StageplayTimeline.sample(document, 99);
        assertEquals(document.set(), before.set());
        assertEquals(0, before.actors().getFirst().x());
        assertEquals(0, before.camera().x());
        assertEquals(.9, before.transition().coverage(), 1e-12);
        assertFalse(before.transition().revealing());
        var switchFrame = StageplayTimeline.sample(document, 100);
        assertEquals(incoming, switchFrame.set());
        assertEquals(1000, switchFrame.actors().getFirst().x());
        assertEquals(0, switchFrame.actors().getFirst().animationTick());
        assertEquals(1, switchFrame.transition().coverage());
        assertTrue(switchFrame.transition().revealing());
        var after = StageplayTimeline.sample(document, 105);
        assertEquals(1050, after.actors().getFirst().x());
        assertEquals(5, after.actors().getFirst().animationTick());
        assertEquals(527.2222222222222, after.camera().x(), 1e-10);
        assertEquals(.5, after.transition().coverage());
        assertEquals(0, StageplayTimeline.sample(document, 110).transition().coverage());
        assertEquals(before, StageplayTimeline.sample(document, 99));
    }

    @Test
    void deterministicEarthquakeIsIndependentOfSeekOrderAndDoesNotChangeNativeCamera() {
        var document = scene().withCameraEffects(List.of(new CameraEffect("quake", 10, 40, 8, 4, 7, 42)));
        var expected = new java.util.HashMap<Integer, StageplayTimeline.Frame>();
        for (int frame = 0; frame < 50; frame++) expected.put(frame, StageplayTimeline.sample(document, frame));
        var frames = new java.util.ArrayList<>(expected.keySet());
        java.util.Collections.shuffle(frames, new java.util.Random(17));
        for (int frame : frames) {
            var sampled = StageplayTimeline.sample(document, frame);
            assertEquals(expected.get(frame), sampled);
            assertEquals(100, sampled.baseCamera().x());
            assertEquals(200, sampled.baseCamera().y());
            assertTrue(Math.abs(sampled.camera().x() - 100) <= 8);
            assertTrue(Math.abs(sampled.camera().y() - 200) <= 4);
        }
        assertNotEquals(expected.get(20).camera(), expected.get(20).baseCamera());
        assertEquals(expected.get(9).camera(), expected.get(9).baseCamera());
        assertEquals(expected.get(40).camera(), expected.get(40).baseCamera());
    }

    @Test
    void cutsAndEveryCoverTransitionSelectTheIncomingSetAtTheExactBoundary() {
        var incoming = new SetSource("s1", 0, 1, "", "");
        var base = scene().withDurationFrames(30).withCamera(List.of(scene().camera().getFirst(),
                new CameraKey(10, 500, 0, 1, Interpolation.HOLD)));
        for (Transition kind : Transition.values()) {
            var document = base.withSceneChanges(List.of(new SceneChange("switch", 10, incoming, kind,
                    kind == Transition.CUT ? 0 : 5)));
            assertEquals(base.set(), StageplayTimeline.sample(document, 9).set());
            var midpoint = StageplayTimeline.sample(document, 10);
            assertEquals(incoming, midpoint.set());
            assertEquals(kind, midpoint.transition().transition());
            assertEquals(kind == Transition.CUT ? 0 : 1, midpoint.transition().coverage());
            assertEquals(0, StageplayTimeline.sample(document, 8).transition().coverage());
            assertEquals(0, StageplayTimeline.sample(document, 13).transition().coverage());
            if (kind != Transition.CUT) {
                assertEquals(.5, StageplayTimeline.sample(document, 9).transition().coverage());
                assertEquals(2.0 / 3, StageplayTimeline.sample(document, 11).transition().coverage(), 1e-12);
            }
        }
    }

    @Test
    void interpolatesMovementAndCameraWithoutRestartingAnimation() {
        var document = scene().withActors(List.of(actor(List.of(
                key(0, 0, 1, -1, Interpolation.LINEAR),
                key(10, 100, 1, -1, Interpolation.SMOOTH),
                key(30, 200, 2, -1, Interpolation.HOLD)))))
                .withCamera(List.of(new CameraKey(0, 100, 200, 1, Interpolation.LINEAR),
                        new CameraKey(20, 200, 300, 2, Interpolation.HOLD)));
        var first = StageplayTimeline.sample(document, 5);
        assertEquals(50, first.actors().getFirst().x());
        assertEquals(5, first.actors().getFirst().animationTick());
        assertEquals(137, first.camera().x(), 1e-10);
        assertEquals(1.25, first.camera().zoom());
        assertEquals(2, StageplayTimeline.sample(document, 20).camera().zoom());
        var smooth = StageplayTimeline.sample(document, 15).actors().getFirst();
        assertEquals(115.625, smooth.x());
        assertEquals(15, smooth.animationTick());
        var changed = StageplayTimeline.sample(document, 31).actors().getFirst();
        assertEquals(2, changed.animation());
        assertEquals(1, changed.animationTick());
        assertEquals(first, StageplayTimeline.sample(document, 5), "backward seeking reconstructs the same pose");
    }

    @Test
    void centredPushInKeepsBothFramingCoordinatesFixedAtEverySample() {
        for (var easing : List.of(Interpolation.LINEAR, Interpolation.SMOOTH)) {
            var document = scene().withCamera(List.of(
                    new CameraKey(0, 0, 0, 1, easing),
                    new CameraKey(20, 80, 56, 2, easing),
                    new CameraKey(40, 120, 84, 4, Interpolation.HOLD)));
            for (int frame = 0; frame <= 40; frame++) {
                var camera = StageplayTimeline.sample(document, frame).camera();
                assertEquals(160, camera.x() + 160 / camera.zoom(), 1e-10, "horizontal centre at " + frame);
                assertEquals(112, camera.y() + 112 / camera.zoom(), 1e-10, "vertical centre at " + frame);
            }
            assertEquals(1.5, StageplayTimeline.sample(document, 10).camera().zoom());
            assertEquals(3, StageplayTimeline.sample(document, 30).camera().zoom());
        }
    }

    @Test
    void nativeWorldZoomSharesCameraEasingWithoutSeekHistoryOrShotBleed() {
        for (var policy : List.of(WorldPolicy.LIVE, WorldPolicy.LIVE)) {
            var set = new SetSource("s2", 0, 0, "", "", policy);
            var document = scene().withSet(set).withCamera(List.of(
                    new CameraKey(0, 0, 0, .75, Interpolation.SMOOTH),
                    new CameraKey(20, 200, 400, 2.25, Interpolation.LINEAR),
                    new CameraKey(40, 400, 800, 1, Interpolation.HOLD)));
            assertEquals(1.5, StageplayTimeline.sample(document, 10).camera().zoom());
            assertEquals(1.625, StageplayTimeline.sample(document, 30).camera().zoom());
            var middle = StageplayTimeline.sample(document, 7);
            StageplayTimeline.sample(document, 39);
            assertEquals(middle, StageplayTimeline.sample(document, 7));
            assertEquals(1, StageplayTimeline.sample(document, 41).camera().zoom());

            var cut = document.withSceneChanges(List.of(new SceneChange("cut", 20, set, Transition.CUT, 0)));
            assertEquals(.75, StageplayTimeline.sample(cut, 19).camera().zoom());
            assertEquals(2.25, StageplayTimeline.sample(cut, 20).camera().zoom());
        }
    }

    @Test
    void holdsDiscretePropertiesAndRestartsAnimationAfterPoseHold() {
        var document = scene().withActors(List.of(actor(List.of(
                key(0, 10, 1, -1, Interpolation.HOLD),
                new ActorKey(10, 100, 200, 1, 8, true, false, 2, Interpolation.LINEAR),
                key(20, 200, 1, -1, Interpolation.HOLD)))));
        var before = StageplayTimeline.sample(document, 9).actors().getFirst();
        assertEquals(10, before.x());
        assertFalse(before.flipX());
        assertTrue(before.visible());
        var pose = StageplayTimeline.sample(document, 15).actors().getFirst();
        assertEquals(150, pose.x());
        assertEquals(8, pose.pose());
        assertTrue(pose.flipX());
        assertFalse(pose.visible());
        assertEquals(1.5, pose.scale());
        assertEquals(2, StageplayTimeline.sample(document, 22).actors().getFirst().animationTick());
    }

    @Test
    void captionIntervalsAreHalfOpenAndFrameBoundsStrict() {
        var document = scene().withCaptions(List.of(new Caption("line", 2, 4, "Hello", 0, 0, -1)));
        assertTrue(StageplayTimeline.sample(document, 1).captions().isEmpty());
        assertEquals(1, StageplayTimeline.sample(document, 2).captions().size());
        assertEquals(1, StageplayTimeline.sample(document, 3).captions().size());
        assertTrue(StageplayTimeline.sample(document, 4).captions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> StageplayTimeline.sample(document, -1));
        assertThrows(IllegalArgumentException.class, () -> StageplayTimeline.sample(document, 600));
    }
}
