package com.openggf.editor;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEditorStockObjectPalette {

    @Test
    void wrapsAllStockIdsAndUsesRegistryLabels() {
        EditorStockObjectPalette palette = new EditorStockObjectPalette(new LabelRegistry());
        assertEquals(0, palette.selectedObjectId());
        palette.previousObject();
        assertEquals(0xFF, palette.selectedObjectId());
        assertEquals("FF: Object-FF", palette.selectedLabel());
        palette.nextObject();
        assertEquals(0, palette.selectedObjectId());
    }

    @Test
    void logicalNavigationAndSubtypeWrapAreDeterministic() {
        EditorStockObjectPalette palette = new EditorStockObjectPalette(new LabelRegistry());
        palette.navigate(EditorStockObjectPalette.Navigation.NEXT_OBJECT);
        palette.navigate(EditorStockObjectPalette.Navigation.INCREMENT_SUBTYPE);
        assertEquals(1, palette.selectedObjectId());
        assertEquals(1, palette.selectedSubtype());
        palette.setSubtype(0);
        palette.navigate(EditorStockObjectPalette.Navigation.DECREMENT_SUBTYPE);
        assertEquals(0xFF, palette.selectedSubtype());
    }

    @Test
    void eyedropCopiesObjectIdAndSubtype() {
        EditorStockObjectPalette palette = new EditorStockObjectPalette(new LabelRegistry());
        palette.eyedrop(new ObjectSpawn(10, 20, 0x26, 0x83, 0, false, 20, 4));
        assertEquals(0x26, palette.selectedObjectId());
        assertEquals(0x83, palette.selectedSubtype());
    }

    private static final class LabelRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "Object-%02X".formatted(objectId); }
    }
}
