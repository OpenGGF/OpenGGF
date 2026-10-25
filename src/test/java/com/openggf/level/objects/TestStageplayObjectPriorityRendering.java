package com.openggf.level.objects;

import com.openggf.level.objects.ObjectVisualFacing.Basis;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayWorldPresentation;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.level.objects.StageplayObjectDirection.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Day Off regression: native and directed pictures use the same foreground occlusion. */
@RequiresRom(SonicGame.SONIC_1)
class TestStageplayObjectPriorityRendering {
    @Test
    void directedMotobugKeepsNativePixelsBehindGreenHillLoop() throws Exception {
        Path rom = Path.of(System.getProperty("sonic1.rom.path"));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 5680, 680);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            try (var library = SceneRomLibrary.open(Map.of("s1", rom));
                 var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
                 var presentation = new StageplayWorldPresentation();
                 var grabber = new GlReadPixelsGrabber(320, 224)) {
                var scene = StageplayDocument.empty("s1", 0, 0, 5520, 550, 320, 224);
                world.resolve(scene, StageplayTimeline.sample(scene, 0));
                GameServices.camera().setX((short) 5520);
                GameServices.camera().setY((short) 550);
                var objects = GameServices.level().getObjectManager();
                var direction = ObjectDirectionAccess.forManager(objects);
                direction.clearObjects();
                RgbaImage empty = draw(presentation, grabber);
                int visible = 0, occluded = 0;
                for (int x : List.of(5580, 5680, 5740, 5800)) {
                    var root = ObjectDirectionAccess.createNative(objects,
                            new ObjectSpawn(x, 680, 0x40, 0, 0, false, 680));
                    assertNotNull(root);
                    var template = direction.captureDynamic(root);
                    RgbaImage nativeImage = draw(presentation, grabber);
                    visible += differences(empty, nativeImage);
                    var pose = new Direction(x, 680, Basis.LEFT, true, 0, -1);
                    direction.direct(root, template, pose);
                    assertPicture(nativeImage, draw(presentation, grabber), "frozen native graph at X=" + x);
                    direction.removeDynamic(root);
                    var visual = direction.installVisual(template, pose);
                    assertPicture(nativeImage, draw(presentation, grabber), "baked Puppet/Pose at X=" + x);
                    direction.removeDynamic(visual);
                    var front = direction.installVisual(withoutOcclusion(template), pose);
                    occluded += differences(nativeImage, draw(presentation, grabber));
                    direction.removeDynamic(front);
                }
                assertTrue(visible > 100, "Reject stale, blank or tiles-only comparisons");
                assertTrue(occluded > 100, "The native oracle must actually hide sprite pixels behind foreground");
            }
        }
    }

    private static VisualTemplate withoutOcclusion(VisualTemplate source) {
        var parts = source.parts().stream().map(part -> new VisualPart(part.partId(), part.bucket(), true,
                part.slotOrder(), part.defaultPose(), part.facing(), part.facingPivotX(), part.poses().stream().map(pose -> new VisualPose(pose.pose(),
                        pose.tiles().stream().map(t -> new VisualTile(t.pattern(), t.palette(), t.flipX(), t.flipY(),
                                true, t.x(), t.y(), t.width(), t.height(), t.priorityShader(), 0,
                                t.rowStart(), t.rowEnd())).toList(), pose.primitives())).toList(), part.animations())).toList();
        return new VisualTemplate(source.anchorX(), source.anchorY(), source.facing(), parts, source.patterns(), source.palettes(), source.limitations());
    }

    private static int differences(RgbaImage a, RgbaImage b) {
        int changed = 0;
        for (int i = 0; i < a.pixels().length; i++) if (a.pixels()[i] != b.pixels()[i]) changed++;
        return changed;
    }

    private static void assertPicture(RgbaImage expected, RgbaImage actual, String context) {
        assertEquals(0, differences(expected, actual), "Native/directed pixels: " + context);
    }

    private static RgbaImage draw(StageplayWorldPresentation presentation, GlReadPixelsGrabber grabber) {
        var level = GameServices.level();
        var graphics = GameServices.graphics();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        presentation.draw(graphics, level, GameServices.camera().getX(), GameServices.camera().getY(), 1, 320, 224, () -> {
            level.drawCinematicWorld(GameServices.sprites());
            graphics.flush();
        });
        byte[] rgba = grabber.grab();
        int[] pixels = new int[320 * 224];
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int i = ((223 - y) * 320 + x) * 4;
            pixels[y * 320 + x] = (rgba[i + 3] & 255) << 24 | (rgba[i] & 255) << 16
                    | (rgba[i + 1] & 255) << 8 | rgba[i + 2] & 255;
        }
        return new RgbaImage(320, 224, pixels);
    }
}
