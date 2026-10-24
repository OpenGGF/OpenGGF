package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayDocument.Facing;

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
    @Test void poserOriginsAndAnimationUseTheNativeWorldRasterWithoutAdvancingTime() {
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
        var document=scene("s2",0,0,128,0,320,224)
                .withCamera(List.of(new CameraKey(0,128,0,2,Interpolation.HOLD)))
                .withActors(List.of(new Actor("hero","Hero","s2","sonic",List.of(
                        new ActorKey(0,148,60,0,-1, Facing.RIGHT,true,1,Interpolation.HOLD)))));
        try(var resources=new StageplayResources(game->art,List.of("s2"));var surface=new SceneSurface()) {
            var renderer=new StageplayRenderer(resources);resources.validate(document);
            var sample=StageplayTimeline.sample(document,3);
            assertEquals(1,renderer.mappingFrame(sample.actors().getFirst()));
            for(int repeat=0;repeat<2;repeat++) {
                surface.record(320,224,canvas->renderer.drawLivePosers(document,sample,canvas,sample.camera()));
                var draw=surface.recordedFrame().stream().filter(op->op.image()==pose.image()).findFirst().orElseThrow();
                assertEquals(18f,draw.x0());assertEquals(57f,draw.y0());assertEquals(26f,draw.x1());
            }
        }
    }
    @Test void registeredAuthoredSetStillRequiresItsRomIdentity() {
        var art=mock(SceneRomArt.class);when(art.romSha1()).thenReturn("b".repeat(40));
        var source=new SetSource("s2",0,0,"a".repeat(40),"set.ogfproject");
        try(var resources=new StageplayResources(game->art,List.of("s2"))) {
            resources.registerSet(source,mock(SceneLevelKit.class));
            assertThrows(IllegalArgumentException.class,()->resources.set(scene("s2",0,0,0,0,320,224).withSet(source)));
        }
    }
    @Test void voiceAssetsCannotEscapeTheirSceneRootThroughSymlinks() throws Exception {
        Path root=Files.createDirectory(temp.resolve("scene"));Path outside=Files.writeString(temp.resolve("outside.wav"),"private");
        Files.createSymbolicLink(root.resolve("voice.wav"),outside);
        assertThrows(java.io.IOException.class,()->StageplayAudio.readAsset(root,"voice.wav"));
        assertThrows(IllegalArgumentException.class,()->StageplayAudio.readAsset(root,"../outside.wav"));
        Files.writeString(root.resolve("inside.wav"),"owned");assertArrayEquals("owned".getBytes(),StageplayAudio.readAsset(root,"inside.wav"));
    }

    @Test void everyShotIsValidatedBeforePlaybackIncludingRegisteredAuthoredRomIdentity() {
        var art = mock(SceneRomArt.class); when(art.romSha1()).thenReturn("a".repeat(40));
        var opening = emptySet(0x112233);
        when(art.levelKit(0, 0)).thenReturn(opening);
        var incoming = new SetSource("s2", 1, 0, "b".repeat(40), "later.ogfproject");
        var scene = twoShots(incoming, Transition.CUT);
        try (var resources = new StageplayResources(game -> art, List.of("s2"))) {
            resources.registerSet(incoming, emptySet(0x332211));
            assertThrows(IllegalArgumentException.class, () -> resources.validate(scene));
        }
        var missing = new SetSource("s2", 1, 0, "", "");
        try (var resources = new StageplayResources(game -> art, List.of("s2"))) {
            assertDoesNotThrow(() -> resources.validate(twoShots(missing, Transition.CUT)),
                    "Native load checks act availability; it does not depend on detached level-kit support");
        }
    }

    @Test void incomingSetSwitchesAtFullCoverageAndMasksCaptionsInScreenSpace() {
        var art = mock(SceneRomArt.class);
        var opening = emptySet(0x112233); var incoming = emptySet(0x332211);
        when(art.levelKit(0, 0)).thenReturn(opening);
        when(art.levelKit(1, 0)).thenReturn(incoming);
        try (var resources = new StageplayResources(game -> art, List.of("s2")); var surface = new SceneSurface()) {
            var renderer = new StageplayRenderer(resources);
            for (var transition : Transition.values()) {
                var scene = twoShots(new SetSource("s2", 1, 0, "", ""), transition)
                        .withCaptions(List.of(new Caption("caption", 0, 12, "VISIBLE TEXT", 8, 8, 0xffffffff)));
                resources.validate(scene);
                for (int at : new int[]{4, 6, 8}) {
                    var frame = StageplayTimeline.sample(scene, at);
                    surface.record(320, 224, canvas -> renderer.drawLiveOverlay(scene, frame, canvas));
                    var ops = surface.recordedFrame();
                    if (transition == Transition.CUT) continue;
                    var mask = ops.getLast(); assertNull(mask.image(), "the screen mask follows every caption glyph");
                    assertEquals(0, mask.y0()); assertEquals(224, mask.y1());
                    if (transition == Transition.FADE_BLACK || transition == Transition.FADE_WHITE) {
                        assertEquals(0, mask.x0()); assertEquals(320, mask.x1());
                        assertEquals(at == 6 ? 255 : 128, mask.tint() >>> 24);
                        assertEquals(transition == Transition.FADE_WHITE ? 0xffffff : 0, mask.tint() & 0xffffff);
                    } else {
                        assertEquals(0xff000000, mask.tint());
                        assertEquals(at == 6 ? 320 : 160, mask.x1() - mask.x0());
                        float left = at == 6 ? 0 : ((transition == Transition.WIPE_LEFT) == (at >= 6) ? 0 : 160);
                        assertEquals(left, mask.x0());
                    }
                }
            }
        }
    }

    @Test void stagedMotionAndFacingUseTheProvidersLatchedAccessorySampleTime() {
        var art = mock(SceneRomArt.class); var sprites = mock(SceneSpriteSet.class);
        var body = new SceneSprite(new SceneImage(8, 8, new int[64]), 4, 4);
        var tail = new SceneSprite(new SceneImage(16, 8, new int[128]), 12, 4);
        var set = emptySet(0);
        when(art.levelKit(0, 0)).thenReturn(set); when(art.character("tails")).thenReturn(sprites);
        when(art.characterAccessory("tails")).thenReturn(sprites); when(sprites.frame(1)).thenReturn(body);
        when(art.characterAccessorySampleTick("tails", 2, 1, 3)).thenReturn(0L);
        when(art.characterAccessoryPose("tails", 2, 1, 3, false, 2, 0))
                .thenReturn(new SceneAccessoryPose(tail, false, true));
        var scene = scene("s2", 0, 0, 0, 0, 320, 224).withActors(List.of(
                new Actor("tails", "Tails", "s2", "tails", List.of(
                        new ActorKey(0, 100, 100, 2, 1, Facing.RIGHT, true, 1, Interpolation.LINEAR),
                        new ActorKey(2, 104, 100, 2, 1, Facing.LEFT, true, 1, Interpolation.LINEAR),
                        new ActorKey(4, 104, 104, 2, 1, Facing.LEFT, true, 1, Interpolation.HOLD)))));
        try (var resources = new StageplayResources(game -> art, List.of("s2")); var surface = new SceneSurface()) {
            var renderer = new StageplayRenderer(resources);
            surface.record(320, 224, canvas -> renderer.drawLivePosers(scene, StageplayTimeline.sample(scene, 3), canvas, StageplayTimeline.sample(scene, 3).camera()));
            verify(art).characterAccessoryPose("tails", 2, 1, 3, false, 2, 0);
            var draw = surface.recordedFrame().stream().filter(op -> op.image() == tail.image()).findFirst().orElseThrow();
            assertTrue(draw.v0() > draw.v1()); assertTrue(draw.u0() < draw.u1());
            assertEquals(92, draw.x0()); assertEquals(98, draw.y0());
        }
    }

    @Test void renderingRequiresAResolvedNativeWorldEvenForAnEmptyCast() {
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
        try (var resources = new StageplayResources(game -> mock(SceneRomArt.class), List.of("s2"));
             var surface = new SceneSurface()) {
            var renderer = new StageplayRenderer(resources);
            assertThrows(IllegalArgumentException.class, () -> surface.record(320, 224,
                    canvas -> renderer.draw(document, StageplayTimeline.sample(document, 0), canvas)));
        }
    }

    private static StageplayDocument twoShots(SetSource incoming, Transition transition) {
        return scene("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(12)
                .withCamera(List.of(new CameraKey(0, 0, 0, 1, Interpolation.HOLD),
                        new CameraKey(6, 100, 40, 2, Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("cut", 6, incoming, transition, transition == Transition.CUT ? 0 : 8)));
    }
    private static SceneLevelKit emptySet(int color) {
        var kit = mock(SceneLevelKit.class); when(kit.palette()).thenReturn(new int[]{color}); when(kit.blockSize()).thenReturn(128);
        when(kit.backdrop()).thenReturn(new SceneBackdrop(new SceneImage(320, 224, new int[320 * 224]),
                List.of(new SceneBackdrop.Band(0, 224, 0, 0))));
        return kit;
    }
    /** These fixtures exercise the pure Poser and overlay layers inside native-world composition. */
    private static StageplayDocument scene(String gameId, int zone, int act, double x, double y, int width, int height) {
        return StageplayDocument.empty(gameId, zone, act, x, y, width, height)
                .withSet(new StageplayDocument.SetSource(gameId, zone, act, "", ""));
    }

}
