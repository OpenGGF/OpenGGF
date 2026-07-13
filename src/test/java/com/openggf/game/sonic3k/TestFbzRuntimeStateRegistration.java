package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzRuntimeStateRegistration {
    @BeforeEach void setUp() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K); }
    @AfterEach void tearDown() { com.openggf.game.session.SessionManager.clear(); }

    @Test
    void initInstallsHandlerBackedStateAndEnsureDoesNotReplaceIt() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);

        ZoneRuntimeState installed = GameServices.zoneRuntimeRegistry().current();
        FbzZoneRuntimeState fbz = assertInstanceOf(FbzZoneRuntimeState.class, installed);
        assertTrue(fbz.isBackedBy(manager.getFbzEvents()));
        assertSame(fbz, S3kRuntimeStates.currentFbz(GameServices.zoneRuntimeRegistry()).orElseThrow());

        manager.ensureZoneRuntimeStateInstalled();
        assertSame(installed, GameServices.zoneRuntimeRegistry().current());
    }

    @Test
    void everyReloadSeamReplacesHandlerAndAdapterWithMatchingBacking() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 0);
        var act1Handler = manager.getFbzEvents();
        var act1State = assertInstanceOf(FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());

        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1); // seamless act reload
        var act2Handler = manager.getFbzEvents();
        var act2State = assertInstanceOf(FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
        assertNotSame(act1Handler, act2Handler);
        assertNotSame(act1State, act2State);
        assertTrue(act2State.isBackedBy(act2Handler));

        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1); // death/checkpoint-style re-init
        var restarted = assertInstanceOf(FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
        assertNotSame(act2Handler, manager.getFbzEvents());
        assertTrue(restarted.isBackedBy(manager.getFbzEvents()));
    }

    @Test
    void standardActTransitionFlagPathIncludesFbz() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 0);

        manager.setEventsFg5ForActTransition();

        assertTrue(manager.getFbzEvents().isEventsFg5());
    }

    @Test
    void checkpointDynamicResizePathLeavesFbzAtZero() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);

        manager.setDynamicResizeRoutine(12);

        assertEquals(0, manager.getDynamicResizeRoutine());
        assertEquals(0, manager.checkpointDynamicResizeRoutine());
    }

    @Test
    void cloudBatchFactoryClosureIsClearedAcrossEveryLevelInitSeam() throws Exception {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 0);
        manager.installFbzCloudRecreationBatchFactory(requests -> null);
        assertNotNull(field(manager, "fbzCloudRecreationBatchFactory"));

        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 0); // restart/checkpoint
        assertNull(field(manager, "fbzCloudRecreationBatchFactory"));
        manager.installFbzCloudRecreationBatchFactory(requests -> null);
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1); // act reload
        assertNull(field(manager, "fbzCloudRecreationBatchFactory"));
        manager.installFbzCloudRecreationBatchFactory(requests -> null);
        manager.initLevel(Sonic3kZoneIds.ZONE_HCZ, 0); // leave zone
        assertNull(field(manager, "fbzCloudRecreationBatchFactory"));
    }

    @Test
    void bridgeCannotReverseTerminalCloudCleanupDuringOrdinaryPlay() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);
        manager.setCloudCleanupTerminal(true);

        assertThrows(IllegalStateException.class, () -> manager.setCloudCleanupTerminal(false));
        assertTrue(manager.getFbzEvents().isCloudCleanupTerminal());
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    void rewindReconciliationRepairsAStaleAdapterBinding() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);
        var currentHandler = manager.getFbzEvents();
        var staleEvents = new com.openggf.game.sonic3k.events.Sonic3kFBZEvents();
        staleEvents.init(1);
        staleEvents.setMagneticState(com.openggf.game.sonic3k.events.Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 77);
        staleEvents.setBossBackgroundState(16, 123, -456);
        staleEvents.setBossLoadPositionAdjustmentPending(true);
        staleEvents.setCloudRewindId(3, com.openggf.game.rewind.identity.ObjectRefId.dynamic(3, 4, 5));
        staleEvents.setEventsFg5(true);
        FbzZoneRuntimeState staleState = new FbzZoneRuntimeState(
                1, com.openggf.game.PlayerCharacter.SONIC_ALONE, staleEvents);
        byte[] restoredBytes = staleState.captureBytes();
        GameServices.zoneRuntimeRegistry().install(staleState);

        manager.reconcileFbzRuntimeStateAfterRestore();

        FbzZoneRuntimeState repaired = assertInstanceOf(FbzZoneRuntimeState.class,
                GameServices.zoneRuntimeRegistry().current());
        assertTrue(repaired.isBackedBy(currentHandler));
        assertArrayEquals(restoredBytes, repaired.captureBytes(),
                "backing repair must migrate restored bytes without resetting state");
        assertEquals(com.openggf.game.rewind.identity.ObjectRefId.dynamic(3, 4, 5), repaired.cloudRewindId(3));
    }
}
