package com.openggf.tests;
import com.openggf.*;
import com.openggf.tests.reel.S1ReelFrames;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** S1_PAUSE_PLC: actual Start pause services a prepared FZ PLC without active updates. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1PausePlcVerdicts {
    @Test void startPauseServicesPreparedFinalZoneArtWithoutRunningGameplay() throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,5,2);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level).build();
            var plc=GameServices.module().getGameService(Sonic1PlcService.class);
            while(plc.isBusy()){plc.prepare();plc.serviceLevelVBlank();}
            plc.append(31); plc.prepare();
            assertTrue(plc.isBusy());
            var context=LevelFrameContext.from(f.runtime());
            int[] active={0};
            int frames=0;
            int activePatterns=plc.capture().activeEntry().remainingPatterns();
            int queued=plc.capture().queuedEntries().size();
            int levelFrame=GameServices.level().getFrameCounter();
            do {
                var result=LevelFrameTestStep.executeWithPause(context,GameServices.level(),f.camera(),()->active[0]++,frames==0,LevelFrameStep.DIRECT_WRAPPER);
                S1ReelFrames.afterPauseStep(f,frames,frames==0,true);
                assertEquals(LevelFrameResult.PAUSED,result);
                assertEquals(0,active[0]);
                frames++;
            } while(plc.capture().activeEntry()!=null && frames<120);
            assertNull(plc.capture().activeEntry(),"Pause finishes the prepared descriptor");
            assertEquals((activePatterns+2)/3,frames);
            assertEquals(queued,plc.capture().queuedEntries().size(),"Pause cannot prepare the next descriptor");
            assertEquals(levelFrame,GameServices.level().getFrameCounter());
            assertTrue(frames>0);
            LevelFrameTestStep.executeWithPause(context,GameServices.level(),f.camera(),()->active[0]++,true,LevelFrameStep.DIRECT_WRAPPER);
            S1ReelFrames.afterPauseStep(f,frames,true,false);
            assertEquals(1,active[0],"Fresh Start resumes the active loop; held pause was the control");
        } finally { level.dispose(); }
    }
    @Test void currentLimitationNativeStartPauseFreezesCapsulePhaseClock() throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,0,0);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level).build();
            S1ReelFrames.idle(f,1);
            var objects=GameServices.level().getObjectManager();
            int before=objects.getVblaCounter();int levelFrame=GameServices.level().getFrameCounter();
            for(int i=0;i<3;i++) {
                assertEquals(LevelFrameResult.PAUSED, f.runner().stepFrame(false,false,false,false,false,i==0));
                S1ReelFrames.afterPauseStep(f,i==0,true);
            }
            assertEquals(levelFrame,GameServices.level().getFrameCounter());
            // Native parity expected before+3, observed before. Retain the limitation.
            assertEquals(before,objects.getVblaCounter(),"Confirmed limitation: paused service freezes the bound VInt counter");
            assertNotEquals(before+3,objects.getVblaCounter(),"Native VBlank_Exit increments even inside PauseGame");
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME,f.runner().stepFrame(false,false,false,false,false,true));
            S1ReelFrames.afterPauseStep(f,true,false);
            assertEquals(before+1,objects.getVblaCounter(),"Unpause really resumes the same counter owner");
            assertEquals(levelFrame+1,GameServices.level().getFrameCounter());
            assertNotEquals((before+4)&7,objects.getVblaCounter()&7,"Pri_Animals now sees the wrong native modulo-eight spawn phase");
        } finally {level.dispose();}
    }

}
