package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestFbzBlaster {
    @BeforeEach void bounds() { AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0); }
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    @Test void subtypeAndPlacementOrientationDecodeIndependently() {
        BlasterBadnikInstance ordinary = new BlasterBadnikInstance(spawn(0x20, 0));
        BlasterBadnikInstance magnetic = new BlasterBadnikInstance(spawn(0x20, 2));
        assertEquals(0x40, ordinary.initialPatrolTimer());
        assertEquals(0x80, ordinary.recurringPatrolTimer());
        assertFalse(ordinary.magneticCapable());
        assertTrue(magnetic.magneticCapable());
    }

    @Test void fallingEntryUsesExactIndexedVelocityAndConvertsToPatrolContract() {
        BlasterBadnikInstance falling = BlasterBadnikInstance.falling(spawn(0, 0), true);
        assertEquals(0x200, falling.xVelocityRaw());
        assertEquals(-0x200, falling.yVelocityRaw());
        assertTrue(falling.fallingEntry());
        int x = falling.getX(), y = falling.getY();
        falling.update(0, null);
        assertEquals(x, falling.getX(), "loc_89666 creation tick is setup-only");
        assertEquals(y, falling.getY(), "loc_89666 creation tick applies no gravity");
    }

    @Test void fallingEntryIntegratesEightEightMotionSnapsAndConvertsInPlace() {
        BlasterBadnikInstance falling = BlasterBadnikInstance.falling(spawn(0, 0), true);
        int startX = falling.getX(), startY = falling.getY();
        falling.update(0, null);
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(7)))
                    .thenReturn(new TerrainCheckResult(-3, (byte) 0, 1));
            for (int frame = 1; frame <= 16; frame++) falling.update(frame, null);
        }
        assertEquals("PATROL", falling.stateName());
        assertEquals(startX + 0x20, falling.getX());
        assertEquals(startY - 0x14, falling.getY());
        assertEquals(0x80, falling.xVelocityRaw());
        assertEquals(0, falling.yVelocityRaw());
    }

    @Test void rawAnimationTablesAndProjectileContractsArePinned() {
        assertArrayEquals(new int[]{0,0x17,1,2,0xFC}, BlasterBadnikInstance.patrolAnimation());
        assertArrayEquals(new int[]{0,4,4,5,0xF4}, BlasterAttackEffectObjectInstance.animationScript());
        assertArrayEquals(new int[]{1,5,6,0xFC}, BlasterProjectileObjectInstance.primaryAnimation());
        assertArrayEquals(new int[]{2,7,8,9,0xA,0xFC}, BlasterProjectileObjectInstance.secondaryAnimation());
    }

    @Test void projectileDeleteTouchXyUsesExactUnsignedCoarseBoundaries() {
        assertFalse(BlasterProjectileObjectInstance.outsideDeleteBounds(0x1000, 0x700, 0xF00, 0x700));
        assertFalse(BlasterProjectileObjectInstance.outsideDeleteBounds(0x1000, 0x880, 0xF00, 0x700));
        assertTrue(BlasterProjectileObjectInstance.outsideDeleteBounds(0x1000, 0x881, 0xF00, 0x700));
        assertTrue(BlasterProjectileObjectInstance.outsideDeleteBounds(0x1000, 0x67F, 0xF00, 0x700));
        assertTrue(BlasterProjectileObjectInstance.outsideDeleteBounds(0x1300, 0x700, 0xF00, 0x700));
    }

    @Test void p1WinsNativeTieAndAdditionalSidekicksDoNotChangeNativeAim() {
        PlayableEntity p1 = player(0x1040, 0x800);
        PlayableEntity p2 = player(0x0FC0, 0x800);
        PlayableEntity extra1 = player(0x1001, 0x800);
        PlayableEntity extra2 = player(0x1002, 0x800);
        var blaster = new BlasterBadnikInstance(spawn(0x20, 0));
        blaster.setServices(new Services(null, p1, List.of(p2, extra1, extra2), null));
        blaster.update(0, p1);
        blaster.update(1, p1);
        blaster.update(2, p1);
        assertSame(p1, blaster.selectedTarget());
        assertEquals("ATTACK_WAIT", blaster.stateName());
    }

    @Test void attackWaitAttemptsEffectThenPrimaryExactlyOnceAndProjectilesFallThroughOnFirstTick() {
        ObjectManager manager = mock(ObjectManager.class);
        PlayableEntity p1 = player(0x1040, 0x800);
        var blaster = new BlasterBadnikInstance(spawn(0x20, 0));
        blaster.setServices(new Services(manager, p1, List.of(), null));
        blaster.update(0, p1);
        blaster.update(1, p1);
        blaster.update(2, p1);
        for (int frame = 3; frame <= 19; frame++) blaster.update(frame, p1);
        ArgumentCaptor<AbstractObjectInstance> capture = ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(manager, times(2)).addDynamicObjectAfterCurrent(capture.capture());
        assertInstanceOf(BlasterAttackEffectObjectInstance.class, capture.getAllValues().get(0));
        BlasterProjectileObjectInstance primary = assertInstanceOf(
                BlasterProjectileObjectInstance.class, capture.getAllValues().get(1));
        primary.update(19, null);
        assertEquals(6, primary.mappingFrame());
    }

    @Test void secondarySpawnsAtRawOffsetSixAfterFrameZeroLongDelayLoads() {
        ObjectManager manager = mock(ObjectManager.class);
        PlayableEntity p1 = player(0x1040, 0x800);
        var blaster = new BlasterBadnikInstance(spawn(0x20, 0));
        blaster.setServices(new Services(manager, p1, List.of(), null));
        for (int frame = 0; frame <= 19; frame++) blaster.update(frame, p1);
        verify(manager, times(2)).addDynamicObjectAfterCurrent(any());

        blaster.update(20, p1);
        blaster.update(21, p1);
        blaster.update(22, p1);
        for (int frame = 23; frame <= 27; frame++) blaster.update(frame, p1);
        verify(manager, times(2)).addDynamicObjectAfterCurrent(any());
        blaster.update(28, p1);
        verify(manager, times(3)).addDynamicObjectAfterCurrent(any());
    }

    @Test void waitOffscreenAndRestoreDispatchRenderNothingUntilInitializationFrame() {
        PlayableEntity p1 = player(0x1400, 0x900);
        Services services = new Services(null, p1, List.of(), null);
        var blaster = new BlasterBadnikInstance(spawn(0x20, 0));
        blaster.setServices(services);
        blaster.appendRenderCommands(new java.util.ArrayList<>());
        verifyNoInteractions(services.renderManager);
        blaster.update(0, p1);
        blaster.appendRenderCommands(new java.util.ArrayList<>());
        verifyNoInteractions(services.renderManager);
        blaster.update(1, p1);
        blaster.appendRenderCommands(new java.util.ArrayList<>());
        verify(services.renderManager).getRenderer(anyString());
    }

    @Test void attackEffectSetupFrameIsHiddenThenRawAnimationSkipsItsSetupMapping() {
        var parent = new BlasterBadnikInstance(spawn(0, 0));
        var effect = new BlasterAttackEffectObjectInstance(spawn(0, 0), parent);
        effect.update(0, null);
        assertFalse(effect.isDestroyed());
        assertEquals(4, effect.mappingFrame());
        effect.update(1, null);
        assertEquals(4, effect.mappingFrame());
        effect.update(2, null);
        assertEquals(5, effect.mappingFrame());
        effect.update(3, null);
        assertTrue(effect.isDestroyed(), "$F4 terminates on the third active raw-animation update");
    }

    @Test void magneticInterruptSavesAndRestoresInProgressRoutineAndVelocity() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE, events);
        PlayableEntity p1 = player(0x1040, 0x800);
        var blaster = new BlasterBadnikInstance(spawn(0x20, 2));
        blaster.setServices(new Services(null, p1, List.of(), state));
        blaster.update(0, p1);
        blaster.update(1, p1);
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
        blaster.update(2, p1);
        assertEquals("MAGNET_RISE", blaster.stateName());
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkCeilingDist(anyInt(), anyInt(), eq(0xE)))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
            blaster.update(3, p1);
            assertEquals("MAGNET_WAIT", blaster.stateName());
            events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 1);
            blaster.update(4, p1);
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0xE)))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
            blaster.update(5, p1);
            assertEquals("PATROL", blaster.stateName());
            assertEquals(0x80, blaster.xVelocityRaw());
        }
    }

    @Test void magneticInterruptRestoresEveryOrdinaryRoutineWithoutAdvancingItsPhase() {
        for (String expectedState : List.of("PATROL", "WAIT_TURN", "ATTACK_WAIT", "ATTACK")) {
            Sonic3kFBZEvents events = new Sonic3kFBZEvents();
            events.init(0);
            FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                    0, PlayerCharacter.SONIC_ALONE, events);
            PlayableEntity target = player(0x1040, 0x800);
            BlasterBadnikInstance blaster = prepareRoutine(expectedState, target, runtime);
            String before = blaster.magneticResumeSignature();

            events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
            blaster.update(100, target);
            assertEquals("MAGNET_RISE", blaster.stateName(), expectedState);
            try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
                terrain.when(() -> ObjectTerrainUtils.checkCeilingDist(anyInt(), anyInt(), eq(0xE)))
                        .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
                terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0xE)))
                        .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
                blaster.update(101, target);
                events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 1);
                blaster.update(102, target);
                blaster.update(103, target);
            }
            assertEquals(before, blaster.magneticResumeSignature(), expectedState);
        }
    }

    @Test void magneticWaitScalarStateSurvivesCompactRewindAndResumesExactly() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE, events);
        PlayableEntity target = player(0x1040, 0x800);
        BlasterBadnikInstance original = prepareRoutine("ATTACK", target, runtime);
        String before = original.magneticResumeSignature();
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
        original.update(100, target);
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkCeilingDist(anyInt(), anyInt(), eq(0xE)))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
            original.update(101, target);
        }
        var compact = GenericFieldCapturer.captureObjectSubclassScalarsCompact(original).orElseThrow();
        BlasterBadnikInstance restored = new BlasterBadnikInstance(spawn(0x20, 2));
        restored.setServices(new Services(null, target, List.of(), runtime));
        GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored, compact);
        assertEquals("MAGNET_WAIT", restored.stateName());
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 1);
        restored.update(102, target);
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0xE)))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
            restored.update(103, target);
        }
        assertEquals(before, restored.magneticResumeSignature());
    }

    private static BlasterBadnikInstance prepareRoutine(
            String state, PlayableEntity target, FbzZoneRuntimeState runtime) {
        PlayableEntity queryTarget = state.equals("WAIT_TURN") || state.equals("PATROL")
                ? player(0x1400, 0x900) : target;
        var blaster = new BlasterBadnikInstance(spawn(0x20, 2));
        blaster.setServices(new Services(null, queryTarget, List.of(), runtime));
        blaster.update(0, queryTarget);
        blaster.update(1, queryTarget);
        if (state.equals("WAIT_TURN") || state.equals("PATROL")) {
            try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
                terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0)))
                        .thenReturn(state.equals("PATROL")
                                ? new TerrainCheckResult(0, (byte) 0, 1)
                                : TerrainCheckResult.noCollision());
                blaster.update(2, queryTarget);
            }
        } else {
            blaster.update(2, queryTarget);
            if (state.equals("ATTACK")) {
                for (int frame = 3; frame <= 20; frame++) blaster.update(frame, queryTarget);
            }
        }
        assertEquals(state, blaster.stateName());
        return blaster;
    }

    private static PlayableEntity player(int x, int y) {
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) x);
        when(player.getCentreY()).thenReturn((short) y);
        return player;
    }

    private static final class Services extends StubObjectServices {
        private final ObjectManager manager;
        private final ObjectPlayerQuery query;
        private final FbzZoneRuntimeState state;
        private final Camera camera = mock(Camera.class);
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        Services(ObjectManager manager, PlayableEntity main, List<PlayableEntity> sidekicks, FbzZoneRuntimeState state) {
            this.manager = manager;
            this.query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
            this.state = state;
            when(camera.getX()).thenReturn((short) 0x0F00);
        }
        @Override public ObjectManager objectManager() { return manager; }
        @Override public ObjectPlayerQuery playerQuery() { return query; }
        @Override public FbzZoneRuntimeState zoneRuntimeState() { return state; }
        @Override public Camera camera() { return camera; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
    }

    private static ObjectSpawn spawn(int subtype, int flags) {
        return new ObjectSpawn(0x1000, 0x800, 0xA8, subtype, flags, true, 3);
    }
}
