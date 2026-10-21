package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.editor.project.*;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.*;
import com.openggf.game.mode.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.io.*;
import com.openggf.level.*;
import com.openggf.mods.*;
import com.openggf.stageplay.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRegistration {
    @TempDir Path temp;
    private ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),ignored->new ModStateSaveResult.Saved(),ignored->{});
    }
    private GameModule install(GameModule base,String owner,boolean exclusive,ModFaultBoundary boundary) {
        var context=new ModContext(owner,"s2",ModAssetRoot.forTests(owner));
        context.registerServiceBundle("session",()->GameServiceBundle.builder().frameController("controller",new Controller(exclusive)).build());
        return OwnedModServices.decorate(base,context.freeze(),boundary);
    }
    @Test void exclusiveControllersRejectBothOrdersAcrossOwnerProxies() {
        for(boolean firstExclusive:List.of(false,true)) {
            var boundary=boundary();
            var first=install(new com.openggf.game.sonic2.Sonic2GameModule(),"first",firstExclusive,boundary);
            var controller=first.gameplayFrameController();
            var error=assertThrows(ModFaultBoundary.CallbackAborted.class,()->install(first,"second",!firstExclusive,boundary));
            assertEquals("second",error.owner());assertTrue(boundary.isOwnerAvailable("first"));
            assertFalse(boundary.isOwnerAvailable("second"));assertSame(controller,first.gameplayFrameController());
        }
    }
    @Test void ordinaryControllerReplacementRemainsSupported() {
        var boundary=boundary(); var first=install(new com.openggf.game.sonic2.Sonic2GameModule(),"first",false,boundary);
        var second=install(first,"second",false,boundary);
        assertNotSame(first.gameplayFrameController(),second.gameplayFrameController());
        assertFalse(second.gameplayFrameController().requiresExclusiveControl());
    }
    @Test void authoredSceneIsDecodedOnceFromBoundedOwnerAssetsAndSharesItsController() throws Exception {
        Path assets=Files.createDirectory(temp.resolve("assets"));Path sceneDir=Files.createDirectory(assets.resolve("scenes"));
        var level=new Fixture();
        var metadata=new EditorProjectMetadata(GameId.S2,"Authored stage",0,0,"",32,64,0,511,0,255,
                new FullLevelExporter.ExportMusic.Stock(0x81),null);
        var project=EditorProjectDocument.create(metadata,level);
        new EditorProjectStore().save(sceneDir.resolve("stage.ogfproject"),project);
        var scene=StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(new StageplayDocument.SetSource("s2",0,0,"","stage.ogfproject"));
        StageplayStore.write(sceneDir.resolve("film.ogfstage"),scene);
        try(var root=ModAssetRoot.directory(temp,assets,ModInputLimits.production(),DirectoryAccess.TEST)) {
            var context=new ModContext("movie","s2",root);
            StageplayPlayback.registerOnEntry(context,"film","scenes/film.ogfstage");
            var plan=context.freeze();
            // No late disk read: registration owns the decoded authored set and immutable scene.
            Files.delete(sceneDir.resolve("stage.ogfproject"));Files.delete(sceneDir.resolve("film.ogfstage"));
            var module=OwnedModServices.decorate(new com.openggf.game.sonic2.Sonic2GameModule(),plan,boundary());
            assertSame(module.gameplayFrameController(),module.getGameService(StageplayControl.class));
            assertEquals(StageplayControl.Status.REQUESTED,module.getGameService(StageplayControl.class).status());
            assertTrue(module.gameplayFrameController().requiresExclusiveControl());
            assertEquals(1,module.rewindAdapters().stream().filter(a->a.key().contains("services/film/playback")).count());
        }
    }
    static class Controller implements GameplayFrameController,RewindSnapshottable<Integer> {
        private final boolean exclusive;Controller(boolean exclusive){this.exclusive=exclusive;}
        public boolean requiresExclusiveControl(){return exclusive;}
        public boolean beforeTick(CourseControl course,LogicalInputSnapshot input){return true;}
        public void afterTick(CourseControl course,boolean advanced){}
        public String key(){return "controller";}public Integer capture(){return 0;}public void restore(Integer state){}
    }
    static class Fixture extends AbstractLevel {
        Fixture() {
            super(0);patterns=new Pattern[]{new Pattern()};patternCount=1;
            chunks=new Chunk[]{new Chunk()};chunkCount=1;blocks=new Block[]{new Block(8)};blockCount=1;
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};solidTileCount=1;
            map=new com.openggf.level.Map(2,4,2);objects=List.of();rings=List.of();
            minX=0;maxX=511;minY=0;maxY=255;
        }
        public int getChunksPerBlockSide(){return 8;}public int getBlockPixelSize(){return 128;}
    }
}
