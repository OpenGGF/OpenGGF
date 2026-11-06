package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 5639S: Up on the final entry-fade sample remains in Ctrl_1 during the
 * interrupt-masked load. Native SpecialStage/loc_8454 runs Process_Sprites
 * before Wait_VSync; loc_9600/loc_964A therefore starts at velocity $200.
 * Declared entry request, real level-to-stage load/fade and GameLoop input.
 * No stage state, input latch or startup counter is written by the fixture. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kSpecialStageEntryInput {
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4})
    void finalEntryFadeInputOwnsBootAndNormalSamplingResumes(int inputTiming) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"knuckles","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),0,0,settings);
            for(int i=0;i<60;i++)com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
            GameServices.level().requestSpecialStageEntry();
            for(int i=0;i<60&&session.loop().getCurrentGameMode()!=GameMode.SPECIAL_STAGE;i++)com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertEquals(GameMode.SPECIAL_STAGE,session.loop().getCurrentGameMode());
            var provider=assertInstanceOf(Sonic3kSpecialStageProvider.class,session.loop().getActiveSpecialStageProvider());
            var manager=provider.getManager();
            assertTrue(manager.isInitialized());assertTrue(manager.getSpheresLeft()>0,"real ROM layout loaded");
            assertTrue(remainingEntryFade(manager)>0,"entry fade, before first player update, was reached");
            for(int i=0;i<22&&remainingEntryFade(manager)>1;i++)com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertEquals(1,remainingEntryFade(manager));
            assertEquals(0,manager.captureComparisonState().frameCounter());
            assertFalse(manager.captureComparisonState().started());
            com.openggf.tests.reel.TestReelCapture.step(session,inputTiming==1||inputTiming==3?up():null);
            assertEquals(0,remainingEntryFade(manager));
            assertEquals(0,manager.captureComparisonState().frameCounter(),"final fade tick does not run sprites");
            assertEquals(inputTiming==1||inputTiming==3?1:0,readInt(manager,"heldButtons"),"pre-load input reached the production stage input owner");
            com.openggf.tests.reel.TestReelCapture.step(session,inputTiming==2||inputTiming==3?up():null);
            var first=manager.captureComparisonState();
            assertEquals(1,first.frameCounter(),"boot Process_Sprites equivalent actually ran");
            boolean startsAtBoot=inputTiming==1||inputTiming==3;
            assertEquals(startsAtBoot,first.started(),"boot reads final fade input before the next native poll");
            assertEquals(startsAtBoot?0x200:0,first.velocity());
            for(int i=0;i<22;i++) {
                com.openggf.tests.reel.TestReelCapture.step(session,inputTiming==3?up():null);
                assertEquals(first.velocity(),manager.captureComparisonState().velocity(),"each fade-in tick holds motion");
            }
            assertEquals(first.velocity(),manager.captureComparisonState().velocity(),"fade-in holds player motion");
            com.openggf.tests.reel.TestReelCapture.step(session,inputTiming==3||inputTiming==4?up():null);
            assertEquals(startsAtBoot?0x400:inputTiming==4?0x200:0,manager.captureComparisonState().velocity(),"first main-loop update takes its normal input sample");
        }
    }
    private static Bk2FrameInput up() {return new Bk2FrameInput(0,AbstractPlayableSprite.INPUT_UP,0,false,"Up");}
    private static int remainingEntryFade(Sonic3kSpecialStageManager manager) throws Exception {
        return readInt(manager,"preBootFadeHoldFrames");
    }
    private static int readInt(Sonic3kSpecialStageManager manager,String name) throws Exception {
        var field=Sonic3kSpecialStageManager.class.getDeclaredField(name);
        field.setAccessible(true);return field.getInt(manager);
    }
}
