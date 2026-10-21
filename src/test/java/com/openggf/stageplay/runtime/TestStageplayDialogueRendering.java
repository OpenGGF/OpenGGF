package com.openggf.stageplay.runtime;

import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayDialogueRendering {
    @TempDir Path temp;

    @Test void labelledNineSliceDialogueFollowsSpeakerCameraAndKeepsRevealRowsFixed() throws Exception {
        var document = scene();
        byte[] panel = png("panel.png", 12, 12), portrait = png("portrait.png", 16, 24);
        var art = mock(SceneRomArt.class); var sprites = mock(SceneSpriteSet.class); var kit = emptySet();
        when(art.levelKit(0, 0)).thenReturn(kit); when(art.character("tails")).thenReturn(sprites);
        when(sprites.frame(1)).thenReturn(new SceneSprite(new SceneImage(8, 8, new int[64]), 4, 4));
        when(sprites.frameCount()).thenReturn(2);
        try (var resources = new StageplayResources(game -> art, List.of("s2")); var surface = new SceneSurface()) {
            resources.setAssetReader(path -> path.equals("panel.png") ? panel : portrait);
            resources.validate(document); var renderer = new StageplayRenderer(resources);
            var decoration = resources.dialogueImage("panel.png"); var portraitImage = resources.dialogueImage("portrait.png");
            for (int at : new int[]{0, 20, 0}) {
                surface.record(320, 224, canvas -> renderer.draw(document, StageplayTimeline.sample(document, at), canvas));
                var ops = surface.recordedFrame();
                var slices = ops.stream().filter(op -> op.image() == decoration).toList(); assertEquals(9, slices.size());
                var corner = slices.getFirst(); assertEquals(12, corner.x0()); assertEquals(130, corner.y0());
                assertEquals(2, corner.x1() - corner.x0()); assertEquals(2, corner.y1() - corner.y0());
                assertEquals(5, ops.stream().filter(op -> op.image() != null && op.tint() == 0xffffd36a).count(),
                        "the full speaker name appears immediately and remains stable");
                int pointerX = at == 0 ? 160 : 120;
                assertTrue(ops.stream().anyMatch(op -> op.image() == null && op.tint() == 0xffc7cbd1
                        && op.x0() == pointerX && op.x1() == pointerX + 1 && op.y0() == 122),
                        "the pointer follows the speaker's camera-transformed position");
                var portraitDraw = ops.stream().filter(op -> op.image() == portraitImage).findFirst().orElseThrow();
                assertEquals(32, portraitDraw.y1() - portraitDraw.y0());
                assertTrue(portraitDraw.x1() - portraitDraw.x0() <= 32);
                var letters = ops.stream().filter(op -> op.image() != null && op.tint() == 0xfffff0cf).toList();
                assertFalse(letters.isEmpty()); assertEquals(150, letters.getFirst().y0());
                if (at == 0) assertEquals(1, letters.size(), "custom schedule reveals one grapheme at tick zero");
                else assertEquals(10, letters.size());
                assertTrue(ops.indexOf(slices.getFirst()) > ops.stream().filter(op -> op.tint() == 0xff66ff66)
                        .mapToInt(ops::indexOf).max().orElseThrow(), "dialogue overlays ordinary captions");
            }
        }
    }

    @Test void panelContentRestoresThePreviousClipAndOffscreenSpeakerDoesNotGetAPointer() throws Exception {
        var document = scene(); byte[] encoded = png("shared.png", 12, 12);
        try (var resources = new StageplayResources(game -> null, List.of()); var surface = new SceneSurface()) {
            resources.setAssetReader(path -> encoded); resources.prepareDialogue(document);
            var frame = StageplayTimeline.sample(document, 0);
            var offscreen = new StageplayTimeline.Frame(frame.frame(), frame.actors(),
                    new StageplayTimeline.CameraPose(1000, 0, 1), frame.captions(), frame.set(), frame.transition(),
                    frame.baseCamera(), frame.dialogue());
            surface.record(320, 224, canvas -> {
                canvas.clip(60, 145, 50, 20);
                StageplayDialogueRenderer.draw(document, offscreen, canvas, resources);
                canvas.fill(0, 0, 1, 1, 0xffabcdef);
            });
            var ops = surface.recordedFrame(); assertArrayEquals(new int[]{60, 145, 50, 20}, ops.getLast().clip());
            var text = ops.stream().filter(op -> op.tint() == 0xffffd36a).findFirst().orElseThrow();
            assertArrayEquals(new int[]{60, 145, 50, 20}, text.clip(), "content must also respect its caller's clip");
            assertTrue(ops.stream().noneMatch(op -> op.image() == null && op.tint() == 0xffc7cbd1));
        }
    }

    private StageplayDocument scene() {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224)
                .withActors(List.of(new Actor("tails", "TAILS", "s2", "tails", List.of(
                        new ActorKey(0, 160, 80, 5, 1, false, true, 1, Interpolation.HOLD)))))
                .withCamera(List.of(new CameraKey(0, 0, 0, 1, Interpolation.LINEAR),
                        new CameraKey(20, 40, 0, 1, Interpolation.HOLD)))
                .withCaptions(List.of(new Caption("caption", 0, 180, "CAPTION", 12, 12, 0xff66ff66)))
                .withDialogueSkins(List.of(TestStageplayDialogueResources.skin("skin", "panel.png", 2)))
                .withDialogue(List.of(new DialogueCue("line", 0, 180, "tails", "", "HELLO\nTHERE", "skin", "",
                        12, 130, 296, 82, "portrait.png", List.of(0, 2, 4, 6, 8, 9, 10, 12, 14, 16, 18))));
    }
    private byte[] png(String name, int width, int height) throws Exception {
        Path path = temp.resolve(name); var image = PixelImage.blank(width, height);
        java.util.Arrays.fill(image.pixels(), 0xff334455); PngCodec.write(path, image); return Files.readAllBytes(path);
    }
    private static SceneLevelKit emptySet() {
        var kit = mock(SceneLevelKit.class); when(kit.palette()).thenReturn(new int[]{0}); when(kit.blockSize()).thenReturn(128);
        when(kit.backdrop()).thenReturn(new SceneBackdrop(new SceneImage(320, 224, new int[320 * 224]),
                List.of(new SceneBackdrop.Band(0, 224, 0, 0))));
        return kit;
    }
}
