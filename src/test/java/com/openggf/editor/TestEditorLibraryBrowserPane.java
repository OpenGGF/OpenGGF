package com.openggf.editor;

import com.openggf.editor.render.EditorLibraryBrowserPane;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.session.EditorCursorState;
import com.openggf.control.InputHandler;
import com.openggf.level.*;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorLibraryBrowserPane {
    @Test
    void cursorWrapFilterTwoDimensionalAndPageNavigationArePure() {
        EditorLibraryBrowserPane browser=new EditorLibraryBrowserPane();
        browser.showBlocks(MutableLevel.snapshot(new LibraryLevel()));
        assertEquals("Block 0",browser.selected().label());
        browser.move(-1); assertEquals("Block 2",browser.selected().label());
        browser.setFilter("1"); assertEquals(List.of("Block 1"),browser.visibleEntries().stream().map(EditorLibraryBrowserPane.Entry::label).toList());
        browser.setFilter(""); browser.setLayout(2,2); browser.move2d(1,1);
        assertEquals("Block 0",browser.selected().label(),"three-entry movement wraps");
        browser.page(1); assertEquals("Block 2",browser.selected().label());
    }

    @Test
    void controllerBrowseConfirmSelectsLoadedBlockAndChunkWithoutMovingWorldCursor() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));
        controller.cycleFocusRegion();
        assertTrue(controller.isLibraryBrowserFocused());
        EditorCursorState before=controller.worldCursor();
        EditorInputHandler input=new EditorInputHandler(controller);
        InputHandler raw=new InputHandler();
        raw.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.update(raw);
        input.handleAction(EditorInputHandler.Action.APPLY_PRIMARY_ACTION);
        assertEquals(1,controller.selection().selectedBlock());
        assertEquals(before,controller.worldCursor());
        controller.descend();
        controller.cycleFocusRegion();
        input.handleAction(EditorInputHandler.Action.BROWSE_LIBRARY_PAGE_NEXT);
        input.handleAction(EditorInputHandler.Action.APPLY_PRIMARY_ACTION);
        assertNotNull(controller.selection().selectedChunk());
    }

    @Test
    void objectPaletteAppendsEnabledKeysInEffectiveOrderAndConfirmsExactTaggedBrush() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));
        controller.configureSpawnEditing(new BrowserRegistry(),new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        assertEquals(EditorLibraryBrowserPane.Kind.BLOCK,controller.libraryBrowser().selected().kind());
        controller.cycleFocusRegion();
        assertEquals(EditorLibraryBrowserPane.Kind.OBJECT,controller.libraryBrowser().selected().kind());
        assertEquals("stock:preview",controller.libraryBrowser().selected().previewArtKey());
        controller.setLibraryFilter(""); controller.appendLibraryFilterText("owner:x");
        controller.backspaceLibraryFilter();
        assertEquals(List.of("owner:zeta","owner:alpha"),controller.libraryBrowser().visibleEntries().stream()
                .map(EditorLibraryBrowserPane.Entry::objectKey).toList());
        controller.browseLibrary(1);
        controller.selectLibraryEntry();
        assertEquals("owner:alpha",controller.objectPalette().selectedObjectKey());
        controller.setLibraryFilter("");
        controller.cycleFocusRegion(); controller.cycleFocusRegion(); controller.cycleFocusRegion();
        controller.placeObjectSpawnAtCursor();
        assertEquals("owner:alpha",controller.currentLevel().getObjects().getLast().objectKey());
    }

    @Test
    void reconfiguringAfterDisableRebuildsPaletteWithoutStaleModKeys() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));
        controller.configureSpawnEditing(new BrowserRegistry(),new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.cycleFocusRegion();
        assertTrue(controller.libraryBrowser().visibleEntries().stream().anyMatch(e->e.objectKey()!=null));
        controller.configureSpawnEditing(new BrowserRegistry(List.of()),new CommonObjectPlacementEncoding());
        assertFalse(controller.libraryBrowser().visibleEntries().stream().anyMatch(e->e.objectKey()!=null));
    }

    @Test
    void eyedropRetainsExactNamespacedIdentityAndSubtypeWithoutNumericFallback() {
        LevelEditorController controller=new LevelEditorController();
        MutableLevel level=MutableLevel.snapshot(new LibraryLevel());
        controller.attachLevel(level);
        controller.configureSpawnEditing(new BrowserRegistry(),new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        level.addObjectSpawn(new ObjectSpawn(0,0,0,37,0,false,0,88,"owner","owner:zeta"));
        controller.eyedropSpawnAtCursor();
        assertEquals("owner:zeta",controller.objectPalette().selectedObjectKey());
        assertEquals(37,controller.objectPalette().selectedSubtype());
    }

    @Test
    void liveTextInputCanRecoverFromZeroMatchAndDatasetSwitch() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));
        controller.cycleFocusRegion();
        EditorInputHandler input=new EditorInputHandler(controller);
        controller.toggleLibraryFilterInput();
        input.handleTextInputCodepoint('z'); input.handleTextInputCodepoint('z');
        assertTrue(controller.libraryBrowser().visibleEntries().isEmpty());
        assertTrue(controller.isLibraryBrowserFocused(),"zero results must retain library focus");
        InputHandler raw=new InputHandler();
        raw.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        raw.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.update(raw);
        assertEquals("",controller.libraryBrowser().filter());
        controller.configureSpawnEditing(new BrowserRegistry(),new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.cycleFocusRegion();controller.cycleFocusRegion();
        assertEquals(258,controller.libraryBrowser().visibleEntries().size());
    }

    @Test
    void filterCaptureSuppressesCharacterShortcutsWhileTypingBlock() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));
        controller.cycleFocusRegion();controller.toggleLibraryFilterInput();
        EditorInputHandler editor=new EditorInputHandler(controller);
        InputHandler raw=new InputHandler();
        for(char c:"block".toCharArray()) {
            int key=switch(c){case 'b'->org.lwjgl.glfw.GLFW.GLFW_KEY_B;case 'l'->org.lwjgl.glfw.GLFW.GLFW_KEY_L;
                case 'o'->org.lwjgl.glfw.GLFW.GLFW_KEY_O;case 'c'->org.lwjgl.glfw.GLFW.GLFW_KEY_C;
                default->org.lwjgl.glfw.GLFW.GLFW_KEY_K;};
            raw.handleKeyEvent(key,org.lwjgl.glfw.GLFW.GLFW_PRESS);
            editor.handleTextInputCodepoint(c);editor.update(raw);
            raw.handleKeyEvent(key,org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        }
        assertEquals("block",controller.libraryBrowser().filter());
        assertEquals(0,controller.activeLayer());
        assertEquals(EditorSpawnEditMode.TILES,controller.spawnEditMode());
        assertFalse(controller.isCollisionOverlayEnabled());
    }

    @Test
    void leavingLibraryByFocusDepthOrModeImmediatelyRestoresNormalShortcuts() {
        LevelEditorController controller=new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new LibraryLevel()));controller.cycleFocusRegion();
        controller.toggleLibraryFilterInput();assertTrue(controller.isLibraryFilterInputActive());
        controller.cycleFocusRegion();assertFalse(controller.isLibraryFilterInputActive());
        controller.cycleFocusRegion();controller.cycleFocusRegion(); // toolbar -> world
        java.util.concurrent.atomic.AtomicInteger saves=new java.util.concurrent.atomic.AtomicInteger();
        EditorInputHandler editor=new EditorInputHandler(controller,()->null,()->null,saves::incrementAndGet);
        InputHandler layer=new InputHandler();layer.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_L,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(layer);
        assertEquals(1,controller.activeLayer());
        InputHandler mode=new InputHandler();mode.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_O,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(mode);
        assertEquals(EditorSpawnEditMode.OBJECTS,controller.spawnEditMode());
        InputHandler collision=new InputHandler();collision.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_C,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(collision);
        assertTrue(controller.isCollisionOverlayEnabled());
        InputHandler save=new InputHandler();save.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        save.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_S,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(save);
        assertEquals(1,saves.get());

        controller.setSpawnEditMode(EditorSpawnEditMode.TILES);controller.selectBlock(0);controller.descend();
        controller.cycleFocusRegion();controller.toggleLibraryFilterInput();assertTrue(controller.isLibraryFilterInputActive());
        controller.ascend();assertFalse(controller.isLibraryFilterInputActive());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);assertFalse(controller.isLibraryFilterInputActive());
    }

    @Test
    void activeFilterCaptureSuppressesDeleteCollisionUndoRedoEnterAndSpace() {
        LevelEditorController controller=new LevelEditorController();MutableLevel level=MutableLevel.snapshot(new LibraryLevel());
        controller.attachLevel(level);controller.configureSpawnEditing(new BrowserRegistry(),new CommonObjectPlacementEncoding());
        level.addObjectSpawn(new CommonObjectPlacementEncoding().create(0,0,1,0,0,false,90));
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);controller.cycleFocusRegion();controller.toggleLibraryFilterInput();
        EditorInputHandler editor=new EditorInputHandler(controller);
        InputHandler delete=new InputHandler();delete.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(delete);
        assertEquals(1,level.getObjects().size());

        controller.endLibraryFilterInput();controller.setSpawnEditMode(EditorSpawnEditMode.TILES);
        controller.placeBlock(0,0,0,1);controller.cycleFocusRegion();controller.cycleFocusRegion();controller.toggleLibraryFilterInput();
        InputHandler undo=new InputHandler();undo.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        undo.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(undo);
        assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
        InputHandler redo=new InputHandler();redo.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        redo.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_Y,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(redo);
        assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
        InputHandler enter=new InputHandler();enter.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(enter);
        assertEquals(EditorHierarchyDepth.WORLD,controller.depth());
        InputHandler space=new InputHandler();space.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(space);
        assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));

        controller.endLibraryFilterInput();controller.selectBlock(0);controller.selectChunk(0);controller.descend();controller.cycleFocusRegion();
        assertEquals(EditorLibraryBrowserPane.Kind.CHUNK,controller.libraryBrowser().selected().kind());
        controller.toggleLibraryFilterInput();int solid=controller.selectedChunkPreview().getSolidTileIndex();
        InputHandler bracket=new InputHandler();bracket.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET,org.lwjgl.glfw.GLFW.GLFW_PRESS);editor.update(bracket);
        assertEquals(solid,controller.selectedChunkPreview().getSolidTileIndex());
    }

    private static final class BrowserRegistry implements ObjectRegistry {
        private final List<String> keys;
        BrowserRegistry(){this(List.of("owner:zeta","owner:alpha"));}
        BrowserRegistry(List<String> keys){this.keys=keys;}
        public ObjectInstance create(ObjectSpawn spawn){return null;}
        public void reportCoverage(List<ObjectSpawn> spawns){}
        public String getPrimaryName(int id){return "Stock "+id;}
        public List<String> browsableObjectKeys(){return keys;}
        public boolean hasObjectKey(String key){return keys.contains(key);}
        public java.util.Optional<String> editorPreviewArtKey(int id){return id==0?java.util.Optional.of("stock:preview"):java.util.Optional.empty();}
        public java.util.Optional<String> editorPreviewArtKey(String key){return key.equals("owner:zeta")?java.util.Optional.of("owner:art/zeta-preview"):java.util.Optional.empty();}
    }

    private static final class LibraryLevel extends AbstractLevel {
        LibraryLevel(){super(0);patternCount=1;patterns=new Pattern[]{new Pattern()};
            chunkCount=4;chunks=new Chunk[]{new Chunk(),new Chunk(),new Chunk(),new Chunk()};
            blockCount=3;blocks=new Block[]{new Block(8),new Block(8),new Block(8)};
            solidTileCount=1;solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};
            map=new com.openggf.level.Map(2,1,1);palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            objects=List.of();rings=List.of();minX=0;maxX=256;minY=0;maxY=256;}
    }
}
