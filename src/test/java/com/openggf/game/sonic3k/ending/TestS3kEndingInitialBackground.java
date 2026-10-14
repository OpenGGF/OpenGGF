package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent native Refresh_PlaneFull + sub_592EE/5928C scene owners, task20261014. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingInitialBackground {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        var f=HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.List.of(1,1,1,1,1,1,1),false);
        return f;
    }
    private EndingZoneRuntimeState zone(){return (EndingZoneRuntimeState)GameServices.zoneRuntimeState();}
    private int word(int line,int color){return PaletteWriteSupport.segaWordFromColor(
            GameServices.level().getCurrentLevel().getPalette(line).getColor(color));}
    private void step(HeadlessTestFixture f){f.stepFrame(false,false,false,false,false);}
    @Test void initialRefreshRetainsBothRomPlaneRingsBeforeAnyObjectDispatch(){
        boot();var manager=GameServices.level();var scene=zone().ending().scene();
        assertEquals(16,scene.foreground().revision(),"Ending_ScreenInit Refresh_PlaneFull");
        assertEquals(16,scene.background().revision(),"Ending_BackgroundInit Refresh_PlaneFull");
        var provider=(com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider)manager.getZoneFeatureProvider();
        assertEquals(16,provider.foregroundDescriptorRevision());assertEquals(16,provider.backgroundDescriptorRevision());
        int nonzero=0;
        for(int y=0;y<256;y+=8)for(int x=0;x<512;x+=8){
            assertEquals(manager.getForegroundTileDescriptorAtWorld(x+0x200,y+0x200),scene.foreground().descriptor(x,y));
            int expected=manager.getBackgroundTileDescriptorAtWorld(x+0x200,y);
            assertEquals(expected,scene.background().descriptor(x,y));if((expected&0x7FF)!=0)nonzero++;
        }
        assertTrue(nonzero>100,"the ROM initial background is a populated name table");
    }
    @Test void initialSkyCycleReadsIncrementedRomRowsAfterTheWhiteFadeAndWraps()throws Exception{
        var f=boot();var rom=TestEnvironment.objectServices().romReader();
        for(int call=1;call<=57;call++){
            step(f);
            if((call-1)%4==0){int offset=((call-1)/4+1)*4%0x38;
                assertEquals(rom.readU16BE(0x5931A+offset),word(3,11),"sub_592EE call="+call);
                assertEquals(rom.readU16BE(0x5931A+offset+2),word(3,12));}
        }
    }
    @Test void cloudWaterCycleUsesSamePrimaryClockAndItsNativeSixByteRows()throws Exception{
        var f=boot();zone().screen().backgroundRoutine=4;
        var rom=TestEnvironment.objectServices().romReader();
        for(int call=1;call<=65;call++){
            step(f);
            if((call-1)%8==0){int offset=((call-1)/8+1)*6%0x30;
                for(int color=0;color<3;color++)assertEquals(rom.readU16BE(0x592BE+offset+color*2),word(3,13+color),
                        "sub_5928C call="+call+" color="+color);}
        }
    }
    @Test void nativeSceneFadeFlagSuspendsPoweredAnPalAndRestoresItsClock(){
        var f=boot();for(int i=0;i<25;i++)step(f);
        var controller=(Sonic3kSuperStateController)f.sprite().getSuperStateController();
        var animation=(Sonic3kLevelAnimationManager)GameServices.level().getAnimatedPatternManager();
        zone().screen().foregroundRoutine=8;zone().screen().foregroundSignal=true;
        ((com.openggf.game.sonic3k.Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).update();
        assertEquals(12,zone().screen().foregroundRoutine); // native paired ST writes the negative fade word and cycle gate.
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        var form=controller.captureRewindState();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(saved);
            for(int i=0;i<20;i++)animation.advanceEndingInitialPalette(TestEnvironment.objectServices());
            assertEquals(form,controller.captureRewindState(),"no AnPal_Load under the native negative fade flag");
            for(var key:saved.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,saved.get(key),registry.capture().get(key)).isEmpty(),key);
        }
    }
    @Test void rowAndColumnStreamingPreserveUnwrittenNativeRingCellsAndPublishedRevision(){
        boot();var scene=zone().ending().scene();var plane=scene.foreground();
        java.util.function.IntBinaryOperator source=(x,y)->((y&0xFF0)+((x>>>4)&15))&0x7FF;
        plane.fill(0x200,0x200,source);
        int revision=plane.revision(),untouched=plane.descriptor(0x200,0x270);
        plane.drawAsYouMove(0x210,0x210,source);
        assertEquals(revision+2,plane.revision(),"one incoming column before one incoming row");
        assertEquals(untouched,plane.descriptor(0x200,0x270));
        assertEquals(source.applyAsInt(0x350,0x240),plane.descriptor(0x350,0x240));
        assertEquals(source.applyAsInt(0x210,0x2F0),plane.descriptor(0x210,0x2F0));
        var provider=(com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider)GameServices.level().getZoneFeatureProvider();
        assertEquals(plane.revision(),provider.foregroundDescriptorRevision(),"completed rings still publish changed cells");
        revision=plane.revision();plane.streamRows(0x200,0x80,32,source);
        assertEquals(revision+2,plane.revision(),"Draw_TileRow byte direction/two-write cap, not full healing");
    }
    @Test void actualSkyBackgroundClocksAndPlanesReplayBothSnapshotDirectionsTwice(){
        var f=boot();var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before=registry.capture();for(int i=0;i<37;i++)step(f);var progressed=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());
            for(int i=0;i<37;i++)step(f);same(progressed,registry.capture());
            registry.restore(before);same(before,registry.capture());
            registry.restore(progressed);same(progressed,registry.capture());
        }
    }
    @Test void firstForegroundSignalResetsRoundedPositionWithoutRewritingTheResidentRingAndReplays(){
        var f=boot();step(f);var state=zone();var plane=state.ending().scene().foreground();
        var old=new int[2048];for(int i=0;i<old.length;i++)old[i]=plane.descriptor((i%64)*8,(i/64)*8);
        int revision=plane.revision();state.screen().foregroundSignal=true;
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        step(f);
        assertEquals(4,state.screen().foregroundRoutine);
        assertEquals(revision,plane.revision(),"loc_5AB36 resets rounded words before DrawTilesAsYouMove");
        for(int i=0;i<old.length;i++)assertEquals(old[i],plane.descriptor((i%64)*8,(i/64)*8),"physical cell "+i);
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());step(f);same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }
    private void same(com.openggf.game.rewind.CompositeSnapshot expected,com.openggf.game.rewind.CompositeSnapshot actual){
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(var key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }
    @Test void fromWhiteRetainsComponentsAlreadyAtOrBelowTarget(){
        assertEquals(0x002, S3kEndingPalette.fromWhiteWord(0x002,0xEEE),"Pal_DecColor2 BLS");
        assertEquals(0x604, S3kEndingPalette.fromWhiteWord(0x806,0x642),"only above-target blue/red decrement");
        assertEquals(0xEEE, S3kEndingPalette.fromWhiteWord(0xFFFF,0xEEE),"native masks remove unused CRAM bits");
    }
    @Test void reunionBackgroundChangesOnlyTheNativeFourLayoutBytesThenRedrawsAndRestores(){
        var f=boot();step(f);var state=zone();var map=GameServices.level().getCurrentLevel().getMap();
        var old=new int[map.getWidth()*map.getHeight()];
        for(int y=0;y<map.getHeight();y++)for(int x=0;x<map.getWidth();x++)old[y*map.getWidth()+x]=map.getValue(1,x,y)&255;
        state.screen().backgroundRoutine=20;
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        step(f);
        for(int y=0;y<map.getHeight();y++)for(int x=0;x<map.getWidth();x++){
            int expected=y==7&&x>=4&&x<8?(x%2==0?0x1A:0x1B):old[y*map.getWidth()+x];
            assertEquals(expected,map.getValue(1,x,y)&255,"background layout "+x+","+y);
        }
        assertEquals(24,state.screen().backgroundRoutine);assertEquals(13,state.screen().backgroundRedrawRows);
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());step(f);same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }
    @Test void aizHoldStreamsIncomingRomRowsAndReplaysTheRetainedBankTwice(){
        var f=boot();GameServices.gameState().restoreS3kEmeraldProgress(java.util.List.of(3,3,3,3,3,3,3),false);
        for(int n=0;n<6000&&zone().screen().foregroundRoutine!=32;n++)step(f);
        assertEquals(32,zone().screen().foregroundRoutine,"actual Super rescue enters AIZ return hold");
        var state=zone();var plane=state.ending().scene().background();int revision=plane.revision();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        int steps=0;
        // Target the actual second incoming 16px row, not a guessed dispatch count.
        while((state.screen().backgroundCameraY&0xFF0)>0xD0&&steps<512){step(f);steps++;}
        assertTrue(steps<512);assertEquals(0xD0,state.screen().backgroundCameraY&0xFF0);
        assertEquals(32,state.screen().foregroundRoutine);assertTrue(plane.revision()>=revision+2,"Draw_BG incoming rows under native hold drift");
        int top=state.screen().backgroundCameraY&0xFF0;
        for(int y=top;y<top+32;y+=8)for(int x=0;x<512;x+=8)
            assertEquals(GameServices.level().getBackgroundTileDescriptorAtWorld(x,y),plane.descriptor(x,y),"incoming native cell "+x+","+y);
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());for(int n=0;n<steps;n++)step(f);same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }
}
