package com.openggf.editor;

import com.openggf.editor.properties.*;

import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.sonic1.objects.Sonic1ObjectProperties;
import com.openggf.game.sonic2.objects.Sonic2ObjectProperties;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectProperties;
import com.openggf.game.session.EditorCursorState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestObjectPropertySchemas {
    @Test void committedCoverageMatchesEveryProviderEntry() throws Exception {
        assertEquals(java.nio.file.Files.readString(java.nio.file.Path.of("docs/status/editor-object-property-coverage.md")),
                com.openggf.tools.editor.ObjectPropertyCoverageTool.report());
    }
    @Test void everySubtypeIncludingUnknownBitsRoundTripsForEverySchema() {
        for (int id = 0; id < 256; id++) for (var schema : List.of(Sonic1ObjectProperties.schema(id),
                Sonic2ObjectProperties.schema(id), Sonic3kObjectProperties.schema(id))) {
            for (int raw = 0; raw < 256; raw++) {
                int rebuilt = raw;
                for (var field : schema.editableFields()) {
                    rebuilt = field.encode(rebuilt, field.decode(raw));
                    for (int value = 0; value <= field.maximum(); value++)
                        assertEquals(raw & ~field.mask(), field.encode(raw, value) & ~field.mask());
                }
                assertEquals(raw, rebuilt);
            }
        }
    }
    @Test void namedChoicesAndUnknownValuesAreNotNormalized() {
        var schema = Sonic2ObjectProperties.schema(0x41);
        assertEquals("Diagonal down", schema.fields().getFirst().display(0x42));
        assertEquals(0xD3, schema.fields().get(1).encode(0xD1, 1));
        assertTrue(schema.fields().getFirst().display(0x70).startsWith("Unknown"));
        assertEquals(0x70, schema.fields().getFirst().encode(0x70, 7));
        assertTrue(Sonic1ObjectProperties.schema(0x99).fields().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ObjectPropertySchema.RAW.encode(5, 256));
    }
    @Test void editingPropertiesPreservesUninterpretedNativeEnvelopeBits() {
        var level = TestEditorInteractionSafety.level();
        for (var encoding : List.of(new CommonObjectPlacementEncoding(), new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding())) {
            var factory = new EditorSpawnFactory(encoding, new EditorPlacementIdAllocator(level));
            var original = encoding.create(64, 64, 0x41, 0xA1, 1, true, 3);
            var imported = new com.openggf.level.objects.ObjectSpawn(original.x(), original.y(), original.objectId(), original.subtype(),
                    original.renderFlags(), original.respawnTracked(), original.rawYWord() | 0x1000, original.layoutIndex());
            assertEquals(imported.rawYWord(), factory.withProperties(imported, 0xA3, 1, true).rawYWord());
            var changed = factory.withProperties(imported, 0xA3, 2, false);
            var nativeChanged = encoding.create(64, 64, 0x41, 0xA3, 2, false, 3);
            assertEquals(nativeChanged.rawYWord() | 0x1000, changed.rawYWord());
        }
    }
    @Test void controllerPropertyEditIsOneUndoAndKeepsBrushAndNativeFlags() {
        var c = new LevelEditorController(); c.attachLevel(TestEditorInteractionSafety.level());
        c.configureSpawnEditing(new Sonic2ObjectRegistry(), new CommonObjectPlacementEncoding());
        c.setSpawnEditMode(EditorSpawnEditMode.OBJECTS); c.objectPalette().setObjectId(0x41); c.objectPalette().setSubtype(0x81);
        c.setWorldCursor(new EditorCursorState(64, 64)); c.placeSpawnAtCursor();
        var before = c.selectedObjectSpawn();
        c.setSelectedObjectProperty("strength", 1);
        var after = c.selectedObjectSpawn();
        assertEquals(0x83, after.subtype()); assertEquals(before.rawYWord(), after.rawYWord());
        assertEquals(before.layoutIndex(), after.layoutIndex()); assertEquals(0x81, c.objectPalette().selectedSubtype());
        c.undo(); assertEquals(before, c.selectedObjectSpawn());
        c.redo(); assertEquals(after, c.selectedObjectSpawn());
    }
}
