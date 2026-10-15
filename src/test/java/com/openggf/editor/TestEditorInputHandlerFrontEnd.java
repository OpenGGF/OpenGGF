package com.openggf.editor;

import com.openggf.control.InputHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEditorInputHandlerFrontEnd {
    @Test
    void mouseReleaseFinishesOnlyTheMouseStrokeNotAFrontEndEdit() {
        var handler = new EditorInputHandler(new LevelEditorController());
        int[] finished = {0};
        int[] ticks = {0};
        handler.setFrontEnd(new EditorInputHandler.FrontEnd() {
            public boolean update(InputHandler input) { ticks[0]++; return false; }
            public void finishPendingEdits() { finished[0]++; }
        });
        var input = new InputHandler();
        handler.update(input);
        handler.update(input);
        assertEquals(2, ticks[0], "The front end sees every editor tick first");
        assertEquals(0, finished[0], "A held pad stroke must survive ticks where the mouse is up");
        handler.finishPendingEdits();
        assertEquals(1, finished[0], "Commands that commit pending edits still reach the front end");
    }

    @Test
    void aFrontEndThatOwnsTheTickStopsWorldEditing() {
        var controller = new LevelEditorController();
        var handler = new EditorInputHandler(controller);
        handler.setFrontEnd(new EditorInputHandler.FrontEnd() {
            public boolean update(InputHandler input) { return true; }
            public void finishPendingEdits() { }
        });
        var input = new InputHandler();
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        handler.update(input);
        assertEquals(0, controller.worldCursor().x(),
                "Movement belongs to the front end while it owns the tick");
    }
}
