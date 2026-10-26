package com.openggf.tools;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 4289S AIZ2: bonus return reloads horizontal bounds, opening the way back
 * from Knuckles' route. Declared bonus-entry/exit boundary, real load and return;
 * does not claim the checkpoint portal approach or the author's ring route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kBonusBounds {
    @Test void bonusReturnClearsKnucklesHorizontalRouteLock() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"knuckles","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),0,1,settings);
            for(int i=0;i<60;i++)session.step(null);
            assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
            assertFalse(session.player().getDead());
            assertEquals(0x1040,GameServices.camera().getMinX()&0xFFFF,"AIZ2_KnuxResize1 publishes native route lock");
            for(int i=0;i<20;i++)session.step(null);
            assertEquals(0x1040,GameServices.camera().getMinX()&0xFFFF,"neutral no-bonus control retains lock");
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            for(int i=0;i<600;i++) {
                session.step(null);
                if(session.loop().getCurrentGameMode()==GameMode.BONUS_STAGE && fadeDone())break;
            }
            assertEquals(GameMode.BONUS_STAGE,session.loop().getCurrentGameMode());assertTrue(fadeDone());
            var coordinator=assertInstanceOf(Sonic3kBonusStageCoordinator.class,GameServices.bonusStage());
            assertEquals(1,coordinator.getSavedState().savedZoneAndAct());
            var interior=GameServices.level().getObjectManager();
            coordinator.requestExit();
            for(int i=0;i<800;i++) {
                session.step(null);
                if(session.loop().getCurrentGameMode()==GameMode.LEVEL && fadeDone()
                        && GameServices.level().getObjectManager()!=interior
                        && !GameServices.level().hasPendingFreshLevelTransitionBoundary())break;
            }
            assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());assertTrue(fadeDone());
            assertNotSame(interior,GameServices.level().getObjectManager());
            assertEquals(0,GameServices.level().getCurrentZone());assertEquals(1,GameServices.level().getCurrentAct());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(0,GameServices.camera().getMinX(),"Load_Starpost_Settings does not restore old horizontal lock");
        }
    }
    private static boolean fadeDone() {
        var fade=SessionManager.getCurrentGameplayMode().getFadeManager();
        return !fade.isActive()&&!fade.hasPendingCompletion();
    }
}
