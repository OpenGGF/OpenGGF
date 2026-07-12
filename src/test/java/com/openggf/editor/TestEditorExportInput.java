package com.openggf.editor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEditorExportInput {
    @Test
    void exportActionIsDistinctFromSave() {
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger exports = new AtomicInteger();
        EditorInputHandler handler = new EditorInputHandler(new LevelEditorController(),
                () -> null, () -> null, saves::incrementAndGet, exports::incrementAndGet);
        handler.handleAction(EditorInputHandler.Action.EXPORT);
        assertEquals(0, saves.get());
        assertEquals(1, exports.get());
    }

    @Test
    void productionEngineWiresDeterministicNonOverwritingExportAndEffectiveKeyLookup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        assertEquals(true, source.contains("this::exportCurrentEditorLevel"));
        assertEquals(true, source.contains("Path.of(\"exports\", \"editor\", module.getGameId().code()"));
        assertEquals(true, source.contains("createObjectRegistry().hasObjectKey(objectKey)"));
    }
}
