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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.control.PhysicalGamepad.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.lwjgl.glfw.GLFW.*;
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
    void focusedCommandReaderPreservesTailWithoutRunningOrChangingItsSelection() {
        var runs = new AtomicInteger();
        String label = "Read this command completely ".repeat(60) + "COMMAND_END_MARKER";
        hud.openMenu();
        hud.menuItems = List.of(new EditorHud.MenuItem("Commands", "", null),
                new EditorHud.MenuItem(label, "Shortcut details", runs::incrementAndGet));
        hud.menuIndex = 1;
        press(BUTTON_RIGHT_THUMB);
        assertTrue(hud.reader.isOpen());
        assertTrue(hud.detailsText().contains(label));
        assertEquals(1, hud.menuIndex);
        press(BUTTON_DPAD_DOWN);
        press(BUTTON_A);
        assertEquals(0, runs.get(), "Reading and scrolling cannot invoke the focused command");
        press(BUTTON_B);
        assertEquals(EditorHud.Modal.MENU, hud.modal);
        press(BUTTON_A);
        assertEquals(1, runs.get(), "A fresh confirmation retains the original action");
    }

    @Test
    void objectLibraryReaderRetainsLongSelectedNameAndNeverAdoptsItOnConfirm() {
        String name = "A complete selected object name ".repeat(25) + "OBJECT_END_MARKER";
        controller.configureSpawnEditing(new com.openggf.level.objects.ObjectRegistry() {
            public com.openggf.level.objects.ObjectInstance create(ObjectSpawn spawn) { return null; }
            public void reportCoverage(List<ObjectSpawn> spawns) { }
            public String getPrimaryName(int id) { return name; }
        }, new com.openggf.game.common.CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(com.openggf.editor.EditorSpawnEditMode.OBJECTS);
        tick(); hud.openLibrary();
        int before = controller.objectPalette().selectedObjectId();
        press(BUTTON_RIGHT_THUMB);
        assertTrue(hud.reader.isOpen());
        assertTrue(hud.detailsText().contains(com.openggf.editor.EditorStockObjectPalette.displayName(name)));
        press(BUTTON_A);
        assertEquals(EditorHud.Modal.LIBRARY, hud.modal);
        assertEquals(before, controller.objectPalette().selectedObjectId());
        assertTrue(level.getObjects().isEmpty());
    }

    @Test
    void readerKeepsLatestStatusAfterToastExpiresAndCanvasStickKeepsItsOriginalAction() {
        controller.setStatus("Status ".repeat(180) + "STATUS_END_MARKER");
        tick();
        for (int i = 0; i < 190; i++) tick();
        assertEquals(0, hud.toastFrames);
        hud.openMenu();
        assertTrue(hud.detailsText().contains("STATUS_END_MARKER"));
        press(BUTTON_B);
        boolean before = controller.isCollisionOverlayEnabled();
        press(BUTTON_RIGHT_THUMB);
        assertNotEquals(before, controller.isCollisionOverlayEnabled());
        assertTrue(!hud.reader.isOpen(), "Canvas R3 retains collision ownership");
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
        assertFalse(hud.menuItems.stream().anyMatch(item -> item.label().startsWith("Pan ")), "panning is a held control");
        assertTrue(hud.menuItems.stream().noneMatch(item -> item.label().equals("Next editor pane")
                || item.label().toLowerCase(Locale.ROOT).startsWith("browse")));
    }

    @Test
    void theMenuIsAGroupedSheetOfAuthoredCommandsEachListedOnce() {
        var palette = com.openggf.editor.EditorCommandPalette.forController(controller);
        palette.setHostActions(() -> { }, () -> { });
        palette.setWorkspaceCommands(List.of(new com.openggf.editor.EditorCommandPalette.HostCommand("Stageplay workspace", "F4", () -> { })));
        palette.setHostCommands(List.of(new com.openggf.editor.EditorCommandPalette.HostCommand("Editor projects", () -> { }),
                new com.openggf.editor.EditorCommandPalette.HostCommand("Import content", () -> { })));
        press(BUTTON_START);
        assertEquals(EditorHud.Modal.MENU, hud.modal);
        var rows = hud.menuItems.stream().filter(item -> !item.header()).toList();
        var labels = rows.stream().map(EditorHud.MenuItem::label).toList();
        assertEquals(labels.size(), labels.stream().distinct().count(), "each command once: " + labels);
        var captions = hud.menuItems.stream().filter(EditorHud.MenuItem::header).map(EditorHud.MenuItem::label).toList();
        assertEquals(List.of("Editor", "Edit", "Region", "Pattern and tile", "Objects", "View", "Playtest", "Project"), captions);
        // Each group's rows, in order, from the drawn sheet.
        var grouped = new java.util.LinkedHashMap<String, List<String>>();
        String current = null;
        for (var item : hud.menuItems) {
            if (item.header()) { current = item.label(); grouped.put(current, new ArrayList<>()); }
            else grouped.get(current).add(item.label());
        }
        assertEquals(List.of("Undo", "Redo"), grouped.get("Edit").subList(0, 2), "history leads Edit");
        assertTrue(grouped.get("Editor").contains("Stageplay workspace"), "a workspace switch sits with the editor's pages");
        assertEquals(List.of("Playtest from cursor", "Playtest from level start"), grouped.get("Playtest"),
                "two playtests, each named by where the player starts, and no unexplained Fresh start");
        assertEquals(List.of("Save project", "Export level", "Editor projects", "Import content"), grouped.get("Project"));
        assertEquals("F4", rows.stream().filter(item -> item.label().equals("Stageplay workspace")).findFirst().orElseThrow().detail());
        assertEquals("Shift+Tab", rows.stream().filter(item -> item.label().equals("Playtest from cursor")).findFirst().orElseThrow().detail());
        assertEquals("F5", rows.stream().filter(item -> item.label().equals("Playtest from level start")).findFirst().orElseThrow().detail());
        for (var descriptor : com.openggf.editor.EditorCommandPalette.descriptors()) {
            var row = rows.stream().filter(item -> item.label().equals(descriptor.label())).findFirst().orElseThrow(
                    () -> new AssertionError(descriptor.label() + " in the menu"));
            assertEquals(descriptor.shortcut(), row.detail(), descriptor.label() + " shows its real shortcut");
        }
        for (var excluded : com.openggf.editor.EditorCommandPalette.excluded().keySet()) {
            String plain = excluded.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            assertTrue(labels.stream().noneMatch(label -> label.equalsIgnoreCase(plain)), plain + " is a held control, not a row");
        }
        assertTrue(labels.stream().noneMatch(label -> label.startsWith("Increment") || label.startsWith("Decrement")
                || label.startsWith("Toggle h") || label.startsWith("Toggle v")), "no enum-derived labels: " + labels);
        assertEquals("Del", rows.stream().filter(item -> item.label().equals("Delete region")).findFirst().orElseThrow().detail());
        assertTrue(labels.stream().noneMatch(label -> label.matches(".*\\((F\\d+|Ctrl\\+.*)\\)")),
                "keys are caps beside labels, never letters inside them: " + labels);
    }

    /**
     * Every shortcut the menu shows reaches its own action through the real HUD front end and
     * {@link EditorInputHandler#update}, in the context its group names (Del, Esc and Enter mean
     * one thing per context), so the captions cannot drift from the bindings.
     */
    @Test
    void everyShownShortcutDispatchesItsOwnActionThroughTheEditorInput() {
        padConnected = false;
        for (var descriptor : com.openggf.editor.EditorCommandPalette.descriptors()) {
            if (descriptor.shortcut().isEmpty()) continue;
            setUp();
            padConnected = false;
            tick();
            // The plainest editor state (no paste preview where possible) in which the context holds.
            int state = 0;
            while (!descriptor.context().matches((state & 1) != 0, (state & 2) != 0, (state & 4) != 0)) state++;
            enterState((state & 1) != 0, (state & 2) != 0, (state & 4) != 0);
            var dispatched = new ArrayList<EditorInputHandler.Action>();
            dispatcher.setDispatchListener(dispatched::add);
            pressChord(descriptor.shortcut());
            assertTrue(dispatched.contains(descriptor.action()),
                    descriptor.shortcut() + " shown on " + descriptor.label() + " dispatched " + dispatched);
            for (var other : dispatched) {
                var shown = com.openggf.editor.EditorCommandPalette.descriptor(other);
                assertTrue(other == descriptor.action() || shown == null || !shown.shortcut().equals(descriptor.shortcut())
                        || shown.context() != descriptor.context(), descriptor.shortcut() + " also ran " + other);
            }
            dispatcher.setDispatchListener(action -> { });
        }
    }

    /**
     * Enter, Esc and Del each pick one command per editor state. Every combination of region,
     * placement mode and paste preview presses the shared key through the real input path, and the
     * command it reaches is the one whose descriptor context matches that state.
     */
    @Test
    void sharedKeysReachTheCommandWhoseContextMatchesEveryEditorState() {
        padConnected = false;
        for (String key : List.of("Enter", "Esc", "Del")) {
            for (int state = 0; state < 8; state++) {
                boolean region = (state & 1) != 0, objects = (state & 2) != 0, preview = (state & 4) != 0;
                setUp();
                padConnected = false;
                tick();
                enterState(region, objects, preview);
                assertEquals(preview, controller.isPastePreviewActive(), key + " state " + state);
                assertEquals(region, controller.regionSelection() != null, key + " state " + state);
                var dispatched = new ArrayList<EditorInputHandler.Action>();
                dispatcher.setDispatchListener(dispatched::add);
                pressChord(key);
                dispatcher.setDispatchListener(action -> { });
                var expected = com.openggf.editor.EditorCommandPalette.descriptors().stream()
                        .filter(d -> d.shortcut().equals(key) && d.context().matches(region, objects, preview))
                        .map(com.openggf.editor.EditorCommandPalette.Descriptor::action).toList();
                assertTrue(expected.size() <= 1, key + " is ambiguous in state " + state + ": " + expected);
                var shown = dispatched.stream().filter(action -> {
                    var d = com.openggf.editor.EditorCommandPalette.descriptor(action);
                    return d != null && d.shortcut().equals(key);
                }).toList();
                assertEquals(expected, shown, key + " with region " + region + ", objects " + objects + ", preview " + preview
                        + " dispatched " + dispatched);
            }
        }
    }

    private void selectRegion() {
        controller.setWorldCursor(new EditorCursorState(32, 32));
        controller.toggleRegionCorner();
        controller.setWorldCursor(new EditorCursorState(96, 96));
        controller.toggleRegionCorner();
        assertNotNull(controller.regionSelection(), "a selected region");
    }

    /**
     * Reaches an editor state through the controller's own commands: a paste preview is a copied
     * region pasted again (Ctrl+V); without a region it is the same after the region is cleared (Esc).
     */
    private void enterState(boolean region, boolean objects, boolean preview) {
        // Region copies carry objects, so the spawn tools are configured; the mode stays Tiles.
        controller.configureSpawnEditing(new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry(),
                new com.openggf.game.common.CommonObjectPlacementEncoding());
        assertEquals(com.openggf.editor.EditorSpawnEditMode.TILES, controller.spawnEditMode());
        if (preview) {
            selectRegion();
            controller.copyRegion();
            if (!region) controller.clearRegionSelection();
            controller.beginPastePreview();
        } else if (region) selectRegion();
        if (objects) objectMode();
    }

    private void objectMode() {
        controller.configureSpawnEditing(new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry(),
                new com.openggf.game.common.CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(com.openggf.editor.EditorSpawnEditMode.OBJECTS);
    }

    @Test
    void ctrlZeroResetsTheZoomWithoutPickingAHotbarSlot() {
        padConnected = false;
        tick();
        var dispatched = new ArrayList<EditorInputHandler.Action>();
        dispatcher.setDispatchListener(dispatched::add);
        int before = hud.hotbar.selected(hud.kind());
        pressChord("Ctrl+0");
        assertEquals(List.of(EditorInputHandler.Action.RESET_CANVAS_ZOOM), dispatched);
        assertEquals(before, hud.hotbar.selected(hud.kind()), "Ctrl+0 is the zoom reset, not hotbar slot 10");
        pressChord("Ctrl+2");
        assertEquals(before, hud.hotbar.selected(hud.kind()), "no Ctrl+digit picks a slot");
        pressChord("2");
        assertEquals(1, hud.hotbar.selected(hud.kind()), "a plain digit still picks its slot");
    }

    private void pressChord(String chord) {
        List<Integer> keys = new ArrayList<>();
        for (String part : chord.split("\\+(?=.)")) {
            keys.add(switch (part) {
                case "Ctrl" -> GLFW_KEY_LEFT_CONTROL;
                case "Shift" -> GLFW_KEY_LEFT_SHIFT;
                case "Enter" -> GLFW_KEY_ENTER;
                case "Esc" -> GLFW_KEY_ESCAPE;
                case "Space" -> GLFW_KEY_SPACE;
                case "Del" -> GLFW_KEY_DELETE;
                case "Tab" -> GLFW_KEY_TAB;
                case "=" -> GLFW_KEY_EQUAL;
                case "-" -> GLFW_KEY_MINUS;
                case "[" -> GLFW_KEY_LEFT_BRACKET;
                case "]" -> GLFW_KEY_RIGHT_BRACKET;
                default -> {
                    if (part.matches("F\\d+")) yield GLFW_KEY_F1 + Integer.parseInt(part.substring(1)) - 1;
                    if (part.length() == 1 && Character.isLetterOrDigit(part.charAt(0)))
                        yield (int) Character.toUpperCase(part.charAt(0));
                    throw new IllegalArgumentException("Unknown key in " + chord + ": " + part);
                }
            });
        }
        for (int key : keys) input.handleKeyEvent(key, GLFW_PRESS);
        tick();
        for (int key : keys.reversed()) input.handleKeyEvent(key, GLFW_RELEASE);
        tick();
    }

    @Test
    void menuRowsFitTheirLabelsAndShortcutCapsShareOneColumn() {
        press(BUTTON_START);
        assertEquals(EditorHud.Modal.MENU, hud.modal);
        // The narrowest HUD has 640 units (EditorHudLayout.uiScale); every row must read whole there.
        for (float layoutWidth : new float[] {640, 800, 1280}) {
            var geometry = EditorHudPainter.menuGeometry(hud.menuItems, layoutWidth);
            assertTrue(geometry.width() <= layoutWidth - 40, "menu stays inside the window at " + layoutWidth);
            assertTrue(geometry.width() >= EditorHudPainter.MENU_MIN_WIDTH);
            for (EditorHud.MenuItem item : hud.menuItems) {
                if (item.header()) continue;
                // Rows are drawn in their own case, so the measured string is the drawn string.
                assertEquals(item.label(), EditorHudCanvas.fit(item.label(), geometry.labelRoom(), EditorHudCanvas.Size.SMALL),
                        "untruncated row at " + layoutWidth + ": " + item.label());
                if (!item.detail().isEmpty()) assertTrue(EditorHudPainter.keycapWidth(item.detail()) <= geometry.capColumn(),
                        "shortcut cap fits the shared column: " + item.detail());
            }
        }
        assertTrue(hud.menuItems.stream().anyMatch(item -> item.label().equals("Level palette (Genesis colors)")
                && !item.header()), "the formerly truncated row is present");
    }

    @Test
    void everyPromptBarFitsTheNarrowestHud() {
        // World, block and chunk depth carry the most prompts; keyboard caps are wider than pad glyphs.
        for (int depth = 0; depth < 3; depth++) {
            for (boolean pad : new boolean[] {true, false}) {
                var fit = EditorHudPainter.promptFit(hud.prompts(), pad, 640);
                assertTrue(fit.total() <= 640 - 12, "prompts fit 640 units at depth " + depth + ", pad " + pad + ": " + fit);
            }
            pullRightTrigger();
        }
        assertEquals("None", EditorHudPainter.readable("NONE"));
        assertEquals("Player right", EditorHudPainter.readable("PLAYER_RIGHT"));
        assertEquals("160", EditorHudPainter.readable(160));
        assertEquals("Monitor", EditorHudPainter.readable("Monitor"));
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
