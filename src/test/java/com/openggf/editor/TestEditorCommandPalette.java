package com.openggf.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorCommandPalette {
    @Test
    void everyActionIsAvailableAndDispatchesThroughTheHost() {
        var actions = new ArrayList<EditorInputHandler.Action>();
        var commands = new EditorCommandPalette().actionCommands(actions::add);
        assertEquals(EditorInputHandler.Action.values().length, commands.size());
        commands.forEach(command -> command.action().run());
        assertEquals(List.of(EditorInputHandler.Action.values()), actions);
        assertTrue(commands.stream().noneMatch(command -> command.label().contains("pane")
                || command.label().toLowerCase(java.util.Locale.ROOT).startsWith("browse")));
    }

    @Test
    void hostsCanSelectTheirActionMenu() {
        var actions = new ArrayList<EditorInputHandler.Action>();
        var commands = new EditorCommandPalette().actionCommands(actions::add,
                action -> action == EditorInputHandler.Action.UNDO);
        assertEquals(1, commands.size());
        assertEquals("Undo", commands.getFirst().label());
        commands.getFirst().action().run();
        assertEquals(List.of(EditorInputHandler.Action.UNDO), actions);
    }

    @Test
    void workflowCommandsRetainTheirActionsWithoutThePaletteUi() {
        var palette = EditorCommandPalette.forController(new LevelEditorController());
        var playtests = new AtomicInteger();
        var starts = new AtomicInteger();
        var projects = new AtomicInteger();
        palette.setHostActions(playtests::incrementAndGet, starts::incrementAndGet);
        palette.setHostCommands(List.of(new EditorCommandPalette.HostCommand("Editor projects", projects::incrementAndGet)));
        var commands = palette.extraCommands();
        assertEquals(List.of("Playtest current edits", "Fresh start", "Editor projects"),
                commands.stream().map(EditorCommandPalette.HostCommand::label).toList());
        commands.forEach(command -> command.action().run());
        assertEquals(1, playtests.get());assertEquals(1, starts.get());assertEquals(1, projects.get());
        palette.setHostCommands(List.of());
        assertEquals(2, palette.extraCommands().size());
    }

    @Test
    void semanticWorkflowCommandsKeepTheExistingDesktopCommandProjection() {
        var palette = new EditorCommandPalette();
        var calls = new AtomicInteger();
        var command = new EditorCommandPalette.HostCommand("Probar nivel", calls::incrementAndGet);
        palette.setWorkflowCommands(List.of(new EditorCommandPalette.WorkflowCommand(
                EditorCommandPalette.WorkflowKind.PLAYTEST, command)));

        assertEquals(List.of(command), palette.extraCommands());
        palette.extraCommands().getFirst().action().run();
        assertEquals(1, calls.get(), "Desktop chrome still receives the original label/action contract");

        palette.setHostCommands(List.of(command));
        assertEquals(EditorCommandPalette.WorkflowKind.COMMAND,
                palette.extraWorkflowCommands().getFirst().kind(), "Legacy host commands have no inferred label semantics");
    }
}
