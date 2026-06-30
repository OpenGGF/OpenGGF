package com.openggf.game.sonic3k.objects;

import org.junit.jupiter.api.Test;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestCutsceneKnucklesAiz1Instance {

    @Test
    public void initPositionIsCorrect() {
        var spawn = new ObjectSpawn(0x1400, 0x440, 0, 0, 0, false, 0);
        var knux = new CutsceneKnucklesAiz1Instance(spawn);
        assertEquals(0x1400, knux.getX());
        assertEquals(0x440, knux.getY());
    }

    @Test
    public void startsInWaitRoutine() {
        var spawn = new ObjectSpawn(0x1400, 0x440, 0, 0, 0, false, 0);
        var knux = new CutsceneKnucklesAiz1Instance(spawn);
        assertEquals(0, knux.getRoutine());
    }

    @Test
    public void rewindCaptureSkipsScratchMotionState() {
        var spawn = new ObjectSpawn(0x1400, 0x440, 0, 0, 0, false, 0);
        var knux = new CutsceneKnucklesAiz1Instance(spawn);

        // The captured field set must EXCLUDE the @RewindTransient scratch
        // SubpixelMotion holder (motionState) while still including the
        // authoritative scalar position/velocity fields it is rebuilt from.
        List<Field> captured = GenericFieldCapturer
                .defaultObjectSubclassCapturedFieldsForAudit(CutsceneKnucklesAiz1Instance.class);
        List<String> names = captured.stream().map(Field::getName).toList();

        assertFalse(names.contains("motionState"),
                "scratch SubpixelMotion holder must not be captured for rewind");
        assertTrue(names.contains("currentX"), "authoritative X position must be captured");
        assertTrue(names.contains("currentY"), "authoritative Y position must be captured");
        assertTrue(names.contains("xVel"), "authoritative X velocity must be captured");
        assertTrue(names.contains("yVel"), "authoritative Y velocity must be captured");
        assertTrue(names.contains("routine"), "routine state machine counter must be captured");

        // And the live capture actually produces a non-empty scalar sidecar
        // (so the field set above is genuinely persisted, not silently dropped).
        PerObjectRewindSnapshot snapshot = knux.captureRewindState();
        boolean hasScalarSidecar = snapshot.compactGenericState() != null
                || (snapshot.genericState() != null && !snapshot.genericState().keys().isEmpty());
        assertTrue(hasScalarSidecar,
                "rewind capture must persist the Knuckles scalar motion state");
    }

}
