package com.openggf.stageplay.runtime;

import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestCdStageplayPoser {
    @Test void pastPoserDrawingAndAccessoryResolveTheSelectedProgram() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("scd",CdTestMedia.usaCue()))) {
            var observed = spy(library);
            try (var resources = new StageplayResources(observed,false); var surface = new SceneSurface()) {
                var document = StageplayDocument.empty("scd",0,1,640,400,320,224)
                        .withActors(List.of(new Actor("hero","Sonic","scd","sonic",List.of(
                                new ActorKey(0,800,549,0,1,Facing.LEFT,true,1,Interpolation.HOLD)))));
                resources.validate(document);
                assertEquals(library.stageArt("scd",0,1).romSha1(),
                        resources.actorArt(document.set(),"scd").romSha1());
                assertNull(resources.actorArt(document.set(),"scd").characterAccessory("sonic"),
                        "CD Sonic has no separate accessory sheet");
                var frame = StageplayTimeline.sample(document,0);
                surface.record(320,224,canvas -> new StageplayRenderer(resources)
                        .drawLivePosers(document,frame,canvas,frame.camera()));
                assertFalse(surface.recordedFrame().isEmpty(),"Disc-native Poser body must draw");
                verify(observed,atLeastOnce()).stageArt("scd",0,1);
                verify(observed,never()).rom("scd");
            }
        }
    }
}
