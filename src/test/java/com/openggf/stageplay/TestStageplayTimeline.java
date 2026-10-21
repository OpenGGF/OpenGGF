package com.openggf.stageplay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.TestStageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayTimeline {
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
        assertEquals(125, first.camera().x());
        assertEquals(1.25, first.camera().zoom());
        var smooth = StageplayTimeline.sample(document, 15).actors().getFirst();
        assertEquals(115.625, smooth.x());
        assertEquals(15, smooth.animationTick());
        var changed = StageplayTimeline.sample(document, 31).actors().getFirst();
        assertEquals(2, changed.animation());
        assertEquals(1, changed.animationTick());
        assertEquals(first, StageplayTimeline.sample(document, 5), "backward seeking reconstructs the same pose");
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
