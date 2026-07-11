package com.openggf.editor;

import com.openggf.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEditorTraceModeGuard {

    @Test
    void activeTraceRefusesEditorEntryEvenWhenEditorIsEnabled() {
        assertFalse(Engine.editorEntryAllowed(true, true));
    }

    @Test
    void enabledEditorCanBeEnteredWithoutAnActiveTrace() {
        assertTrue(Engine.editorEntryAllowed(true, false));
    }

    @Test
    void disabledEditorCannotBeEnteredWithoutAnActiveTrace() {
        assertFalse(Engine.editorEntryAllowed(false, false));
    }
}
