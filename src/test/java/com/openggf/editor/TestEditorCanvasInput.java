package com.openggf.editor;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.editor.selection.CellRectangle;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.session.EditorCursorState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

class TestEditorCanvasInput {
    @Test
    void quarterZoomPaintsTheWorldCellUnderThePointer() {
        var level = TestEditorInteractionSafety.level();
        var controller = controller(level);
        controller.selectBlock(1);
        var editor = mouse(controller);
        controller.canvasView().zoomAt(-2, 0, 0);
        var input = new InputHandler();
        input.handleMouseMove(100, 20);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        editor.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        editor.update(input);
        assertEquals(1, level.getMap().getValue(0, 12, 2));
        assertEquals(0, level.getMap().getValue(0, 3, 0));
        controller.undo();
        assertEquals(0, level.getMap().getValue(0, 12, 2));
    }

    @Test
    void arrowsStepBlockCentersWithRepeatAndRespectMapEdges() {
        var level = TestEditorInteractionSafety.level();
        var controller = controller(level);
        controller.setCanvasNavigationSupplier(() -> true);
        controller.setWorldCursor(new EditorCursorState(40, 45));
        var editor = new EditorInputHandler(controller);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        editor.update(input); input.update();
        assertEquals(new EditorCursorState(80, 48), controller.worldCursor());
        for (int i = 0; i < 23; i++) { editor.update(input); input.update(); }
        assertEquals(new EditorCursorState(80, 48), controller.worldCursor(), "held keys use menu repeat");
        editor.update(input); input.update();
        assertEquals(new EditorCursorState(112, 48), controller.worldCursor());
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
        editor.update(input); input.update();
        input.handleKeyEvent(GLFW_KEY_LEFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_UP, GLFW_PRESS);
        controller.setWorldCursor(new EditorCursorState(1, 1));
        editor.update(input);
        assertEquals(new EditorCursorState(0, 0), controller.worldCursor());
        assertFalse(controller.hasUndoHistory());
    }

    @Test
    void arrowsUseEightPixelSpawnSteps() {
        var controller = controller(TestEditorInteractionSafety.level());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.setWorldCursor(new EditorCursorState(43, 45));
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_LEFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        new EditorInputHandler(controller).update(input);
        assertEquals(new EditorCursorState(35, 53), controller.worldCursor());
        assertFalse(controller.hasUndoHistory());
    }

    @Test
    void controlCopyDoesNotAlsoDeleteTheSelectedSpawnThroughLogicalActionC() {
        MutableLevel level = TestEditorInteractionSafety.level();
        var object = new CommonObjectPlacementEncoding().create(80, 80, 1, 0, 0, false, 5);
        level.addObjectSpawn(object);
        var controller = controller(level);
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.setWorldCursor(new EditorCursorState(80, 80));
        controller.performEyedrop();
        controller.beginRegionAtCursor();
        controller.finishRegionSelection();
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0,
                InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, false, false), PlayerInputState.neutral()));
        input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_C, GLFW_PRESS);
        new EditorInputHandler(controller).update(input);
        assertEquals(List.of(object), level.getObjects());
        assertEquals(object, controller.selectedObjectSpawn());
        assertFalse(controller.hasUndoHistory());
        controller.beginPastePreview();
        assertTrue(controller.isPastePreviewActive(), "Ctrl+C produced the region clipboard");
    }

    @Test
    void shiftDragSelectsCellsWithoutPaintingAndFinishesOnMouseRelease() {
        MutableLevel level = TestEditorInteractionSafety.level();
        var controller = controller(level);
        controller.selectBlock(3);
        var editor = mouse(controller);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        input.handleMouseMove(40, 40);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        editor.update(input); input.update();
        input.handleMouseMove(104, 72);
        editor.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        editor.update(input);
        assertEquals(new CellRectangle(1, 1, 3, 2), controller.regionSelection());
        assertFalse(controller.isSelectingRegion());
        assertFalse(controller.hasUndoHistory());
        for (int y = 1; y <= 2; y++) for (int x = 1; x <= 3; x++) {
            assertEquals(0, level.getMap().getValue(0, x, y));
        }
    }

    @Test
    void f6SelectionSurvivesMouseUpTicksAndExtendsWithKeyboardMovement() {
        var controller = controller(TestEditorInteractionSafety.level());
        var editor = new EditorInputHandler(controller);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        editor.update(input); input.update();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS);
        for (int i = 0; i < 11; i++) { editor.update(input); input.update(); }
        assertTrue(controller.isSelectingRegion(), "a mouse-up tick must not finish a keyboard selection");
        assertEquals(new CellRectangle(0, 0, 2, 2), controller.regionSelection());
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        editor.update(input);
        assertFalse(controller.isSelectingRegion());
        assertEquals(new CellRectangle(0, 0, 2, 2), controller.regionSelection());
        assertFalse(controller.hasUndoHistory());
    }

    @Test
    void clickingPastePreviewPlacesClipboardOnceWithoutStartingBrushStroke() {
        MutableLevel level = TestEditorInteractionSafety.level();
        level.setBlockInMap(0, 0, 0, 1);
        var controller = controller(level);
        controller.selectBlock(3);
        controller.beginRegionAtCursor();
        controller.finishRegionSelection();
        controller.copyRegion();
        controller.setWorldCursor(new EditorCursorState(80, 80));
        controller.beginPastePreview();
        var editor = mouse(controller);
        var input = new InputHandler();
        input.handleMouseMove(80, 80);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        editor.update(input); input.update();
        assertFalse(controller.isPastePreviewActive());
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        editor.update(input);
        assertEquals(1, level.getMap().getValue(0, 2, 2), "clipboard content wins over active block brush");
        controller.undo();
        assertEquals(0, level.getMap().getValue(0, 2, 2));
        assertFalse(controller.hasUndoHistory(), "paste click is exactly one history entry");
    }

    private static LevelEditorController controller(MutableLevel level) {
        var controller = new LevelEditorController();
        controller.attachLevel(level);
        var registry = mock(ObjectRegistry.class);
        when(registry.getPrimaryName(anyInt())).thenAnswer(call -> "Object " + call.getArgument(0));
        controller.configureSpawnEditing(registry, new CommonObjectPlacementEncoding());
        return controller;
    }

    private static EditorInputHandler mouse(LevelEditorController controller) {
        var graphics = mock(GraphicsManager.class);
        when(graphics.getProjectionWidth()).thenReturn(320);
        when(graphics.getScreenHeight()).thenReturn(224f);
        when(graphics.getViewportWidth()).thenReturn(320);
        when(graphics.getViewportHeight()).thenReturn(224);
        var camera = new Camera(SonicConfigurationService.getInstance());
        camera.setX((short) 0); camera.setY((short) 0);
        return new EditorInputHandler(controller, () -> camera, () -> graphics);
    }
}
