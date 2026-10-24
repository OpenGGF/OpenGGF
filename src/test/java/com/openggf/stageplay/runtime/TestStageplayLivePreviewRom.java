package com.openggf.stageplay.runtime;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayLivePreviewRom {
    @Test
    void pausedDraftClampsItsBaseAndShakeEvenWhenItsFinalCameraMatchesTheCachedPicture() throws Exception {
        GameServices.graphics().initHeadless();
        var path = Path.of(System.getProperty("s3k.rom.path"));
        GameServices.configuration().setSessionOverride(SonicConfiguration.SONIC_3K_ROM, path.toString());
        var parent = SessionManager.getCurrentGameplayMode();
        try (var library = SceneRomLibrary.open(Map.of("s3k", path));
             var resources = new StageplayResources(library, false)) {
            var source = TestStageplayLiveWorldRom.platform();
            var level = library.level("s3k", source.set().zone(), source.set().act());
            var document = source.withCamera(List.of(new CameraKey(
                    0, 5280, level.getMaxY(), 1, Interpolation.HOLD)))
                    .withCameraEffects(List.of(new CameraEffect(
                            "edge-shake", 0, source.durationFrames(), 0, 16, 8, 7)));
            int at = IntStream.range(0, 30).filter(frame -> {
                var sample = StageplayTimeline.sample(document, frame);
                return sample.camera().y() - sample.baseCamera().y() > 1;
            }).findFirst().orElseThrow();
            var authored = StageplayTimeline.sample(document, at);
            double shake = authored.camera().y() - authored.baseCamera().y();
            var frame = resources.resolveFrame(document, authored);
            assertEquals(level.getMaxY(), frame.baseCamera().y());
            assertEquals(level.getMaxY(), frame.camera().y());
            // Same final picture, different unshaken origin: cache identity must include the request.
            var base = new StageplayTimeline.CameraPose(frame.baseCamera().x(), level.getMaxY() - shake, 1);
            var draft = new StageplayTimeline.Frame(frame.frame(), frame.actors(), frame.camera(), frame.captions(),
                    frame.set(), frame.transition(), base, frame.dialogue(), frame.puppets(), frame.world());
            var directed = resources.resolveFrame(document, draft);
            assertEquals(frame.camera(), directed.camera());
            assertEquals(base, directed.baseCamera());
            assertEquals(frame.world(), directed.world(), "Paused framing cannot tick the world");
            assertSame(directed, resources.resolveFrame(document, draft));
            assertSame(directed, resources.resolveFrame(document, directed),
                    "Drawing an already resolved draft cannot reinterpret its clipped camera");

            var outside = new StageplayTimeline.CameraPose(-1000, 100_000, 1);
            var outsideDraft = new StageplayTimeline.Frame(frame.frame(), frame.actors(), outside, frame.captions(),
                    frame.set(), frame.transition(), outside, frame.dialogue(), frame.puppets(), frame.world());
            var clamped = resources.resolveFrame(document, outsideDraft);
            assertEquals(level.getMinX(), clamped.camera().x());
            assertEquals(level.getMaxY(), clamped.camera().y());
            assertEquals(level.getMaxY(), clamped.baseCamera().y());
            assertEquals(frame.world(), clamped.world());
            assertSame(parent, SessionManager.getCurrentGameplayMode());
        }
    }

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
            long previewPid = resources.previewProcessId();
            var query = new com.openggf.stageplay.StageplayGround.Query("floor",(int)Math.round(frame.actors().getFirst().x()),0,1023,
                    new com.openggf.stageplay.StageplayGround.Player(document.actors().getFirst().gameId(),document.actors().getFirst().character(),0),document.actors().getFirst().id());
            var ground = resources.ground(document,35,new com.openggf.stageplay.StageplayGround.Batch(List.of(query),20000),() -> false);
            assertEquals(new com.openggf.stageplay.StageplayGround.SceneFrame(frame.set(),35),ground.basis());
            assertNotEquals(com.openggf.stageplay.StageplayGround.Outcome.CONTEXT_UNAVAILABLE,ground.answer("floor").outcome(),ground.toString());
            assertNotEquals(com.openggf.stageplay.StageplayGround.Outcome.INCOMPLETE,ground.answer("floor").outcome(),ground.toString());
            assertEquals(0,ground.work().preparationTicks());
            assertEquals(previewPid,resources.previewProcessId(),"Floor requests reuse the exact preview worker");
            assertSame(parent,SessionManager.getCurrentGameplayMode());
            assertEquals(frame.world(),resources.resolveFrame(document,StageplayTimeline.sample(document,35)).world());
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
