package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal Pal_FillBlack D01 branch + Animate_Palette/Pal_FromWhite, no reference-row hydration. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingInitialPalette {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear(); }
    private HeadlessTestFixture boot(String main,String sidekick,String emeralds) {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,sidekick==null?"":sidekick);
        var f=HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        var digits=emeralds.chars().map(c->c-'0').boxed().toList();
        GameServices.gameState().restoreS3kEmeraldProgress(digits,digits.stream().anyMatch(c->c>=2));
        return f;
    }
    @ParameterizedTest
    @CsvSource({"sonic,,0000000","sonic,,1111111","sonic,,3333333",
            "tails,,0000000","tails,,1111111","tails,,3333333",
            "sonic,tails,0000000","sonic,tails,1111111","sonic,tails,3333333"})
    void freshEndingUsesWhiteNormalPaletteAndLiteralTwentyTwoCallCadence(
            String main,String sidekick,String emeralds)throws Exception {
        var f=boot(main,sidekick,emeralds);
        assertWhite();
        // The convenience runner internally retries SETUP_ONLY, then admits LevelLoop call1.
        var reader=GameServices.level().getObjectManager().getObjectServices().romReader();
        var expected=new int[3][16];for(var line:expected)java.util.Arrays.fill(line,0xEEE);
        int[] targets={0x5B16E,0xA97BC,0xA989C}; // two source patches + retained entry27 palette37 line3
        for(int call=1;call<=22;call++) {
            f.stepFrame(false,false,false,false,false);
            // Pal_fade_delay2 cleared by Level clearRAM(_unkFA80,$80). Expiry1,4,...,22.
            if((call-1)%3==0)for(int line=0;line<3;line++)for(int color=0;color<16;color++)
                expected[line][color]=decrementWord(expected[line][color],reader.readU16BE(targets[line]+color*2));
            // loc_5AF88 is later than Animate_Palette: its shared BG clock owns these two cells.
            if((call-1)%4==0){int row=(((call-1)/4+1)*4)%0x38;
                expected[2][11]=reader.readU16BE(0x5931A+row);expected[2][12]=reader.readU16BE(0x5931A+row+2);}
            for(int line=0;line<3;line++)for(int color=0;color<16;color++) {
                // loc_5D9EE admits loc_5DF58 after the fade; its native positive
                // FAC1 selects line3 cells14/15. Existing independent script
                // vectors cover their ROM cursor/expiry; they are not fade-only cells.
                if(emeralds.equals("3333333")&&line==1&&color>=14)continue;
                assertEquals(expected[line][color],word(line+1,color),"call="+call+" line="+line+" color="+color);
            }
            if(call==1&&emeralds.equals("3333333")) {
                assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(EndingMasterPaletteObjectInstance.class).size());
                assertEquals("s3k-ending-master",GameServices.paletteOwnershipRegistryOrNull()
                        .ownerAt(com.openggf.game.palette.PaletteSurface.NORMAL,2,14),"native late helper owns its cells after fade");
            }
            assertEquals(call<22,((Sonic3kLevelAnimationManager)GameServices.level().getAnimatedPatternManager())
                    .paletteFadeTimerRunning(),"native fade timer counts once per admitted LevelLoop");
        }
        assertEquals(0x0462,word(1,0));
        f.stepFrame(false,false,false,false,false);assertEquals(0x0462,word(1,0));
    }
    @Test void bothRestoreDirectionsReplayInitialWhiteFadeWithoutDuplicateCalls() {
        var f=boot("sonic","","1111111");var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before=registry.capture();
        for(int i=0;i<8;i++)f.stepFrame(false,false,false,false,false);var middle=registry.capture();
        for(int i=0;i<14;i++)f.stepFrame(false,false,false,false,false);var complete=registry.capture();
        assertEquals(0x0462,word(1,0));
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture(),"before"+cycle);
            registry.restore(middle);same(middle,registry.capture(),"middle"+cycle);
            for(int i=0;i<14;i++)f.stepFrame(false,false,false,false,false);
            same(complete,registry.capture(),"replayed22"+cycle);
            registry.restore(before);same(before,registry.capture(),"reverse"+cycle);assertWhite();
        }
    }
    @Test void poweredPaletteWaitsForActualAnPalAdmissionAndRestoresWithoutEarlyPublication() {
        var f=boot("sonic","","1111111");
        f.stepFrame(false,false,false,false,false); // component fixture's first admitted loop initializes rescue
        var controller=(com.openggf.game.sonic3k.Sonic3kSuperStateController)f.sprite().getSuperStateController();
        for(int color=2;color<=4;color++)assertEquals(0xEEE,word(0,color),"loc_5D95C white body entries");
        assertEquals(0x24,controller.captureRewindState().paletteFrame());
        assertEquals(0,controller.captureRewindState().paletteTimer());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var first=registry.capture();
        for(int call=2;call<=22;call++) {
            f.stepFrame(false,false,false,false,false);
            assertEquals(0x24,controller.captureRewindState().paletteFrame(),"no SuperHyper_PalCycle during fade call"+call);
            assertEquals(0,controller.captureRewindState().paletteTimer());
            for(int color=2;color<=4;color++)assertEquals(0xEEE,word(0,color));
        }
        var held=registry.capture();f.stepFrame(false,false,false,false,false);var admitted=registry.capture();
        assertEquals(0x2A,controller.captureRewindState().paletteFrame());assertEquals(6,controller.captureRewindState().paletteTimer());
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(first);same(first,registry.capture(),"first powered hold"+cycle);
            for(int call=2;call<=22;call++)f.stepFrame(false,false,false,false,false);
            same(held,registry.capture(),"held before admission"+cycle);
            f.stepFrame(false,false,false,false,false);same(admitted,registry.capture(),"first AnPal replay"+cycle);
            registry.restore(held);same(held,registry.capture(),"reverse unpublished hold"+cycle);
            for(int color=2;color<=4;color++)assertEquals(0xEEE,word(0,color),"derived refresh respects captured gate");
        }
    }
    @ParameterizedTest @CsvSource({"0000000","1111111","3333333"})
    void tailsRescueHoldsOnlyTheNativeSonicCompanionTimerAndReplaysItsByte(String emeralds)throws Exception {
        var f=boot("tails","",emeralds);var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before=registry.capture();int[] normal=new int[3];for(int i=0;i<3;i++)normal[i]=word(0,i+2);
        f.stepFrame(false,false,false,false,false);
        var controller=(com.openggf.game.sonic3k.Sonic3kSuperStateController)f.sprite().getSuperStateController();
        assertEquals(0x7F,companionTimer(controller),"Obj_5D86A writes Palette_timer after the rescue routine, even before AnPal");
        var first=registry.capture();boolean tailsBodyAdvanced=false;
        for(int call=2;call<=64;call++) {
            f.stepFrame(false,false,false,false,false);
            assertEquals(0x7F,companionTimer(controller),"the wrapper repeats its byte writer on call"+call);
            for(int i=0;i<3;i++)assertEquals(normal[i],word(0,i+2),"the Sonic/Flicky palette is held, not Tails' body");
            tailsBodyAdvanced|=controller.captureRewindState().paletteFrame()!=0;
        }
        assertEquals(emeralds.equals("3333333"),tailsBodyAdvanced,"only actual powered Tails retains its separate body cycle");
        var later=registry.capture();f.stepFrame(false,false,false,false,false);var next=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(first);same(first,registry.capture(),"first hold"+cycle);assertEquals(0x7F,companionTimer(controller));
            registry.restore(later);same(later,registry.capture(),"later hold"+cycle);assertEquals(0x7F,companionTimer(controller));
            f.stepFrame(false,false,false,false,false);same(next,registry.capture(),"held replay"+cycle);
            registry.restore(before);same(before,registry.capture(),"before entry"+cycle);
            f.stepFrame(false,false,false,false,false);same(first,registry.capture(),"first writer replay"+cycle);
        }
    }
    @Test void tailsReunionHoldsTheCompanionOnlyAtItsActualTerminalRoutine()throws Exception {
        var f=boot("tails","","3333333");f.stepFrame(false,false,false,false,false);
        var manager=GameServices.level().getObjectManager();
        for(var object:java.util.List.copyOf(manager.getActiveObjects()))manager.removeDynamicObject(object);
        var owner=manager.createDynamicObject(()->new EndingRescueDirectorObjectInstance(EndingRescueDirectorObjectInstance.REUNION));
        var routine=EndingRescueDirectorObjectInstance.class.getDeclaredField("routine");routine.setAccessible(true);
        var timer=com.openggf.game.sonic3k.Sonic3kSuperStateController.class.getDeclaredField("superTailsCompanionPaletteTimer");timer.setAccessible(true);
        var controller=(com.openggf.game.sonic3k.Sonic3kSuperStateController)f.sprite().getSuperStateController();
        // Independently declared native routine2 versus loc_5E4E2 components;
        // this is not a reference row or a fitted elapsed-frame gate.
        routine.setInt(owner,2);timer.setInt(controller,3);owner.update(0,f.sprite());
        assertEquals(3,companionTimer(controller),"ordinary reunion swing does not own the terminal hold");
        routine.setInt(owner,18);owner.update(0,f.sprite());
        assertEquals(0x7F,companionTimer(controller),"loc_5E4E2 owns the terminal Palette_timer writer");
    }
    private int companionTimer(com.openggf.game.sonic3k.Sonic3kSuperStateController controller)throws Exception {
        var timer=com.openggf.game.sonic3k.Sonic3kSuperStateController.class.getDeclaredField("superTailsCompanionPaletteTimer");
        timer.setAccessible(true);return timer.getInt(controller);
    }
    private void assertWhite() {for(int line=1;line<4;line++)for(int color=0;color<16;color++)
        assertEquals(0xEEE,word(line,color),"D01 Pal_FillWhite line="+line+" color="+color);}
    private int word(int line,int color) {return PaletteWriteSupport.segaWordFromColor(
            GameServices.level().getCurrentLevel().getPalette(line).getColor(color));}
    /** Pal_DecColor2 compares and decrements all three byte/nibble components independently. */
    private int decrementWord(int current,int target) {
        int result=current;
        for(int mask:new int[]{0xE00,0xE0,0xE})if((current&mask)>(target&mask))result-=mask==0xE00?0x200:mask==0xE0?0x20:2;
        return result;
    }
    private void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(var key:expected.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),()->label+" "+key+" "+diff);
        }
    }
}
