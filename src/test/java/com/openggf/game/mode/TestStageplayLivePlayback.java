package com.openggf.game.mode;

import com.openggf.LevelFrameResult;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.stageplay.StageplayControl;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayPlayback;
import com.openggf.stageplay.runtime.StageplayNativeBinding;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Actual LiveHost ROM art/audio and native handover, rendered only on explicitly selected display-free EGL. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestStageplayLivePlayback {
    @Test void realSceneHoldsTheWorldRendersAndSoundsThenRestoresEntryOnCompletionAndCancel() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Live Stageplay render requires explicit surfaceless EGL");
        var graphics=GraphicsManager.getInstance();
        int[] oldViewport={graphics.getViewportX(),graphics.getViewportY(),graphics.getViewportWidth(),graphics.getViewportHeight()};
        int oldWidth=graphics.getProjectionWidth();float[] oldProjection=graphics.getProjectionMatrixBuffer();
        boolean oldHeadless=graphics.isHeadlessMode(),oldBatching=graphics.isBatchingEnabled(),oldInstanced=graphics.isInstancedBatchingEnabled();
        TestEnvironment.resetAll();
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED,true);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        var scene=StageplayDocument.empty("s2",0,0,0,500,320,224).withDurationFrames(3)
                .withActors(List.of(new Actor("hero","Sonic","s2","sonic",List.of(
                        new ActorKey(0,144,640,5,-1,false,true,1,Interpolation.LINEAR),
                        new ActorKey(2,176,640,5,-1,false,true,1,Interpolation.HOLD)))))
                .withAudio(List.of(new AudioCue("music",0,AudioKind.ROM_MUSIC,"s2",129,"",1),
                        new AudioCue("ring",1,AudioKind.ROM_SFX,"s2",181,"",1)));
        var playback=new StageplayPlayback(scene,"hero",StageplayNativeBinding.FinishMode.RESTORE_ENTRY);
        try(var boot=new HeadlessGameBoot(320,224)) {
            boot.setModuleDecorator(base->new DelegatingGameModule(base,"test:stageplay-live") {
                @Override public GameplayFrameController gameplayFrameController() {return playback;}
                @Override public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
                    var adapters=new java.util.ArrayList<>(super.rewindAdapters());adapters.add(playback);return List.copyOf(adapters);
                }
            });
            var loop=boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(),0,0);
            var runtime=SessionManager.getCurrentGameplayMode();
            // Complete real setup and the pending native entry-music publication before the cutscene borrows it.
            for(int frame=0;frame<180;frame++) {loop.step();HeadlessGameBoot.presentHeadlessOuterAudioFrame();}
            assertTrue(new CourseControl(runtime).presentationReady());
            assertFalse(GameServices.module().getLevelInitProfile().isLevelMusicPublicationPending());
            assertTrue(graphics.isGlInitialized());
            graphics.setViewport(0,0,320,224);glViewport(0,0,320,224);
            var nativePlayer=runtime.getCamera().getFocusedSprite();
            assertFalse(nativePlayer.isControlLocked());
            var entry=new CourseControl(runtime).playerState();int entryMapping=nativePlayer.getMappingFrame();
            var entryCamera=runtime.getCamera().capture();int nativeClock=runtime.getLevelManager().getFrameCounter();
            var audio=GameServices.audio();audio.stopMusic();audio.stopAllSfx();HeadlessGameBoot.presentHeadlessOuterAudioFrame();
            assertNull(audio.captureLogicalSnapshot().presentation().activeMusic());
            var input=new InputHandler();var neutral=LogicalInputSnapshot.neutral();
            audio.beginCaptureMode(audio.outputSampleRate(),60);
            try(var grabber=new GlReadPixelsGrabber(320,224)) {
                short[] pcm=new short[(audio.outputSampleRate()/60+1)*2];boolean audible=false;byte[] first=null,last=null;
                playback.play();
                for(int frame=0;frame<3;frame++) {
                    assertEquals(LevelFrameResult.HELD,ControlledFrameRuntime.step(runtime,input,neutral));
                    assertEquals(frame,playback.frame());assertEquals(StageplayControl.Status.PLAYING,playback.status());
                    assertEquals(nativeClock,runtime.getLevelManager().getFrameCounter(),"staged direction must hold native world clocks");
                    assertEquals(144+frame*16,nativePlayer.getCentreX());assertEquals(640,nativePlayer.getCentreY());
                    assertTrue(nativePlayer.isControlLocked());assertTrue(nativePlayer.isObjectMappingFrameControl());
                    assertTrue(nativePlayer.getMappingFrame()>0,"WAIT must resolve visible ROM mapping art");
                    assertTrue(playback.drawScene());assertEquals(GL_NO_ERROR,glGetError());
                    byte[] pixels=grabber.grab().clone();if(frame==0)first=pixels;last=pixels;
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                    int samples=audio.drainCaptureFrame(pcm);assertEquals(audio.outputSampleRate()/60,samples);
                    for(int sample=0;sample<samples*2;sample++)audible|=pcm[sample]!=0;
                    assertEquals(129,audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
                }
                assertFalse(Arrays.equals(first,last),"actual GL frames must change as the directed actor moves");
                assertTrue(audible,"ROM music/SFX must reach the final capture mix");
                assertEquals(LevelFrameResult.HELD,ControlledFrameRuntime.step(runtime,input,neutral));
                assertEquals(StageplayControl.Status.COMPLETED,playback.status());assertFalse(playback.drawScene());
                assertEquals(entry,new CourseControl(runtime).playerState());assertEquals(entryMapping,nativePlayer.getMappingFrame());
                assertEquals(entryCamera,runtime.getCamera().capture());assertFalse(nativePlayer.isControlLocked());
                assertNull(audio.captureLogicalSnapshot().presentation().activeMusic(),"completion restores the borrowed silent entry");
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME,ControlledFrameRuntime.step(runtime,input,neutral));
                assertEquals(nativeClock+1,runtime.getLevelManager().getFrameCounter());
                var cancelEntry=new CourseControl(runtime).playerState();
                playback.play();assertEquals(LevelFrameResult.HELD,ControlledFrameRuntime.step(runtime,input,neutral));
                HeadlessGameBoot.presentHeadlessOuterAudioFrame();audio.drainCaptureFrame(pcm);
                playback.cancel();assertEquals(LevelFrameResult.HELD,ControlledFrameRuntime.step(runtime,input,neutral));
                assertEquals(StageplayControl.Status.CANCELLED,playback.status());assertEquals(cancelEntry,new CourseControl(runtime).playerState());
                assertFalse(nativePlayer.isControlLocked());assertNull(runtime.directedPlayerLease().capture().entry());
                assertNull(audio.captureLogicalSnapshot().presentation().activeMusic());
            } finally {audio.endCaptureMode();}
        } finally {
            playback.close();SessionManager.clear();RomManager.getInstance().setRom(null);
            TestEnvironment.resetAll();config.resetToDefaults();GameModuleRegistry.reset();
            graphics.setViewport(oldViewport[0],oldViewport[1],oldViewport[2],oldViewport[3]);
            graphics.setProjectionWidth(oldWidth);graphics.setProjectionMatrixBuffer(oldProjection);
            graphics.setHeadlessMode(oldHeadless);graphics.setBatchingEnabled(oldBatching);graphics.setInstancedBatchingEnabled(oldInstanced);
        }
    }
}
