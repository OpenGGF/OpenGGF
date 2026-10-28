package com.openggf.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared command catalogue for the built-in HUD and desktop editor hosts. */
public final class EditorCommandPalette {
    private static final EditorInputHandler.Action[] ACTIONS = EditorInputHandler.Action.values();
    private Runnable playtest;
    private Runnable freshStart;
    private List<ExtraCommand> extraCommands = List.of();
    private List<HostCommand> hostCommands = List.of();
    /** A host workflow command; {@code shortcut} is the key the host binds to it ("" for none), shown as a key cap. */
    public record HostCommand(String label, String shortcut, Runnable action) {
        public HostCommand {
            java.util.Objects.requireNonNull(label, "label");
            java.util.Objects.requireNonNull(shortcut, "shortcut");
            java.util.Objects.requireNonNull(action, "action");
        }

        public HostCommand(String label, Runnable action) { this(label, "", action); }
    }
    public void setHostCommands(List<HostCommand> commands) {
        hostCommands = List.copyOf(commands);
        rebuildExtraCommands();
    }
    public void setHostActions(Runnable playtest, Runnable freshStart) {
        this.playtest = playtest;
        this.freshStart = freshStart;
        rebuildExtraCommands();
    }

    public static EditorCommandPalette forController(LevelEditorController controller) {
        return controller.commandPalette();
    }

    /** Host and workflow commands only (projects, playtest, import), for hosts that draw their own menus. */
    public List<HostCommand> extraCommands() {
        return extraCommands.stream().map(command -> new HostCommand(command.label(), command.shortcut(), command.action())).toList();
    }

    /** Every editor action with its palette label, dispatched through {@code dispatch}. */
    public List<HostCommand> actionCommands(Consumer<EditorInputHandler.Action> dispatch) {
        return actionCommands(dispatch, action -> true);
    }

    /** The editor actions {@code include} accepts, for a host-specific menu. */
    public List<HostCommand> actionCommands(Consumer<EditorInputHandler.Action> dispatch,
                                            java.util.function.Predicate<EditorInputHandler.Action> include) {
        java.util.Objects.requireNonNull(dispatch, "dispatch");
        java.util.Objects.requireNonNull(include, "include");
        List<HostCommand> commands = new ArrayList<>(ACTIONS.length);
        for (var action : ACTIONS) {
            if (include.test(action)) commands.add(new HostCommand(label(action), () -> dispatch.accept(action)));
        }
        return List.copyOf(commands);
    }

    private void rebuildExtraCommands() {
        List<ExtraCommand> commands = new ArrayList<>();
        if (playtest != null) commands.add(new ExtraCommand("Playtest current edits", "", playtest));
        if (freshStart != null) commands.add(new ExtraCommand("Fresh start", "", freshStart));
        hostCommands.forEach(command -> commands.add(new ExtraCommand(command.label(), command.shortcut(), command.action())));
        extraCommands = List.copyOf(commands);
    }

    private record ExtraCommand(String label, String shortcut, Runnable action) { }

    /** Where a command sits in the editor menu, in menu order. */
    public enum Group {
        EDIT("Edit"), REGION("Region"), PATTERN("Pattern and tile"), OBJECTS("Objects"), VIEW("View"), PROJECT("Project");

        public final String caption;

        Group(String caption) { this.caption = caption; }
    }

    /**
     * When a shared key reaches this command. Del, Esc and Enter each mean one thing per context;
     * {@code EditorInputHandler} picks the command, and the menu groups say which is which.
     */
    public enum Context {
        ANY, NO_REGION, REGION_SELECTED, OBJECT_MODE, NOT_OBJECT_MODE
    }

    /**
     * An authored menu entry for one editor action: its label, its group and the keyboard shortcut
     * {@link EditorInputHandler#update} actually binds (empty when it has none), plus the context in
     * which that shortcut reaches it.
     */
    public record Descriptor(EditorInputHandler.Action action, String label, Group group, String shortcut, Context context) {
        public Descriptor {
            java.util.Objects.requireNonNull(action, "action");
            java.util.Objects.requireNonNull(label, "label");
            java.util.Objects.requireNonNull(group, "group");
            java.util.Objects.requireNonNull(shortcut, "shortcut");
            java.util.Objects.requireNonNull(context, "context");
        }
    }

    private static final List<Descriptor> DESCRIPTORS;
    /** Actions with no menu row, and why: continuous movement is a held control, not a command. */
    private static final java.util.Map<EditorInputHandler.Action, String> EXCLUDED;

    static {
        List<Descriptor> d = new ArrayList<>();
        Group g = Group.EDIT;
        d.add(new Descriptor(EditorInputHandler.Action.APPLY_PRIMARY_ACTION, "Place brush / apply", g, "Space", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PERFORM_EYEDROP, "Pick content at cursor", g, "E", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ERASE_SELECTION, "Erase at cursor", g, "Del", Context.NOT_OBJECT_MODE));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_H_FLIP, "Flip horizontally", g, "H", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_V_FLIP, "Flip vertically", g, "J", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DESCEND, "Edit selected block / chunk", g, "Enter", Context.NO_REGION));
        d.add(new Descriptor(EditorInputHandler.Action.ASCEND, "Back to parent view", g, "Esc", Context.NO_REGION));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_LAYER, "Switch layer", g, "L", Context.ANY));
        g = Group.REGION;
        d.add(new Descriptor(EditorInputHandler.Action.SELECT_REGION_CORNER, "Set corner / finish region", g, "F6", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.COPY_REGION, "Copy region", g, "Ctrl+C", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CUT_REGION, "Cut region", g, "Ctrl+X", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PASTE_REGION, "Paste region preview", g, "Ctrl+V", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DELETE_REGION, "Delete region", g, "Del", Context.REGION_SELECTED));
        d.add(new Descriptor(EditorInputHandler.Action.FILL_REGION, "Fill region with brush", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.FLOOD_FILL, "Flood fill at cursor", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CLEAR_REGION, "Clear region", g, "Esc", Context.REGION_SELECTED));
        g = Group.PATTERN;
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_PATTERN_PALETTE, "Next palette line", g, "G", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_PATTERN_PRIORITY, "Toggle priority", g, "T", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_COLLISION_MODE, "Next collision mode", g, "V", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SOLID_TILE_INDEX, "Next solid tile", g, "]", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SOLID_TILE_INDEX, "Previous solid tile", g, "[", Context.ANY));
        g = Group.OBJECTS;
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_SPAWN_EDIT_MODE, "Next placement mode", g, "O", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DELETE_SPAWN, "Delete object at cursor", g, "Del", Context.OBJECT_MODE));
        d.add(new Descriptor(EditorInputHandler.Action.MOVE_SELECTED_SPAWN_TO_CURSOR, "Move selected object here", g, "M", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SELECTED_SUBTYPE, "Selected: next subtype", g, "=", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SELECTED_SUBTYPE, "Selected: previous subtype", g, "-", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_SELECTED_RESPAWN, "Selected: toggle respawn", g, "R", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.NEXT_OBJECT, "Brush: next object", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PREVIOUS_OBJECT, "Brush: previous object", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SUBTYPE, "Brush: next subtype", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SUBTYPE, "Brush: previous subtype", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_H_FLIP, "Brush: flip horizontally", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_V_FLIP, "Brush: flip vertically", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_RESPAWN, "Brush: toggle respawn", g, "", Context.ANY));
        g = Group.VIEW;
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_COLLISION_OVERLAY, "Show collision", g, "C", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_COLLISION_PATH, "Switch collision path", g, "P", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ZOOM_IN, "Zoom in", g, "Ctrl+=", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ZOOM_OUT, "Zoom out", g, "Ctrl+-", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.RESET_CANVAS_ZOOM, "Reset zoom", g, "Ctrl+0", Context.ANY));
        g = Group.PROJECT;
        d.add(new Descriptor(EditorInputHandler.Action.SAVE, "Save project", g, "Ctrl+S", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.UNDO, "Undo", g, "Ctrl+Z", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.REDO, "Redo", g, "Ctrl+Y", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.EXPORT, "Export level", g, "Ctrl+Shift+E", Context.ANY));
        DESCRIPTORS = List.copyOf(d);
        var excluded = new java.util.EnumMap<EditorInputHandler.Action, String>(EditorInputHandler.Action.class);
        String pan = "continuous movement: Ctrl+arrows in the world view, or LB+RB with the left stick";
        for (var action : List.of(EditorInputHandler.Action.PAN_LEFT, EditorInputHandler.Action.PAN_RIGHT,
                EditorInputHandler.Action.PAN_UP, EditorInputHandler.Action.PAN_DOWN)) excluded.put(action, pan);
        EXCLUDED = java.util.Collections.unmodifiableMap(excluded);
    }

    /** Every menu command in menu order (group, then authored order within the group). */
    public static List<Descriptor> descriptors() { return DESCRIPTORS; }

    /** Actions deliberately kept out of menus, with the reason. */
    public static java.util.Map<EditorInputHandler.Action, String> excluded() { return EXCLUDED; }

    /** The authored descriptor for {@code action}, or null for an excluded action. */
    public static Descriptor descriptor(EditorInputHandler.Action action) {
        for (Descriptor descriptor : DESCRIPTORS) if (descriptor.action() == action) return descriptor;
        return null;
    }

    private static String label(EditorInputHandler.Action action) {
        Descriptor descriptor = descriptor(action);
        // Excluded actions keep a plain name for hosts that list every action.
        return descriptor != null ? descriptor.label() : switch (action) {
            case PAN_LEFT -> "Pan left";
            case PAN_RIGHT -> "Pan right";
            case PAN_UP -> "Pan up";
            case PAN_DOWN -> "Pan down";
            default -> throw new IllegalStateException("No descriptor for " + action);
        };
    }
}
