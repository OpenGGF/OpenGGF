package com.openggf.editor;

import com.openggf.level.metadata.*;

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
    @Test void commonAuthoringFamiliesHaveNamedFieldsWithOwningReferences() {
        int[][] ids = {
                {0x0C, 0x0D, 0x11, 0x15, 0x16, 0x18, 0x1A, 0x1E, 0x26, 0x2C, 0x32, 0x33, 0x36, 0x3E, 0x41, 0x42, 0x51, 0x53, 0x79},
                {0x0D, 0x11, 0x15, 0x18, 0x19, 0x1A, 0x1B, 0x1F, 0x26, 0x36, 0x3E, 0x41, 0x47, 0x68, 0x6B, 0x6D, 0x77, 0x79, 0x81, 0x95},
                {0x01, 0x02, 0x04, 0x05, 0x07, 0x08, 0x0D, 0x26, 0x28, 0x33, 0x34, 0x3D, 0x51}
        };
        List<java.util.function.IntFunction<ObjectPropertySchema>> providers = List.of(
                Sonic1ObjectProperties::schema, Sonic2ObjectProperties::schema, Sonic3kObjectProperties::schema);
        for (int game = 0; game < ids.length; game++) for (int id : ids[game]) {
            ObjectPropertySchema schema = providers.get(game).apply(id);
            assertFalse(schema.fields().isEmpty(), "Missing common authoring schema for game " + game + " object " + id);
            assertTrue(schema.reference().contains(".asm:"), "Authored meanings have an owning ROM reference");
        }
    }
    @Test void spikesRetainDistinctGameShapesAndCheckpointHighBits() {
        assertEquals("1 upright", field(Sonic1ObjectProperties.schema(0x36), "shape").display(0x21));
        assertEquals("Upright 96px", field(Sonic2ObjectProperties.schema(0x36), "shape").display(0x21));
        assertEquals("Upright 96px", field(Sonic3kObjectProperties.schema(8), "shape").display(0x21));
        assertEquals("Push-triggered sideways", field(Sonic3kObjectProperties.schema(8), "movement").display(0x43));
        assertTrue(field(Sonic1ObjectProperties.schema(0x36), "movement").display(3).startsWith("Unknown"));
        for (ObjectPropertySchema schema : List.of(Sonic1ObjectProperties.schema(0x79),
                Sonic2ObjectProperties.schema(0x79), Sonic3kObjectProperties.schema(0x34))) {
            var number = field(schema, "number");
            assertEquals(5, number.decode(0x85));
            assertEquals(0x8C, number.encode(0x85, 12), "checkpoint edits retain native bit 7");
        }
    }
    @Test void movingPlatformsExposeTheirOwnTablesAndKeepUninterpretedBits() {
        assertEquals("Large GHZ2 vertical", field(Sonic1ObjectProperties.schema(0x18), "movement").display(0x0A));
        assertEquals("Circular phase 2", field(Sonic2ObjectProperties.schema(0x19), "movement").display(0x2A));
        var mtz = Sonic2ObjectProperties.schema(0x6B);
        assertEquals("Stair large", field(mtz, "movement").display(0xFA));
        assertTrue(field(mtz, "shape").display(0xFA).startsWith("Unknown"));
        assertEquals(0x93, field(mtz, "shape").encode(0xF3, 1), "shape editing preserves reserved bit 7 and movement");
        var floating = Sonic3kObjectProperties.schema(0x51);
        assertEquals("Horizontal 256px", field(floating, "movement").display(0x2C));
        assertEquals("32 x 20", field(floating, "shape").display(0x2C));
        assertEquals(0xFF, field(floating, "movement").encode(0xFF, 15), "unknown movement remains byte-exact");
    }
    @Test void buttonsAndTriggersKeepTheirPerGameBitsRatherThanSharingGuessedMeanings() {
        var s1 = Sonic1ObjectProperties.schema(0x32);
        assertEquals(1, field(s1, "flash").decode(0x20));
        assertEquals(0xE4, field(s1, "switch").encode(0xEF, 4));
        var s2 = Sonic2ObjectProperties.schema(0x47);
        assertEquals(0x2F, field(s2, "alternate").encode(0x6F, 0));
        assertFalse(s2.fields().stream().anyMatch(f -> (f.mask() & 0x20) != 0), "S2 does not interpret S1's flashing bit");
        var s3k = Sonic3kObjectProperties.schema(0x33);
        assertEquals(1, field(s3k, "latch").decode(0x10));
        assertEquals(1, field(s3k, "topOnly").decode(0x20));
        assertEquals(0x9F, field(s3k, "alternate").encode(0xDF, 0));
        assertEquals(15, field(Sonic2ObjectProperties.schema(0x77), "switch").decode(15));
        assertEquals(0xE8, field(Sonic3kObjectProperties.schema(2), "range").encode(0xEB, 0));
    }
    @Test void badnikVariantsAndCapsulePartsExposeOnlyVerifiedNamedChoices() {
        var newtron = field(Sonic1ObjectProperties.schema(0x42), "variant");
        assertEquals("Blue running", newtron.display(0));
        assertEquals("Green missile-firing", newtron.display(1));
        assertTrue(newtron.display(0xE1).startsWith("Unknown"));
        assertEquals(0xE1, newtron.encode(0xE1, newtron.decode(0xE1)));
        assertEquals(3, field(Sonic1ObjectProperties.schema(0x1E), "fuse").decode(3));
        assertEquals(2, field(Sonic1ObjectProperties.schema(0x2C), "turnInterval").decode(2));
        var sol = field(Sonic2ObjectProperties.schema(0x95), "variant");
        assertEquals("Orbiting fireballs without throwing", sol.display(2));
        assertTrue(sol.display(4).startsWith("Unknown"), "child routines are not advertised as parent variants");
        assertEquals("Switch", field(Sonic1ObjectProperties.schema(0x3E), "part").display(1));
        for (ObjectPropertySchema schema : List.of(Sonic1ObjectProperties.schema(0x0D),
                Sonic2ObjectProperties.schema(0x0D), Sonic2ObjectProperties.schema(0x3E))) {
            var unused = field(schema, "unused");
            assertTrue(unused.label().contains("ignored"));
            assertEquals(0xA7, unused.encode(0xA7, unused.decode(0xA7)));
        }
    }
    @Test void s3kConflictingObjectTableIdsKeepRawFallback() {
        for (int id : new int[]{0x03, 0x06, 0x09, 0x11, 0x31, 0x9B}) {
            assertTrue(Sonic3kObjectProperties.schema(id).fields().isEmpty(),
                    "A zone-family-specific meaning cannot be attached to numeric ID alone: " + id);
        }
        assertEquals(0xB7, field(Sonic3kObjectProperties.schema(5), "shape").encode(0x87, 3));
        assertEquals(0xFF, field(Sonic3kObjectProperties.schema(0x28), "width").encode(0xFF, 15));
    }
    private static ObjectPropertySchema.Field field(ObjectPropertySchema schema, String id) {
        return schema.fields().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }
}
