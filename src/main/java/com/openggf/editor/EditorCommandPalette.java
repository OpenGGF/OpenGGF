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
    public record HostCommand(String label, Runnable action) {
        public HostCommand {
            java.util.Objects.requireNonNull(label, "label");
            java.util.Objects.requireNonNull(action, "action");
        }
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
        return extraCommands.stream().map(command -> new HostCommand(command.label(), command.action())).toList();
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
        if (playtest != null) commands.add(new ExtraCommand("Playtest current edits", playtest));
        if (freshStart != null) commands.add(new ExtraCommand("Fresh start", freshStart));
        hostCommands.forEach(command -> commands.add(new ExtraCommand(command.label(), command.action())));
        extraCommands = List.copyOf(commands);
    }

    private record ExtraCommand(String label, Runnable action) { }

    private static String label(EditorInputHandler.Action action) {
        return switch (action) {
            case DESCEND -> "Edit selected block / chunk";
            case ASCEND -> "Return to parent view";
            case APPLY_PRIMARY_ACTION -> "Place / apply selection";
            case PERFORM_EYEDROP -> "Pick content at cursor";
            case CYCLE_SPAWN_EDIT_MODE -> "Next placement mode";
            default -> {
                // Enum names abbreviate the flip axes; menu rows spell them out ("Toggle horizontal flip").
                String text = action.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                        .replace(" h flip", " horizontal flip").replace(" v flip", " vertical flip");
                yield Character.toUpperCase(text.charAt(0)) + text.substring(1);
            }
        };
    }
}
