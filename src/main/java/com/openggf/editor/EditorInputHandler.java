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
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
public final class EditorInputHandler {
    /**
     * A host front end that sees each editor tick first, such as an {@link EditorWindowChrome}.
     * {@link #update} returns true when it owned the whole tick.
     */
    public interface FrontEnd {
        boolean update(InputHandler input);
        void finishPendingEdits();
    }

    private FrontEnd frontEnd;

    public enum Action {
        DESCEND,
        ASCEND,
        APPLY_PRIMARY_ACTION,
        PERFORM_EYEDROP,
        TOGGLE_LAYER,
        SAVE,
        EXPORT,
        UNDO,
        REDO,
        CYCLE_SPAWN_EDIT_MODE,
        NEXT_OBJECT,
        PREVIOUS_OBJECT,
        INCREMENT_SUBTYPE,
        DECREMENT_SUBTYPE,
        DELETE_SPAWN,
        MOVE_SELECTED_SPAWN_TO_CURSOR,
        TOGGLE_COLLISION_OVERLAY,
        TOGGLE_COLLISION_PATH,
        CYCLE_COLLISION_MODE,
        INCREMENT_SOLID_TILE_INDEX,
        DECREMENT_SOLID_TILE_INDEX,
        ERASE_SELECTION,
        TOGGLE_H_FLIP,
        TOGGLE_V_FLIP,
        CYCLE_PATTERN_PALETTE,
        TOGGLE_PATTERN_PRIORITY,
        INCREMENT_SELECTED_SUBTYPE,
        DECREMENT_SELECTED_SUBTYPE,
        TOGGLE_SELECTED_RESPAWN,
        TOGGLE_BRUSH_H_FLIP,
        TOGGLE_BRUSH_V_FLIP,
        TOGGLE_BRUSH_RESPAWN
    }

    private static final int WORLD_MOVE_SPEED = 3;

    private final LevelEditorController controller;
    private final Supplier<Camera> cameraSupplier;
    private final Supplier<GraphicsManager> graphicsSupplier;
    private final Runnable saveAction;
    private final Runnable exportAction;
    private DragStroke activeStroke;
    private double previousMouseX = Double.NaN;
    private double previousMouseY = Double.NaN;

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
        this(controller, cameraSupplier, graphicsSupplier, saveAction, () -> {});
    }

    public EditorInputHandler(LevelEditorController controller,
                              Supplier<Camera> cameraSupplier,
                              Supplier<GraphicsManager> graphicsSupplier,
                              Runnable saveAction,
                              Runnable exportAction) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.cameraSupplier = Objects.requireNonNull(cameraSupplier, "cameraSupplier");
        this.graphicsSupplier = Objects.requireNonNull(graphicsSupplier, "graphicsSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        this.exportAction = Objects.requireNonNull(exportAction, "exportAction");
        controller.setPendingEditsFinisher(this::finishPendingEdits);
    }

    public void update(InputHandler inputHandler) {
        Objects.requireNonNull(inputHandler, "inputHandler");
        if (frontEnd != null && frontEnd.update(inputHandler)) {
            finishActiveStroke();
            return;
        }
        handleMouseInput(inputHandler);
        var logical = inputHandler.logical();
        int logicalActions = logical.player1().actionPressedMask();
        int dx = 0;
        int dy = 0;
        if (inputHandler.isDirectionHeld(GLFW_KEY_LEFT, AbstractPlayableSprite.INPUT_LEFT)) dx -= 1;
        if (inputHandler.isDirectionHeld(GLFW_KEY_RIGHT, AbstractPlayableSprite.INPUT_RIGHT)) dx += 1;
        if (inputHandler.isDirectionHeld(GLFW_KEY_UP, AbstractPlayableSprite.INPUT_UP)) dy -= 1;
        if (inputHandler.isDirectionHeld(GLFW_KEY_DOWN, AbstractPlayableSprite.INPUT_DOWN)) dy += 1;
        if ((dx != 0 || dy != 0) && activeStroke == null) {
            if (controller.depth() == EditorHierarchyDepth.WORLD) {
                controller.moveWorldCursor(dx * WORLD_MOVE_SPEED, dy * WORLD_MOVE_SPEED);
            } else {
                controller.moveActiveSelection(dx, dy);
            }
        }
        boolean controlDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_CONTROL)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
        boolean shiftDown = inputHandler.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || inputHandler.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        boolean exportChord = controlDown && shiftDown && inputHandler.isKeyPressed(GLFW_KEY_E);
        boolean rawPrimary = inputHandler.isKeyPressed(GLFW_KEY_SPACE);
        boolean rawEyedrop = !exportChord && inputHandler.isKeyPressed(GLFW_KEY_E);
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
        if (rawDelete && !controller.isSpawnEditing()) handleAction(Action.ERASE_SELECTION);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_H))handleAction(Action.TOGGLE_H_FLIP);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_J))handleAction(Action.TOGGLE_V_FLIP);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_G))handleAction(Action.CYCLE_PATTERN_PALETTE);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_T))handleAction(Action.TOGGLE_PATTERN_PRIORITY);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL))handleAction(Action.INCREMENT_SELECTED_SUBTYPE);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS))handleAction(Action.DECREMENT_SELECTED_SUBTYPE);
        if(inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_R))handleAction(Action.TOGGLE_SELECTED_RESPAWN);
        if (inputHandler.isKeyPressed(GLFW_KEY_M)) {
            handleAction(Action.MOVE_SELECTED_SPAWN_TO_CURSOR);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_C)) {
            handleAction(Action.TOGGLE_COLLISION_OVERLAY);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_P)) {
            handleAction(Action.TOGGLE_COLLISION_PATH);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_V)) {
            handleAction(Action.CYCLE_COLLISION_MODE);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_RIGHT_BRACKET)) {
            handleAction(Action.INCREMENT_SOLID_TILE_INDEX);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_LEFT_BRACKET)) {
            handleAction(Action.DECREMENT_SOLID_TILE_INDEX);
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
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_Z)) {
            handleAction(Action.UNDO);
        }
        if (controlDown && inputHandler.isKeyPressed(GLFW_KEY_S)) {
            handleAction(Action.SAVE);
        }
        if (exportChord) {
            handleAction(Action.EXPORT);
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
        finishPendingEdits();
        try {
        switch (action) {
            case DESCEND -> controller.descend();
            case ASCEND -> controller.ascend();
            case APPLY_PRIMARY_ACTION -> controller.applyPrimaryAction();
            case PERFORM_EYEDROP -> controller.performEyedrop();
            case TOGGLE_LAYER -> controller.toggleActiveLayer();
            case SAVE -> saveAction.run();
            case EXPORT -> exportAction.run();
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
            case TOGGLE_COLLISION_OVERLAY -> controller.toggleCollisionOverlay();
            case TOGGLE_COLLISION_PATH -> controller.toggleCollisionPath();
            case CYCLE_COLLISION_MODE -> controller.cycleSelectedCellCollisionMode();
            case INCREMENT_SOLID_TILE_INDEX -> controller.adjustSelectedChunkSolidTileIndex(1);
            case DECREMENT_SOLID_TILE_INDEX -> controller.adjustSelectedChunkSolidTileIndex(-1);
            case ERASE_SELECTION -> controller.eraseSelection();
            case TOGGLE_H_FLIP -> {if(controller.isSpawnEditing())controller.toggleSelectedSpawnFlag(1);else controller.toggleDescriptorHFlip();}
            case TOGGLE_V_FLIP -> {if(controller.isSpawnEditing())controller.toggleSelectedSpawnFlag(2);else controller.toggleDescriptorVFlip();}
            case CYCLE_PATTERN_PALETTE -> controller.cyclePatternPalette();
            case TOGGLE_PATTERN_PRIORITY -> controller.togglePatternPriority();
            case INCREMENT_SELECTED_SUBTYPE -> controller.adjustSelectedSpawnSubtype(1);
            case DECREMENT_SELECTED_SUBTYPE -> controller.adjustSelectedSpawnSubtype(-1);
            case TOGGLE_SELECTED_RESPAWN -> controller.toggleSelectedSpawnRespawn();
            case TOGGLE_BRUSH_H_FLIP -> controller.objectPalette().toggleRenderFlag(1);
            case TOGGLE_BRUSH_V_FLIP -> controller.objectPalette().toggleRenderFlag(2);
            case TOGGLE_BRUSH_RESPAWN -> controller.objectPalette().toggleRespawnTracked();
        }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            controller.setStatus(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
        }
    }

    public void finishPendingEdits() {
        finishActiveStroke();
        if (frontEnd != null) frontEnd.finishPendingEdits();
    }

    public void setFrontEnd(FrontEnd frontEnd) { this.frontEnd = frontEnd; }

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
        boolean leftDown = inputHandler.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (!leftDown) finishActiveStroke();
        if (!inputHandler.hasMouseInputSeen()) return;
        MutableLevel level = controller.currentLevel();
        Camera camera = cameraSupplier.get();
        GraphicsManager graphics = graphicsSupplier.get();
        if (level == null || camera == null || graphics == null) return;
        double mouseX = inputHandler.getMouseX(), mouseY = inputHandler.getMouseY();
        boolean moved = mouseX != previousMouseX || mouseY != previousMouseY;
        previousMouseX = mouseX; previousMouseY = mouseY;
        EditorMouseTransform.Result hover = EditorMouseTransform.toWorldTile(inputHandler,camera,graphics,level);
        if (!hover.inViewport()) return;
        // Composition cells belong to the HUD or desktop workbench, outside the game canvas.
        if (controller.depth() != EditorHierarchyDepth.WORLD) return;
        boolean click = inputHandler.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean pick = inputHandler.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT);
        if (moved || click || pick || activeStroke != null)
            controller.setPointerWorldCursor(new com.openggf.game.session.EditorCursorState(hover.worldX(),hover.worldY()));
        if ((click || pick)&&controller.focusRegion()!=EditorFocusRegion.WORLD_CANVAS) controller.focusRegion(EditorFocusRegion.WORLD_CANVAS);
        if (pick) controller.performEyedrop();
        if (controller.isSpawnEditing()) {
            if (click) handleAction(Action.APPLY_PRIMARY_ACTION);
            return;
        }
        if (click && activeStroke == null) activeStroke = new DragStroke(level,controller.activeLayer());
        if (leftDown && activeStroke != null)
            activeStroke.record(level,hover.tileX(),hover.tileY(),controller.selectedBlockIndex());
    }

    private static final class DragStroke {
        private final int layer;
        private final Map<CellKey, StrokeCommand.CellDelta> deltas = new LinkedHashMap<>();

        private final MutableLevel owner;

        private DragStroke(MutableLevel owner, int layer) {
            this.owner = owner;
            this.layer = layer;
        }

        private void record(MutableLevel level, int x, int y, Integer selectedBlock) {
            if (level != owner || selectedBlock == null) {
                return;
            }
            CellKey key = new CellKey(layer, x, y);
            deltas.computeIfAbsent(key, ignored -> {
                int before = Byte.toUnsignedInt(level.getMap().getValue(layer, x, y));
                return before == selectedBlock ? null : new StrokeCommand.CellDelta(layer, x, y, before, selectedBlock);
            });
        }

        private StrokeCommand toCommand(MutableLevel level) {
            return new StrokeCommand(owner, java.util.List.copyOf(deltas.values()));
        }
    }

    private record CellKey(int layer, int x, int y) {
    }
}
