package com.openggf.stageplay.runtime;

import com.openggf.audio.*;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.audio.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Scene cues must reach actual disc/PCM synthesis, without a desktop audio device. */
class TestCdStageplayMusic {
    @Test void sceneCuePlaysDiscTrack() throws Exception { check(0x0F); }
    @Test void sceneCuePlaysSelectedPastPcm() throws Exception {
        check(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
    }

    private void check(int command) throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("scd",CdTestMedia.usaCue()))) {
            var audio = AudioManager.getInstance();
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
            var profile = new SonicCdAudioProfile(() -> (CdDiscDataSource)library.source("scd"));
            AudioManagerInternalAccess.installMatchedSource(audio,null,profile);
            audio.setSoundMap(profile.getSoundMap());
            try (var capture = audio.beginLiveCaptureAudio(60);
                 var sceneAudio = new StageplayAudio(audio,library,asset -> { throw new AssertionError("No assets"); })) {
                {
                    audio.stopMusic();
                    if (command != 0x0F) profile.subCpuAudio().loadStage(CdStagePayload.R11B);
                    var scene = StageplayDocument.empty("scd",0,1,0,0,320,224).withDurationFrames(180)
                            .withAudio(List.of(new AudioCue("music",0,AudioKind.ROM_MUSIC,"scd",command,"",1)));
                    sceneAudio.prepare(scene);
                    long energy = 0;
                    short[] packet = new short[capture.maxStereoFramesPerPacket()*2];
                    audio.presentFrame(PresentationMode.FORWARD);
                    int quiet = capture.drainPresentationFrame(packet);
                    for (int i=0;i<quiet*2;i++) assertEquals(0,packet[i],"No cue means silence");
                    for (int f=0;f<120;f++) {
                        sceneAudio.playFrame(scene,f);
                        audio.presentFrame(PresentationMode.FORWARD);
                        int n = capture.drainPresentationFrame(packet);
                        assertTrue(n>0,"Offline production packet");
                        if(f>=60) for(int i=0;i<n*2;i++) energy += Math.abs((int)packet[i]);
                    }
                    assertTrue(energy>10000,"Scene command "+command+" must synthesize sounding media");
                    if(command==0x0F) assertEquals(3,profile.subCpuAudio().state().cdda().track());
                }
            } finally { audio.resetState(); }
        }
    }
}
