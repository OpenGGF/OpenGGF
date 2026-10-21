package com.openggf.stageplay.runtime;

import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayRenderer {
    @TempDir Path temp;
    @Test void cameraTransformsOriginsAndDrawsAuthoredBlockZeroWithoutAdvancingAnimation() {
        var art=mock(SceneRomArt.class);var kit=mock(SceneLevelKit.class);var sprites=mock(SceneSpriteSet.class);
        var block=new SceneImage(128,128,new int[128*128]);var background=new SceneImage(320,224,new int[320*224]);
        var pose=new SceneSprite(new SceneImage(8,8,new int[64]),2,3);
        when(art.levelKit(0,0)).thenReturn(kit);when(art.character("sonic")).thenReturn(sprites);
        when(sprites.animationFrames(0)).thenReturn(new int[]{0,1});when(sprites.animationDelay(0)).thenReturn(2);
        when(sprites.frame(1)).thenReturn(pose);when(sprites.frameCount()).thenReturn(2);
        when(kit.palette()).thenReturn(new int[]{0xff112233});when(kit.blockSize()).thenReturn(128);
        when(kit.columns()).thenReturn(2);when(kit.rows()).thenReturn(2);when(kit.blockCount()).thenReturn(1);
        when(kit.blockImage(0)).thenReturn(block);
        when(kit.backdrop()).thenReturn(new SceneBackdrop(background,List.of(new SceneBackdrop.Band(0,224,0,0))));
        var document=StageplayDocument.empty("s2",0,0,128,0,320,224)
                .withCamera(List.of(new CameraKey(0,128,0,2,Interpolation.HOLD)))
                .withActors(List.of(new Actor("hero","Hero","s2","sonic",List.of(
                        new ActorKey(0,148,60,0,-1,false,true,1,Interpolation.HOLD)))));
        try(var resources=new StageplayResources(game->art,List.of("s2"));var surface=new SceneSurface()) {
            var renderer=new StageplayRenderer(resources);resources.validate(document);
            var sample=StageplayTimeline.sample(document,3);
            assertEquals(1,renderer.mappingFrame(sample.actors().getFirst()));
            for(int repeat=0;repeat<2;repeat++) {
                surface.record(320,224,canvas->renderer.draw(document,sample,canvas));
                assertEquals(1,surface.recordedFrame().stream().filter(op->op.image()==block).count(),"block zero may contain authored terrain");
                var draw=surface.recordedFrame().stream().filter(op->op.image()==pose.image()).findFirst().orElseThrow();
                assertEquals(36f,draw.x0());assertEquals(114f,draw.y0());assertEquals(52f,draw.x1());
            }
        }
    }
    @Test void registeredAuthoredSetStillRequiresItsRomIdentity() {
        var art=mock(SceneRomArt.class);when(art.romSha1()).thenReturn("b".repeat(40));
        var source=new SetSource("s2",0,0,"a".repeat(40),"set.ogfproject");
        try(var resources=new StageplayResources(game->art,List.of("s2"))) {
            resources.registerSet(source,mock(SceneLevelKit.class));
            assertThrows(IllegalArgumentException.class,()->resources.set(StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(source)));
        }
    }
    @Test void voiceAssetsCannotEscapeTheirSceneRootThroughSymlinks() throws Exception {
        Path root=Files.createDirectory(temp.resolve("scene"));Path outside=Files.writeString(temp.resolve("outside.wav"),"private");
        Files.createSymbolicLink(root.resolve("voice.wav"),outside);
        assertThrows(java.io.IOException.class,()->StageplayAudio.readAsset(root,"voice.wav"));
        assertThrows(IllegalArgumentException.class,()->StageplayAudio.readAsset(root,"../outside.wav"));
        Files.writeString(root.resolve("inside.wav"),"owned");assertArrayEquals("owned".getBytes(),StageplayAudio.readAsset(root,"inside.wav"));
    }
}
