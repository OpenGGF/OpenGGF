package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pause_Main/Pause_Loop and VInt_Done: pause changes global phase without
 * spending IGT or running objects. 4514S/4515S representative clock mechanism. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kPauseClock {
    @Test void startPauseAdvancesVintWhileLevelAndPlayerStayFrozen() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),0,0,settings);
            for(int i=0;i<600;i++) {
                com.openggf.tests.reel.TestReelCapture.step(session,null);
                if(session.loop().getCurrentGameMode()==GameMode.LEVEL&&!GameServices.level().hasPendingFreshLevelTransitionBoundary()) break;
            }
            assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
            for(int i=0;i<60;i++)com.openggf.tests.reel.TestReelCapture.step(session,null);
            var level=GameServices.level();var player=session.player();var manager=level.getObjectManager();
            int vint=manager.getVblaCounter(),levelFrame=level.getFrameCounter();
            assertTrue(vint>0,"unpaused gameplay reached the V-int clock owner");
            long timer=level.getLevelGamestate().getTimerFrames();
            int x=player.getCentreX(),y=player.getCentreY();
            com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(0,0,0,true,"pause"));
            assertTrue(GameServices.gameState().isGamePaused());
            com.openggf.tests.reel.TestReelCapture.step(session,null);com.openggf.tests.reel.TestReelCapture.step(session,null);
            // Historical base froze here; Pause_Loop still reaches VInt_Done.
            assertEquals(vint+3,manager.getVblaCounter());
            assertEquals(vint+3,com.openggf.game.session.EngineTiming.vIntRunCounter().value());
            assertEquals(levelFrame,level.getFrameCounter());assertEquals(timer,level.getLevelGamestate().getTimerFrames());
            assertEquals(x,player.getCentreX());assertEquals(y,player.getCentreY());
            com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(0,0,0,true,"resume"));
            assertFalse(GameServices.gameState().isGamePaused());
            assertEquals(levelFrame+1,level.getFrameCounter());
            assertEquals(vint+4,manager.getVblaCounter(),"resume control executes the clock owner again");
        }
    }
}
