package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.EditorFocusRegion;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorInputHandler.Action;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.commands.StrokeCommand;
import com.openggf.game.session.EditorCursorState;
import com.openggf.level.Block;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static com.openggf.editor.hud.EditorHudPad.Button.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Controller-first editor front end. A pad drives everything without menus of menus: the stick
 * moves the cursor, South paints (hold to drag a stroke that undoes as one edit), West picks,
 * North opens the library sheet, the bumpers cycle a ten-slot brush hotbar, the triggers zoom the
 * hierarchy (world, block, chunk), View holds a radial wheel of contextual tools and Menu lists
 * every command. Keyboard and mouse keep their existing bindings; the HUD adds Tab (library),
 * Q (tools), F1 (menu) and 1-0 (hotbar). All edits dispatch the existing controller actions.
 */
public final class EditorHud {
    public enum LibraryKind { BLOCK, CHUNK, PATTERN, OBJECT, NONE }
    enum Modal { NONE, LIBRARY, WHEEL, MENU }

    /** Which control a prompt names; drawn as a pad glyph or a keyboard keycap. */
    enum Glyph { SOUTH, EAST, WEST, NORTH, LB, RB, LT, RT, VIEW, MENU, DPAD, LSTICK, BUMPERS }

    record Prompt(Glyph glyph, String key, String label) { }
    record WheelItem(String label, String detail, Runnable action) { }
    record MenuItem(String label, String detail, Runnable action) {
        boolean header() { return action == null; }
    }
    /** Pointer target recorded by the painter for the next update. */
    record Hit(EditorHudLayout.Rect rect, Modal modal, int kind, int index) { }

    static final int HIT_HOTBAR = 0;
    static final int HIT_LIBRARY = 1;
    static final int HIT_MENU = 2;
    static final int HIT_WHEEL = 3;
    static final int HIT_CELL = 4;
    private static final float CURSOR_SPEED = 7f;
    private static final float FAST_CURSOR_SPEED = 20f;
    /** Status messages stay readable for three seconds at 60 frames per second. */
    private static final int TOAST_FRAMES = 180;

    private final LevelEditorController controller;
    final EditorHudPad pad = new EditorHudPad();
    final EditorHudHotbar hotbar = new EditorHudHotbar();
    private Consumer<Action> dispatch = action -> { };
    private MutableLevel seededLevel;

    Modal modal = Modal.NONE;
    long tick;
    boolean controllerPrompts;
    // Library sheet
    LibraryKind libraryKind = LibraryKind.NONE;
    int libraryCursor;
    float libraryScroll;
    int libraryColumns = 8;
    int libraryVisibleRows = 4;
    // Wheel
    List<WheelItem> wheelItems = List.of();
    int wheelSlot = -1;
    private boolean wheelChosenByStick;
    private boolean wheelOpenedByHold;
    // Menu
    List<MenuItem> menuItems = List.of();
    int menuIndex;
    float menuScroll;
    // Status toast
    String toast;
    int toastFrames;
    private String lastStatus;
    // Pointer
    List<Hit> hits = List.of();
    private double pointerUnits = Double.NaN;
    private double pointerX = Double.NaN;
    private double pointerY = Double.NaN;
    // Painting
    private PadStroke stroke;
    private int paintedCell = -1;
    private EditorHierarchyDepth lastDepth;
    private float cursorRemainderX;
    private float cursorRemainderY;
    boolean contentChanged;

    public EditorHud(LevelEditorController controller) {
        this.controller = controller;
    }

    LevelEditorController controller() { return controller; }

    /** Window pixels to UI units, refreshed by the runtime each frame. */
    void setPointerScale(double unitsPerWindowPixel) { pointerUnits = unitsPerWindowPixel; }

    /**
     * Runs one editor tick. Returns true when the HUD owned the whole frame (a sheet, wheel or menu
     * is open, or it consumed a shortcut), so the world editing input handler must not also act on it.
     */
    public boolean update(InputHandler input, Consumer<Action> dispatch) {
        this.dispatch = dispatch;
        tick++;
        pad.sample(input.capturePhysicalInput().gamepads());
        controllerPrompts = MenuInput.controller(input);
        trackStatus();
        MutableLevel level = controller.currentLevel();
        if (level == null) return false;
        seedHotbars(level);
        syncHotbarFromController();
        settleDepth();
        LibraryKind kind = kind();
        // Entering a block or chunk without a brush adopts the hotbar's highlighted entry.
        if (kind != LibraryKind.NONE && currentBrush(kind) < 0) applyBrush(kind, hotbar.value(kind));
        if (!Double.isNaN(pointerUnits) && input.hasMouseInputSeen()) {
            pointerX = input.getMouseX() * pointerUnits;
            pointerY = input.getMouseY() * pointerUnits;
        }
        boolean click = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean rightClick = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT);
        return switch (modal) {
            case LIBRARY -> { updateLibrary(input, click); yield true; }
            case WHEEL -> { updateWheel(input, click); yield true; }
            case MENU -> { updateMenu(input, click); yield true; }
            case NONE -> updateCanvas(input, level, click, rightClick);
        };
    }

    /** Called when the HUD stops owning the editor, or before another edit commits. */
    public void finishPendingEdits() {
        PadStroke finishing = stroke;
        stroke = null;
        paintedCell = -1;
        if (finishing != null) finishing.commit(controller);
    }

    /** The HUD owns the editor again after a project menu, import screen or playtest. */
    void reactivated() {
        pad.resync();
        lastPointerX = pointerX;
        lastPointerY = pointerY;
    }

    // ------------------------------------------------------------------ canvas

    private boolean updateCanvas(InputHandler input, MutableLevel level, boolean click, boolean rightClick) {
        if (click || rightClick) {
            Hit hit = hitAt(Modal.NONE);
            if (hit != null && hit.kind() == HIT_HOTBAR) {
                hotbar.select(kind(), hit.index());
                applyBrush(kind(), hotbar.value(kind()));
                return true;
            }
            if (hit != null && hit.kind() == HIT_CELL) {
                int gx = hit.index() & 0xFF, gy = hit.index() >>> 8;
                focusWorkbench();
                controller.selectActiveCell(gx, gy);
                act(rightClick ? Action.PERFORM_EYEDROP : Action.APPLY_PRIMARY_ACTION);
                return true;
            }
        }
        boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        if (input.isKeyPressed(GLFW_KEY_TAB) && !shift) { openLibrary(); return true; }
        if (input.isKeyPressed(GLFW_KEY_F1)) { openMenu(); return true; }
        if (input.isKeyPressed(GLFW_KEY_Q)) { openWheel(false); return true; }
        for (int slot = 0; slot < EditorHudHotbar.SLOTS; slot++) {
            if (input.isKeyPressed(slot == 9 ? GLFW_KEY_0 : GLFW_KEY_1 + slot)) {
                hotbar.select(kind(), slot);
                applyBrush(kind(), hotbar.value(kind()));
            }
        }
        // Target the editing canvas for the current composition depth.
        if (controller.depth() == EditorHierarchyDepth.WORLD) {
            if (controller.focusRegion() != EditorFocusRegion.WORLD_CANVAS) controller.focusRegion(EditorFocusRegion.WORLD_CANVAS);
        } else {
            focusWorkbench();
        }
        if (!pad.connected()) return false;
        if (pad.pressed(MENU)) { finishPendingEdits(); openMenu(); return true; }
        if (pad.pressed(NORTH)) { finishPendingEdits(); openLibrary(); return true; }
        if (pad.pressed(VIEW)) { finishPendingEdits(); openWheel(true); return true; }
        if (pad.pressed(LB)) cycleBrush(-1);
        if (pad.pressed(RB)) cycleBrush(1);
        if (pad.pressed(RT)) descend();
        if (pad.pressed(LT) || pad.pressed(EAST)) act(Action.ASCEND);
        if (pad.pressed(L3)) act(Action.TOGGLE_LAYER);
        if (pad.pressed(R3)) act(Action.TOGGLE_COLLISION_OVERLAY);
        // A South held through a zoom must not paint the new view's first cell.
        if (settleDepth()) return false;
        if (controller.depth() == EditorHierarchyDepth.WORLD) updateWorldPad(level);
        else updateWorkbenchPad();
        return false;
    }

    private void updateWorldPad(MutableLevel level) {
        float[] left = pad.leftStick(), right = pad.rightStick();
        float speed = CURSOR_SPEED * curve(left);
        float fast = FAST_CURSOR_SPEED * curve(right);
        cursorRemainderX += left[0] * speed + right[0] * fast;
        cursorRemainderY += left[1] * speed + right[1] * fast;
        int dx = (int) cursorRemainderX, dy = (int) cursorRemainderY;
        cursorRemainderX -= dx;
        cursorRemainderY -= dy;
        if (dx != 0 || dy != 0) controller.moveWorldCursor(dx, dy);
        int stepX = 0, stepY = 0;
        if (pad.repeated(DPAD_LEFT)) stepX--;
        if (pad.repeated(DPAD_RIGHT)) stepX++;
        if (pad.repeated(DPAD_UP)) stepY--;
        if (pad.repeated(DPAD_DOWN)) stepY++;
        if (stepX != 0 || stepY != 0) {
            EditorCursorState cursor = controller.worldCursor();
            if (controller.isSpawnEditing()) {
                controller.setWorldCursor(new EditorCursorState(cursor.x() + stepX * 8, cursor.y() + stepY * 8));
            } else {
                // Terrain paints whole blocks, so the D-pad steps from block centre to block centre.
                int size = level.getBlockPixelSize();
                controller.setWorldCursor(new EditorCursorState(
                        (Math.floorDiv(cursor.x(), size) + stepX) * size + size / 2,
                        (Math.floorDiv(cursor.y(), size) + stepY) * size + size / 2));
            }
        }
        if (pad.pressed(WEST)) act(Action.PERFORM_EYEDROP);
        if (controller.isSpawnEditing()) {
            if (pad.pressed(SOUTH)) act(Action.APPLY_PRIMARY_ACTION);
            return;
        }
        if (pad.pressed(SOUTH)) {
            finishPendingEdits();
            stroke = new PadStroke(level, controller.activeLayer());
        }
        if (stroke != null && pad.held(SOUTH)) {
            Integer brush = controller.selectedBlockIndex();
            if (brush == null) {
                controller.setStatus("Choose a block from the library first");
                stroke = null;
            } else {
                EditorCursorState cursor = controller.worldCursor();
                int size = level.getBlockPixelSize();
                stroke.paint(Math.floorDiv(cursor.x(), size), Math.floorDiv(cursor.y(), size), brush);
            }
        }
        if (stroke != null && !pad.held(SOUTH)) {
            finishPendingEdits();
            contentChanged = true;
        }
    }

    private void updateWorkbenchPad() {
        int dx = pad.navX(), dy = pad.navY();
        if (dx != 0 || dy != 0) controller.moveActiveSelection(dx, dy);
        int cell = controller.depth() == EditorHierarchyDepth.BLOCK
                ? controller.selectedBlockCellY() << 8 | controller.selectedBlockCellX()
                : controller.selectedChunkCellY() << 8 | controller.selectedChunkCellX();
        if (pad.pressed(SOUTH) || (pad.held(SOUTH) && cell != paintedCell)) {
            act(Action.APPLY_PRIMARY_ACTION);
            paintedCell = cell;
        }
        if (!pad.held(SOUTH)) paintedCell = -1;
        if (pad.pressed(WEST)) act(Action.PERFORM_EYEDROP);
    }

    /** Edits the block or chunk under the cursor. */
    private void descend() {
        // The controller enters a chunk only with a chunk selected; the hotbar's brush counts.
        if (controller.depth() == EditorHierarchyDepth.BLOCK && controller.selection().selectedChunk() == null
                && currentBrush(LibraryKind.CHUNK) >= 0) {
            applyBrush(LibraryKind.CHUNK, currentBrush(LibraryKind.CHUNK));
        }
        act(Action.DESCEND);
    }

    /** Changing focus commits pending edits, so it is only set when it differs. */
    private void focusWorkbench() {
        EditorFocusRegion region = controller.depth() == EditorHierarchyDepth.BLOCK
                ? EditorFocusRegion.BLOCK_PANE : EditorFocusRegion.CHUNK_PANE;
        if (controller.focusRegion() != region) controller.focusRegion(region);
    }

    /** Swallows held pad controls when the hierarchy depth changed; true if it did. */
    private boolean settleDepth() {
        EditorHierarchyDepth depth = controller.depth();
        if (depth == lastDepth) return false;
        lastDepth = depth;
        paintedCell = -1;
        pad.consumeHeld();
        return true;
    }

    /** Leaves a sheet, wheel or menu; whatever closed it stays swallowed until released. */
    private void closeModal() {
        modal = Modal.NONE;
        pad.consumeHeld();
    }

    /** Ignores where the pointer already rests when a sheet, wheel or menu opens. */
    private void notePointer() {
        lastPointerX = pointerX;
        lastPointerY = pointerY;
    }

    private static float curve(float[] stick) {
        float magnitude = (float) Math.hypot(stick[0], stick[1]);
        return magnitude <= 0 ? 0 : magnitude;
    }

    // ------------------------------------------------------------------ library

    void openLibrary() {
        LibraryKind kind = kind();
        if (kind == LibraryKind.NONE) {
            controller.setStatus("Rings need no brush; South places a ring");
            return;
        }
        libraryKind = kind;
        int current = currentBrush(kind);
        libraryCursor = Math.max(0, Math.min(libraryCount(kind) - 1, current < 0 ? 0 : current));
        notePointer();
        modal = Modal.LIBRARY;
    }

    private void updateLibrary(InputHandler input, boolean click) {
        int count = libraryCount(libraryKind);
        if (count == 0) { closeModal(); return; }
        int dx = pad.navX(), dy = pad.navY();
        if (MenuInput.left(input)) dx--;
        if (MenuInput.right(input)) dx++;
        if (MenuInput.up(input)) dy--;
        if (MenuInput.down(input)) dy++;
        int page = libraryColumns * Math.max(1, libraryVisibleRows);
        int move = dx + dy * libraryColumns;
        if (pad.repeated(LB) || input.isKeyPressed(GLFW_KEY_PAGE_UP)) move -= page;
        if (pad.repeated(RB) || input.isKeyPressed(GLFW_KEY_PAGE_DOWN)) move += page;
        if (move != 0) libraryCursor = Math.max(0, Math.min(count - 1, libraryCursor + move));
        Hit hit = hitAt(Modal.LIBRARY);
        if (hit != null && hit.kind() == HIT_LIBRARY && pointerMoved()) libraryCursor = hit.index();
        if (click && hit != null && hit.kind() == HIT_LIBRARY) libraryCursor = hit.index();
        boolean choose = pad.pressed(SOUTH) || MenuInput.accept(input) || (click && hit != null && hit.kind() == HIT_LIBRARY);
        if (choose) {
            hotbar.put(libraryKind, libraryCursor);
            applyBrush(libraryKind, libraryCursor);
            closeModal();
            return;
        }
        if (pad.pressed(EAST) || pad.pressed(NORTH) || MenuInput.back(input) || input.isKeyPressed(GLFW_KEY_TAB)
                || (click && hit == null)) {
            closeModal();
        }
    }

    int libraryCount(LibraryKind kind) {
        MutableLevel level = controller.currentLevel();
        if (level == null) return 0;
        return switch (kind) {
            case BLOCK -> Math.min(256, level.getBlockCount());
            case CHUNK -> Math.min(1024, level.getChunkCount());
            case PATTERN -> Math.min(0x800, level.getPatternCount());
            case OBJECT -> controller.objectPaletteOrNull() == null ? 0 : controller.objectPaletteOrNull().entries().size();
            case NONE -> 0;
        };
    }

    // ------------------------------------------------------------------ wheel

    void openWheel(boolean byHold) {
        wheelItems = wheelItems();
        // Nothing is chosen until the stick, arrows or pointer pick a tool, so a stray tap is harmless.
        wheelSlot = -1;
        notePointer();
        wheelChosenByStick = false;
        wheelOpenedByHold = byHold;
        modal = Modal.WHEEL;
    }

    private void updateWheel(InputHandler input, boolean click) {
        wheelItems = wheelItems();
        int count = wheelItems.size();
        float[] stick = pad.leftStick();
        if (Math.hypot(stick[0], stick[1]) > .6) {
            double angle = Math.atan2(stick[0], -stick[1]);
            wheelSlot = Math.floorMod((int) Math.round(angle / (2 * Math.PI / count)), count);
            wheelChosenByStick = true;
        }
        int rotate = 0;
        if (pad.repeated(DPAD_LEFT) || MenuInput.left(input)) rotate--;
        if (pad.repeated(DPAD_RIGHT) || MenuInput.right(input)) rotate++;
        if (pad.pressed(DPAD_UP) || MenuInput.up(input)) wheelSlot = 0;
        if (pad.pressed(DPAD_DOWN) || MenuInput.down(input)) wheelSlot = count / 2;
        if (rotate != 0) wheelSlot = wheelSlot < 0 ? (rotate > 0 ? 0 : count - 1) : Math.floorMod(wheelSlot + rotate, count);
        Hit hit = hitAt(Modal.WHEEL);
        if (hit != null && hit.kind() == HIT_WHEEL && (pointerMoved() || click)) wheelSlot = hit.index();
        boolean released = pad.released(VIEW) && wheelOpenedByHold && wheelChosenByStick;
        if (pad.released(VIEW) && !wheelChosenByStick) wheelOpenedByHold = false;
        boolean chosen = wheelSlot >= 0 && wheelSlot < count;
        if (chosen && (pad.pressed(SOUTH) || MenuInput.accept(input) || released
                || (click && hit != null && hit.kind() == HIT_WHEEL))) {
            closeModal();
            wheelItems.get(wheelSlot).action().run();
            contentChanged = true;
            return;
        }
        if (pad.pressed(EAST) || pad.pressed(VIEW) || MenuInput.back(input) || input.isKeyPressed(GLFW_KEY_Q)
                || (click && hit == null)) {
            closeModal();
        }
    }

    List<WheelItem> wheelItems() {
        List<WheelItem> items = new ArrayList<>(8);
        items.add(new WheelItem("Undo", "Last edit", () -> act(Action.UNDO)));
        items.add(new WheelItem("Redo", "", () -> act(Action.REDO)));
        if (controller.depth() == EditorHierarchyDepth.BLOCK) {
            items.add(new WheelItem("Flip H", "This cell", () -> act(Action.TOGGLE_H_FLIP)));
            items.add(new WheelItem("Flip V", "This cell", () -> act(Action.TOGGLE_V_FLIP)));
            items.add(new WheelItem("Solid", "Cycle", () -> act(Action.CYCLE_COLLISION_MODE)));
            items.add(new WheelItem("Shape +", "Height map", () -> act(Action.INCREMENT_SOLID_TILE_INDEX)));
            items.add(new WheelItem("Shape -", "Height map", () -> act(Action.DECREMENT_SOLID_TILE_INDEX)));
            items.add(new WheelItem("Erase", "Empty chunk", () -> act(Action.ERASE_SELECTION)));
        } else if (controller.depth() == EditorHierarchyDepth.CHUNK) {
            items.add(new WheelItem("Flip H", "Pattern", () -> act(Action.TOGGLE_H_FLIP)));
            items.add(new WheelItem("Flip V", "Pattern", () -> act(Action.TOGGLE_V_FLIP)));
            items.add(new WheelItem("Palette", "Next line", () -> act(Action.CYCLE_PATTERN_PALETTE)));
            items.add(new WheelItem("Priority", "High/low", () -> act(Action.TOGGLE_PATTERN_PRIORITY)));
            items.add(new WheelItem("Erase", "Blank", () -> act(Action.ERASE_SELECTION)));
            items.add(new WheelItem("Back", "To block", () -> act(Action.ASCEND)));
        } else if (controller.spawnEditMode() == EditorSpawnEditMode.OBJECTS) {
            items.add(new WheelItem("Delete", "At cursor", () -> act(Action.DELETE_SPAWN)));
            items.add(new WheelItem("Move", "To cursor", () -> act(Action.MOVE_SELECTED_SPAWN_TO_CURSOR)));
            items.add(new WheelItem("Type +", "Subtype", () -> act(Action.INCREMENT_SELECTED_SUBTYPE)));
            items.add(new WheelItem("Type -", "Subtype", () -> act(Action.DECREMENT_SELECTED_SUBTYPE)));
            items.add(new WheelItem("Flip", "Selected", () -> act(Action.TOGGLE_H_FLIP)));
            items.add(new WheelItem("Rings", "Mode", () -> setMode(EditorSpawnEditMode.RINGS)));
        } else if (controller.spawnEditMode() == EditorSpawnEditMode.RINGS) {
            items.add(new WheelItem("Delete", "At cursor", () -> act(Action.DELETE_SPAWN)));
            items.add(new WheelItem("Move", "To cursor", () -> act(Action.MOVE_SELECTED_SPAWN_TO_CURSOR)));
            items.add(new WheelItem("Terrain", "Mode", () -> setMode(EditorSpawnEditMode.TILES)));
            items.add(new WheelItem("Objects", "Mode", () -> setMode(EditorSpawnEditMode.OBJECTS)));
            items.add(new WheelItem("Collision", onOff(controller.isCollisionOverlayEnabled()),
                    () -> act(Action.TOGGLE_COLLISION_OVERLAY)));
            items.add(playtestItem());
        } else {
            items.add(new WheelItem("Layer", controller.activeLayer() == 0 ? "FG > BG" : "BG > FG",
                    () -> act(Action.TOGGLE_LAYER)));
            items.add(new WheelItem("Collision", onOff(controller.isCollisionOverlayEnabled()),
                    () -> act(Action.TOGGLE_COLLISION_OVERLAY)));
            items.add(new WheelItem("Path", controller.collisionPath().name().startsWith("P") ? "A > B" : "B > A",
                    () -> act(Action.TOGGLE_COLLISION_PATH)));
            items.add(new WheelItem("Erase", "Empty block", () -> act(Action.ERASE_SELECTION)));
            items.add(new WheelItem("Objects", "Mode", () -> setMode(EditorSpawnEditMode.OBJECTS)));
            items.add(playtestItem());
        }
        return List.copyOf(items);
    }

    private WheelItem playtestItem() {
        for (EditorCommandPalette.HostCommand command : allHostCommands()) {
            if (command.label().toLowerCase(Locale.ROOT).contains("playtest")) {
                return new WheelItem("Playtest", "From here", command.action());
            }
        }
        return new WheelItem("Save", "Project", () -> act(Action.SAVE));
    }

    private void setMode(EditorSpawnEditMode mode) {
        finishPendingEdits();
        try {
            controller.setSpawnEditMode(mode);
        } catch (IllegalStateException | IllegalArgumentException failure) {
            controller.setStatus(failure.getMessage());
        }
    }

    private static String onOff(boolean on) { return on ? "On > Off" : "Off > On"; }

    // ------------------------------------------------------------------ menu

    void openMenu() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem("Editor", "", null));
        items.add(new MenuItem("Resume", "", () -> { }));
        items.add(new MenuItem("Save project", "Ctrl+S", () -> act(Action.SAVE)));
        Map<String, Runnable> extra = new LinkedHashMap<>();
        for (EditorCommandPalette.HostCommand command : allHostCommands()) extra.put(command.label(), command.action());
        extra.forEach((label, action) -> {
            if (!label.toLowerCase(Locale.ROOT).contains("filter")) items.add(new MenuItem(label, "", action));
        });
        items.add(new MenuItem("Export level", "Ctrl+Shift+E", () -> act(Action.EXPORT)));
        items.add(new MenuItem("Every command", "", null));
        for (EditorCommandPalette.HostCommand command : EditorCommandPalette.forController(controller)
                .actionCommands(this::act)) {
            items.add(new MenuItem(command.label(), "", command.action()));
        }
        menuItems = List.copyOf(items);
        menuIndex = 1;
        menuScroll = 0;
        notePointer();
        modal = Modal.MENU;
    }

    private void updateMenu(InputHandler input, boolean click) {
        int dy = pad.navY();
        if (MenuInput.up(input)) dy--;
        if (MenuInput.down(input)) dy++;
        if (pad.repeated(LB) || input.isKeyPressed(GLFW_KEY_PAGE_UP)) dy -= 8;
        if (pad.repeated(RB) || input.isKeyPressed(GLFW_KEY_PAGE_DOWN)) dy += 8;
        if (dy != 0) moveMenu(dy);
        Hit hit = hitAt(Modal.MENU);
        if (hit != null && hit.kind() == HIT_MENU && pointerMoved() && !menuItems.get(hit.index()).header()) {
            menuIndex = hit.index();
        }
        boolean clicked = click && hit != null && hit.kind() == HIT_MENU;
        if (clicked && menuItems.get(hit.index()).header()) return;
        if (clicked) menuIndex = hit.index();
        if (pad.pressed(SOUTH) || MenuInput.accept(input) || clicked) {
            MenuItem item = menuItems.get(menuIndex);
            closeModal();
            if (!item.header()) item.action().run();
            contentChanged = true;
            return;
        }
        if (pad.pressed(EAST) || pad.pressed(MENU) || MenuInput.back(input) || input.isKeyPressed(GLFW_KEY_F1)
                || (click && hit == null)) {
            closeModal();
        }
    }

    private void moveMenu(int delta) {
        int count = menuItems.size();
        int target = Math.max(0, Math.min(count - 1, menuIndex + delta));
        int step = Integer.signum(delta);
        while (target > 0 && target < count - 1 && menuItems.get(target).header()) target += step;
        if (menuItems.get(target).header()) target = menuIndex;
        menuIndex = target;
    }

    private List<EditorCommandPalette.HostCommand> allHostCommands() {
        return EditorCommandPalette.forController(controller).extraCommands();
    }

    // ------------------------------------------------------------------ brushes

    LibraryKind kind() {
        if (controller.depth() == EditorHierarchyDepth.BLOCK) return LibraryKind.CHUNK;
        if (controller.depth() == EditorHierarchyDepth.CHUNK) return LibraryKind.PATTERN;
        if (controller.spawnEditMode() == EditorSpawnEditMode.OBJECTS)
            return controller.objectPaletteOrNull() == null ? LibraryKind.NONE : LibraryKind.OBJECT;
        if (controller.spawnEditMode() == EditorSpawnEditMode.RINGS) return LibraryKind.NONE;
        return LibraryKind.BLOCK;
    }

    /** The controller's active brush for {@code kind}, as a library index, or -1. */
    int currentBrush(LibraryKind kind) {
        return switch (kind) {
            case BLOCK -> controller.selectedBlockIndex() == null ? -1 : controller.selectedBlockIndex();
            case CHUNK -> controller.chunkBrushIndex() == null ? -1 : controller.chunkBrushIndex();
            case PATTERN -> controller.selectedPatternRaw() == null ? -1 : controller.selectedPatternRaw() & 0x7FF;
            case OBJECT -> objectIndex(controller.objectPaletteOrNull());
            case NONE -> -1;
        };
    }

    private static int objectIndex(EditorStockObjectPalette palette) {
        if (palette == null) return -1;
        String key = palette.selectedObjectKey();
        if (key == null) return palette.selectedObjectId();
        List<EditorStockObjectPalette.Entry> entries = palette.entries();
        for (int i = 0; i < entries.size(); i++) if (key.equals(entries.get(i).objectKey())) return i;
        return -1;
    }

    private void cycleBrush(int direction) {
        LibraryKind kind = kind();
        if (kind == LibraryKind.NONE) return;
        hotbar.cycle(kind, direction);
        applyBrush(kind, hotbar.value(kind));
    }

    void applyBrush(LibraryKind kind, int value) {
        if (value < 0) return;
        try {
            switch (kind) {
                case BLOCK -> controller.selectBlock(value);
                case CHUNK -> controller.selectChunk(value);
                case PATTERN -> controller.selectPattern(value);
                case OBJECT -> {
                    EditorStockObjectPalette.Entry entry = controller.objectPalette().entries().get(value);
                    if (entry.objectKey() != null) controller.objectPalette().setObjectKey(entry.objectKey());
                    else controller.objectPalette().setObjectId(entry.stockObjectId());
                }
                case NONE -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            controller.setStatus(failure.getMessage());
        }
    }

    private void syncHotbarFromController() {
        LibraryKind kind = kind();
        if (kind == LibraryKind.NONE) return;
        int brush = currentBrush(kind);
        if (brush >= 0 && brush != hotbar.value(kind)) hotbar.put(kind, brush);
    }

    private void seedHotbars(MutableLevel level) {
        if (level == seededLevel) return;
        seededLevel = level;
        hotbar.clear();
        int blocks = Math.min(256, level.getBlockCount());
        int[] blockUse = new int[blocks];
        var map = level.getMap();
        for (int layer = 0; layer < map.getLayerCount(); layer++)
            for (int y = 0; y < map.getHeight(); y++)
                for (int x = 0; x < map.getWidth(); x++) {
                    int value = Byte.toUnsignedInt(map.getValue(layer, x, y));
                    if (value < blocks) blockUse[value]++;
                }
        hotbar.seed(LibraryKind.BLOCK, ranked(blockUse));
        int chunks = Math.min(1024, level.getChunkCount());
        int[] chunkUse = new int[chunks];
        for (int i = 0; i < blocks; i++) {
            Block block = level.getBlock(i);
            for (int y = 0; y < block.getGridSide(); y++)
                for (int x = 0; x < block.getGridSide(); x++) {
                    int chunk = block.getChunkDesc(x, y).getChunkIndex();
                    if (chunk >= 0 && chunk < chunks) chunkUse[chunk]++;
                }
        }
        hotbar.seed(LibraryKind.CHUNK, ranked(chunkUse));
        int patterns = Math.min(0x800, level.getPatternCount());
        int[] patternUse = new int[patterns];
        for (int i = 0; i < chunks; i++)
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++) {
                    int pattern = level.getChunk(i).getPatternDesc(x, y).getPatternIndex();
                    if (pattern >= 0 && pattern < patterns) patternUse[pattern]++;
                }
        hotbar.seed(LibraryKind.PATTERN, ranked(patternUse));
        EditorStockObjectPalette palette = controller.objectPaletteOrNull();
        if (palette != null) {
            int[] objectUse = new int[palette.entries().size()];
            for (ObjectSpawn spawn : level.getObjects()) {
                if (spawn.objectKey() == null && spawn.objectId() >= 0 && spawn.objectId() < objectUse.length) {
                    objectUse[spawn.objectId()]++;
                }
            }
            hotbar.seed(LibraryKind.OBJECT, ranked(objectUse));
        }
        // The brush already in hand stays highlighted without pushing out a most-used entry.
        for (LibraryKind kind : LibraryKind.values()) {
            if (kind != LibraryKind.NONE) hotbar.adopt(kind, currentBrush(kind));
        }
    }

    /** Used indices by descending use, then unused ones; index 0 (the empty entry) is never offered. */
    static int[] ranked(int[] use) {
        Integer[] order = new Integer[use.length];
        for (int i = 0; i < use.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> use[a] != use[b] ? Integer.compare(use[b], use[a]) : Integer.compare(a, b));
        List<Integer> ranked = new ArrayList<>(EditorHudHotbar.SLOTS);
        for (Integer index : order) {
            if (ranked.size() == EditorHudHotbar.SLOTS) break;
            if (use[index] > 0 && index != 0) ranked.add(index);
        }
        for (int i = 1; i < use.length && ranked.size() < EditorHudHotbar.SLOTS; i++) if (!ranked.contains(i)) ranked.add(i);
        return ranked.stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ shared

    private void act(Action action) {
        finishPendingEdits();
        dispatch.accept(action);
        contentChanged = true;
    }

    private void trackStatus() {
        String status = controller.statusText();
        if (status != null && !status.equals(lastStatus)) {
            lastStatus = status;
            if (!"Ready".equals(status)) {
                toast = status;
                toastFrames = TOAST_FRAMES;
            }
        } else if (toastFrames > 0) {
            toastFrames--;
        }
    }

    private Hit hitAt(Modal modal) {
        if (Double.isNaN(pointerX)) return null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.modal() == modal && hit.rect().contains(pointerX, pointerY)) return hit;
        }
        return null;
    }

    private double lastPointerX = Double.NaN;
    private double lastPointerY = Double.NaN;

    private boolean pointerMoved() {
        boolean moved = pointerX != lastPointerX || pointerY != lastPointerY;
        lastPointerX = pointerX;
        lastPointerY = pointerY;
        return moved;
    }

    /** Prompts for the current context, in reading order. */
    List<Prompt> prompts() {
        List<Prompt> prompts = new ArrayList<>(8);
        switch (modal) {
            case LIBRARY -> {
                prompts.add(new Prompt(Glyph.DPAD, "Arrows", "Browse"));
                prompts.add(new Prompt(Glyph.BUMPERS, "PgUp/PgDn", "Page"));
                prompts.add(new Prompt(Glyph.SOUTH, "Enter", "Use"));
                prompts.add(new Prompt(Glyph.EAST, "Esc", "Close"));
            }
            case WHEEL -> {
                prompts.add(new Prompt(Glyph.LSTICK, "Arrows", "Choose"));
                prompts.add(new Prompt(Glyph.SOUTH, "Enter", "Apply"));
                prompts.add(new Prompt(Glyph.EAST, "Esc", "Cancel"));
            }
            case MENU -> {
                prompts.add(new Prompt(Glyph.DPAD, "Arrows", "Select"));
                prompts.add(new Prompt(Glyph.SOUTH, "Enter", "Run"));
                prompts.add(new Prompt(Glyph.EAST, "Esc", "Close"));
            }
            case NONE -> {
                EditorHierarchyDepth depth = controller.depth();
                boolean spawns = controller.isSpawnEditing();
                prompts.add(new Prompt(depth == EditorHierarchyDepth.WORLD ? Glyph.LSTICK : Glyph.DPAD, "Arrows",
                        depth == EditorHierarchyDepth.WORLD ? "Move" : "Cell"));
                prompts.add(new Prompt(Glyph.SOUTH, "Space", spawns ? "Place" : "Paint"));
                prompts.add(new Prompt(Glyph.WEST, "E", "Pick"));
                if (kind() != LibraryKind.NONE) {
                    prompts.add(new Prompt(Glyph.NORTH, "Tab", "Library"));
                    prompts.add(new Prompt(Glyph.BUMPERS, "1-0", "Brush"));
                }
                if (depth == EditorHierarchyDepth.WORLD && !spawns)
                    prompts.add(new Prompt(Glyph.RT, "Enter", "Edit block"));
                if (depth == EditorHierarchyDepth.BLOCK) prompts.add(new Prompt(Glyph.RT, "Enter", "Edit chunk"));
                if (depth != EditorHierarchyDepth.WORLD) prompts.add(new Prompt(Glyph.EAST, "Esc", "Back"));
                prompts.add(new Prompt(Glyph.VIEW, "Q", "Tools"));
                prompts.add(new Prompt(Glyph.MENU, "F1", "Menu"));
            }
        }
        return prompts;
    }

    /** World-terrain drag painting: live preview, committed as one undoable stroke on release. */
    private static final class PadStroke {
        private final MutableLevel level;
        private final int layer;
        private final Map<Integer, StrokeCommand.CellDelta> deltas = new LinkedHashMap<>();

        PadStroke(MutableLevel level, int layer) {
            this.level = level;
            this.layer = layer;
        }

        void paint(int x, int y, int block) {
            var map = level.getMap();
            if (x < 0 || y < 0 || x >= map.getWidth() || y >= map.getHeight()) return;
            if (block < 0 || block >= Math.min(256, level.getBlockCount())) return;
            int key = y * map.getWidth() + x;
            if (deltas.containsKey(key)) return;
            int before = Byte.toUnsignedInt(map.getValue(layer, x, y));
            if (before == block) return;
            deltas.put(key, new StrokeCommand.CellDelta(layer, x, y, before, block));
            level.setBlockInMap(layer, x, y, block);
        }

        void commit(LevelEditorController controller) {
            if (deltas.isEmpty()) return;
            List<StrokeCommand.CellDelta> painted = new ArrayList<>(deltas.values());
            // Restore the preview, then let history apply the stroke so one undo reverts it.
            for (int i = painted.size() - 1; i >= 0; i--) {
                StrokeCommand.CellDelta delta = painted.get(i);
                level.setBlockInMap(delta.layer(), delta.x(), delta.y(), delta.before());
            }
            controller.executeCommand(new StrokeCommand(level, painted));
            controller.setStatus(painted.size() == 1 ? "Painted 1 block" : "Painted " + painted.size() + " blocks");
        }
    }
}
