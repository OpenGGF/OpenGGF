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
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
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
        var scene=StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(new StageplayDocument.SetSource("s2",0,0,"","stage.ogfproject"))
                .withCamera(List.of(new StageplayDocument.CameraKey(0,0,0,1,StageplayDocument.Interpolation.HOLD),
                        new StageplayDocument.CameraKey(100,300,0,1,StageplayDocument.Interpolation.HOLD)))
                .withSceneChanges(List.of(new StageplayDocument.SceneChange("second-set",100,
                        new StageplayDocument.SetSource("s2",0,0,"","second.ogfproject"),StageplayDocument.Transition.FADE_BLACK,20)));
        StageplayStore.write(sceneDir.resolve("film.ogfstage"),scene);
        try(var root=ModAssetRoot.directory(temp,assets,ModInputLimits.production(),DirectoryAccess.TEST)) {
            var context=new ModContext("movie","s2",root);
            assertThrows(java.io.IOException.class,()->StageplayPlayback.registerOnEntry(context,"film","scenes/film.ogfstage"),
                    "a missing later set fails during registration, before a controller is published");
        }
        new EditorProjectStore().save(sceneDir.resolve("second.ogfproject"),project);
        // Asset roots are immutable snapshots: corrected content needs a new registration root.
        try(var root=ModAssetRoot.directory(temp,assets,ModInputLimits.production(),DirectoryAccess.TEST)) {
            var context=new ModContext("movie","s2",root);
            StageplayPlayback.registerOnEntry(context,"film","scenes/film.ogfstage");
            var plan=context.freeze();
            // No late disk read: registration owns the decoded authored set and immutable scene.
            Files.delete(sceneDir.resolve("stage.ogfproject"));Files.delete(sceneDir.resolve("second.ogfproject"));Files.delete(sceneDir.resolve("film.ogfstage"));
            var module=OwnedModServices.decorate(new com.openggf.game.sonic2.Sonic2GameModule(),plan,boundary());
            assertSame(module.gameplayFrameController(),module.getGameService(StageplayControl.class));
            assertEquals(StageplayControl.Status.REQUESTED,module.getGameService(StageplayControl.class).status());
            assertTrue(module.gameplayFrameController().requiresExclusiveControl());
            assertEquals(1,module.rewindAdapters().stream().filter(a->a.key().contains("services/film/playback")).count());
        }
    }
    @Test void dialogueImagesMustExistAndShareTheVoiceAssetCountBudget() throws Exception {
        Path assets = Files.createDirectory(temp.resolve("dialogue-assets"));
        var cue = new StageplayDocument.DialogueCue("line", 0, 180, "", "NARRATOR", "Hello", "", "",
                12, 130, 296, 82, "portrait.png", List.of());
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDialogue(List.of(cue));
        StageplayStore.write(assets.resolve("film.ogfstage"), document);
        try (var root = ModAssetRoot.directory(temp, assets, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var context = new ModContext("dialogue", "s2", root);
            assertThrows(java.io.IOException.class, () -> StageplayPlayback.register(context, "film", "film.ogfstage"));
        }
        var voices = new java.util.ArrayList<StageplayDocument.AudioCue>();
        var lines = new java.util.ArrayList<StageplayDocument.DialogueCue>();
        for (int index = 0; index < 200; index++) voices.add(new StageplayDocument.AudioCue("voice" + index, 0,
                StageplayDocument.AudioKind.VOICE, "s2", 0, "voice" + index + ".wav", 1));
        for (int index = 0; index < 57; index++) lines.add(new StageplayDocument.DialogueCue("line" + index,
                0, 180, "", "NARRATOR", "Hello", "", "", 12, 130, 296, 82, "portrait" + index + ".png", List.of()));
        StageplayStore.write(assets.resolve("film.ogfstage"), document.withDialogue(lines).withAudio(voices));
        try (var root = ModAssetRoot.directory(temp, assets, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var context = new ModContext("dialogue", "s2", root);
            var failure = assertThrows(java.io.IOException.class, () -> StageplayPlayback.register(context, "film", "film.ogfstage"));
            assertTrue(failure.getMessage().contains("count budget"), "the combined count fails before reading any missing file");
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void registeredDialogueCanPrepareAndDrawAfterItsOwnedFilesAndAssetRootClose() throws Exception {
        Path assets = Files.createDirectory(temp.resolve("prepared-dialogue"));
        Path sceneDir = Files.createDirectory(assets.resolve("scenes"));
        var metadata = new EditorProjectMetadata(GameId.S2, "Dialogue set", 0, 0, "", 32, 64, 0, 511, 0, 255,
                new FullLevelExporter.ExportMusic.Stock(0x81), null);
        new EditorProjectStore().save(sceneDir.resolve("set.ogfproject"), EditorProjectDocument.create(metadata, new Fixture()));
        PngCodec.write(sceneDir.resolve("panel.png"), PixelImage.blank(12, 12));
        var skin = new StageplayDocument.DialogueSkin("panel", 0xffffffff, 0xffffff00, 0xff102030, 0xffddeeff,
                6, "panel.png", 2, true);
        var scene = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224)
                .withSet(new StageplayDocument.SetSource("s2", 0, 0, "", "set.ogfproject"))
                .withDialogueSkins(List.of(skin)).withDialogue(List.of(new StageplayDocument.DialogueCue("line", 0, 180,
                        "", "NARRATOR", "Prepared once.", "panel", "", 12, 130, 296, 82, "", List.of())));
        StageplayStore.write(sceneDir.resolve("film.ogfstage"), scene);
        ModRegistrationPlan plan;
        try (var root = ModAssetRoot.directory(temp, assets, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var context = new ModContext("dialogue", "s2", root);
            StageplayPlayback.registerOnEntry(context, "film", "scenes/film.ogfstage"); plan = context.freeze();
        }
        Files.delete(sceneDir.resolve("panel.png")); Files.delete(sceneDir.resolve("set.ogfproject"));
        Files.delete(sceneDir.resolve("film.ogfstage"));
        GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        GameServices.graphics().initHeadless();
        var module = OwnedModServices.decorate(GameServices.module(), plan, boundary());
        var controller = module.gameplayFrameController();
        var course = org.mockito.Mockito.mock(CourseControl.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(course.presentationReady()).thenReturn(true);
        try {
            assertEquals(GameplayFrameDisposition.HOLD, controller.beforeFrame(course, LogicalInputSnapshot.neutral()));
            assertEquals(StageplayControl.Status.PLAYING, module.getGameService(StageplayControl.class).status());
            assertTrue(controller.drawScene());
            // Drawing defers errors to the next owner-bound frame; advance once to prove it succeeded.
            assertEquals(GameplayFrameDisposition.HOLD, controller.beforeFrame(course, LogicalInputSnapshot.neutral()));
            assertEquals(1, module.getGameService(StageplayControl.class).frame());
        } finally { controller.close(); com.openggf.tests.TestEnvironment.resetAll(); }
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
