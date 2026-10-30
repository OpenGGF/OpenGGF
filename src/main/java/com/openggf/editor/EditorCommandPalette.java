package com.openggf.editor;

import com.openggf.i18n.UiMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared command catalogue for the built-in HUD and desktop editor hosts. */
public final class EditorCommandPalette {
    private static final EditorInputHandler.Action[] ACTIONS = EditorInputHandler.Action.values();
    private Runnable playtest;
    private Runnable freshStart;
    private Runnable exit;
    private List<ExtraCommand> extraCommands = List.of();
    private List<WorkflowCommand> hostCommands = List.of();
    private List<HostCommand> workspaceCommands = List.of();
    /** Semantic role used by the HUD, independent of localized wording. */
    public enum WorkflowKind { COMMAND, PLAYTEST, FRESH_START, FILTER }

    public record WorkflowCommand(WorkflowKind kind, HostCommand command) {
        public WorkflowCommand {
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(command, "command");
        }
    }

    /** A host workflow command; {@code shortcut} is the key the host binds to it ("" for none), shown as a key cap. */
    public record HostCommand(String label, String shortcut, Runnable action) {
        public HostCommand {
            java.util.Objects.requireNonNull(label, "label");
            java.util.Objects.requireNonNull(shortcut, "shortcut");
            java.util.Objects.requireNonNull(action, "action");
        }

        public HostCommand(String label, Runnable action) { this(label, "", action); }
    }
    /** Project and file commands from the host; the menu lists them under Project. */
    public void setHostCommands(List<HostCommand> commands) {
        setWorkflowCommands(commands.stream()
                .map(command -> new WorkflowCommand(WorkflowKind.COMMAND, command)).toList());
    }
    /** Engine hosts can identify special workflow commands without encoding their role in a label. */
    public void setWorkflowCommands(List<WorkflowCommand> commands) {
        hostCommands = List.copyOf(commands);
        rebuildExtraCommands();
    }
    /** Commands that switch the editor's workspace (such as Stageplay); the menu lists them under Editor. */
    public void setWorkspaceCommands(List<HostCommand> commands) {
        workspaceCommands = List.copyOf(commands);
        rebuildExtraCommands();
    }
    /**
     * The host's playtest entry points. {@code playtest} starts play at the editor cursor (the host
     * binds it to Shift+Tab, which also returns to the editor) and {@code freshStart} starts the
     * level from its start position (F5); the menu lists both under Playtest.
     */
    public void setHostActions(Runnable playtest, Runnable freshStart) {
        this.playtest = playtest;
        this.freshStart = freshStart;
        rebuildExtraCommands();
    }

    /**
     * The host's way out of the editor (it asks about unsaved work first). The built-in menu lists it
     * on its front page and at the end of Project; null when the host offers none.
     */
    public void setExitAction(Runnable exit) { this.exit = exit; }
    public Runnable exitAction() { return exit; }

    public static EditorCommandPalette forController(LevelEditorController controller) {
        return controller.commandPalette();
    }

    /** Host and workflow commands only (projects, playtest, import), for hosts that draw their own menus. */
    public List<HostCommand> extraCommands() {
        return extraCommands.stream().map(ExtraCommand::host).toList();
    }

    /** The host and workflow commands the menu lists under {@code group}, in order. */
    public List<HostCommand> extraCommands(Group group) {
        java.util.Objects.requireNonNull(group, "group");
        return extraCommands.stream().filter(command -> command.group() == group && command.kind() != WorkflowKind.FILTER)
                .map(ExtraCommand::host).toList();
    }

    /** Semantic entries for the HUD; desktop chrome retains the original command projection. */
    public List<WorkflowCommand> extraWorkflowCommands() {
        return extraCommands.stream().map(command -> new WorkflowCommand(command.kind(), command.host())).toList();
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
        // Both play the current edits; they differ only in where the player starts.
        if (playtest != null) commands.add(new ExtraCommand(new HostCommand(UiMessages.text("editor.editor_project_workspace.playtest_from_cursor_1o3u636"), "Shift+Tab", playtest), Group.PLAYTEST, WorkflowKind.PLAYTEST));
        if (freshStart != null) commands.add(new ExtraCommand(new HostCommand(UiMessages.text("editor.editor_project_workspace.playtest_from_level_start_qbtxze"), "F5", freshStart), Group.PLAYTEST, WorkflowKind.FRESH_START));
        workspaceCommands.forEach(command -> commands.add(new ExtraCommand(command, Group.EDITOR, WorkflowKind.COMMAND)));
        hostCommands.forEach(command -> commands.add(new ExtraCommand(command.command(),
                command.kind() == WorkflowKind.PLAYTEST || command.kind() == WorkflowKind.FRESH_START ? Group.PLAYTEST : Group.PROJECT,
                command.kind())));
        extraCommands = List.copyOf(commands);
    }

    private record ExtraCommand(HostCommand host, Group group, WorkflowKind kind) { }

    /**
     * Where a command sits in the editor menu, in menu order. Editor holds the HUD's own pages
     * and workspace switches; Playtest and the host's file commands have no editor action.
     */
    public enum Group {
        EDITOR("Editor"), EDIT("Edit"), REGION("Region"), PATTERN("Pattern and tile"), OBJECTS("Objects"), VIEW("View"),
        PLAYTEST("Playtest"), PROJECT("Project");

        public final String caption;

        Group(String caption) { this.caption = caption; }
        public String caption() { return UiMessages.text(switch (this) {
            case EDITOR -> "editor.editor_hud.editor_y90ux9";
            case EDIT -> "editor.menu.group.edit";
            case REGION -> "editor.menu.group.region";
            case PATTERN -> "editor.menu.group.pattern";
            case OBJECTS -> "editor.editor_hud.objects_2z9is";
            case VIEW -> "editor.menu.group.view";
            case PLAYTEST -> "editor.editor_hud.playtest_w5m8km";
            case PROJECT -> "editor.editor_hud.project_mexp15";
        }); }
    }

    /**
     * When a shared key reaches this command. Del, Esc and Enter each mean one thing per context;
     * {@code EditorInputHandler} picks the command, and the menu groups say which is which. Each
     * context states the editor state it needs: a selected region, object placement mode and an
     * active paste preview, each required ({@code TRUE}), excluded ({@code FALSE}) or not
     * consulted ({@code null}).
     */
    public enum Context {
        ANY(null, null, null),
        /** Enter descends unless a paste preview is open (Enter then places the paste). */
        NO_PASTE_PREVIEW(null, null, false),
        /** Esc goes up a level when neither a paste preview nor a region is open. */
        NO_REGION_OR_PASTE_PREVIEW(false, null, false),
        /** Del deletes a selected region whatever else is open. */
        REGION_SELECTED(true, null, null),
        /** Esc clears a selected region once no paste preview is open. */
        REGION_SELECTED_NO_PASTE_PREVIEW(true, null, false),
        /** Del deletes the object at the cursor in placement mode when no region is selected. */
        OBJECT_MODE_NO_REGION(false, true, null),
        /** Del erases at the cursor outside placement mode when no region is selected. */
        NO_REGION_NOT_OBJECT_MODE(false, false, null);

        private final Boolean region, objectMode, pastePreview;

        Context(Boolean region, Boolean objectMode, Boolean pastePreview) {
            this.region = region;
            this.objectMode = objectMode;
            this.pastePreview = pastePreview;
        }

        /** Whether a shortcut with this context reaches its command in the given editor state. */
        public boolean matches(boolean regionSelected, boolean inObjectMode, boolean pastePreviewActive) {
            return (region == null || region == regionSelected)
                    && (objectMode == null || objectMode == inObjectMode)
                    && (pastePreview == null || pastePreview == pastePreviewActive);
        }
    }

    /**
     * An authored menu entry for one editor action: its label, its group and the keyboard shortcut
     * {@link EditorInputHandler#update} actually binds (empty when it has none), plus the context in
     * which that shortcut reaches it.
     */
    public record Descriptor(EditorInputHandler.Action action, String labelId, Group group, String shortcut, Context context) {
        public String label() { return UiMessages.text(labelId); }
        public Descriptor {
            java.util.Objects.requireNonNull(action, "action");
            java.util.Objects.requireNonNull(labelId, "labelId");
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
        d.add(new Descriptor(EditorInputHandler.Action.UNDO, "editor.editor_hud.undo_1klxg", g, "Ctrl+Z", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.REDO, "editor.editor_hud.redo_1iiam", g, "Ctrl+Y", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.APPLY_PRIMARY_ACTION, "editor.menu.action.apply_primary_action", g, "Space", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PERFORM_EYEDROP, "editor.editor_command_palette.pick_content_at_cursor_g7qti5", g, "E", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ERASE_SELECTION, "editor.menu.action.erase_selection", g, "Del", Context.NO_REGION_NOT_OBJECT_MODE));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_H_FLIP, "editor.menu.action.toggle_h_flip", g, "H", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_V_FLIP, "editor.menu.action.toggle_v_flip", g, "J", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DESCEND, "editor.editor_command_palette.edit_selected_block_chunk_z5bwyi", g, "Enter", Context.NO_PASTE_PREVIEW));
        d.add(new Descriptor(EditorInputHandler.Action.ASCEND, "editor.menu.action.ascend", g, "Esc", Context.NO_REGION_OR_PASTE_PREVIEW));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_LAYER, "editor.menu.action.toggle_layer", g, "L", Context.ANY));
        g = Group.REGION;
        d.add(new Descriptor(EditorInputHandler.Action.SELECT_REGION_CORNER, "editor.menu.action.select_region_corner", g, "F6", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.COPY_REGION, "editor.editor_hud.copy_region_hskli7", g, "Ctrl+C", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CUT_REGION, "editor.editor_hud.cut_region_bjfs1e", g, "Ctrl+X", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PASTE_REGION, "editor.editor_hud.paste_region_preview_1jfjdux", g, "Ctrl+V", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DELETE_REGION, "editor.editor_hud.delete_region_ac533d", g, "Del", Context.REGION_SELECTED));
        d.add(new Descriptor(EditorInputHandler.Action.FILL_REGION, "editor.menu.action.fill_region", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.FLOOD_FILL, "editor.editor_hud.flood_fill_at_cursor_17z3uvc", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CLEAR_REGION, "editor.action.clear_region", g, "Esc", Context.REGION_SELECTED_NO_PASTE_PREVIEW));
        g = Group.PATTERN;
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_PATTERN_PALETTE, "editor.menu.action.cycle_pattern_palette", g, "G", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_PATTERN_PRIORITY, "editor.menu.action.toggle_pattern_priority", g, "T", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_COLLISION_MODE, "editor.menu.action.cycle_collision_mode", g, "V", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SOLID_TILE_INDEX, "editor.menu.action.increment_solid_tile_index", g, "]", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SOLID_TILE_INDEX, "editor.menu.action.decrement_solid_tile_index", g, "[", Context.ANY));
        g = Group.OBJECTS;
        d.add(new Descriptor(EditorInputHandler.Action.CYCLE_SPAWN_EDIT_MODE, "editor.editor_command_palette.next_placement_mode_szgx4b", g, "O", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DELETE_SPAWN, "editor.menu.action.delete_spawn", g, "Del", Context.OBJECT_MODE_NO_REGION));
        d.add(new Descriptor(EditorInputHandler.Action.MOVE_SELECTED_SPAWN_TO_CURSOR, "editor.menu.action.move_selected_spawn_to_cursor", g, "M", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SELECTED_SUBTYPE, "editor.menu.action.increment_selected_subtype", g, "=", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SELECTED_SUBTYPE, "editor.menu.action.decrement_selected_subtype", g, "-", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_SELECTED_RESPAWN, "editor.menu.action.toggle_selected_respawn", g, "R", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.NEXT_OBJECT, "editor.menu.action.next_object", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.PREVIOUS_OBJECT, "editor.menu.action.previous_object", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.INCREMENT_SUBTYPE, "editor.menu.action.increment_subtype", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.DECREMENT_SUBTYPE, "editor.menu.action.decrement_subtype", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_H_FLIP, "editor.menu.action.toggle_brush_h_flip", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_V_FLIP, "editor.menu.action.toggle_brush_v_flip", g, "", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_BRUSH_RESPAWN, "editor.menu.action.toggle_brush_respawn", g, "", Context.ANY));
        g = Group.VIEW;
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_COLLISION_OVERLAY, "editor.menu.action.toggle_collision_overlay", g, "C", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.TOGGLE_COLLISION_PATH, "editor.menu.action.toggle_collision_path", g, "P", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ZOOM_IN, "editor.action.zoom_in", g, "Ctrl+=", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.ZOOM_OUT, "editor.action.zoom_out", g, "Ctrl+-", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.RESET_CANVAS_ZOOM, "editor.menu.action.reset_canvas_zoom", g, "Ctrl+0", Context.ANY));
        g = Group.PROJECT;
        d.add(new Descriptor(EditorInputHandler.Action.SAVE, "editor.editor_hud.save_project_1tfh0gm", g, "Ctrl+S", Context.ANY));
        d.add(new Descriptor(EditorInputHandler.Action.EXPORT, "editor.editor_hud.export_level_zarsqg", g, "Ctrl+Shift+E", Context.ANY));
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
            case PAN_LEFT -> EditorLabels.action(action);
            case PAN_RIGHT -> EditorLabels.action(action);
            case PAN_UP -> EditorLabels.action(action);
            case PAN_DOWN -> EditorLabels.action(action);
            default -> throw new IllegalStateException("No descriptor for " + action);
        };
    }
}
