package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level/loc_6068 and loc_60DA submit Nemesis descriptors before the first
 * loc_62CC Wait_VSync. That loop dispatches Obj_TitleCardInit, then prepares
 * the Nemesis head. Its next VInt_A_C consumes six patterns. No trace rows
 * or recording are needed to exercise these production owners.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshTitlePlcLoadBoundary {
    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loadSubmitsNemesisBeforeEitherDriverEntersTheTitleLoop(boolean recording) throws Exception {
        start(recording);
        var queued = plc().capture();
        assertNull(queued.activeEntry(), "Level has not reached Process_Nem_Queue_Init");
        assertEquals(10, queued.queuedEntries().size(), "HCZ1 PLC_0E followed by PLC_01");
        assertEquals(56, queued.queuedEntries().getFirst().totalPatterns());
        assertTrue(title().capture().artHandles().isEmpty(),
                "the load must not dispatch Obj_TitleCardInit's four KosM archives early");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void firstTitleLoopPreparesOnceAndNextVblankDrainsWithRewind(boolean recording) throws Exception {
        Runnable step = start(recording);
        step.run();
        var prepared = plc().capture();
        assertNotNull(prepared.activeEntry(), "the first loc_62CC tail prepares the head");
        assertEquals(56, prepared.activeEntry().remainingPatterns(),
                "the first title VBlank preceded preparation and consumed no patterns");
        assertEquals(9, prepared.queuedEntries().size());
        var handles = title().capture().artHandles();
        assertEquals(4, handles.size(), "only Obj_TitleCardInit submits the four archives");
        assertEquals(java.util.List.of(0, 0x10, 0x3D, 0x4D),
                title().capture().artDestinations());
        assertTrue(handles.stream().allMatch(handle -> handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE));
        var timing = GameServices.hardwareTiming();
        var nextOrdinals = timing.capture().nextOrdinals();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var rewind = registry.capture();
        step.run();
        var drained = plc().capture();
        assertEquals(50, drained.activeEntry().remainingPatterns());
        var afterTiming = timing.capture();
        assertEquals(nextOrdinals.get(HardwareWorkKind.KOS_MODULE_QUEUE),
                afterTiming.nextOrdinals().get(HardwareWorkKind.KOS_MODULE_QUEUE),
                "the second dispatch must not resubmit the parent's archives");
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(rewind);
            assertEquals(prepared, plc().capture());
            assertEquals(handles, title().capture().artHandles());
            assertEquals(nextOrdinals, timing.capture().nextOrdinals());
            step.run();
            assertEquals(drained, plc().capture());
            assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    "hardware-timing", afterTiming, timing.capture()),
                    "queue progress and job ordinals replay exactly");
        }
    }

    private static Runnable start(boolean recording) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        Runnable step;
        if (recording) {
            var driver = new RecordingFrameDriver(GameServices.sprites().getMainPlayable());
            step = () -> driver.stepFrame(false, false, false, false, false);
        } else {
            var loop = new GameLoop(new InputHandler());
            loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
            step = loop::step;
        }
        GameServices.level().loadZoneAndActAtFreshTitleCardBoundary(1, 0);
        return step;
    }

    private static Sonic3kLevelTitlePlcService plc() {
        return com.openggf.game.session.WorldPlcLifecycleAccess.require(GameServices.level().getWorldSession(), Sonic3kLevelTitlePlcService.class);
    }

    private static Sonic3kTitleCardManager title() {
        return (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();
    }
}
