package com.openggf.editor;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.session.EditorCursorState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestEditorInteractionSafety {
    static MutableLevel level() { return MutableLevel.snapshot(new Fixture()); }
    static final class Fixture extends AbstractLevel {
        Fixture() {
            super(0);
            patternCount=4; patterns=new Pattern[]{new Pattern(),new Pattern(),new Pattern(),new Pattern()};
            chunkCount=5; chunks=new Chunk[]{new Chunk(),new Chunk(),new Chunk(),new Chunk(),new Chunk()};
            blockCount=5; blocks=new Block[]{new Block(2),new Block(2),new Block(2),new Block(2),new Block(2)};
            solidTileCount=3;solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0),new SolidTile(1,new byte[16],new byte[16],(byte)0),new SolidTile(2,new byte[16],new byte[16],(byte)0)};
            map=new Map(2,16,8);palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            objects=List.of();rings=List.of();minX=0;maxX=511;minY=0;maxY=255;
        }
        @Override public int getChunksPerBlockSide(){return 2;}
        @Override public int getBlockPixelSize(){return 32;}
    }
    private static EditorInputHandler mouse(LevelEditorController controller,Camera camera) {
        GraphicsManager graphics=GraphicsManager.getInstance();graphics.setViewport(0,0,320,224);
        return new EditorInputHandler(controller,()->camera,()->graphics);
    }
    @Test void stationaryPointerDoesNotReplaceKeyboardCursorAfterCameraMovement(){
        LevelEditorController controller=new LevelEditorController();controller.attachLevel(level());
        Camera camera=new Camera(SonicConfigurationService.getInstance());camera.setX((short)0);camera.setY((short)0);
        EditorInputHandler editor=mouse(controller,camera);InputHandler input=new InputHandler();
        input.handleMouseMove(100,100);editor.update(input);input.update();
        controller.moveWorldCursor(20,0);camera.setX((short)40);
        editor.update(input);
        assertEquals(120,controller.worldCursor().x());
    }
    @Test void entireGameViewportIsPaintableWithoutLogicalChrome(){
        MutableLevel level=level();LevelEditorController controller=new LevelEditorController();controller.attachLevel(level);controller.selectBlock(1);
        Camera camera=new Camera(SonicConfigurationService.getInstance());camera.setX((short)0);camera.setY((short)0);
        EditorInputHandler editor=mouse(controller,camera);InputHandler input=new InputHandler();
        input.handleMouseMove(40,10);input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS);editor.update(input);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE);editor.update(input);
        assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,1,0)));
        assertTrue(controller.hasUndoHistory());
    }
    @Test void saveIncludesPendingStrokeBeforeCallingHost(){
        MutableLevel level=level();LevelEditorController controller=new LevelEditorController();controller.attachLevel(level);controller.selectBlock(1);
        Camera camera=new Camera(SonicConfigurationService.getInstance());camera.setX((short)0);camera.setY((short)0);
        GraphicsManager graphics=GraphicsManager.getInstance();graphics.setViewport(0,0,320,224);
        int[] saved={-1};EditorInputHandler editor=new EditorInputHandler(controller,()->camera,()->graphics,()->saved[0]=Byte.toUnsignedInt(level.getMap().getValue(0,3,3)));
        InputHandler input=new InputHandler();input.handleMouseMove(100,100);input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS);editor.update(input);
        editor.handleAction(EditorInputHandler.Action.SAVE);
        assertEquals(1,saved[0]);controller.undo();assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,3,3)));
    }
    @Test void reattachingSameAuthoredLevelPreservesUndo(){
        MutableLevel level=level();LevelEditorController controller=new LevelEditorController();controller.attachLevel(level);
        controller.placeBlock(0,1,1,1);controller.attachLevel(level);controller.undo();
        assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,1,1)));
    }
    @Test void collisionEditDoesNotChangeAnotherPlacementOfSharedBlock(){
        MutableLevel level=level();LevelEditorController controller=new LevelEditorController();controller.attachLevel(level);controller.selectBlock(0);controller.selectChunk(0);
        controller.setWorldCursor(new EditorCursorState(0,0));controller.descend();controller.cycleSelectedCellCollisionMode();
        int local=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));
        assertEquals(CollisionMode.TOP_SOLID,level.getBlock(local).getChunkDesc(0,0).getPrimaryCollisionMode());
        assertEquals(CollisionMode.NO_COLLISION,level.getBlock(0).getChunkDesc(0,0).getPrimaryCollisionMode());
        controller.undo();assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
    }
    @Test void patternSelectionAppliesAnyLoadedPatternLocally(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);
        c.descend();c.descend();c.selectPattern(3);
        assertEquals(3,c.selectedPatternRaw()&0x7FF);c.focusRegion(EditorFocusRegion.CHUNK_PANE);c.applyPrimaryAction();
        int b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0)),ch=level.getBlock(b).getChunkDesc(0,0).getChunkIndex();
        assertEquals(3,level.getChunk(ch).getPatternDesc(0,0).getPatternIndex());
        assertEquals(0,level.getChunk(0).getPatternDesc(0,0).getPatternIndex());
        c.undo();assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));c.redo();
        assertEquals(3,level.getChunk(ch).getPatternDesc(0,0).getPatternIndex());
    }
    @Test void descriptorFlagsAreLocalAndUndoRestoresBothLevels(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);
        c.descend();c.toggleDescriptorHFlip();int b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));
        assertTrue(level.getBlock(b).getChunkDesc(0,0).getHFlip());assertFalse(level.getBlock(0).getChunkDesc(0,0).getHFlip());
        c.descend();c.toggleDescriptorVFlip();c.cyclePatternPalette();c.togglePatternPriority();
        b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));int ch=level.getBlock(b).getChunkDesc(0,0).getChunkIndex();
        PatternDesc changed=level.getChunk(ch).getPatternDesc(0,0);
        assertTrue(changed.getVFlip());assertEquals(1,changed.getPaletteIndex());assertTrue(changed.getPriority());
        c.undo();c.undo();c.undo();c.undo();assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
    }
    @Test void tolerantSpawnPickAndPropertiesKeepNativeEnvelopeAndBrush(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);
        c.configureSpawnEditing(new com.openggf.level.objects.ObjectRegistry(){
            public com.openggf.level.objects.ObjectInstance create(com.openggf.level.objects.ObjectSpawn spawn){return null;}
            public void reportCoverage(List<com.openggf.level.objects.ObjectSpawn> spawns){}
            public String getPrimaryName(int id){return "Object "+id;}
        },new com.openggf.game.common.CommonObjectPlacementEncoding());
        c.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);c.objectPalette().setObjectId(6);c.objectPalette().setSubtype(12);
        c.setWorldCursor(new EditorCursorState(80,80));c.placeSpawnAtCursor();
        c.setWorldCursor(new EditorCursorState(84,83));c.performEyedrop();assertNotNull(c.selectedObjectSpawn());
        c.editSelectedSpawnProperties(25,3,false);var changed=level.getObjects().getFirst();
        assertEquals(25,changed.subtype());assertEquals(3,changed.renderFlags());assertEquals(0x6050,changed.rawYWord());
        assertFalse(changed.respawnTracked());assertEquals(12,c.objectPalette().selectedSubtype());
        c.undo();assertEquals(12,level.getObjects().getFirst().subtype());
    }
    @Test void keyboardNavigationIsTheOnlyHoverIndependentCameraRequest(){
        LevelEditorController c=new LevelEditorController();c.attachLevel(level());assertTrue(c.consumeCameraNavigationRequest());
        c.setPointerWorldCursor(new EditorCursorState(90,80));assertFalse(c.consumeCameraNavigationRequest());
        c.moveWorldCursor(3,0);assertTrue(c.consumeCameraNavigationRequest());assertFalse(c.consumeCameraNavigationRequest());
    }

    @Test void everyCompositionCellEditsLocallyWithoutMovingTheWorldAnchor(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);
        c.selectBlock(0);c.selectChunk(0);c.descend();c.descend();c.selectPattern(3);
        EditorCursorState anchor=c.worldCursor();
        for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            c.selectActiveCell(x,y);c.applyPrimaryAction();assertEquals(anchor,c.worldCursor());
        }
        int block=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));
        int chunk=level.getBlock(block).getChunkDesc(0,0).getChunkIndex();
        for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            assertEquals(3,level.getChunk(chunk).getPatternDesc(x,y).getPatternIndex());
            assertEquals(0,level.getChunk(0).getPatternDesc(x,y).getPatternIndex());
        }
        assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,1,0)));
        for(int i=0;i<4;i++)c.undo();
        assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));assertFalse(c.hasUndoHistory());
    }
    @Test void exhaustedDefinitionBankFailsVisiblyWithoutMutatingAnotherPlacement(){
        MutableLevel level=level();for(int i=0;i<5;i++)level.setBlockInMap(0,i,0,i);
        LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);c.descend();
        c.cycleSelectedCellCollisionMode();assertTrue(c.statusText().contains("No free block slots"));
        assertFalse(c.hasUndoHistory());assertEquals(CollisionMode.NO_COLLISION,level.getBlock(0).getChunkDesc(0,0).getPrimaryCollisionMode());
    }
    @Test void repeatedLocalEditsReuseOwnedDefinitionsAndAllUndoRestoresOriginal(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);c.descend();c.descend();
        for(int i=0;i<40;i++)c.togglePatternPriority();
        int b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));assertEquals(1,b);
        assertEquals(1,level.getBlock(b).getChunkDesc(0,0).getChunkIndex());
        for(int i=0;i<40;i++)c.undo();assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));assertFalse(c.hasUndoHistory());
    }
    @Test void allEditingBoundariesCommitExactlyOnePendingStroke(){
        for(EditorInputHandler.Action action:new EditorInputHandler.Action[]{EditorInputHandler.Action.SAVE,
                EditorInputHandler.Action.EXPORT,EditorInputHandler.Action.UNDO,EditorInputHandler.Action.REDO,
                EditorInputHandler.Action.TOGGLE_LAYER,EditorInputHandler.Action.CYCLE_SPAWN_EDIT_MODE,
                EditorInputHandler.Action.DESCEND}) {
            MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(1);
            Camera camera=new Camera(SonicConfigurationService.getInstance());camera.setX((short)0);camera.setY((short)0);
            EditorInputHandler editor=mouse(c,camera);InputHandler input=new InputHandler();
            input.handleMouseMove(100,100);input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS);editor.update(input);editor.handleAction(action);
            if(action==EditorInputHandler.Action.UNDO)assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,3,3)));
            else {assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,3,3)));c.undo();}
            assertFalse(c.hasUndoHistory(),action.name());
        }
    }
    @Test void fittedTextHasReadableScaleAndStaysInsideNativeAndWideChrome(){
        for(int width:new int[]{320,528}) {
            var renderer=new com.openggf.editor.render.EditorTextRenderer(GraphicsManager.getInstance());
            var commands=renderer.buildFittedTextCommands(List.of("A long actionable error ".repeat(8)),10,10,width-20,.65f,8);
            for(var command:commands)assertTrue(command.x()+command.text().length()*9*command.scale()<=width-10);
        }
    }

    @Test void unchangedEraseAndApplyDoNotConsumeDefinitionsOrUndoEntries(){
        MutableLevel level=level();LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);
        c.eraseSelection();assertFalse(c.hasUndoHistory());c.descend();c.applyPrimaryAction();c.eraseSelection();
        assertFalse(c.hasUndoHistory());c.descend();c.selectPattern(0);c.applyPrimaryAction();c.eraseSelection();
        assertFalse(c.hasUndoHistory());assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
    }
    @Test void eraseUsesTransparentDefinitionsWhenEntryZeroContainsArt(){
        MutableLevel level=level();level.getPattern(0).setPixel(0,0,(byte)1);
        for(int y=0;y<2;y++)for(int x=0;x<2;x++)level.setPatternDescInChunk(1,x,y,new PatternDesc(1));
        for(int y=0;y<2;y++)for(int x=0;x<2;x++)level.setChunkInBlock(1,x,y,new ChunkDesc(1));
        LevelEditorController c=new LevelEditorController();c.attachLevel(level);c.selectBlock(0);c.selectChunk(0);
        c.eraseSelection();assertEquals(1,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));c.undo();
        assertEquals(0,Byte.toUnsignedInt(level.getMap().getValue(0,0,0)));
        c.descend();c.eraseSelection();int b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));
        assertEquals(1,level.getBlock(b).getChunkDesc(0,0).getChunkIndex());c.undo();
        c.selectChunk(0);c.descend();c.eraseSelection();b=Byte.toUnsignedInt(level.getMap().getValue(0,0,0));
        int chunk=level.getBlock(b).getChunkDesc(0,0).getChunkIndex();
        assertEquals(1,level.getChunk(chunk).getPatternDesc(0,0).getPatternIndex());
        assertEquals(0,level.getChunk(0).getPatternDesc(0,0).getPatternIndex());
    }

    @Test void worldCursorUsesSignedCameraDomainAcrossNativeCoordinateBoundary(){
        LevelEditorController c=new LevelEditorController();c.attachLevel(level());
        Camera camera=new Camera(SonicConfigurationService.getInstance());camera.setX((short)0x8000);camera.setY((short)0x8000);
        class Overlay extends com.openggf.editor.render.EditorWorldOverlayRenderer {
            Overlay(){super(c,GraphicsManager.getInstance());}
            EditorCursorState transformed(){return cursorInCameraDomain(new EditorCursorState(0x8010,0x8018),camera);}
        }
        var cursor=new Overlay().transformed();assertEquals(16,cursor.x()-camera.getX());assertEquals(24,cursor.y()-camera.getY());
    }

}
