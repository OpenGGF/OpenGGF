package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestSonic3kLbzBigArmTransitionBridge {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kLBZEvents events = manager.getLbzEvents();
        GameServices.camera().setMaxX((short) 0x5F00);
        GameServices.camera().setMinY((short) 0x0100);
        GameServices.camera().setMaxY((short) 0x0F00);
        GameServices.camera().setMaxYTarget((short) 0x0F00);

        manager.prepareLbzBigArmFloorTransition();

        assertTrue(events.isPostTitleAct2SizeChangeActiveForTest());
        assertTrue(events.werePostTitleAct2WorkersCreatedThisPassForTest(),
                "Change_Act2Sizes creates all three later worker slots together");
        assertArrayEquals(new int[]{0x6000, 0, 0x1000},
                events.postTitleAct2TargetsForTest());
        assertEquals(0x5F00, GameServices.camera().getMaxX() & 0xFFFF);
        assertEquals(0x0100, GameServices.camera().getMinY() & 0xFFFF);
        assertEquals(0x0F00, GameServices.camera().getMaxY() & 0xFFFF);
        assertEquals(0x1000, GameServices.camera().getMaxYTarget() & 0xFFFF);
        assertArrayEquals(new int[]{0x4000, 0x4000, 0x8000},
                events.postTitleAct2WorkerAccumulatorsForTest(),
                "later SST worker slots execute their creation entries in the settlement pass");

        events.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x5F00, 0x0100, 0x0F01}, currentBounds());
        events.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x5F00, 0x0100, 0x0F02}, currentBounds());
        events.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x5F01, 0x00FF, 0x0F04}, currentBounds(),
                "the three native workers keep independent $4000/$4000/$8000 accumulators");

        GameServices.camera().setMaxX((short) 0x5FFF);
        GameServices.camera().setMinY((short) 1);
        GameServices.camera().setMaxY((short) 0x0FFF);
        setIntField(events, "act2MaxXAccumulator", 0xC000);
        setIntField(events, "act2MinYAccumulator", 0xC000);
        setIntField(events, "act2MaxYAccumulator", 0x8000);
        events.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x6000, 0, 0x1000}, currentBounds());
        assertArrayEquals(new boolean[]{false, false, true, true, true, false},
                events.postTitleAct2WorkerPhasesForTest(),
                "max X/min Y delete on equality, while native bgt keeps equal max Y alive");
        assertTrue(events.isPostTitleAct2SizeChangeActiveForTest());

        events.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new boolean[]{false, false, false, true, true, true},
                events.postTitleAct2WorkerPhasesForTest());
        assertFalse(events.isPostTitleAct2SizeChangeActiveForTest(),
                "max Y deletes only on the entry that overshoots equality");
    }

    @Test
    void genericTitleCardWorkersKeepTheirCreationMarkerUntilFirstCentralizedEntry() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kLBZEvents events = manager.getLbzEvents();
        GameServices.camera().setMaxX((short) 0x5F00);
        GameServices.camera().setMinY((short) 0x0100);
        GameServices.camera().setMaxY((short) 0x0F00);
        int[] incomingBounds = currentBounds();

        manager.preparePreloadedActTitleCardCompletion();

        assertTrue(events.isPostTitleAct2SizeChangeActiveForTest());
        assertTrue(events.werePostTitleAct2WorkersCreatedThisPassForTest());
        assertArrayEquals(new int[]{0, 0, 0},
                events.postTitleAct2WorkerAccumulatorsForTest(),
                "the generic caller has not run any later-slot worker entry");

        events.updatePostTitleAct2SizeWorkers();

        assertFalse(events.werePostTitleAct2WorkersCreatedThisPassForTest());
        assertTrue(events.isPostTitleAct2SizeChangeActiveForTest());
        assertArrayEquals(new int[]{0, 0, 0},
                events.postTitleAct2WorkerAccumulatorsForTest(),
                "the first centralized call only consumes the generic creation marker");
        assertArrayEquals(incomingBounds, currentBounds(),
                "generic Change_Act2Sizes must not advance a worker one event frame early");

        events.updatePostTitleAct2SizeWorkers();

        assertArrayEquals(new int[]{0x4000, 0x4000, 0x8000},
                events.postTitleAct2WorkerAccumulatorsForTest(),
                "the following centralized call is each generic worker's first own entry");
    }

    @Test
    void bigArmFloorBridgeIsNoOpWithoutActiveLbzAct2Handler() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        int originalTarget = GameServices.camera().getMaxYTarget() & 0xFFFF;

        manager.prepareLbzBigArmFloorTransition();

        assertFalse(manager.getLbzEvents().isPostTitleAct2SizeChangeActiveForTest(),
                "an LBZ act-1 handler must not impersonate the act-2 owner");
        assertTrue((GameServices.camera().getMaxYTarget() & 0xFFFF) == originalTarget);

        manager.initLevel(Sonic3kZoneIds.ZONE_MHZ, 0);
        manager.prepareLbzBigArmFloorTransition();

        assertNull(manager.getLbzEvents(),
                "the semantic bridge must not fabricate an LBZ handler in another zone");
        assertTrue((GameServices.camera().getMaxYTarget() & 0xFFFF) == originalTarget);
    }

    @Test
    void grabFloorImpactPublishesTimedShakeInNativeFgBgOrder() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(
                GameServices.zoneRuntimeRegistry()).orElseThrow();
        LbzFinalBoss2Instance boss = GameServices.level().getObjectManager()
                .createDynamicObject(() -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, 0xCC, 0, 0, false, 0)));
        fixture.stepIdleFrames(1);

        manager.startLbzBigArmTimedShake(1);
        manager.update();
        assertEquals(1, state.getTimedShakePreparedOffset());
        assertEquals(0, state.getTimedShakeAppliedOffset());
        int baseCameraY = GameServices.camera().getY() & 0xFFFF;
        setIntField(boss, "routine", 0x24);
        setIntField(boss, "y", (baseCameraY + 0x88) & 0xFFFF);

        fixture.stepIdleFrames(1);

        assertEquals(0x26, boss.getRoutineForTest());
        assertEquals(19, state.getTimedShakeCountdown());
        assertEquals(1, state.getTimedShakeAppliedOffset(),
                "foreground consumes the previously prepared offset on the trigger frame");
        assertEquals(-5, state.getTimedShakePreparedOffset(),
                "background prepares ScreenShakeArray[19] for the next frame");
        assertEquals((baseCameraY + 1) & 0xFFFF, state.getCameraYCopy(baseCameraY));

        fixture.stepIdleFrames(1);

        assertEquals(-5, state.getTimedShakeAppliedOffset());
        assertEquals((baseCameraY - 5) & 0xFFFF, state.getCameraYCopy(baseCameraY));
        assertEquals(18, state.getTimedShakeCountdown());
    }

    @Test
    void timedShakePausesAndPublishesZeroForDeadPlayer() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(
                GameServices.zoneRuntimeRegistry()).orElseThrow();
        AbstractPlayableSprite player = fixture.sprite();

        manager.startLbzBigArmTimedShake(20);
        manager.update();
        assertEquals(19, state.getTimedShakeCountdown());
        assertEquals(-5, state.getTimedShakePreparedOffset());

        player.setObjectRoutineOverride(6);
        manager.update();

        assertEquals(19, state.getTimedShakeCountdown());
        assertEquals(-5, state.getTimedShakeAppliedOffset());
        assertEquals(0, state.getTimedShakePreparedOffset());

        player.setObjectRoutineOverride(2);
        manager.update();

        assertEquals(18, state.getTimedShakeCountdown());
        assertEquals(0, state.getTimedShakeAppliedOffset());
        assertEquals(5, state.getTimedShakePreparedOffset());
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int[] currentBounds() {
        return new int[]{
                GameServices.camera().getMaxX() & 0xFFFF,
                GameServices.camera().getMinY() & 0xFFFF,
                GameServices.camera().getMaxY() & 0xFFFF
        };
    }
}
