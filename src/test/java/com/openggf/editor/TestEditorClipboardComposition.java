package com.openggf.editor;

import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestEditorClipboardComposition {
    @Test void editingAnOwnedBlockAfterCopyPreservesTheCopiedComposition() {
        var c = controller();
        c.selectChunk(1); c.descend(); c.applyPrimaryAction(); c.ascend();
        int copied = c.currentLevel().getMap().getValue(0, 0, 0) & 255;
        int[] before = c.currentLevel().getBlock(copied).saveState();
        c.beginRegionAtCursor(); c.finishRegionSelection(); c.copyRegion();
        c.descend(); c.selectChunk(2); c.applyPrimaryAction(); c.ascend();
        assertArrayEquals(before, c.currentLevel().getBlock(copied).saveState());
        assertNotEquals(copied, c.currentLevel().getMap().getValue(0, 0, 0) & 255);
        c.setWorldCursor(new EditorCursorState(80, 0)); c.beginPastePreview(); c.commitPastePreview();
        assertEquals(copied, c.currentLevel().getMap().getValue(0, 2, 0) & 255);
        c.undo(); assertEquals(0, c.currentLevel().getMap().getValue(0, 2, 0));
        c.undo(); assertEquals(copied, c.currentLevel().getMap().getValue(0, 0, 0) & 255);
    }

    @Test void cutDefinitionsAreNotReusedAsFreeCompositionSlots() {
        var c = controller();
        c.selectChunk(1); c.descend(); c.applyPrimaryAction(); c.ascend();
        int copied = c.currentLevel().getMap().getValue(0, 0, 0) & 255;
        int[] before = c.currentLevel().getBlock(copied).saveState();
        c.beginRegionAtCursor(); c.finishRegionSelection(); c.cutRegion();
        c.setWorldCursor(new EditorCursorState(48, 0)); c.descend(); c.selectChunk(3); c.applyPrimaryAction(); c.ascend();
        assertArrayEquals(before, c.currentLevel().getBlock(copied).saveState());
        assertNotEquals(copied, c.currentLevel().getMap().getValue(0, 1, 0) & 255);
        c.setWorldCursor(new EditorCursorState(80, 0)); c.beginPastePreview(); c.commitPastePreview();
        assertEquals(copied, c.currentLevel().getMap().getValue(0, 2, 0) & 255);
    }

    private static LevelEditorController controller() {
        var c = new LevelEditorController(); c.attachLevel(TestEditorInteractionSafety.level());
        c.configureSpawnEditing(new Sonic2ObjectRegistry(), new CommonObjectPlacementEncoding());
        c.selectBlock(0); return c;
    }
}
