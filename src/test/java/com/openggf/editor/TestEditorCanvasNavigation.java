package com.openggf.editor;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MouseWheel;
import com.openggf.editor.render.EditorCanvasRenderPass;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

class TestEditorCanvasNavigation {
    @Test
    void zoomKeepsPointerWorldPointAndPanUsesVisiblePixels() {
        var view = new EditorCanvasNavigation();
        view.zoomAt(1, 120, 80);
        var pan = view.consumePan();
        assertEquals(2, view.zoom());
        assertEquals(120, pan.dx() + view.worldOffset(120));
        assertEquals(80, pan.dy() + view.worldOffset(80));
        view.panBy(32, -24);
        assertEquals(new EditorCanvasNavigation.Pan(16, -12), view.consumePan());
        assertFalse(view.consumePan().moved());
    }

    @Test
    void quarterZoomIsLowerBoundAndResetPreservesVisibleCenter() {
        var view = new EditorCanvasNavigation();
        view.setViewportSize(640, 224);
        view.zoomOut();
        assertEquals(.5, view.zoom());
        assertEquals("0.5", view.zoomLabel());
        assertEquals(new EditorCanvasNavigation.Pan(-320, -112), view.consumePan());
        view.zoomOut();
        assertEquals(.25, view.zoom());
        assertEquals("0.25", view.zoomLabel());
        assertEquals(new EditorCanvasNavigation.Pan(-640, -224), view.consumePan());
        view.zoomOut();
        assertFalse(view.consumePan().moved());
        view.resetZoom();
        assertEquals(1, view.zoom());
        assertEquals("1", view.zoomLabel());
        assertEquals(new EditorCanvasNavigation.Pan(960, 336), view.consumePan());
        view.zoomIn();
        assertEquals(new EditorCanvasNavigation.Pan(160, 56), view.consumePan());
        view.zoomAt(Integer.MAX_VALUE, 0, 0);
        assertEquals(8, view.zoom());
        view.resetZoom();
        assertEquals(1, view.zoom());
        assertEquals(new EditorCanvasNavigation.Pan(-280, -98), view.consumePan());
        view.zoomAt(Integer.MIN_VALUE, 0, 0);
        assertEquals(.25, view.zoom());
        view.panBy(20, 10);
        view.reset();
        assertEquals(1, view.zoom());
        assertFalse(view.consumePan().moved());
    }

    @Test
    void zoomingOutKeepsPointerWorldPointAndScalesPan() {
        var view = new EditorCanvasNavigation();
        view.zoomAt(-2, 120, 80);
        var pan = view.consumePan();
        assertEquals(.25, view.zoom());
        assertEquals(120, pan.dx() + view.worldOffset(120));
        assertEquals(80, pan.dy() + view.worldOffset(80));
        view.panBy(32, -24);
        assertEquals(new EditorCanvasNavigation.Pan(128, -96), view.consumePan());
    }

    @Test
    void fractionalPanningAccumulatesAndRejectsNonfiniteInput() {
        var view = new EditorCanvasNavigation();
        view.zoomAt(7, 0, 0);
        for (int i = 0; i < 7; i++) {
            view.panBy(1, -1);
            assertFalse(view.consumePan().moved());
        }
        view.panBy(1, -1);
        assertEquals(new EditorCanvasNavigation.Pan(1, -1), view.consumePan());
        assertThrows(IllegalArgumentException.class, () -> view.panBy(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> view.zoomAt(1, 0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> view.setViewportSize(0, 224));
    }

    @Test
    void keyboardPanDoesNotMoveAuthoringCursorOrCreateHistory() {
        var controller = new LevelEditorController();
        var input = new InputHandler();
        var handler = new EditorInputHandler(controller);
        handler.handleAction(EditorInputHandler.Action.PAN_RIGHT);
        handler.handleAction(EditorInputHandler.Action.PAN_UP);
        assertEquals(new EditorCanvasNavigation.Pan(32, -32), controller.canvasView().consumePan());
        input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        handler.update(input);
        assertEquals(new EditorCanvasNavigation.Pan(3, 0), controller.canvasView().consumePan());
        assertEquals(0, controller.worldCursor().x());
        assertEquals(0, controller.worldCursor().y());
        assertFalse(controller.hasUndoHistory());
    }

    @Test
    void wheelAndMiddleDragUseCanvasWithoutPainting() {
        Level source = mock(Level.class);
        when(source.getMap()).thenReturn(new Map(1, 8, 8));
        when(source.getObjects()).thenReturn(List.of());
        when(source.getRings()).thenReturn(List.of());
        when(source.getBlockPixelSize()).thenReturn(32);
        var controller = new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(source));
        GraphicsManager graphics = mock(GraphicsManager.class);
        when(graphics.getProjectionWidth()).thenReturn(320);
        when(graphics.getScreenHeight()).thenReturn(224f);
        when(graphics.getViewportWidth()).thenReturn(320);
        when(graphics.getViewportHeight()).thenReturn(224);
        var camera = new Camera(SonicConfigurationService.getInstance());
        var handler = new EditorInputHandler(controller, () -> camera, () -> graphics);
        var input = new InputHandler();
        input.handleMouseMove(100, 80);
        MouseWheel.of(input).scroll(1);
        assertTrue(handler.handleCanvasNavigation(input));
        assertEquals(2, controller.canvasView().zoom());
        assertEquals(new EditorCanvasNavigation.Pan(50, 40), controller.canvasView().consumePan());
        input.handleMouseButton(GLFW_MOUSE_BUTTON_MIDDLE, GLFW_PRESS);
        assertTrue(handler.handleCanvasNavigation(input));
        input.handleMouseMove(80, 60);
        assertTrue(handler.handleCanvasNavigation(input));
        assertEquals(new EditorCanvasNavigation.Pan(10, 10), controller.canvasView().consumePan());
        assertFalse(controller.hasUndoHistory());
        assertEquals(0, controller.worldCursor().x());
        input.handleMouseButton(GLFW_MOUSE_BUTTON_MIDDLE, GLFW_RELEASE);
        input.handleMouseMove(400, 60);
        MouseWheel.of(input).scroll(1);
        assertFalse(handler.handleCanvasNavigation(input));
        assertEquals(2, controller.canvasView().zoom(), "wheel outside canvas is discarded");
    }

    @Test
    void nativeRenderPathRunsProducerWithoutTouchingGraphics() {
        var graphics = mock(GraphicsManager.class);
        Runnable draw = mock(Runnable.class);
        new EditorCanvasRenderPass().render(graphics, 1, 320, 224, draw);
        verify(draw).run();
        verifyNoInteractions(graphics);
    }
}
