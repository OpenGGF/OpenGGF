package com.openggf.stageplay.runtime;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayLivePreviewRom {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void isolatedEditorWorkerReturnsNativePixelsAndStateWithoutReplacingItsParentWorld(boolean composite) throws Exception {
        GameServices.graphics().initHeadless();
        var path=Path.of(System.getProperty("s3k.rom.path"));
        GameServices.configuration().setSessionOverride(SonicConfiguration.SONIC_3K_ROM,composite ? "" : path.toString());
        if (composite) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.ROMS_DIRECTORY,path.toAbsolutePath().getParent().toString());
            GameServices.configuration().setSessionOverride(SonicConfiguration.ROMS_PREFER_COMPOSITE,true);
            assertTrue(GameServices.rom().resolveLogicalRom(com.openggf.data.RomIdentity.S3K).orElseThrow().isComposite());
        }
        var parent=SessionManager.getCurrentGameplayMode(); var level=parent.getLevelManager().getCurrentLevel();
        ProcessHandle child;
        try(var library=SceneRomLibrary.open(Map.of("s3k",path));var resources=new StageplayResources(library,false)) {
            var document=TestStageplayLiveWorldRom.platform();
            resources.validate(document);
            var frame=resources.resolveFrame(document,StageplayTimeline.sample(document,35));
            assertSame(parent,SessionManager.getCurrentGameplayMode()); assertSame(level,parent.getLevelManager().getCurrentLevel());
            assertTrue(frame.world().actors().stream().allMatch(a->a.ridingLayoutIndex()==179));
            assertNotNull(resources.livePreviewImage());
            assertEquals(320*224,resources.livePreviewImage().pixels().length);
            assertSame(frame,resources.resolveFrame(document,StageplayTimeline.sample(document,35)));
            var camera=new StageplayTimeline.CameraPose(frame.camera().x()+32,frame.camera().y()+16,1);
            var draft=new StageplayTimeline.Frame(frame.frame(),frame.actors(),camera,frame.captions(),frame.set(),frame.transition(),camera,frame.dialogue(),frame.puppets(),frame.world());
            var panned=resources.resolveFrame(document,draft);
            assertEquals(camera,panned.camera()); assertEquals(frame.world(),panned.world(),"Unkeyed framing cannot tick the native world");
            assertSame(parent,SessionManager.getCurrentGameplayMode());
            boolean nativeSound=false;
            for(int f=0;f<24;f++) {
                resources.resolveFrame(document,StageplayTimeline.sample(document,f));
                var pcm=resources.previewSound(f); assertNotNull(pcm);
                for(short value:pcm.samples()) if(value!=0) { nativeSound=true; break; }
            }
            assertTrue(nativeSound,"Native ring SFX must be synthesized in the no-device worker, not inferred from telemetry");
            child=ProcessHandle.of(resources.previewProcessId()).orElseThrow();
            assertTrue(child.isAlive(),"Use the owner's exact PID, not an optional OS command-line observation");
        }
        child.onExit().get(5,TimeUnit.SECONDS); assertFalse(child.isAlive());
        assertSame(parent,SessionManager.getCurrentGameplayMode());
    }
}
