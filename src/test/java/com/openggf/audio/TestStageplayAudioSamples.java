package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.Arrays;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestStageplayAudioSamples {
    private final AudioManager audio=AudioManager.getInstance();
    @BeforeEach void prepare() {
        audio.resetState(); SonicConfigurationService.getInstance().resetToDefaults();
        audio.setBackend(new NullAudioBackend());
    }
    @AfterEach void cleanup() {audio.endCaptureMode();audio.resetState();audio.setBackend(new NullAudioBackend());}
    private StreamedMusicPort.SfxPcm sample(short value,int frames,float gain) {
        short[] data=new short[frames]; Arrays.fill(data,value);
        return new StreamedMusicPort.SfxPcm(48000,1,data,gain);
    }
    private short[] frame(PresentationMode mode) {
        audio.presentFrame(mode); short[] pcm=new short[1600]; assertEquals(800,audio.drainCaptureFrame(pcm));return pcm;
    }
    @Test void voicesMixIntoExistingSceneCaptureAndCloseOnlyTheirOwnScope() {
        audio.setScenePcmSource((pcm,frames)->Arrays.fill(pcm,0,frames*2,(short)100));
        var offered=sample((short)1000,2400,.5f);
        try(var first=audio.prepareSceneSamples(Map.of("voice",offered));
            var second=audio.prepareSceneSamples(Map.of("voice",sample((short)2000,2400,1)))) {
            Arrays.fill(offered.samples(),(short)30000);
            first.play("voice",1); second.play("voice",.5f); audio.beginCaptureMode(48000,60);
            short[] mixed=frame(PresentationMode.FORWARD); for(short value:mixed)assertEquals(1600,value);
            first.close(); short[] remaining=frame(PresentationMode.FORWARD); for(short value:remaining)assertEquals(1100,value);
            second.stop(); short[] base=frame(PresentationMode.FORWARD); for(short value:base)assertEquals(100,value);
        }
    }
    @Test void pauseDoesNotConsumePreparedVoicesAndAdmissionIsBounded() {
        try(var scope=audio.prepareSceneSamples(Map.of("voice",sample((short)1234,800,1)))) {
            scope.play("voice",1); audio.beginCaptureMode(48000,60);
            for(short value:frame(PresentationMode.SILENT))assertEquals(0,value);
            for(short value:frame(PresentationMode.FORWARD))assertEquals(1234,value);
            for(short value:frame(PresentationMode.FORWARD))assertEquals(0,value);
            assertThrows(IllegalArgumentException.class,()->scope.play("missing",1));
            for(int i=0;i<32;i++)scope.play("voice",1);
            assertThrows(IllegalStateException.class,()->scope.play("voice",1));
        }
    }
    @Test void replacedProducerExpiresPlaybackButStillAllowsSafeOwnedCleanup() {
        var scope=audio.prepareSceneSamples(Map.of("voice",sample((short)10,800,1)));
        audio.resetState(); assertThrows(IllegalStateException.class,()->scope.play("voice",1));
        assertDoesNotThrow(scope::stop); assertDoesNotThrow(scope::close); assertDoesNotThrow(scope::close);
    }
}
