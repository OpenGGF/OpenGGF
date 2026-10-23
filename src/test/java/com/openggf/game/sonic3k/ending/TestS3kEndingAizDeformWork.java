package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlEnding;
import com.openggf.game.sonic3k.timing.S3kAizDeformInstructionWork;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Short event-owner controls, independent of the full ending route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingAizDeformWork {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }
    private HeadlessTestFixture boot() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        return HeadlessTestFixture.builder().withZoneAndAct(13, 1).withFreshLevelStartLifecycle().build();
    }
    private EndingZoneRuntimeState zone() { return (EndingZoneRuntimeState) GameServices.zoneRuntimeState(); }
    private void event() {
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).update();
    }
    @Test void actualBackgroundEventPublishesOneClosedCalleeTerm() {
        boot(); var ram = zone().screen(); var work = ram.aizDeformWork;
        assertEquals(0, work.executionCount());
        assertEquals(S3kAizDeformInstructionWork.Qualification.NOT_EXECUTED, work.qualification());
        ram.backgroundRoutine = 16;
        event();
        assertEquals(1, work.executionCount(), "actual Ending_BackgroundEvent AIZ1_Deform execution");
        assertEquals(1094, work.lastInstructionCycles());
        assertEquals(S3kAizDeformInstructionWork.Qualification.QUALIFIED, work.qualification());
        assertEquals(GameServices.level().getFrameCounter(), work.lastExecutionFrame());
        assertEquals(java.util.Set.of(S3kAizDeformInstructionWork.UnpricedTerm.values()), work.unpricedTerms());
    }
    private void preparePhase(int routine) {
        var ram = zone().screen(); ram.backgroundRoutine = routine;
        ram.aizBackgroundScrollFixed = 2 << 16;
        ram.hScrollWords[30] = 0x1234; ram.hScrollWords[31] = 0x5678;
        // ScreenEvents takes its copies from the live camera before dispatch.
        GameServices.camera().setX((short) 0x1EB8);
        GameServices.camera().setY((short) 0x1E0);
        GameServices.camera().setXCopy((short) 0x1EB8);
        GameServices.camera().setYCopy((short) 0x1E0);
    }
    @ParameterizedTest @ValueSource(ints = {8, 12, 16})
    void existingEventBranchesPrepareOnceAndDrawsNeverPublishWork(int routine) throws Exception {
        var f = boot(); preparePhase(routine); var ram = zone().screen();
        event();
        assertEquals(1, ram.aizDeformWork.executionCount());
        assertEquals(1094, ram.aizDeformWork.lastInstructionCycles());
        assertArrayEquals(new short[]{28477,23723,18969,14215,9461,4707},
                java.util.Arrays.copyOfRange(ram.hScrollWords,4,10), "retained R16 fixed-point vector");
        assertEquals(0x1234, ram.hScrollWords[30]); assertEquals(0x7678, ram.hScrollWords[31]);
        assertEquals(routine == 16 ? 0xEE : 0xF0, ram.backgroundCameraY,
                "state-16 subtraction remains caller-owned and unpriced");
        var saved = zone().captureBytes();
        SwScrlEnding.prepareAizBackgroundEvent(TestEnvironment.objectServices());
        var handler = new SwScrlEnding(GameServices.rom().getRom()); var output = new int[224];
        int frame = GameServices.level().getFrameCounter();
        handler.update(output,0x1EB8,0x1E0,frame,1); var first = output.clone();
        handler.update(output,0x1EB8,0x1E0,frame,1);
        handler.rebuildOutput(output,0x1EB8,0x1E0,frame,1);
        assertArrayEquals(first,output); assertArrayEquals(saved,zone().captureBytes());
        f.stepFrame(false,false,false,false,false);
        assertEquals(2,ram.aizDeformWork.executionCount(),"next actual logical call, once despite output composition");
    }
    @Test void drawFallbackCanPrepareRamWithoutInventingLogicalInstructionWork() throws Exception {
        boot(); preparePhase(16); var ram = zone().screen();
        var handler = new SwScrlEnding(GameServices.rom().getRom());
        handler.update(new int[224],0x1EB8,0x1E0,GameServices.level().getFrameCounter(),1);
        assertEquals(0,ram.aizDeformWork.executionCount());
        assertEquals(-1,ram.aizDeformWork.lastInstructionCycles());
        assertEquals(0x7678,ram.hScrollWords[31]);
    }
    @Test void unsupportedOperandRefusesOnlyQualificationAndPreservesSemanticRam() throws Exception {
        boot(); preparePhase(16);
        byte[] bytes = TestEnvironment.objectServices().romReader().slice(0,
                S3kAizDeformInstructionWork.CODE_START + S3kAizDeformInstructionWork.CODE_LENGTH);
        bytes[S3kAizDeformInstructionWork.CODE_START + 2] ^= 1; // absolute RAM operand, in memory only
        var services = org.mockito.Mockito.spy(TestEnvironment.objectServices());
        org.mockito.Mockito.doReturn(com.openggf.data.RomByteReader.fromBytes(bytes)).when(services).romReader();
        SwScrlEnding.prepareAizBackgroundEvent(services);
        var ram = zone().screen();
        assertEquals(1,ram.aizDeformWork.executionCount()); assertEquals(-1,ram.aizDeformWork.lastInstructionCycles());
        assertEquals(S3kAizDeformInstructionWork.Qualification.ROM_UNSUPPORTED,ram.aizDeformWork.qualification());
        assertArrayEquals(new short[]{28477,23723,18969,14215,9461,4707},
                java.util.Arrays.copyOfRange(ram.hScrollWords,4,10));
        assertEquals(0xEE,ram.backgroundCameraY); assertEquals(0x7678,ram.hScrollWords[31]);
    }
    @Test void liveWorkAndWholeWorldRestoreAndReplayTwice() {
        var f = boot(); preparePhase(16);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        for(int n=0;n<6;n++)f.stepFrame(false,false,false,false,false);
        assertEquals(6,zone().screen().aizDeformWork.executionCount());
        assertEquals(1094,zone().screen().aizDeformWork.lastInstructionCycles());
        var after = registry.capture();
        for(int replay=0;replay<2;replay++){
            registry.restore(before); same(before,registry.capture());
            for(int n=0;n<6;n++)f.stepFrame(false,false,false,false,false);
            same(after,registry.capture());
            assertEquals(6,zone().screen().aizDeformWork.executionCount());
            registry.restore(before); registry.restore(after); same(after,registry.capture());
        }
    }
    private void same(com.openggf.game.rewind.CompositeSnapshot expected,
                      com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(var key:expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }
}
