package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.*;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fixed Breathing_bubbles writes and physical collision pointers, independently
 * of the native competition domain implementation. loc_36612 writes slot 94
 * unconditionally (sonic3k.asm:73550-73570; sonic3k.constants.asm:310-330).
 */
class TestNativeCompetitionFixedSstAccess {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private static class Actor extends AbstractObjectInstance implements TouchResponseProvider, RewindRecreatable {
        int ticks;
        int unloads;
        int constructionSlot;
        boolean publishes = true;
        Actor(ObjectSpawn spawn) {
            super(spawn, "fixed-native-test");
            constructionSlot = getSlotIndex();
        }
        ObjectServices injectedServices() { return services(); }
        @Override public void update(int frame, PlayableEntity player) { ticks++; }
        @Override public void onUnload() { unloads++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return publishes; }
        @Override public int getCollisionFlags() { return 0x40; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Actor(context.spawn());
        }
    }

    /** Typed test projection only; literal word seeding below is fixture setup, not a native birth/clear claim. */
    private static final class WordActor extends Actor implements NativeSstWord2CSource {
        int word;
        int reads;
        WordActor(ObjectSpawn spawn, int word) { super(spawn); this.word = word; }
        @Override public int nativeWord2C() { reads++; return word; }
        @Override public void writeNativeWord2C(int value) { word = value & 0xFFFF; }
    }

    private static final class Wheel extends Actor {
        boolean replaced;
        Wheel(ObjectSpawn spawn) { super(spawn); }
        @Override public void update(int frame, PlayableEntity player) {
            super.update(frame, player);
            if (!replaced) {
                replaced = true;
                ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(
                        services().objectManager(), getSpawn().subtype(), () -> new Actor(spawn(0x22, 0)));
            }
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Wheel(context.spawn());
        }
    }

    private static final class Fixture {
        final Camera camera = new Camera(SonicConfigurationService.getInstance());
        final ObjectManager manager;
        final ObjectServices services;
        Fixture() { this(true, List.of()); }
        Fixture(boolean explicitRoster, List<ObjectSpawn> spawns) {
            GameModule module = mock(GameModule.class);
            WorldSessionPolicyState policies = mock(WorldSessionPolicyState.class);
            if (explicitRoster) when(policies.getService(NativeCompetitionRoster.class))
                    .thenReturn(mock(NativeCompetitionRoster.class));
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policies);
            WorldSession world = new WorldSession(module);
            ObjectManager[] holder = new ObjectManager[1];
            services = new StubObjectServices() {
                @Override public Camera camera() { return camera; }
                @Override public WorldSession worldSession() { return world; }
                @Override public ObjectManager objectManager() { return holder[0]; }
            };
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new Actor(spawn); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "fixed-native-test"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            manager = new ObjectManager(spawns, registry, -1, null, null, null, camera, services);
            holder[0] = manager;
            manager.enableExecThenLoadPlacement();
            manager.reset(0);
        }
        Actor at(int slot, int id) {
            Actor actor = new Actor(spawn(id, 0));
            manager.addDynamicObjectAtSlot(actor, slot);
            return actor;
        }
        Actor replace(int slot, int id) {
            return ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(manager, slot,
                    () -> new Actor(spawn(id, 0)));
        }
        Actor owner(int slot) { return (Actor) NativeSstSlotAccess.currentOwner(manager, slot); }
        void step() { manager.update(0, null, List.of(), 0, false); }
        void initialPass() {
            try (InitialObjectDispatchScope scope = manager.beginInitialProcessSprites(0, null, List.of())) {
                manager.freezeInitialCollisionResponseReadView();
                manager.resetInitialCollisionResponseBuild();
                manager.loadInitialDynamicSlots(scope);
                manager.processInitialDynamicSlots(scope);
                manager.finishInitialProcessSprites(scope);
            }
        }
        void publish(ObjectInstance... entries) {
            manager.initialCollisionResponseList().captureCompletedBuild(Arrays.asList(entries));
            manager.snapshotTouchResponseState(true); // prepareTouchResponseSnapshots' object-owned step
        }
        List<ObjectInstance> previous() {
            return ObjectManagerInternalAccess.previousCollisionResponseObjects(manager);
        }
        RewindRegistry worldSnapshots() {
            RewindRegistry snapshots = new RewindRegistry();
            snapshots.registerOrRefresh(manager.rewindSnapshottable());
            snapshots.registerOrRefresh(ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(manager));
            return snapshots;
        }
        List<String> graph() {
            return manager.getActiveObjects().stream().map(o -> (Actor) o)
                    .sorted(Comparator.comparingInt(Actor::getSlotIndex))
                    .map(a -> a.getSlotIndex() + ":" + a.getSpawn().objectId() + ":" + a.ticks).toList();
        }
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x100, 0x100, id, subtype, 0, false, 0x100);
    }
    private static void assertSameSnapshot(ObjectManagerSnapshot expected, ObjectManagerSnapshot actual) {
        assertTrue(RewindSnapshotDiff.keyEquals("objects", expected, actual),
                () -> RewindSnapshotDiff.diffKey("objects", expected, actual).toString());
    }

    @Test void dynamicWheelCreatesSlot94AndReplacementExecutesExactlyOnceLaterInSamePass() {
        Fixture f = new Fixture();
        Wheel wheel = new Wheel(spawn(0x11, 94));
        f.manager.addDynamicObjectAtSlot(wheel, 4);
        f.step();
        Actor bubble = f.owner(94);
        assertNotNull(bubble);
        assertEquals(94, bubble.constructionSlot);
        assertSame(f.services, bubble.injectedServices());
        assertEquals(1, wheel.ticks);
        assertEquals(1, bubble.ticks, "The future native SST is reached in this same physical walk");
        assertEquals(1, f.manager.getActiveObjectSlotCount(), "Fixed slot 94 consumes no dynamic capacity");
        f.step();
        assertSame(bubble, f.owner(94));
        assertEquals(2, bubble.ticks);
    }

    @Test void occupiedFixedSlotIsUnconditionallyReplacedWithoutUnloadOrRespawnMutation() {
        ObjectSpawn tracked = new ObjectSpawn(0x100, 0x100, 1, 0, 0, true, 0x8100, 0);
        Fixture f = new Fixture(true, List.of(tracked));
        f.manager.setSpawnStateBit(tracked, 2);
        Actor old = f.at(94, 0x31);
        old.setRespawnStateIndex(0);
        f.publish(old, old);
        var before = f.manager.rewindSnapshottable().capture();
        Actor next = f.replace(94, 0x32);
        var after = f.manager.rewindSnapshottable().capture();
        assertSame(next, f.owner(94));
        assertFalse(f.manager.getActiveObjects().contains(old));
        assertEquals(0, old.unloads, "Registration overwrite has no native delete/onUnload tail");
        assertArrayEquals(before.placement().objState(), after.placement().objState());
        assertEquals(List.of(next, next), f.previous());
        assertEquals(before.collisionResponseState().previousCollisionObjectIds(),
                after.collisionResponseState().previousCollisionObjectIds(), "The same physical pointer identity survives");
        f.step();
        assertEquals(0, old.ticks);
        assertEquals(1, next.ticks);
        assertEquals(0, old.unloads);
    }

    @Test void fixedReplacementImportsKnownWordAndRetainsItsPhysicalIdentity() {
        Fixture f = new Fixture();
        // Explicit literal fixture state: this does not assert that native birth clears SST $2C.
        NativeSstSlotAccess.writeWord2C(f.manager, 94, 0x1357);
        WordActor old = new WordActor(spawn(0x35, 0), 0x1357);
        f.manager.addDynamicObjectAtSlot(old, 94);
        f.publish(old, old);
        var identity = f.manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(entry -> entry.slotIndex() == 94).findFirst().orElseThrow().objectId();
        int allocated = f.manager.getAllocatedSlotCount();
        old.reads = 0;
        WordActor next = ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> {
            assertSame(old, f.owner(94), "Factory sees the registered old owner until construction succeeds");
            old.word = 0xBEEF;
            // Deliberately copy the typed fixture field without refreshing the word port's old cache.
            return new WordActor(spawn(0x36, 0), old.word);
        });
        assertEquals(1, old.reads, "Successful construction samples the old word before retiring its owner");
        assertSame(next, f.owner(94));
        assertEquals(0xBEEF, next.word);
        assertEquals(0xBEEF, NativeSstSlotAccess.readWord2C(f.manager, 94));
        assertEquals(List.of(next, next), f.previous());
        assertEquals(0, old.unloads);
        assertEquals(allocated, f.manager.getAllocatedSlotCount());
        var replacementIdentity = f.manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(entry -> entry.slotIndex() == 94).findFirst().orElseThrow().objectId();
        assertEquals(identity, replacementIdentity, "A fixed-address replacement retains its physical rewind identity");
    }

    @Test void refusedFixedFactoriesDoNotSampleOrRetireTheWordOwner() {
        Fixture f = new Fixture();
        // A known literal test cell, independent of any unproven native initialization or alias.
        NativeSstSlotAccess.writeWord2C(f.manager, 94, 0x2468);
        WordActor old = new WordActor(spawn(0x37, 0), 0x2468);
        f.manager.addDynamicObjectAtSlot(old, 94);
        f.publish(old, old);
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(f.manager);
        Object addresses = pointers.capture();
        var words = f.manager.nativeSstWord2C().capture();
        old.reads = 0;
        RuntimeException failure = new IllegalStateException("fixed factory refusal");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> { throw failure; })));
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> null));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> old));
        assertEquals(0, old.reads, "Throw, null and already-registered results cannot trigger word preservation");
        assertSame(old, f.owner(94));
        assertEquals(List.of(old, old), f.previous());
        assertEquals(0, old.unloads);
        assertTrue(RewindSnapshotDiff.keyEquals(pointers.key(), addresses, pointers.capture()));
        assertEquals(words, f.manager.nativeSstWord2C().capture());
        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
        assertNull(AbstractObjectInstance.PRE_ALLOCATED_SLOT.get());
    }

    @Test void previouslyVisitedSlotWaitsUntilNextPass() {
        Fixture f = new Fixture();
        Actor old = f.at(94, 0x41);
        Wheel lateWriter = new Wheel(spawn(0x11, 94));
        f.manager.addDynamicObjectAtSlot(lateWriter, 95);
        f.step();
        Actor next = f.owner(94);
        assertNotSame(old, next);
        assertEquals(1, old.ticks);
        assertEquals(0, next.ticks, "Writing an earlier SST does not execute it immediately");
        f.step();
        assertEquals(1, next.ticks);
        assertEquals(1, old.ticks, "Retired Java owner never executes again");
        assertEquals(0, old.unloads);
    }

    @Test void replacementAtCurrentCursorWaitsWithoutAnExtraExecution() {
        Fixture f = new Fixture();
        Wheel selfWriter = new Wheel(spawn(0x11, 94));
        f.manager.addDynamicObjectAtSlot(selfWriter, 94);
        f.step();
        Actor next = f.owner(94);
        assertNotSame(selfWriter, next);
        assertEquals(1, selfWriter.ticks);
        assertEquals(0, next.ticks);
        f.step();
        assertEquals(1, next.ticks);
        assertEquals(1, selfWriter.ticks);
        assertEquals(0, selfWriter.unloads);
    }

    @ParameterizedTest @ValueSource(ints = {4, 94, 95})
    void initialPhysicalPassPublishesCurrentSlotOwnerForAheadCurrentAndPastWrites(int writerSlot) {
        Fixture f = new Fixture();
        Actor old = writerSlot == 94 ? null : f.at(94, 0x48);
        Wheel writer = new Wheel(spawn(0x11, 94));
        f.manager.addDynamicObjectAtSlot(writer, writerSlot);
        f.initialPass();
        Actor next = f.owner(94);
        assertNotSame(writer, next);
        assertEquals(writerSlot < 94 ? 1 : 0, next.ticks);
        assertDoesNotThrow(() -> f.manager.rewindSnapshottable().capture(),
                "Initial publication cannot retain the retired local Java owner");
        List<ObjectInstance> pointers = f.manager.initialCollisionResponseList().playerReadView();
        assertTrue(pointers.contains(next));
        assertFalse(pointers.contains(writerSlot == 94 ? writer : old));
        assertEquals(writerSlot == 94 ? 1 : 2, pointers.size());
        f.step();
        assertEquals(writerSlot < 94 ? 2 : 1, next.ticks);
    }

    @Test void fullDynamicRamCannotBlockTheFixedWriteOrChangeSlotPressure() {
        Fixture f = new Fixture();
        f.manager.reserveAllButNFreeSlots(0);
        Actor old = f.at(94, 0x51);
        assertEquals(-1, f.manager.firstFreeDynamicSlot());
        int used = f.manager.getActiveObjectSlotCount();
        long[] reservations = f.manager.rewindSnapshottable().capture().usedSlotsBits();
        Actor next = f.replace(94, 0x52);
        assertSame(next, f.owner(94));
        assertEquals(90, used);
        assertEquals(used, f.manager.getActiveObjectSlotCount());
        assertArrayEquals(reservations, f.manager.rewindSnapshottable().capture().usedSlotsBits());
        assertEquals(-1, f.manager.firstFreeDynamicSlot());
        assertEquals(0, old.unloads);
    }

    @Test void throwingOrEmptyFactoryPreservesOldOwnerCanonicalListsAndConstructionContext() {
        Fixture f = new Fixture();
        Actor old = f.at(94, 0x61);
        Actor other = f.at(5, 0x62);
        f.publish(old, other, old);
        var before = f.manager.rewindSnapshottable().capture();
        RuntimeException failure = new IllegalStateException("construction failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> {
                    Actor underConstruction = new Actor(spawn(0x63, 0));
                    assertEquals(94, underConstruction.constructionSlot);
                    assertSame(f.services, underConstruction.injectedServices());
                    assertSame(old, f.owner(94), "Old owner remains registered throughout construction");
                    throw failure;
                })));
        assertSame(old, f.owner(94));
        assertSameSnapshot(before, f.manager.rewindSnapshottable().capture());
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> null));
        assertSameSnapshot(before, f.manager.rewindSnapshottable().capture());
        assertEquals(-1, new Actor(spawn(0x64, 0)).constructionSlot, "Failed factory restores the surrounding context");
        assertEquals(0, old.unloads);
    }

    @Test void explicitRosterAndExactProcessedFixedRangeAreRequiredBeforeFactoryRuns() {
        Fixture ordinary = new Fixture(false, List.of());
        AtomicInteger invoked = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(
                ordinary.manager, 94, () -> { invoked.incrementAndGet(); return new Actor(spawn(1, 0)); }));
        assertThrows(IllegalStateException.class, ordinary::previous);
        Fixture nativeWorld = new Fixture();
        for (int slot : new int[]{-1, 0, 1, 2, 3, 4, 93, 110, 111}) {
            assertThrows(IllegalArgumentException.class, () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(
                    nativeWorld.manager, slot, () -> { invoked.incrementAndGet(); return new Actor(spawn(1, 0)); }), "slot " + slot);
        }
        assertEquals(0, invoked.get());
        assertEquals(109, nativeWorld.replace(109, 0x71).getSlotIndex());
    }

    @Test void previousReadRetainsPublishedOrderDuplicatesAndCapacityWithoutLiveEligibilityRescan() {
        Fixture f = new Fixture();
        Actor first = f.at(94, 0x81);
        Actor second = f.at(5, 0x82);
        Actor unlisted = f.at(6, 0x83);
        List<ObjectInstance> input = new ArrayList<>();
        for (int i = 0; i < 80; i++) input.add(i % 3 == 1 ? second : first);
        // Add_SpriteToCollisionResponseList admits $7E bytes: 63 pointers
        // (sonic3k.asm:21236-21246), retaining duplicate RAM addresses.
        f.publish(input.toArray(ObjectInstance[]::new));
        second.publishes = false;
        first.publishes = false;
        List<ObjectInstance> view = f.previous();
        assertEquals(input.subList(0, 63), view);
        assertFalse(view.contains(unlisted));
        assertThrows(UnsupportedOperationException.class, () -> view.add(unlisted));
        Actor next = ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(f.manager, 94, () -> {
            Actor actor = new Actor(spawn(0x84, 0)); actor.publishes = false; return actor;
        });
        List<ObjectInstance> expected = input.subList(0, 63).stream().map(a -> a == first ? next : a).toList();
        assertEquals(expected, f.previous());
        assertEquals(63, f.manager.initialCollisionResponseList().playerReadView().size());
        assertEquals(input.subList(0, 63), view, "The earlier returned copy remains immutable and independent");
    }

    @Test void previousPointerDereferencesCurrentDynamicOwnerAndNeverManufacturesATouchForDeletion() {
        Fixture f = new Fixture();
        Actor fixed = f.at(94, 0x91);
        Actor dynamic = f.at(5, 0x92);
        f.publish(fixed, dynamic, fixed);
        f.manager.removeDynamicObject(dynamic);
        assertEquals(List.of(fixed, fixed), f.previous());
        Actor reusedDynamic = f.at(5, 0x93);
        assertEquals(List.of(fixed, reusedDynamic, fixed), f.previous(),
                "Canonical pointers resolve current SST code even outside this fixed-write bridge");
        f.manager.removeDynamicObject(fixed);
        f.at(95, 0x94);
        assertEquals(List.of(reusedDynamic), f.previous(), "An unrelated live object cannot fill a deleted pointer");
        Actor reusedFixed = f.replace(94, 0x95);
        assertEquals(List.of(reusedFixed, reusedDynamic, reusedFixed), f.previous());
    }

    @Test void deletedAddressesKeepBothNativeListsFullUntilTheCanonicalBuildResets() {
        Fixture f = new Fixture();
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(f.manager);
        Actor old = f.at(94, 0x96);
        ObjectInstance[] full = new ObjectInstance[80];
        Arrays.fill(full, old);
        f.publish(full);
        f.manager.removeDynamicObject(old);
        assertTrue(f.previous().isEmpty());
        Object fullAddresses = pointers.capture();
        Actor unlisted = f.at(95, 0x97);
        f.manager.initialCollisionResponseList().addToCurrentBuild(unlisted);
        assertFalse(f.manager.admitTransientContactBeforePlayer(unlisted));
        assertTrue(f.manager.initialCollisionResponseList().currentBuildView().isEmpty());
        assertTrue(RewindSnapshotDiff.keyEquals(pointers.key(), fullAddresses, pointers.capture()),
                "Deleting an occupant cannot reclaim any of the 63 recorded addresses");
        assertEquals(63, f.manager.rewindSnapshottable().capture()
                .collisionResponseState().currentCollisionBuildCursor());
        Actor reused = f.replace(94, 0x98);
        assertEquals(63, f.previous().size());
        assertTrue(f.previous().stream().allMatch(owner -> owner == reused));
        f.manager.resetInitialCollisionResponseBuild();
        f.manager.initialCollisionResponseList().addToCurrentBuild(unlisted);
        assertEquals(List.of(unlisted), f.manager.initialCollisionResponseList().currentBuildView());
        assertEquals(63, f.previous().size(), "Resetting the current builder preserves the previous player addresses");
    }

    @Test void emptyAddressesSurviveCompositeRestoreAndReuseWithIdenticalForwardGraph() {
        Fixture f = new Fixture();
        RewindRegistry world = f.worldSnapshots(); // ordinary objects first, physical pointers second
        Actor old = f.at(94, 0xC1);
        Actor dynamic = f.at(5, 0xC2);
        f.publish(old, dynamic, old);
        f.manager.removeDynamicObject(old);
        f.manager.removeDynamicObject(dynamic);
        assertTrue(f.previous().isEmpty());
        CompositeSnapshot emptyAddresses = world.capture();
        Object pointerState = emptyAddresses.get("native_competition_collision_pointers");
        assertTrue(f.manager.rewindSnapshottable().capture().collisionResponseState()
                .previousCollisionObjectIds().isEmpty(), "Only live owners enter the ordinary snapshot ids");
        f.replace(94, 0xC3);
        Actor reusedDynamic = f.at(5, 0xC4);
        assertEquals(List.of(f.owner(94), reusedDynamic, f.owner(94)), f.previous());
        f.step();
        List<String> expectedGraph = f.graph();
        CompositeSnapshot expected = world.capture();
        world.restore(emptyAddresses);
        assertTrue(f.manager.getActiveObjects().isEmpty());
        assertTrue(f.previous().isEmpty());
        assertTrue(RewindSnapshotDiff.keyEquals("native_competition_collision_pointers", pointerState,
                world.capture().get("native_competition_collision_pointers")));
        f.replace(94, 0xC3);
        reusedDynamic = f.at(5, 0xC4);
        assertEquals(List.of(f.owner(94), reusedDynamic, f.owner(94)), f.previous());
        f.step();
        assertEquals(expectedGraph, f.graph());
        CompositeSnapshot actual = world.capture();
        expected.entries().forEach((key, value) -> assertTrue(
                RewindSnapshotDiff.keyEquals(key, value, actual.get(key)), "world key " + key));
        world.restore(emptyAddresses);
        assertTrue(f.previous().isEmpty(), "The independent checkpoint was not mutated by later replay");
    }

    @Test void ordinaryPublicationRecordsSlotsThroughItsProvidedOwnerFaultBoundary() {
        var insideBoundary = new java.util.concurrent.atomic.AtomicBoolean(true);
        Actor actor = new Actor(spawn(0xC9, 0)) {
            @Override public int getSlotIndex() {
                assertTrue(insideBoundary.get(), "Slot metadata must retain the creator callback boundary");
                return 94;
            }
        };
        insideBoundary.set(false);
        ObjectCallbackBoundary boundary = new ObjectCallbackBoundary() {
            @Override public <T> T call(String owner, java.util.function.Supplier<T> callback) {
                assertEquals("creator", owner);
                insideBoundary.set(true);
                try { return callback.get(); }
                finally { insideBoundary.set(false); }
            }
        };
        class BoundaryRegistry implements ObjectRegistry, java.util.function.Supplier<Object> {
            @Override public Object get() { return boundary; }
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int id) { return "boundary-test"; }
        }
        ObjectCallbackRouter callbacks = new ObjectCallbackRouter(new BoundaryRegistry());
        callbacks.registerOwnerIfAbsent(actor, "creator");
        ObjectCollisionResponseList canonical = new ObjectCollisionResponseList();
        canonical.captureForNextFrame(List.of(actor), callbacks);
        assertEquals(List.of(actor), canonical.playerReadView());
        assertFalse(insideBoundary.get());
    }

    @Test void pointerAdapterRequiresCompleteHistoryAndNormalManagerResetClearsBothAddressLists() {
        Fixture f = new Fixture();
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(f.manager);
        Object empty = pointers.capture();
        Actor fixed = f.at(94, 0xD1);
        f.publish(fixed, fixed);
        Object published = pointers.capture();
        assertFalse(RewindSnapshotDiff.keyEquals(pointers.key(), empty, published));
        assertThrows(IllegalStateException.class, pointers::resetForMissingSnapshot,
                "Missing physical addresses cannot be inferred from live owner ids");
        assertThrows(IllegalArgumentException.class, () -> pointers.restore(new Object()));
        assertTrue(RewindSnapshotDiff.keyEquals(pointers.key(), published, pointers.capture()));
        f.manager.reset(0);
        assertTrue(f.previous().isEmpty());
        assertTrue(f.manager.initialCollisionResponseList().currentBuildView().isEmpty());
        assertTrue(RewindSnapshotDiff.keyEquals(pointers.key(), empty, pointers.capture()));
        fixed = f.at(94, 0xD2);
        f.publish(fixed);
        pointers.restore(empty);
        assertTrue(f.previous().isEmpty(), "An empty pointer checkpoint clears future addresses, without erasing actors");
        assertNotNull(f.owner(94));
    }

    @Test void overwriteOfDestroyedButStillRegisteredOwnerRetiresItsRegistration() {
        Fixture f = new Fixture();
        Actor old = f.at(94, 0xA1);
        f.publish(old);
        old.setDestroyed(true);
        Actor next = f.replace(94, 0xA2);
        assertFalse(f.manager.getActiveObjects().contains(old));
        assertEquals(List.of(next), f.previous());
        f.step();
        assertEquals(1, next.ticks);
        assertEquals(0, old.unloads, "Pending ordinary deletion must not unload the replacement's SST");
    }

    @Test void genericRecreationRestoresFixed94CanonicalPointersAndForwardExecution() {
        Fixture f = new Fixture();
        Actor old = f.at(94, 0xB1);
        Actor dynamic = f.at(5, 0xB2);
        f.publish(old, dynamic, old);
        Actor next = f.replace(94, 0xB3);
        next.ticks = 7;
        var objects = f.manager.rewindSnapshottable();
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(f.manager);
        var snapshot = objects.capture();
        Object pointerSnapshot = pointers.capture();
        assertEquals(List.of(next, dynamic, next), f.previous());
        f.step();
        List<String> expectedGraph = f.graph();
        var expected = objects.capture();
        f.manager.removeDynamicObject(f.owner(94));
        f.replace(94, 0xB4);
        f.step();
        objects.restore(snapshot); pointers.restore(pointerSnapshot);
        Actor restored = f.owner(94);
        assertNotSame(next, restored, "The normal generic recreation path restores the fixed owner");
        assertEquals(0xB3, restored.getSpawn().objectId());
        assertEquals(7, restored.ticks);
        assertEquals(List.of(94, 5, 94), f.previous().stream().map(o -> ((Actor) o).getSlotIndex()).toList());
        assertSame(restored, f.previous().get(0));
        assertSame(restored, f.previous().get(2));
        assertSameSnapshot(snapshot, objects.capture());
        // Replacing after a generic restore must rebind restored collision aliases too.
        f.replace(94, 0xB5);
        assertEquals(0xB5, ((Actor) f.previous().getFirst()).getSpawn().objectId());
        objects.capture();
        objects.restore(snapshot); pointers.restore(pointerSnapshot);
        f.step();
        assertEquals(expectedGraph, f.graph());
        assertSameSnapshot(expected, objects.capture());
        assertEquals(1, f.manager.getActiveObjectSlotCount());
    }
}
