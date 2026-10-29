package com.openggf.editor;

import com.openggf.i18n.UiMessages;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared command catalogue for the built-in HUD and desktop editor hosts. */
public final class EditorCommandPalette {
    private static final EditorInputHandler.Action[] ACTIONS = EditorInputHandler.Action.values();
    private Runnable playtest;
    private Runnable freshStart;
    private List<WorkflowCommand> extraCommands = List.of();
    private List<WorkflowCommand> hostCommands = List.of();
    /** Host semantics used by the built-in HUD, independent of presentation wording. */
    public enum WorkflowKind { COMMAND, PLAYTEST, FRESH_START, FILTER }

    /** Keeps the existing desktop chrome command record free of HUD-specific metadata. */
    public record WorkflowCommand(WorkflowKind kind, HostCommand command) {
        public WorkflowCommand {
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(command, "command");
        }
    }
    public record HostCommand(String label, Runnable action) {
        public HostCommand {
            java.util.Objects.requireNonNull(label, "label");
            java.util.Objects.requireNonNull(action, "action");
        }
    }
    public void setHostCommands(List<HostCommand> commands) {
        setWorkflowCommands(commands.stream()
                .map(command -> new WorkflowCommand(WorkflowKind.COMMAND, command)).toList());
    }
    /** Engine hosts can identify special workflow commands without encoding their role in a label. */
    public void setWorkflowCommands(List<WorkflowCommand> commands) {
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
        return extraCommands.stream().map(WorkflowCommand::command).toList();
    }

    /** Semantic workflow entries for the built-in HUD; desktop chrome retains {@link #extraCommands()}. */
    public List<WorkflowCommand> extraWorkflowCommands() {
        return extraCommands;
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
        List<WorkflowCommand> commands = new ArrayList<>();
        if (playtest != null) commands.add(new WorkflowCommand(WorkflowKind.PLAYTEST,
                new HostCommand(UiMessages.text("editor.editor_command_palette.playtest_current_edits_1ci1094"), playtest)));
        if (freshStart != null) commands.add(new WorkflowCommand(WorkflowKind.FRESH_START,
                new HostCommand(UiMessages.text("editor.editor_command_palette.fresh_start_rhl974"), freshStart)));
        commands.addAll(hostCommands);
        extraCommands = List.copyOf(commands);
    }

    private static String label(EditorInputHandler.Action action) {
        return switch (action) {
            case DESCEND -> UiMessages.text("editor.editor_command_palette.edit_selected_block_chunk_z5bwyi");
            case ASCEND -> UiMessages.text("editor.editor_command_palette.return_to_parent_view_xuxxqe");
            case APPLY_PRIMARY_ACTION -> UiMessages.text("editor.editor_command_palette.place_apply_selection_1xavng0");
            case PERFORM_EYEDROP -> UiMessages.text("editor.editor_command_palette.pick_content_at_cursor_g7qti5");
            case CYCLE_SPAWN_EDIT_MODE -> UiMessages.text("editor.editor_command_palette.next_placement_mode_szgx4b");
            default -> EditorLabels.action(action);
        };
    }
}
