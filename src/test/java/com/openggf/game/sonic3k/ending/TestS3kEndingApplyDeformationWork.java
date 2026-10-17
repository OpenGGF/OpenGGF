package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kEndingEvents;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlEnding;
import com.openggf.game.sonic3k.timing.S3kEndingApplyDeformationWork;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Short declared BG0/4 component boundaries, independent of the ending route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingApplyDeformationWork {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides(); TestEnvironment.resetAll();
    }
    private HeadlessTestFixture boot() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    private EndingZoneRuntimeState zone() {return (EndingZoneRuntimeState)GameServices.zoneRuntimeState();}
    private void event() {((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).update();}

    @ParameterizedTest @ValueSource(ints={0,4})
    void actualEventPublishesApplyCallEvenWhenTheScrollBankIsAlreadyPrepared(int routine) throws Exception {
        var f=boot();var ram=zone().screen();ram.backgroundRoutine=routine;
        ram.backgroundScrollFrame=Integer.MIN_VALUE;
        SwScrlEnding.prepareBackgroundEvent(TestEnvironment.objectServices());
        var bank=ram.hScrollWords.clone();int y=ram.backgroundCameraY;
        assertEquals(GameServices.level().getFrameCounter(),ram.backgroundScrollFrame);
        event();var work=ram.applyDeformationWork;
        assertEquals(1,work.executionCount(),"actual ApplyDeformation call is independent of parameter-bank admission");
        assertEquals(routine==0?S3kEndingApplyDeformationWork.ISLAND_BANDS:S3kEndingApplyDeformationWork.CLOUD_BANDS,
                work.tableAddress());
        assertEquals(y,work.backgroundY());assertEquals(224,work.lines());
        assertTrue(work.lastInstructionCycles()>0);
        assertArrayEquals(bank,ram.hScrollWords);
        assertEquals(S3kEndingApplyDeformationWork.Qualification.QUALIFIED,work.qualification());
        assertEquals(java.util.Set.of(S3kEndingApplyDeformationWork.UnpricedTerm.values()),work.unpricedTerms());
        var saved=zone().captureBytes();var handler=new SwScrlEnding(GameServices.rom().getRom());
        var output=new int[224];int frame=GameServices.level().getFrameCounter();
        handler.update(output,GameServices.camera().getXCopy(),GameServices.camera().getYCopy(),frame,1);
        var expected=output.clone();handler.rebuildOutput(output,0x200,0x200,frame,1);
        assertArrayEquals(expected,output);assertArrayEquals(saved,zone().captureBytes());
        Sonic3kEndingEvents.prepareApplyDeformation(TestEnvironment.objectServices(),routine);
        assertArrayEquals(saved,zone().captureBytes(),"same logical frame does not resubmit");
        f.stepFrame(false,false,false,false,false);assertEquals(2,work.executionCount());
    }
    @ParameterizedTest @ValueSource(ints={8,12,16,20,24})
    void waterAndDelayedRedrawEntriesNeverPublishThisBoundedTerm(int routine) {
        boot();var ram=zone().screen();ram.backgroundRoutine=routine;
        event();assertEquals(0,ram.applyDeformationWork.executionCount());
        // Include a delayed redraw that changes the current state to4: entry still controls this scope.
        ram.backgroundRoutine=4;Sonic3kEndingEvents.prepareApplyDeformation(TestEnvironment.objectServices(),routine);
        assertEquals(0,ram.applyDeformationWork.executionCount());
    }
    @ParameterizedTest @ValueSource(ints={0,4})
    void operandRefusalLeavesTheOrdinaryScrollOutputAndRamUntouched(int routine) throws Exception {
        boot();var ram=zone().screen();ram.backgroundRoutine=routine;
        ram.backgroundScrollFrame=Integer.MIN_VALUE;
        SwScrlEnding.prepareBackgroundEvent(TestEnvironment.objectServices());
        var initial=zone().captureBytes();var handler=new SwScrlEnding(GameServices.rom().getRom());
        int[] expected=new int[224],actual=new int[224];int frame=GameServices.level().getFrameCounter();
        Sonic3kEndingEvents.prepareApplyDeformation(TestEnvironment.objectServices(),routine);
        handler.rebuildOutput(expected,0x200,0x200,frame,1);var expectedWords=ram.hScrollWords.clone();
        zone().restoreBytes(initial);
        byte[] bytes=TestEnvironment.objectServices().romReader().slice(0,S3kEndingApplyDeformationWork.CLOUD_BANDS+20);
        bytes[S3kEndingApplyDeformationWork.CODE_START+2]^=1; // native #$DF operand, memory only
        var services=org.mockito.Mockito.spy(TestEnvironment.objectServices());
        org.mockito.Mockito.doReturn(com.openggf.data.RomByteReader.fromBytes(bytes)).when(services).romReader();
        Sonic3kEndingEvents.prepareApplyDeformation(services,routine);
        handler.rebuildOutput(actual,0x200,0x200,frame,1);
        assertArrayEquals(expected,actual);assertArrayEquals(expectedWords,ram.hScrollWords);
        assertEquals(S3kEndingApplyDeformationWork.Qualification.ROM_UNSUPPORTED,ram.applyDeformationWork.qualification());
        assertEquals(-1,ram.applyDeformationWork.lastInstructionCycles());assertEquals(1,ram.applyDeformationWork.executionCount());
    }
    @Test void drawFallbackPreparesParametersWithoutSubmittingAnApplyCall() throws Exception {
        boot();var ram=zone().screen();ram.backgroundRoutine=4;ram.backgroundScrollFrame=Integer.MIN_VALUE;
        var handler=new SwScrlEnding(GameServices.rom().getRom());
        handler.update(new int[224],0x200,0x200,GameServices.level().getFrameCounter(),1);
        assertEquals(0,ram.applyDeformationWork.executionCount());
        assertEquals(-1,ram.applyDeformationWork.lastInstructionCycles());
    }
    @ParameterizedTest @ValueSource(ints={0,4})
    void wholeWorldRecreationAndLiveCallDescriptorReplayTwice(int routine) {
        var f=boot();zone().screen().backgroundRoutine=routine;
        var manager=GameServices.level().getObjectManager();manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var original=director();assertNotNull(original);
        for(int i=0;i<6;i++)f.stepFrame(false,false,false,false,false);
        assertEquals(6,zone().screen().applyDeformationWork.executionCount());var after=registry.capture();
        Object previous=original;
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());
            var recreated=director();assertNotNull(recreated);assertNotSame(previous,recreated);
            for(int i=0;i<6;i++)f.stepFrame(false,false,false,false,false);
            same(after,registry.capture());assertEquals(6,zone().screen().applyDeformationWork.executionCount());
            assertEquals(routine==0?S3kEndingApplyDeformationWork.ISLAND_BANDS:S3kEndingApplyDeformationWork.CLOUD_BANDS,
                    zone().screen().applyDeformationWork.tableAddress());
            previous=recreated;
        }
    }
    private Object director(){return GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(o->o instanceof EndingRescueDirectorObjectInstance).findFirst().orElse(null);}
    private void same(com.openggf.game.rewind.CompositeSnapshot expected,com.openggf.game.rewind.CompositeSnapshot actual){
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(var key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }
}
