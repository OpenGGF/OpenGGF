package com.openggf.editor.hud;

import com.openggf.InputBindingFactory;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.EditorFocusRegion;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorInputHandler;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.session.EditorCursorState;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.control.PhysicalGamepad.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * ROM-free pad behaviour of the controller HUD, driven tick by tick as the engine drives it:
 * the world editing input handler with the HUD as its front end and the engine's pad mappings
 * suppressed. Each test holds controls across several ticks, which one-tick presses cannot see.
 */
class TestEditorHudControls {
    private final boolean[] buttons = new boolean[15];
    private final float[] axes = {0, 0, 0, 0, -1, -1};
    private boolean padConnected = true;
    private MutableLevel level;
    private LevelEditorController controller;
    private EditorInputHandler dispatcher;
    private EditorHud hud;
    private InputHandler input;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        level = MutableLevel.snapshot(new TestLevel(4, 3, 2, 4, 12));
        controller = new LevelEditorController();
        controller.attachLevel(level);
        dispatcher = new EditorInputHandler(controller);
        hud = new EditorHud(controller);
        dispatcher.setFrontEnd(new EditorInputHandler.FrontEnd() {
            public boolean update(InputHandler in) { return hud.update(in, dispatcher::handleAction); }
            public void finishPendingEdits() { hud.finishPendingEdits(); }
        });
        GamepadStateSource source = () -> padConnected
                ? List.of(GamepadStateSource.DeviceState.connected(0, "Xbox Controller", buttons, axes))
                : List.of();
        input = new InputHandler(InputBindingFactory.standaloneSupplier(), source);
        InputHandlerInternalAccess.suppressGamepad(input, true);
        // Centre of map cell (0, 0); blocks are 64 pixels square.
        controller.setWorldCursor(new EditorCursorState(32, 32));
        tick();
    }

    @Test
    void namedPropertiesAreNavigableWithoutPaintingAndEachChangeUndoes() {
        controller.configureSpawnEditing(new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry(),
                new com.openggf.game.common.CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(com.openggf.editor.EditorSpawnEditMode.OBJECTS);
        controller.objectPalette().setObjectId(0x41);
        controller.placeSpawnAtCursor();
        hud.openProperties(); tick();
        assertEquals(EditorHud.Modal.PROPERTIES, hud.modal);
        press(BUTTON_DPAD_DOWN); // Strength
        press(BUTTON_DPAD_RIGHT);
        assertEquals(2, controller.selectedObjectSpawn().subtype());
        assertEquals(1, level.getObjects().size());
        controller.undo(); assertEquals(0, controller.selectedObjectSpawn().subtype());
        press(BUTTON_B); assertEquals(EditorHud.Modal.NONE, hud.modal);
    }

    @Test
    void southStillHeldAfterChoosingFromTheLibraryDoesNotPaintTheWorkbench() {
        controller.placeBlock(0, 0, 0, 2);
        pullRightTrigger();
        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        int before = editedChunk();

        press(BUTTON_Y);
        assertEquals(EditorHud.Modal.LIBRARY, hud.modal);
        press(BUTTON_DPAD_RIGHT);
        int chosen = hud.libraryCursor;
        assertNotEquals(before, chosen);
        buttons[BUTTON_A] = true;
        tick();
        assertEquals(EditorHud.Modal.NONE, hud.modal, "South chooses and closes the library");
        tick();
        tick();
        assertEquals(before, editedChunk(), "The South that chose the brush must not also paint the selected cell");

        buttons[BUTTON_A] = false;
        tick();
        press(BUTTON_A);
        assertEquals(chosen, editedChunk(), "A fresh South paints");
    }

    @Test
    void objectLibraryHoverAndConfirmDoNotPlaceUntilAFreshSouthPress() {
        controller.configureSpawnEditing(new com.openggf.level.objects.ObjectRegistry() {
            public com.openggf.level.objects.ObjectInstance create(ObjectSpawn spawn) { return null; }
            public void reportCoverage(List<ObjectSpawn> spawns) { }
            public String getPrimaryName(int id) { return "Object " + id; }
        }, new com.openggf.game.common.CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(com.openggf.editor.EditorSpawnEditMode.OBJECTS);
        tick();
        int before = controller.objectPalette().selectedObjectId();
        press(BUTTON_Y);
        assertEquals(EditorHud.Modal.LIBRARY, hud.modal);
        press(BUTTON_DPAD_RIGHT);
        assertEquals(before, controller.objectPalette().selectedObjectId(), "Hover leaves the brush unchanged");
        buttons[BUTTON_A] = true;
        tick();
        int chosen = controller.objectPalette().selectedObjectId();
        assertNotEquals(before, chosen);
        tick();
        assertTrue(level.getObjects().isEmpty(), "Choosing a brush cannot place through the library");
        buttons[BUTTON_A] = false;
        tick();
        press(BUTTON_A);
        assertEquals(1, level.getObjects().size());
        assertEquals(chosen, level.getObjects().getFirst().objectId());
        dispatcher.handleAction(EditorInputHandler.Action.UNDO);
        assertTrue(level.getObjects().isEmpty());
    }

    @Test
    void southHeldThroughAZoomDoesNotPaintTheNewView() {
        buttons[BUTTON_A] = true;
        tick();
        pullRightTrigger();
        tick();
        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        assertEquals(0, editedChunk(), "The South that painted the world must not paint the block it zoomed into");
    }

    @Test
    void buttonsHeldWhileTheHudWasAwayDoNotActWhenItReturns() {
        controller.placeBlock(0, 0, 0, 2);
        pullRightTrigger();
        // Control: a B still held from a project menu reads as a fresh press without a resync.
        buttons[BUTTON_B] = true;
        tick();
        assertEquals(EditorHierarchyDepth.WORLD, controller.depth(), "Control: the carried-over B ascends");
        buttons[BUTTON_B] = false;
        tick();

        pullRightTrigger();
        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
        buttons[BUTTON_B] = true;
        hud.reactivated();
        tick();
        tick();
        assertEquals(EditorHierarchyDepth.BLOCK, controller.depth(), "A button held on return is swallowed");
        buttons[BUTTON_B] = false;
        tick();
        press(BUTTON_B);
        assertEquals(EditorHierarchyDepth.WORLD, controller.depth(), "The next press acts");
    }

    @Test
    void aSouthStrokeAcrossThreeCellsUndoesAsOneEdit() {
        int brush = controller.selectedBlockIndex();
        assertNotEquals(0, brush);
        buttons[BUTTON_A] = true;
        tick();
        for (int step = 0; step < 2; step++) {
            buttons[BUTTON_DPAD_RIGHT] = true;
            tick();
            buttons[BUTTON_DPAD_RIGHT] = false;
            tick();
        }
        buttons[BUTTON_A] = false;
        tick();
        for (int x = 0; x < 3; x++) assertEquals(brush, cell(x, 0), "Cell " + x + " painted");
        assertEquals(0, cell(3, 0));

        dispatcher.handleAction(EditorInputHandler.Action.UNDO);
        for (int x = 0; x < 3; x++) assertEquals(0, cell(x, 0), "One undo reverts the whole stroke");
    }

    @Test
    void tappingToolsThenSouthChoosesNothing() {
        press(BUTTON_A);
        int painted = cell(0, 0);
        assertNotEquals(0, painted);
        press(BUTTON_BACK);
        assertEquals(EditorHud.Modal.WHEEL, hud.modal);
        press(BUTTON_A);
        assertEquals(painted, cell(0, 0), "The wheel opens with no tool chosen, so South cannot undo");
        assertEquals(EditorHud.Modal.WHEEL, hud.modal);
        press(BUTTON_B);
        assertEquals(EditorHud.Modal.NONE, hud.modal);
    }

    @Test
    void theStickThatSteeredTheWheelDoesNotThenMoveTheCursor() {
        buttons[BUTTON_BACK] = true;
        tick();
        axes[AXIS_LEFT_X] = 1;
        tick();
        EditorCursorState before = controller.worldCursor();
        buttons[BUTTON_BACK] = false;
        tick();
        tick();
        assertEquals(EditorHud.Modal.NONE, hud.modal, "Releasing View applies the steered tool");
        assertEquals(before, controller.worldCursor(), "The stick stays latched until it returns to centre");
        axes[AXIS_LEFT_X] = 0;
        tick();
        axes[AXIS_LEFT_X] = 1;
        tick();
        assertNotEquals(before, controller.worldCursor());
    }

    @Test
    void theMenuLeavesOutCommandsForPanesTheHudDoesNotDraw() {
        press(BUTTON_START);
        assertEquals(EditorHud.Modal.MENU, hud.modal);
        assertTrue(hud.menuItems.stream().anyMatch(item -> item.label().equals("Undo")));
        assertTrue(hud.menuItems.stream().noneMatch(item -> item.label().equals("Next editor pane")
                || item.label().toLowerCase(Locale.ROOT).startsWith("browse")));
    }

    @Test
    void hostCommandsWithTheSameTranslatedLabelKeepBothActions() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        Runnable firstAction = first::incrementAndGet;
        Runnable secondAction = second::incrementAndGet;
        EditorCommandPalette.forController(controller).setHostCommands(List.of(
                new EditorCommandPalette.HostCommand("Ouvrir", firstAction),
                new EditorCommandPalette.HostCommand("Ouvrir", secondAction)));

        hud.openMenu();
        var commands = hud.menuItems.stream().filter(item -> item.action() == firstAction
                || item.action() == secondAction).toList();
        assertEquals(2, commands.size(), "Translated labels cannot merge distinct commands");
        commands.forEach(item -> item.action().run());
        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void ordinaryHostCommandContainingFilterRemainsAvailable() {
        var imports = new AtomicInteger();
        Runnable importAction = imports::incrementAndGet;
        EditorCommandPalette.forController(controller).setHostCommands(List.of(
                new EditorCommandPalette.HostCommand("Filter importieren", importAction)));

        hud.openMenu();
        var command = hud.menuItems.stream().filter(item -> item.action() == importAction)
                .findFirst().orElseThrow(() -> new AssertionError("Wording cannot hide a host command"));
        command.action().run();
        assertEquals(1, imports.get());
    }

    @Test
    void translatedWorkflowKindsChoosePlaytestAndHideOnlyTheFilter() {
        var playtests = new AtomicInteger();
        var unrelated = new AtomicInteger();
        Runnable playtestAction = playtests::incrementAndGet;
        Runnable filterAction = () -> fail("A desktop-only filter must not reach the HUD");
        Runnable unrelatedAction = unrelated::incrementAndGet;
        EditorCommandPalette.forController(controller).setWorkflowCommands(List.of(
                new EditorCommandPalette.WorkflowCommand(EditorCommandPalette.WorkflowKind.COMMAND,
                        new EditorCommandPalette.HostCommand("Playtest archives", unrelatedAction)),
                new EditorCommandPalette.WorkflowCommand(EditorCommandPalette.WorkflowKind.PLAYTEST,
                        new EditorCommandPalette.HostCommand("Probar nivel", playtestAction)),
                new EditorCommandPalette.WorkflowCommand(EditorCommandPalette.WorkflowKind.FILTER,
                        new EditorCommandPalette.HostCommand("Objekte eingrenzen", filterAction))));

        hud.wheelItems().getLast().action().run();
        assertEquals(1, playtests.get(), "The wheel selects semantic Playtest, regardless of either label");
        assertEquals(0, unrelated.get());
        hud.openMenu();
        assertTrue(hud.menuItems.stream().anyMatch(item -> item.action() == playtestAction));
        assertTrue(hud.menuItems.stream().anyMatch(item -> item.action() == unrelatedAction));
        assertTrue(hud.menuItems.stream().noneMatch(item -> item.action() == filterAction));
    }

    @Test
    void focusReturnsToTheCanvasWithoutAPad() {
        padConnected = false;
        controller.focusRegion(EditorFocusRegion.BLOCK_PANE);
        assertNotEquals(EditorFocusRegion.WORLD_CANVAS, controller.focusRegion());
        tick();
        assertEquals(EditorFocusRegion.WORLD_CANVAS, controller.focusRegion());
    }

    @Test
    void aClickChoosesTheEntryUnderThePointer() {
        hud.setPointerScale(1);
        input.handleMouseMove(50, 50);
        tick();
        int brush = controller.selectedBlockIndex();
        press(BUTTON_Y);
        hud.hits = List.of(new EditorHud.Hit(new EditorHudLayout.Rect(40, 40, 20, 20),
                EditorHud.Modal.LIBRARY, EditorHud.HIT_LIBRARY, 3));
        tick();
        assertEquals(brush, hud.libraryCursor, "A pointer resting where the sheet opened does not move the highlight");
        press(BUTTON_DPAD_RIGHT);
        assertNotEquals(3, hud.libraryCursor);

        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        tick();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        tick();
        assertEquals(3, controller.selectedBlockIndex(), "The click chooses the entry it landed on");
    }

    /**
     * Chunk at the selected cell of the block placed under the world cursor. Composition edits
     * copy a shared block to a free slot and repoint this placement, so read through the map.
     */
    private int editedChunk() {
        Block placed = level.getBlock(cell(0, 0));
        return placed.getChunkDesc(controller.selectedBlockCellX(), controller.selectedBlockCellY()).getChunkIndex();
    }

    private int cell(int x, int y) {
        return Byte.toUnsignedInt(level.getMap().getValue(0, x, y));
    }

    private void tick() {
        input.refreshLogicalSnapshot();
        dispatcher.update(input);
        input.update();
    }

    private void press(int button) {
        buttons[button] = true;
        tick();
        buttons[button] = false;
        tick();
    }

    private void pullRightTrigger() {
        axes[AXIS_RIGHT_TRIGGER] = 1;
        tick();
        axes[AXIS_RIGHT_TRIGGER] = -1;
        tick();
    }

    private static final class TestLevel extends AbstractLevel {
        private TestLevel(int mapWidth, int mapHeight, int blockGridSide, int blockCount, int chunkCount) {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] {new Pattern()};
            this.chunkCount = chunkCount;
            chunks = new Chunk[chunkCount];
            for (int i = 0; i < chunkCount; i++) chunks[i] = new Chunk();
            this.blockCount = blockCount;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) blocks[i] = new Block(blockGridSide);
            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            map = new Map(1, mapWidth, mapHeight);
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = mapWidth * blockGridSide * 32;
            minY = 0;
            maxY = mapHeight * blockGridSide * 32;
        }

        @Override public int getChunksPerBlockSide() { return blocks.length > 0 ? blocks[0].getGridSide() : 0; }
        @Override public int getBlockPixelSize() { return getChunksPerBlockSide() * 32; }
        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return patternCount; }
        @Override public Pattern getPattern(int index) { return patterns[index]; }
        @Override public int getChunkCount() { return chunkCount; }
        @Override public Chunk getChunk(int index) { return chunks[index]; }
        @Override public int getBlockCount() { return blockCount; }
        @Override public Block getBlock(int index) { return blocks[index]; }
        @Override public SolidTile getSolidTile(int index) { return solidTiles[index]; }
        @Override public Map getMap() { return map; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return minX; }
        @Override public int getMaxX() { return maxX; }
        @Override public int getMinY() { return minY; }
        @Override public int getMaxY() { return maxY; }
        @Override public int getZoneIndex() { return 0; }
    }
}
