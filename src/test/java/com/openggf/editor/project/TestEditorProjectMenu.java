package com.openggf.editor.project;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuTextEditor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestEditorProjectMenu {
    @Test
    void navigationRunsOnlyTheSelectedActionAndOwnsTheInput() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var menu = new EditorProjectMenu();
        menu.showActions("Projects", List.of(
                new EditorProjectMenu.Action("New", first::incrementAndGet),
                new EditorProjectMenu.Action("Open", second::incrementAndGet)));
        var input = new InputHandler();
        press(menu, input, GLFW_KEY_DOWN);
        press(menu, input, GLFW_KEY_ENTER);
        assertEquals(0, first.get());
        assertEquals(1, second.get());
        assertFalse(menu.isOpen());
    }

    @Test
    void rejectedFormPreservesValuesAndCanBeCorrectedWithoutClosing() {
        var accepted = new AtomicReference<String>();
        var menu = new EditorProjectMenu();
        menu.showForm("Save project", List.of(new EditorProjectMenu.Field("path", "Path", "",
                MenuTextEditor.Mode.PATH)), "Save", values -> {
            if (values.get("path").isBlank()) throw new IllegalArgumentException("Choose a project path");
            accepted.set(values.get("path"));
        });
        menu.submit();
        assertTrue(menu.isOpen());
        assertEquals("Choose a project path", menu.error());
        menu.setValue("path", "projects/my-level.ogfproject");
        menu.submit();
        assertEquals("projects/my-level.ogfproject", accepted.get());
        assertFalse(menu.isOpen());
    }

    @Test
    void textCaptureSuppressesMenuActionsAndEscapeCancelsOnlyTheField() {
        var menu = new EditorProjectMenu();
        var submits = new AtomicInteger();
        menu.showForm("Properties", List.of(new EditorProjectMenu.Field("name", "Name", "Level",
                MenuTextEditor.Mode.TEXT)), "Apply", values -> submits.incrementAndGet());
        var input = new InputHandler();
        press(menu, input, GLFW_KEY_ENTER);
        assertTrue(menu.capturesText());
        MenuInput.handleCharEvent(input, 'x');
        input.refreshLogicalSnapshot();
        menu.update(input);
        input.update();
        press(menu, input, GLFW_KEY_ESCAPE);
        assertFalse(menu.capturesText());
        assertTrue(menu.isOpen());
        assertEquals("Level", menu.values().get("name"));
        assertEquals(0, submits.get());
    }

    @Test
    void callbackCanReplaceTheMenuWithTheNextWizardPage() {
        var menu = new EditorProjectMenu();
        menu.showActions("Games", List.of(new EditorProjectMenu.Action("Sonic 1", () ->
                menu.showActions("Zones", List.of(new EditorProjectMenu.Action("Green Hill", () -> {}))))));
        press(menu, new InputHandler(), GLFW_KEY_ENTER);
        assertTrue(menu.isOpen());
        assertEquals("Zones", menu.title());
    }

    private static void press(EditorProjectMenu menu, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        if (key == GLFW_KEY_ENTER) {
            assertFalse(MenuInput.back(input), "physical Enter must not be Back");
            assertTrue(MenuInput.accept(input), "physical Enter must be Accept");
        }
        menu.update(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }
}
