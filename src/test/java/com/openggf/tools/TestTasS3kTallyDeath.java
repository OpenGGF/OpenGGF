package com.openggf.tools;

import com.openggf.game.*;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 4514S LBZ1: dying after the act-2 resource reload abandons the act-1 tally.
 * Declared post-boss results-owner inlet, actual ResultsCreate -> BG reload ->
 * death -> restart. Does not claim the preceding boss fight or pit approach. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kTallyDeath {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void deathAfterResultsDrivenActLoadAbandonsTally(boolean die) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,0x3EC0,0x1A0);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),6,0,settings);
            for(int i=0;i<5;i++)session.step(null);
            var level=GameServices.level();var manager=level.getObjectManager();
            assertEquals(0,level.getCurrentAct());assertFalse(session.player().getDead());
            // ResultsInit is normally created by the stopped signpost. This
            // valid owner inlet precedes its art queue, children and act signal.
            GameServices.gameState().setEndOfLevelActive(true);
            var results=manager.createDynamicObject(()->new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE,0));
            for(int i=0;i<180&&level.getCurrentAct()==0;i++)session.step(null);
            assertEquals(1,level.getCurrentAct(),"real ResultsCreate signalled LBZ1BGE_DoTransition");
            assertTrue(GameServices.gameState().isEndOfLevelActive());
            assertTrue(level.getObjectManager().getActiveObjects().contains(results),"tally survived seamless act load");
            int lives=GameServices.gameState().getLives(),score=GameServices.gameState().getScore();
            var beforeRestart=level.getObjectManager();
            if(die) {
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                for(int i=0;i<600&&level.getObjectManager()==beforeRestart;i++)session.step(null);
                assertNotSame(beforeRestart,level.getObjectManager(),"death reached actual fresh reload");
                assertEquals(1,level.getCurrentAct(),"restart uses already-loaded act 2");
                assertFalse(level.getObjectManager().getActiveObjects().contains(results));
                assertFalse(GameServices.gameState().isEndOfLevelActive());
                assertEquals(lives-1,GameServices.gameState().getLives());
                assertEquals(score,GameServices.gameState().getScore(),"death skips remaining tally rewards");
            } else {
                session.step(null);
                assertSame(beforeRestart,level.getObjectManager());
                assertTrue(level.getObjectManager().getActiveObjects().contains(results));
                assertEquals(lives,GameServices.gameState().getLives());
            }
        }
    }
}
