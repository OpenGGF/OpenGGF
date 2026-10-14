package com.openggf;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestContentImportWindowBridge {
    @Test
    void callbackDoesNotOpenAModalAndNextUiFrameConsumesTheDropOnce() {
        var delivered = new ArrayList<List<Path>>();
        var errors = new ArrayList<String>();
        var bridge = new ContentImportWindowBridge(delivered::add, () -> null, errors::add);
        var input = new ArrayList<>(List.of("a level.zip"));
        bridge.receive(input);
        input.clear();
        assertTrue(delivered.isEmpty(), "GLFW callbacks must not open or inspect content");
        bridge.update(); bridge.update();
        assertEquals(List.of(List.of(Path.of("a level.zip"))), delivered);
        assertTrue(errors.isEmpty());
    }

    @Test
    void blockedDropIsDiscardedAndNeverReappearsAfterGameplayOrUnsavedPrompt() {
        var delivered = new ArrayList<List<Path>>();
        var errors = new ArrayList<String>();
        var block = new AtomicReference<>("Finish the current dialog before importing.");
        var bridge = new ContentImportWindowBridge(delivered::add, block::get, errors::add);
        bridge.receive(List.of("course.ogfproject"));
        bridge.update();
        block.set(null); bridge.update();
        assertTrue(delivered.isEmpty());
        assertEquals(List.of("Finish the current dialog before importing."), errors);
    }

    @Test
    void invalidPathCannotEscapeTheUiFrame() {
        var errors = new ArrayList<String>();
        var bridge = new ContentImportWindowBridge(paths -> fail("invalid path must not reach import"), () -> null, errors::add);
        bridge.receive(List.of("bad\0name.zip"));
        assertDoesNotThrow(bridge::update);
        assertEquals(1, errors.size());
    }
}
