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
        palette.setHostCommands(List.of(new EditorCommandPalette.HostCommand("Editor projects", "F4", projects::incrementAndGet)));
        var commands = palette.extraCommands();
        assertEquals(List.of("Playtest current edits", "Fresh start", "Editor projects"),
                commands.stream().map(EditorCommandPalette.HostCommand::label).toList());
        assertEquals("F4", commands.getLast().shortcut(), "a host's real key travels with its command");
        commands.forEach(command -> command.action().run());
        assertEquals(1, playtests.get());assertEquals(1, starts.get());assertEquals(1, projects.get());
        palette.setHostCommands(List.of());
        assertEquals(2, palette.extraCommands().size());
    }

    @Test
    void everyActionHasOneAuthoredDescriptorOrAReasonToStayOutOfMenus() {
        var described = EditorCommandPalette.descriptors().stream().map(EditorCommandPalette.Descriptor::action).toList();
        assertEquals(described.size(), described.stream().distinct().count(), "one descriptor per action");
        for (var action : EditorInputHandler.Action.values()) {
            boolean excluded = EditorCommandPalette.excluded().containsKey(action);
            assertTrue(described.contains(action) ^ excluded, action + " is described or excluded, not both");
            if (excluded) assertFalse(EditorCommandPalette.excluded().get(action).isBlank());
        }
        var labels = EditorCommandPalette.descriptors().stream().map(EditorCommandPalette.Descriptor::label).toList();
        assertEquals(labels.size(), labels.stream().distinct().count(), "labels are unique: " + labels);
        for (var descriptor : EditorCommandPalette.descriptors()) {
            assertFalse(descriptor.label().matches("(Increment|Decrement|Cycle|Toggle [hv] |Perform|Apply primary).*"),
                    "authored, not derived from the enum: " + descriptor.label());
            assertEquals(descriptor.label(), descriptor.label().strip());
            assertTrue(Character.isUpperCase(descriptor.label().charAt(0)), "sentence case: " + descriptor.label());
        }
        var groups = EditorCommandPalette.descriptors().stream().map(EditorCommandPalette.Descriptor::group).distinct().toList();
        assertEquals(List.of(EditorCommandPalette.Group.values()), groups, "descriptors are in menu group order");
    }
}
