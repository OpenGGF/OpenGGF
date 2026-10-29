package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestLanguagePickerScreen {
    @AfterEach
    void restoreEnglish() { UiMessages.selectLanguage("en"); }

    @Test
    void everyDiscoveredCatalogAndAutomaticCanBeSelectedWithoutAHardcodedLanguageRegistry() {
        UiMessages.selectLanguage("en");
        var selected = new ArrayList<String>();
        assertFalse(UiMessages.languages().isEmpty());
        new LanguagePickerScreen("auto", selected::add).choose();
        assertEquals("auto", selected.removeFirst());
        for (var language : UiMessages.languages()) {
            new LanguagePickerScreen(language.tag(), selected::add).choose();
            assertEquals(language.tag(), selected.removeFirst());
            assertEquals(language.name(), LanguagePickerScreen.displayName(language.tag()));
        }
        assertEquals("en", UiMessages.locale().toLanguageTag(), "selection callback owns activation");
    }

    @Test
    void backClosesWithoutCallingTheSaveBoundary() {
        var calls = new AtomicInteger();
        var picker = new LanguagePickerScreen("auto", tag -> calls.incrementAndGet());
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        assertTrue(picker.update(input));
        assertEquals(0, calls.get());
    }

    @Test
    void aFailedSaveKeepsTheSameChoiceForRetry() {
        var calls = new AtomicInteger();
        var selected = new ArrayList<String>();
        var picker = new LanguagePickerScreen("ja", tag -> {
            selected.add(tag);
            if (calls.incrementAndGet() == 1) throw new IOException("retry me");
        });
        picker.choose();
        assertFalse(picker.update(null));
        picker.choose();
        assertTrue(picker.update(null));
        assertEquals(java.util.List.of("ja", "ja"), selected);
    }
}
