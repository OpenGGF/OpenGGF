package com.openggf.level.objects;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayWorldPresentation;
import com.openggf.tools.HeadlessGameBoot;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.openggf.level.objects.TestStageplayObjectPriorityRendering.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disc-native object pixels, including a real foreground occlusion negative control. */
class TestCdStageplayObjectPriorityRendering {
    @Test void pastProgramNativeFrozenAndBakedPicturesRetainForeground() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("scd", CdTestMedia.usaCue()));
             var boot = new HeadlessGameBoot(320,224)) {
            boot.setBootToTitle(true);
            boot.boot(library.source("scd"), library.module("scd"),0,1);
            try (var world = StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode());
                 var presentation = new StageplayWorldPresentation();
                 var grabber = new GlReadPixelsGrabber(320,224)) {
                var scene = StageplayDocument.empty("scd",0,1,640,440,320,224);
                world.resolve(scene,StageplayTimeline.sample(scene,0));
                GameServices.camera().setX((short)640);
                GameServices.camera().setY((short)440);
                var objects = GameServices.level().getObjectManager();
                var direction = ObjectDirectionAccess.forManager(objects);
                direction.clearObjects();
                int visible = 0, occluded = 0;
                // R11B's native priority-marked canopy, found from the disc layout.
                // Keep fixed witnesses rather than searching until a comparison passes.
                for (int x : new int[]{5376,5456,5520,5616}) {
                    int y = 112;
                    GameServices.camera().setX((short)(x-128));
                    GameServices.camera().setY((short)(y-96));
                    var empty = draw(presentation,grabber);
                    var root = ObjectDirectionAccess.createNative(objects,new ObjectSpawn(x,y,0x3F,0,0,false,y));
                    assertNotNull(root);
                    root.update(0,GameServices.sprites().getMainPlayable());
                    var template = direction.captureDynamic(root);
                    var nativeImage = draw(presentation,grabber);
                    visible += differences(empty,nativeImage);
                    var pose = new StageplayObjectDirection.Direction(template.anchorX(),template.anchorY(),
                            template.facing(),true,0,-1);
                    direction.direct(root,template,pose);
                    assertPicture(nativeImage,draw(presentation,grabber),"CD frozen at "+x+","+y);
                    direction.removeDynamic(root);
                    var visual = direction.installVisual(template,pose);
                    assertPicture(nativeImage,draw(presentation,grabber),"CD baked at "+x+","+y);
                    direction.removeDynamic(visual);
                    var front = direction.installVisual(withoutOcclusion(template),pose);
                    int hidden = differences(nativeImage,draw(presentation,grabber));
                    assertTrue(hidden > 100, "Foreground witness at " + x + "," + y);
                    occluded += hidden;
                    direction.removeDynamic(front);
                }
                assertTrue(visible>100,"Native CD body pixels must be visible: "+visible);
                assertTrue(occluded>100,"Native CD foreground must hide body pixels: "+occluded);
            } finally { GameServices.graphics().cleanup(); }
        }
    }
}
