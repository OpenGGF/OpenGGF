package com.openggf.stageplay.runtime;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual ROM mappings, DPLCs and pixels; canvas recording requires no display or audio device. */
class TestStageplayTailsRom {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sharedBodyArtProvidesVisibleIdleAndSpinTailsAtTheBodyOrigin() throws java.io.IOException {
        exercise("s2", 9, 0x81);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void separateTailBankMatchesIdleSpinAndFlightAndSuppressesBodyInclusiveTails() throws java.io.IOException {
        exercise("s3k", 0x22, 1);
        var rom = art();
        var flight = rom.characterAccessoryPose("tails", 0x20, -1, 0, false, 0, 0);
        assertNotNull(flight); assertSame(rom.characterAccessory("tails").frame(0x27), flight.sprite());
        assertVisible(flight.sprite());
        assertNull(rom.characterAccessoryPose("tails", 0x25, -1, 0, false, 0, 0));
    }

    private void exercise(String game, int swishFrame, int spinFrame) throws java.io.IOException {
        var rom = art(); assertEquals(game, rom.gameId());
        var body = rom.character("tails"); var accessory = rom.characterAccessory("tails");
        assertNotNull(accessory, "S2's tails reuse the main art bank and must not be omitted");
        var idle = rom.characterAccessoryPose("tails", 5, -1, 0, false, 0, 0);
        assertNotNull(idle); assertSame(accessory.frame(swishFrame), idle.sprite()); assertVisible(idle.sprite());
        var spin = rom.characterAccessoryPose("tails", 9, -1, 0, false, 0, 0);
        assertNotNull(spin); assertSame(accessory.frame(spinFrame), spin.sprite()); assertVisible(spin.sprite());
        assertNull(rom.characterAccessoryPose("tails", 1, -1, 0, false, 5, 0), "run already includes tails");
        assertNull(rom.characterAccessoryPose("tails", 0x10, -1, 0, false, 0, -5), "spring already includes tails");
        int idleBody = body.animationFrames(5)[0];
        assertSame(idle.sprite(), rom.characterAccessoryPose("tails", 0, idleBody, 0, false, 0, 0).sprite(),
                "an explicit idle body hold infers its tail relationship even with default WALK context");
        int runBody = body.animationFrames(1)[0];
        assertNull(rom.characterAccessoryPose("tails", 5, runBody, 0, false, 0, 0), "a held run body needs no second tail");
        assertTrue(exposedPixels(idle.sprite(), body.frame(idleBody)) > 30,
                "the accessory must add visible pixels outside the body's opaque coverage");

        var sceneRom = spy(rom); var kit = emptySet(); doReturn(kit).when(sceneRom).levelKit(0, 0);
        try (var resources = new StageplayResources(id -> sceneRom, List.of(game)); var surface = new SceneSurface()) {
            var renderer = new StageplayRenderer(resources);
            for (boolean flip : new boolean[]{false, true}) {
                var document = StageplayDocument.empty(game, 0, 0, 100, 50, 320, 224)
                        .withCamera(List.of(new CameraKey(0, 100, 50, 2, Interpolation.HOLD)))
                        .withActors(List.of(new Actor("tails", "Tails", game, "tails", List.of(
                                new ActorKey(0, 160, 100, 5, idleBody, flip, true, 1.5, Interpolation.HOLD)))));
                resources.validate(document);
                surface.record(320, 224, canvas -> renderer.draw(document, StageplayTimeline.sample(document, 0), canvas));
                var ops = surface.recordedFrame();
                var tailDraw = ops.stream().filter(op -> op.image() == idle.sprite().image()).findFirst().orElseThrow();
                var bodyDraw = ops.stream().filter(op -> op.image() == body.frame(idleBody).image()).findFirst().orElseThrow();
                assertTrue(ops.indexOf(tailDraw) < ops.indexOf(bodyDraw), "native tail lies behind body");
                float expectedLeft = 120 - (flip ? idle.sprite().width() - idle.sprite().originX() : idle.sprite().originX()) * 3f;
                assertEquals(expectedLeft, tailDraw.x0());
                assertEquals(100 - idle.sprite().originY() * 3f, tailDraw.y0());
                assertEquals(idle.sprite().width() * 3f, tailDraw.x1() - tailDraw.x0());
                assertEquals(flip, tailDraw.u0() > tailDraw.u1());
                // Random access does not retain the later sample's script or facing.
                surface.record(320, 224, canvas -> renderer.draw(document, StageplayTimeline.sample(document, 37), canvas));
                surface.record(320, 224, canvas -> renderer.draw(document, StageplayTimeline.sample(document, 0), canvas));
                assertTrue(surface.recordedFrame().stream().anyMatch(op -> op.image() == idle.sprite().image()
                        && op.x0() == expectedLeft));
            }
        }
    }

    private static SceneRomArt art() throws java.io.IOException {
        return SceneRomArtFactory.forModule(GameServices.module(), GameServices.rom().getRom());
    }
    private static void assertVisible(SceneSprite sprite) {
        assertTrue(Arrays.stream(sprite.image().pixels()).filter(pixel -> (pixel >>> 24) != 0).count() > 30);
    }
    private static int exposedPixels(SceneSprite tail, SceneSprite body) {
        int count = 0;
        for (int y = 0; y < tail.height(); y++) for (int x = 0; x < tail.width(); x++) {
            if ((tail.image().pixel(x, y) >>> 24) == 0) continue;
            int bx = x - tail.originX() + body.originX(), by = y - tail.originY() + body.originY();
            if (bx < 0 || by < 0 || bx >= body.width() || by >= body.height()
                    || (body.image().pixel(bx, by) >>> 24) == 0) count++;
        }
        return count;
    }
    private static SceneLevelKit emptySet() {
        var kit = mock(SceneLevelKit.class); when(kit.palette()).thenReturn(new int[]{0xff101020});
        when(kit.blockSize()).thenReturn(128);
        when(kit.backdrop()).thenReturn(new SceneBackdrop(new SceneImage(320, 224, new int[320 * 224]),
                List.of(new SceneBackdrop.Band(0, 224, 0, 0))));
        return kit;
    }
}
