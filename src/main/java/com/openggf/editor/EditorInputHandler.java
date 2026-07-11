package com.openggf.editor;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.editor.commands.StrokeCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.MutableLevel;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public final class EditorInputHandler {
    public enum Action {
        DESCEND,
        ASCEND,
        CYCLE_FOCUS_REGION,
        APPLY_PRIMARY_ACTION,
        PERFORM_EYEDROP,
        TOGGLE_LAYER,
        SAVE,
        UNDO,
        REDO,
        CYCLE_SPAWN_EDIT_MODE,
        NEXT_OBJECT,
        PREVIOUS_OBJECT,
        INCREMENT_SUBTYPE,
        DECREMENT_SUBTYPE,
        DELETE_SPAWN,
        MOVE_SELECTED_SPAWN_TO_CURSOR
    }

    private static final int WORLD_MOVE_SPEED = 3;

    private final LevelEditorController controller;
    private final Supplier<Camera> cameraSupplier;
    private final Supplier<GraphicsManager> graphicsSupplier;
    private final Runnable saveAction;
    private DragStroke activeStroke;

    public EditorInputHandler(LevelEditorController controller) {
        this(controller, () -> null, () -> null, () -> {});
    }

    public EditorInputHandler(LevelEditorController controller,
                              Supplier<Camera> cameraSupplier,
                              Supplier<GraphicsManager> graphicsSupplier) {
        this(controller, cameraSupplier, graphicsSupplier, () -> {});
    }

    public EditorInputHandler(LevelEditorController controller,
                              Supplier<Camera> cameraSupplier,
                              Supplier<GraphicsManager> graphicsSupplier,
                              Runnable saveAction) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.cameraSupplier = Objects.requireNonNull(cameraSupplier, "cameraSupplier");
        this.graphicsSupplier = Objects.requireNonNull(graphicsSupplier, "graphicsSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    public void update(InputHandler inputHandler) {
        Objects.requireNonNull(inputHandler, "inputHandler");
        handleMouseInput(inputHandler);
        var logical = inputHandler.logical();
        int logicalActions = logical.player1().actionPressedMask();
        int dx = 0;
        int dy = 0;
        if (controller.focusRegion() == EditorFocusRegion.SPAWN_PALETTE
                && controller.spawnEditMode() == EditorSpawnEditMode.OBJECTS) {
            if (inputHandler.isKeyPressed(GLFW_KEY_LEFT) || logical.menuLeft()) handleAction(Action.PREVIOUS_OBJECT);
            if (inputHandler.isKeyPressed(GLFW_KEY_RIGHT) || logical.menuRight()) handleAction(Action.NEXT_OBJECT);
            if (inputHandler.isKeyPressed(GLFW_KEY_UP) || logical.menuUp()) handleAction(Action.INCREMENT_SUBTYPE);
            if (inputHandler.isKeyPressed(GLFW_KEY_DOWN) || logical.menuDown()) handleAction(Action.DECREMENT_SUBTYPE);
        } else {
            if (inputHandler.isDirectionHeld(GLFW_KEY_LEFT, AbstractPlayableSprite.INPUT_LEFT)) dx -= 1;
            if (inputHandler.isDirectionHeld(GLFW_KEY_RIGHT, AbstractPlayableSprite.INPUT_RIGHT)) dx += 1;
            if (inputHandler.isDirectionHeld(GLFW_KEY_UP, AbstractPlayableSprite.INPUT_UP)) dy -= 1;
            if (inputHandler.isDirectionHeld(GLFW_KEY_DOWN, AbstractPlayableSprite.INPUT_DOWN)) dy += 1;
        }
        if ((dx != 0 || dy != 0) && activeStroke == null) {
            if (controller.depth() == EditorHierarchyDepth.WORLD) {
                controller.moveWorldCursor(dx * WORLD_MOVE_SPEED, dy * WORLD_MOVE_SPEED);
            } else {
                controller.moveActiveSelection(dx, dy);
            }
        }
        boolean shiftDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        if (inputHandler.isKeyPressed(GLFW_KEY_TAB) && !shiftDown) {
            handleAction(Action.CYCLE_FOCUS_REGION);
        }
        boolean rawPrimary = inputHandler.isKeyPressed(GLFW_KEY_SPACE);
        boolean rawEyedrop = inputHandler.isKeyPressed(GLFW_KEY_E);
        boolean rawModeCycle = inputHandler.isKeyPressed(GLFW_KEY_O);
        boolean rawDelete = inputHandler.isKeyPressed(GLFW_KEY_DELETE);
        if (rawPrimary && !controller.isSpawnEditing()) {
            handleAction(Action.APPLY_PRIMARY_ACTION);
        }
        if (rawEyedrop && !controller.isSpawnEditing()) {
            handleAction(Action.PERFORM_EYEDROP);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_L)) {
            handleAction(Action.TOGGLE_LAYER);
        }
        if (rawModeCycle || logical.menuStart()) {
            handleAction(Action.CYCLE_SPAWN_EDIT_MODE);
        }
        if (rawDelete && !controller.isSpawnEditing()) {
            handleAction(Action.DELETE_SPAWN);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_M)) {
            handleAction(Action.MOVE_SELECTED_SPAWN_TO_CURSOR);
        }
        if (controller.isSpawnEditing()) {
            if (rawPrimary || (logicalActions & InputActionMasks.ACTION_A) != 0) {
                handleAction(Action.APPLY_PRIMARY_ACTION);
            }
            if (rawEyedrop || (logicalActions & InputActionMasks.ACTION_B) != 0) {
                handleAction(Action.PERFORM_EYEDROP);
            }
            if (rawDelete || (logicalActions & InputActionMasks.ACTION_C) != 0) {
                handleAction(Action.DELETE_SPAWN);
            }
        }
        boolean controlDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_CONTROL)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_Z)) {
            handleAction(Action.UNDO);
        }
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_S)) {
            handleAction(Action.SAVE);
        }
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_Y)) {
            handleAction(Action.REDO);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_ENTER)) {
            handleAction(Action.DESCEND);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_ESCAPE)) {
            handleAction(Action.ASCEND);
        }
    }

    public void handleAction(Action action) {
        Objects.requireNonNull(action, "action");
        switch (action) {
            case DESCEND -> controller.descend();
            case ASCEND -> controller.ascend();
            case CYCLE_FOCUS_REGION -> controller.cycleFocusRegion();
            case APPLY_PRIMARY_ACTION -> controller.applyPrimaryAction();
            case PERFORM_EYEDROP -> controller.performEyedrop();
            case TOGGLE_LAYER -> controller.toggleActiveLayer();
            case SAVE -> saveAction.run();
            case UNDO -> controller.undo();
            case REDO -> controller.redo();
            case CYCLE_SPAWN_EDIT_MODE -> controller.cycleSpawnEditMode();
            case NEXT_OBJECT -> controller.objectPalette().navigate(EditorStockObjectPalette.Navigation.NEXT_OBJECT);
            case PREVIOUS_OBJECT -> controller.objectPalette().navigate(EditorStockObjectPalette.Navigation.PREVIOUS_OBJECT);
            case INCREMENT_SUBTYPE -> controller.objectPalette().navigate(EditorStockObjectPalette.Navigation.INCREMENT_SUBTYPE);
            case DECREMENT_SUBTYPE -> controller.objectPalette().navigate(EditorStockObjectPalette.Navigation.DECREMENT_SUBTYPE);
            case DELETE_SPAWN -> controller.deleteSpawnAtCursor();
            case MOVE_SELECTED_SPAWN_TO_CURSOR -> controller.moveSelectedSpawn(
                    controller.worldCursor().x(), controller.worldCursor().y());
        }
    }

    public void finishActiveStroke() {
        if (activeStroke == null) {
            return;
        }
        MutableLevel level = controller.currentLevel();
        StrokeCommand command = level != null ? activeStroke.toCommand(level) : null;
        activeStroke = null;
        if (command != null && !command.isEmpty()) {
            controller.executeCommand(command);
        }
    }

    private void handleMouseInput(InputHandler inputHandler) {
        if (!inputHandler.hasMouseInputSeen()) {
            return;
        }
        MutableLevel level = controller.currentLevel();
        Camera camera = cameraSupplier.get();
        GraphicsManager graphics = graphicsSupplier.get();
        if (level == null || camera == null || graphics == null) {
            return;
        }

        EditorMouseTransform.Result hover = EditorMouseTransform.toWorldTile(inputHandler, camera, graphics, level);
        if (hover.inViewport()) {
            controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(hover.worldX(), hover.worldY()));
        }

        if (inputHandler.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT) && hover.inViewport()) {
            controller.performEyedrop();
        }

        if (controller.isSpawnEditing()) {
            if (inputHandler.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT) && hover.inViewport()) {
                controller.placeSpawnAtCursor();
            }
            return;
        }

        boolean leftDown = inputHandler.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (leftDown && activeStroke == null && hover.inViewport()) {
            activeStroke = new DragStroke(controller.activeLayer());
        }
        if (leftDown && activeStroke != null && hover.inViewport()) {
            activeStroke.record(level, hover.tileX(), hover.tileY(), controller.selectedBlockIndex());
        }
        if (!leftDown && activeStroke != null) {
            finishActiveStroke();
        }
    }

    private static final class DragStroke {
        private final int layer;
        private final Map<CellKey, StrokeCommand.CellDelta> deltas = new LinkedHashMap<>();

        private DragStroke(int layer) {
            this.layer = layer;
        }

        private void record(MutableLevel level, int x, int y, Integer selectedBlock) {
            if (selectedBlock == null) {
                return;
            }
            CellKey key = new CellKey(layer, x, y);
            deltas.computeIfAbsent(key, ignored -> {
                int before = Byte.toUnsignedInt(level.getMap().getValue(layer, x, y));
                return new StrokeCommand.CellDelta(layer, x, y, before, selectedBlock);
            });
        }

        private StrokeCommand toCommand(MutableLevel level) {
            return new StrokeCommand(level, java.util.List.copyOf(deltas.values()));
        }
    }

    private record CellKey(int layer, int x, int y) {
    }
}
