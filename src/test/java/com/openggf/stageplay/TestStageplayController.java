package com.openggf.stageplay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.TestStageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayController {
    @Test
    void linkedDocumentChangesUndoAtomicallyAndDirtyTracksSavedEquality() {
        var controller = new StageplayController(scene());
        assertFalse(controller.dirty());
        assertTrue(controller.edit(doc -> doc.withName("Arrival").withSet(
                new SetSource("s3k", 0, 0, "", "sets/arrival.ogfproject"))));
        assertTrue(controller.dirty());
        controller.markSaved();
        assertTrue(controller.edit(doc -> doc.withName("Warning")));
        assertTrue(controller.undo());
        assertEquals("Arrival", controller.document().name());
        assertFalse(controller.dirty());
        assertEquals("sets/arrival.ogfproject", controller.document().set().levelProject());
        assertTrue(controller.undo());
        assertEquals("", controller.document().set().levelProject());
        assertTrue(controller.dirty());
        assertTrue(controller.redo());
        assertFalse(controller.dirty());
        assertFalse(controller.edit(doc -> doc));
        assertTrue(controller.canRedo(), "a no-op does not discard redo");
        assertTrue(controller.edit(doc -> doc.withName("Branch")));
        assertFalse(controller.canRedo());
    }

    @Test
    void transportDoesNotEnterUndoHistoryAndStopsAtLastFrame() {
        var controller = new StageplayController(scene().withDurationFrames(3));
        controller.seek(-10);
        assertEquals(0, controller.frame());
        assertFalse(controller.advance());
        controller.play();
        assertTrue(controller.advance());
        assertTrue(controller.advance());
        assertEquals(2, controller.frame());
        assertFalse(controller.playing());
        assertFalse(controller.advance());
        assertFalse(controller.canUndo());
        assertFalse(controller.dirty());
        controller.seek(10);
        assertEquals(2, controller.frame());
        controller.play();
        assertEquals(0, controller.frame(), "replaying from the end starts at frame zero");
        controller.pause();
        assertFalse(controller.playing());
    }

    @Test
    void failedEditsLeaveDocumentAndHistoryUntouchedAndRemovedSelectionClears() {
        var doc = scene().withActors(List.of(actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD)))));
        var controller = new StageplayController(doc);
        controller.selectActor("hero");
        assertThrows(IllegalArgumentException.class, () -> controller.selectActor("missing"));
        assertThrows(IllegalArgumentException.class, () -> controller.edit(value -> value.withDurationFrames(-1)));
        assertEquals(doc, controller.document());
        assertFalse(controller.canUndo());
        controller.edit(value -> value.withActors(List.of()));
        assertEquals("", controller.selectedActorId());
        controller.replace(doc);
        assertFalse(controller.canUndo());
        assertFalse(controller.dirty());
    }

    @Test
    void undoHistoryIsBounded() {
        var controller = new StageplayController(scene());
        for (int i = 0; i < 140; i++) {
            String name = "Edit " + i;
            controller.edit(doc -> doc.withName(name));
        }
        int count = 0;
        while (controller.undo()) count++;
        assertEquals(128, count);
    }
}
