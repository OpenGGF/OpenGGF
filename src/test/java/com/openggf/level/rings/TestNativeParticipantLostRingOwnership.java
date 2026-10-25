package com.openggf.level.rings;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSession;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.NativeCompetitionRuntimeFixture;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestNativeParticipantLostRingOwnership {
    private NativeCompetitionRuntimeFixture world;
    private ObjectManager objects;
    private RingManager rings;
    private com.openggf.audio.AudioManager audio;

    @BeforeEach void setUp() {
        GameServices.graphics().initHeadless();
        world = new NativeCompetitionRuntimeFixture();
        var services = new StubObjectServices() {
            @Override public WorldSession worldSession() { return world.world; }
            @Override public LevelManager levelManager() { return world.level; }
            @Override public com.openggf.game.LevelState levelGamestate() { return world.ordinary; }
            @Override public com.openggf.camera.Camera camera() { return world.cameras[0]; }
            @Override public List<PlayableEntity> sidekicks() { return List.of(world.p2); }
            @Override public ObjectManager objectManager() { return objects; }
            @Override public RingManager ringManager() { return rings; }
            @Override public com.openggf.sprites.managers.SpriteManager spriteManager() { return world.sprites; }
        };
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_2);
        objects = new ObjectManager(List.of(), registry, 0, null, null, null, world.cameras[0], services);
        objects.reset(0);
        when(world.level.getObjectManager()).thenReturn(objects);
        var frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        var sheet = new RingSpriteSheet(new Pattern[]{new Pattern()}, List.of(frame, frame, frame), 1, 1, 1, 2);
        audio = mock(com.openggf.audio.AudioManager.class);
        rings = new RingManager(List.of(), sheet, world.level, null, audio);
        when(world.level.getRingManager()).thenReturn(rings);
        resetBanks();
    }
    private void resetBanks() {
        world.ordinary.setRings(63);
        for (int i = 0; i < 2; i++) { world.banks[i].setRings(11 + i * 11); world.banks[i].setRingExtraLifeFlags(6); }
    }

    @Test void immediateLossClearsOnlyTheDamagedHumanIncludingExtraLifeFlags() {
        for (int slot = 0; slot < 2; slot++) {
            resetBanks();
            rings.spawnLostRings(world.player(slot), 3, 0);
            assertEquals(0, world.banks[slot].getRings());
            assertEquals(0, world.banks[slot].getRingExtraLifeFlags());
            assertEquals(11 + (1 - slot) * 11, world.banks[1 - slot].getRings());
            assertEquals(6, world.banks[1 - slot].getRingExtraLifeFlags());
            assertEquals(63, world.ordinary.getRings());
        }
    }

    @Test void noAllocatedOwnerLeavesBothNativeBanksUntouched() {
        int[] previousAnimation = {44, 0x1234, 2};
        rings.getSpillAnimationState().restore(previousAnimation);
        objects.reserveAllButNFreeSlots(0);
        rings.spawnLostRings(world.p2, 22, 0);
        assertTrue(objects.activeObjectsOfType(LostRingObjectInstance.class).isEmpty());
        assertEquals(11, world.banks[0].getRings()); assertEquals(22, world.banks[1].getRings());
        assertEquals(6, world.banks[0].getRingExtraLifeFlags()); assertEquals(6, world.banks[1].getRingExtraLifeFlags());
        assertEquals(63, world.ordinary.getRings());
        assertArrayEquals(previousAnimation, rings.getSpillAnimationState().snapshot());
        verifyNoInteractions(audio);
    }

    @Test void noAllocatedOwnerDoesNotRestartAnExistingSpill() {
        rings.spawnLostRings(world.p1, 1, 0);
        var existing = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        int[] previousAnimation = {44, 0x1234, 2};
        rings.getSpillAnimationState().restore(previousAnimation);
        clearInvocations(audio);
        objects.reserveAllButNFreeSlots(0);
        rings.spawnLostRings(world.p2, 22, 1);
        assertEquals(List.of(existing), objects.activeObjectsOfType(LostRingObjectInstance.class));
        assertArrayEquals(previousAnimation, rings.getSpillAnimationState().snapshot());
        assertEquals(22, world.banks[1].getRings());
        verifyNoInteractions(audio);
    }

    @Test void deferredOwnerClearsP2OnFirstExecutionWithP1AsTheObjectPassArgument() {
        rings.spawnLostRingsWithInitialObjectStep(world.p2, 3, 0, 0x100, 0x100, new int[0], false, true);
        var owner = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertEquals(22, world.banks[1].getRings());
        owner.update(1, world.p1);
        assertEquals(0, world.banks[1].getRings()); assertEquals(0, world.banks[1].getRingExtraLifeFlags());
        assertEquals(11, world.banks[0].getRings());
        world.banks[1].addRings(4);
        owner.update(2, world.p1);
        assertEquals(4, world.banks[1].getRings());
    }

    @Test void pendingLossOwnerSurvivesActualRingAndActorRemovalAndGenericRecreation() {
        rings.spawnLostRingsWithInitialObjectStep(world.p2, 3, 0, 0x100, 0x100, new int[0], false, true);
        var original = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        var snapshot = objects.rewindSnapshottable().capture();
        original.update(1, world.p1);
        objects.reset(0);
        var previousP2 = world.p2;
        world.replacePlayers();
        assertNotSame(previousP2, world.p2);
        assertFalse(world.sprites.getAllSprites().contains(previousP2));
        assertSame(world.p2, world.sprites.getSprite("tails"));
        resetBanks();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        objects.rewindSnapshottable().restore(snapshot);
        var recreated = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertNotSame(original, recreated);
        recreated.update(1, world.p1);
        assertEquals(0, world.banks[1].getRings()); assertEquals(11, world.banks[0].getRings());
        assertEquals(63, world.ordinary.getRings());
    }

    @Test void nativeCollectorReceivesOtherPlayersSpillAtTheObjectAwardBoundaryOnce() {
        for (int slot = 0; slot < 2; slot++) {
            objects.reset(0);
            resetBanks();
            rings.spawnLostRings(world.player(1 - slot), 1, 0);
            var ring = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
            ring.markCollected(1, world.player(slot));
            ring.markCollected(1, world.player(1 - slot));
            assertEquals(11 + slot * 11, world.banks[slot].getRings());
            ring.update(2, world.player(1 - slot));
            ring.update(3, world.player(1 - slot));
            assertEquals(12 + slot * 11, world.banks[slot].getRings());
            assertEquals(0, world.banks[1 - slot].getRings());
            assertEquals(63, world.ordinary.getRings());
        }
    }

    @Test void latchedCollectorSurvivesActorRemovalAndObjectRecreationBeforeAward() {
        var ring = LostRingObjectInstance.spawn(0x100, 0x100, 0, 0, 0, 0xFF, rings.getSpillAnimationState());
        int slot = objects.allocateDynamicSlot(); objects.spawnLostRingObjectAtSlot(ring, slot);
        ring.markCollected(1, world.p2);
        var snapshot = objects.rewindSnapshottable().capture();
        objects.reset(0); world.replacePlayers();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        objects.rewindSnapshottable().restore(snapshot);
        var restored = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertNotSame(ring, restored);
        restored.update(2, world.p1);
        assertEquals(23, world.banks[1].getRings()); assertEquals(11, world.banks[0].getRings());
        restored.update(3, world.p1); assertEquals(23, world.banks[1].getRings());
    }

    @Test void ordinaryCpuSpillAndCollectionRetainTheSharedOrdinaryBank() {
        var cpu = world.ordinaryCpu();
        rings.spawnLostRings(cpu, 3, 0);
        assertEquals(0, world.ordinary.getRings());
        assertEquals(11, world.banks[0].getRings()); assertEquals(22, world.banks[1].getRings());
        var ring = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        ring.markCollected(1, cpu); ring.update(2, world.p1);
        assertEquals(1, world.ordinary.getRings());
        assertEquals(11, world.banks[0].getRings()); assertEquals(22, world.banks[1].getRings());
    }
}
