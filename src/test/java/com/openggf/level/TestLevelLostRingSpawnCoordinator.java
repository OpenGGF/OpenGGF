package com.openggf.level;

import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.tests.MutatorPhysicsWorld;
import java.util.concurrent.atomic.AtomicReference;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLevelLostRingSpawnCoordinator {
    private LevelManager manager;
    private RingManager rings;
    private ObjectManager objects;
    private LevelLostRingSpawnCoordinator coordinator;
    private AbstractPlayableSprite player;

    private AtomicReference<GameplayMutatorPolicy> bindPolicy(int percent, int cap, ObjectSlotLayout layout) {
        var policy = new AtomicReference<>(new GameplayMutatorPolicy(percent, cap, 100, 0xC00, 100, false));
        var services = mock(ObjectServices.class);
        var world = MutatorPhysicsWorld.create(policy::get);
        when(services.worldSession()).thenReturn(world);
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(layout);
        objects = new ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        manager.objectManager = objects;
        return policy;
    }

    @ParameterizedTest
    @CsvSource({"0,10,0,0", "1,10,0,1", "5,10,0,1", "32,10,0,3", "100,10,0,3",
            "100,50,0,16", "100,50,2,2", "5,100,1,1", "32,100,32,32", "100,100,0,100"})
    void resolvesStockScatterBasisBeforeTheProductionSpawn(int held, int percent, int cap, int expected) {
        bindPolicy(percent, cap, ObjectSlotLayout.SONIC_3K);
        when(player.getRingCount()).thenReturn(held);
        coordinator.spawnImmediately(player, 50);
        if (held == 0) verify(rings, never()).spawnLostRings(any(), anyInt(), anyInt());
        else verify(rings).spawnLostRings(player, expected, 50);
        verify(player, never()).setRingCount(anyInt());
    }

    @ParameterizedTest
    @CsvSource({"SONIC_1", "SONIC_2", "SONIC_3K"})
    void pendingCountSurvivesEditsAndRecreatedOwnerRestoreAcrossNativeAllocators(String game) {
        ObjectSlotLayout layout = switch (game) {
            case "SONIC_1" -> ObjectSlotLayout.SONIC_1;
            case "SONIC_3K" -> ObjectSlotLayout.SONIC_3K;
            default -> ObjectSlotLayout.SONIC_2;
        };
        var policy = bindPolicy(50, 0, layout);
        when(player.getRingCount()).thenReturn(100);
        coordinator.queue(player, 40, true);
        var saved = coordinator.capture();
        int reservations = objects.getAllocatedSlotCount();
        if (layout == ObjectSlotLayout.SONIC_3K) assertEquals(16, reservations);
        policy.set(new GameplayMutatorPolicy(10, 2, 100, 0xC00, 100, false));
        var recreated = mock(AbstractPlayableSprite.class);
        when(recreated.getCode()).thenReturn("p1");
        when(recreated.getRingCount()).thenReturn(100);
        manager.spriteManager.addSprite(recreated);
        coordinator.restore(saved);
        manager.frameCounter = 41;
        coordinator.processPending();
        verify(rings).spawnLostRingsWithInitialObjectStep(eq(recreated), eq(16), eq(41),
                eq(0x120), eq(0x90), any(), anyBoolean(), eq(true));
        verify(player, never()).setRingCount(anyInt());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void noRingsDeniesSpillBeforeAnyReservationAndClearsFullInventory(boolean deferred, boolean useLevelState) {
        var services = mock(ObjectServices.class);
        var denial = new com.openggf.game.mutators.LevelMutatorPolicy(java.util.Set.of(), true, false, false, false);
        var world = MutatorPhysicsWorld.create(() -> GameplayMutatorPolicy.STOCK, denial);
        when(services.worldSession()).thenReturn(world);
        when(services.bonusStageProviderOrNull()).thenReturn(com.openggf.game.NoOpBonusStageProvider.INSTANCE);
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
        objects = new ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        manager.objectManager = objects;
        when(player.getRingCount()).thenReturn(100);
        var state = mock(com.openggf.game.LevelState.class);
        if (useLevelState) when(manager.getLevelGamestate()).thenReturn(state);
        if (deferred) coordinator.queue(player, 40, true); else coordinator.spawnImmediately(player, 40);
        assertEquals(0, objects.getAllocatedSlotCount());
        Object[] pending = coordinator.capture().pending();
        assertEquals(0, pending.length);
        org.mockito.Mockito.verifyNoInteractions(rings);
        if (useLevelState) verify(state).resetRingsForLoss(); else verify(player).setRingCount(0);
    }

    @BeforeEach
    void setUp() {
        manager = mock(LevelManager.class);
        rings = mock(RingManager.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
        objects = new ObjectManager(List.of(), registry, 0, null, null);
        manager.ringManager = rings;
        manager.objectManager = objects;
        player = mock(AbstractPlayableSprite.class);
        when(player.getCode()).thenReturn("p1");
        when(player.getRingCount()).thenReturn(3);
        when(player.getCentreX()).thenReturn((short) 0x120);
        when(player.getCentreY()).thenReturn((short) 0x90);
        manager.spriteManager = new com.openggf.sprites.managers.SpriteManager();
        manager.spriteManager.addSprite(player);
        coordinator = new LevelLostRingSpawnCoordinator(manager);
    }

    @Test
    void deferredQueueWaitsUntilTheFollowingFrameAndUsesItsReservedSlots() {
        coordinator.queue(player, 40, true);
        assertEquals(3, objects.getAllocatedSlotCount());

        manager.frameCounter = 40;
        coordinator.processPending();
        verify(rings, never()).spawnLostRingsWithInitialObjectStep(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), anyBoolean(), anyBoolean());

        manager.frameCounter = 41;
        coordinator.processPending();
        verify(rings).spawnLostRingsWithInitialObjectStep(
                player, 3, 41, 0x120, 0x90, new int[] {4, 5, 6}, true, true);
    }

    @Test
    void cancelledDeferredQueueReleasesEveryReservation() {
        coordinator.queue(player, 40, false);
        assertEquals(3, objects.getAllocatedSlotCount());
        when(player.getRingCount()).thenReturn(0);

        manager.frameCounter = 41;
        coordinator.processPending();

        assertEquals(0, objects.getAllocatedSlotCount());
        verify(rings, never()).spawnLostRingsWithInitialObjectStep(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void resetReleasesPendingReservations() {
        coordinator.queue(player, 40, false);
        assertEquals(3, objects.getAllocatedSlotCount());

        coordinator.reset();

        assertEquals(0, objects.getAllocatedSlotCount());
    }

    @Test
    void rewindRestoreUsesTheCurrentPlayerForTheCapturedPendingQueue() {
        coordinator.queue(player, 40, true);
        var registry = new com.openggf.game.rewind.RewindRegistry();
        when(manager.createLostRingSpawnRewindAdapterInternal()).thenReturn(coordinator);
        LevelLostRingSpawnRewindAccess.register(manager, registry);
        registry.register(objects.rewindSnapshottable());
        var snapshot = registry.capture();
        coordinator.reset();

        AbstractPlayableSprite restoredPlayer = mock(AbstractPlayableSprite.class);
        when(restoredPlayer.getCode()).thenReturn("p1");
        when(restoredPlayer.getRingCount()).thenReturn(3);
        manager.spriteManager.addSprite(restoredPlayer);
        // Production restores the player graph before the queue and object slot occupancy.
        registry.restore(snapshot);
        assertEquals(3, objects.getAllocatedSlotCount());

        manager.frameCounter = 41;
        coordinator.processPending();

        verify(rings).spawnLostRingsWithInitialObjectStep(
                restoredPlayer, 3, 41, 0x120, 0x90, new int[] {4, 5, 6}, true, true);
    }

    @Test
    void exhaustedSlotsDoNotBecomeQueueReservationsOrGetReleasedByReset() {
        while (objects.allocateDynamicSlot() >= 0) {
            // Fill every available slot with unrelated owners.
        }
        int occupied = objects.getAllocatedSlotCount();
        coordinator.queue(player, 40, false);
        manager.frameCounter = 41;
        coordinator.processPending();
        verify(rings).spawnLostRingsWithInitialObjectStep(
                player, 3, 41, 0x120, 0x90, new int[0], false, false);
        coordinator.reset();
        assertEquals(occupied, objects.getAllocatedSlotCount());
    }

    @Test
    void activeRingfallWithExhaustedSlotsKeepsLatchedCountWithoutClaimingOrReleasingForeignSlots() {
        bindPolicy(50, 0, ObjectSlotLayout.SONIC_3K);
        when(player.getRingCount()).thenReturn(100);
        while (objects.allocateDynamicSlot() >= 0) { }
        int occupied = objects.getAllocatedSlotCount();
        coordinator.queue(player, 40, true);
        manager.frameCounter = 41;
        coordinator.processPending();
        verify(rings).spawnLostRingsWithInitialObjectStep(player, 16, 41, 0x120, 0x90, new int[0], false, true);
        coordinator.reset();
        assertEquals(occupied, objects.getAllocatedSlotCount());
    }

    @Test
    void partialReservationSurvivesRegistryRestoreWithoutAllocatingBeyondItsBoundary() {
        int previousSlot = -1;
        int lastSlot = -1;
        int slot;
        while ((slot = objects.allocateDynamicSlot()) >= 0) {
            previousSlot = lastSlot;
            lastSlot = slot;
        }
        int capacity = objects.getAllocatedSlotCount();
        objects.releaseDynamicSlot(previousSlot);
        objects.releaseDynamicSlot(lastSlot);
        coordinator.queue(player, 40, true);
        assertEquals(capacity, objects.getAllocatedSlotCount());

        var registry = new com.openggf.game.rewind.RewindRegistry();
        when(manager.createLostRingSpawnRewindAdapterInternal()).thenReturn(coordinator);
        LevelLostRingSpawnRewindAccess.register(manager, registry);
        registry.register(objects.rewindSnapshottable());
        var snapshot = registry.capture();
        coordinator.reset();
        assertEquals(capacity - 2, objects.getAllocatedSlotCount());
        registry.restore(snapshot);
        assertEquals(2, objects.getAllocatedSlotCount(),
                "ObjectManager only persists object-backed occupancy; the callback restores the two queue slots");

        manager.frameCounter = 41;
        coordinator.processPending();
        verify(rings).spawnLostRingsWithInitialObjectStep(
                player, 3, 41, 0x120, 0x90, new int[] {previousSlot, lastSlot}, true, true);
    }

}
