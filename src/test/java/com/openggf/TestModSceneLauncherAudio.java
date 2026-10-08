package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.mods.code.ExampleModHarness;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Cold mod startup must synthesize ROM audio even though no title or level initializes it. */
@RequiresRom(SonicGame.SONIC_3K)
class TestModSceneLauncherAudio {
    @TempDir Path work;
    @Test void coldStartupAttachesRomAudioAndPresentsMusicAndJumpEffect() throws Exception {
        SharedLevel level=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        var config=SonicConfigurationService.getInstance();
        boolean oldTest=config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
        try(var harness=ExampleModHarness.build(Path.of("examples/starfall-frontier"),work.resolve("build"))) {
            var effective=harness.apply(GameServices.module());
            TestEnvironment.configureGameModuleFixture(effective);
            AudioManager audio=GameServices.audio();audio.resetState();assertNull(audio.getAudioProfile());
            config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED,false);
            GameLoop loop=new GameLoop(new InputHandler());
            assertTrue(ModSceneLauncher.openStartupScene(loop,config,0,GraphicsManager.getInstance(),528,224));
            assertEquals(GameMode.MOD_SCENE,loop.getCurrentGameMode());
            assertSame(effective.getAudioProfile().getClass(),audio.getAudioProfile().getClass());
            audio.beginCaptureMode(audio.outputSampleRate(),audio.presentationFrameRate());
            try {
                short[] pcm=new short[4096];int peak=0;
                for(int i=0;i<90;i++) {
                    Engine.presentOuterAudioFrame(loop,false,false);
                    int frames=audio.drainCaptureFrame(pcm);
                    for(int n=0;n<frames*2;n++)peak=Math.max(peak,Math.abs((int)pcm[n]));
                }
                assertTrue(peak>100,"Cold startup music must produce audible PCM");
                audio.stopMusic();audio.playSfx(0x62);peak=0;
                for(int i=0;i<20;i++) {
                    Engine.presentOuterAudioFrame(loop,false,false);
                    int frames=audio.drainCaptureFrame(pcm);
                    for(int n=0;n<frames*2;n++)peak=Math.max(peak,Math.abs((int)pcm[n]));
                }
                assertTrue(peak>100,"Jump effect must produce audible PCM");
            }finally{audio.endCaptureMode();loop.modSceneHost.close();}
            assertTrue(harness.findings().isEmpty(),harness.findings()::toString);
        }finally{config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED,oldTest);level.dispose();}
    }
}
