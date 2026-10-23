package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.*;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Shared GAME/TIME and OVER SSTs are physical slots 2/3, after both players
 * (s2.constants.asm:1096-1113). CheckGameOver/Obj01_ResetLevel write them
 * unconditionally (s2.asm:38274-38316); P2 targets the same addresses
 * (Obj02_CheckGameOver_2Pmode / Obj02_ResetLevel_Part2, s2.asm:41157-41209).
 * Domain card fields and player dispatch remain the lifecycle owner's work.
 */
class TestNativeCompetitionPrefixSstAccess {
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
        Actor(ObjectSpawn spawn) { super(spawn, "native-prefix-test"); constructionSlot = getSlotIndex(); }
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

    /** Typed fixture projection; literal word seeding makes no native birth or clear claim. */
    private static final class WordActor extends Actor implements NativeSstWord2CSource {
        int word;
        int reads;
        WordActor(ObjectSpawn spawn, int word) { super(spawn); this.word = word; }
        @Override public int nativeWord2C() { reads++; return word; }
        @Override public void writeNativeWord2C(int value) { word = value & 0xFFFF; }
    }

    private static final class Writer extends Actor {
        boolean replaced;
        Writer(ObjectSpawn spawn) { super(spawn); }
        @Override public void update(int frame, PlayableEntity player) {
            super.update(frame, player);
            if (!replaced) {
                replaced = true;
                ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(services().objectManager(),
                        getSpawn().subtype(), () -> new Actor(spawn(0x39, 0)));
            }
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Writer(context.spawn());
        }
    }

    private static final class Fixture {
        final Camera camera = new Camera(SonicConfigurationService.getInstance());
        final ObjectManager manager;
        final ObjectServices services;
        Fixture() { this(true, ObjectSlotLayout.SONIC_2, List.of()); }
        Fixture(boolean explicitRoster, ObjectSlotLayout layout, List<ObjectSpawn> spawns) {
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
                @Override public String getPrimaryName(int id) { return "native-prefix-test"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return layout; }
            };
            manager = new ObjectManager(spawns, registry, -1, null, null, null, camera, services);
            holder[0] = manager;
            manager.enableExecThenLoadPlacement(); manager.reset(0);
        }
        Actor at(int slot, int id) {
            Actor actor = new Actor(spawn(id, 0)); manager.addDynamicObjectAtSlot(actor, slot); return actor;
        }
        Actor replace(int slot, int id) {
            return ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(manager, slot,
                    () -> new Actor(spawn(id, 0)));
        }
        Actor owner(int slot) { return (Actor) NativeSstSlotAccess.currentOwner(manager, slot); }
        void step() { manager.update(0, null, List.of(), 0, false); }
        void initialPass() {
            try (InitialObjectDispatchScope scope = manager.beginInitialProcessSprites(0, null, List.of())) {
                manager.freezeInitialCollisionResponseReadView(); manager.resetInitialCollisionResponseBuild();
                manager.loadInitialDynamicSlots(scope); manager.processInitialDynamicSlots(scope);
                manager.finishInitialProcessSprites(scope);
            }
        }
        void publish(ObjectInstance... entries) {
            ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(manager);
            manager.initialCollisionResponseList().captureCompletedBuild(Arrays.asList(entries));
            manager.snapshotTouchResponseState(true);
        }
        List<ObjectInstance> previous() { return ObjectManagerInternalAccess.previousCollisionResponseObjects(manager); }
        RewindRegistry snapshots() {
            RewindRegistry snapshots = new RewindRegistry();
            snapshots.register(manager.rewindSnapshottable());
            snapshots.register(ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(manager));
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
    private static void assertSnapshot(ObjectManagerSnapshot expected, ObjectManagerSnapshot actual) {
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected, actual));
    }

    @Test void occupiedS2PrefixWritesInjectConstructionAndPreserveRespawnStateWithoutUnload() {
        ObjectSpawn tracked = new ObjectSpawn(0x100, 0x100, 1, 0, 0, true, 0x8100, 0);
        Fixture f = new Fixture(true, ObjectSlotLayout.SONIC_2, List.of(tracked));
        f.manager.setSpawnStateBit(tracked, 2);
        Actor old = f.at(2, 0x10); old.setRespawnStateIndex(0);
        Actor over = f.at(3, 0x11); f.publish(old, over, old);
        var before = f.manager.rewindSnapshottable().capture();
        Actor next = f.replace(2, 0x39);
        var after = f.manager.rewindSnapshottable().capture();
        assertEquals(2, next.constructionSlot); assertSame(f.services, next.injectedServices());
        assertSame(next, f.owner(2)); assertFalse(f.manager.getActiveObjects().contains(old));
        assertEquals(0, old.unloads); assertArrayEquals(before.placement().objState(), after.placement().objState());
        assertArrayEquals(before.usedSlotsBits(), after.usedSlotsBits());
        assertEquals(before.collisionResponseState().previousCollisionObjectIds(),
                after.collisionResponseState().previousCollisionObjectIds());
        assertEquals(List.of(next, over, next), f.previous());
        f.step(); assertEquals(0, old.ticks); assertEquals(1, next.ticks); assertEquals(0, old.unloads);
    }

    @Test void prefixReplacementRetainsKnownLiteralWordAndPhysicalIdentity() {
        Fixture f = new Fixture();
        // Known literal fixture state, independent of native birth, clear or foreign aliases.
        NativeSstSlotAccess.writeWord2C(f.manager, 2, 0x1357);
        WordActor old = new WordActor(spawn(0x35, 0), 0x1357);
        f.manager.addDynamicObjectAtSlot(old, 2);
        f.publish(old, old);
        var identity = f.manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(entry -> entry.slotIndex() == 2).findFirst().orElseThrow().objectId();
        int allocated = f.manager.getAllocatedSlotCount();
        old.reads = 0;
        WordActor next = ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2,
                () -> new WordActor(spawn(0x36, 0), 0x1357));
        assertEquals(1, old.reads, "Successful construction samples the old word before retiring its owner");
        assertEquals(2, next.constructionSlot); assertSame(f.services, next.injectedServices());
        assertSame(next, f.owner(2));
        assertEquals(0x1357, NativeSstSlotAccess.readWord2C(f.manager, 2));
        assertEquals(List.of(next, next), f.previous());
        assertEquals(0, old.unloads); assertEquals(allocated, f.manager.getAllocatedSlotCount());
        var replacementIdentity = f.manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(entry -> entry.slotIndex() == 2).findFirst().orElseThrow().objectId();
        assertEquals(identity, replacementIdentity, "Prefix replacement retains its physical rewind identity");
    }

    @Test void refusedPrefixFactoriesDoNotSampleOrRetireTheWordOwner() {
        Fixture f = new Fixture();
        NativeSstSlotAccess.writeWord2C(f.manager, 2, 0x2468);
        WordActor old = new WordActor(spawn(0x37, 0), 0x2468);
        f.manager.addDynamicObjectAtSlot(old, 2);
        f.publish(old, old);
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(f.manager);
        Object addresses = pointers.capture();
        var words = f.manager.nativeSstWord2C().capture();
        old.reads = 0;
        RuntimeException failure = new IllegalStateException("prefix factory refusal");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> { throw failure; })));
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> null));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> old));
        assertEquals(0, old.reads, "Throw, null and registered results cannot trigger word preservation");
        assertSame(old, f.owner(2)); assertEquals(List.of(old, old), f.previous());
        assertEquals(0, old.unloads);
        assertTrue(RewindSnapshotDiff.keyEquals(pointers.key(), addresses, pointers.capture()));
        assertEquals(words, f.manager.nativeSstWord2C().capture());
        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
        assertNull(AbstractObjectInstance.PRE_ALLOCATED_SLOT.get());
    }

    @Test void prefixFactoryUpdatesOldWordAfterConstructionBeforePreservation() {
        Fixture f = new Fixture();
        NativeSstSlotAccess.writeWord2C(f.manager, 3, 0x1357);
        WordActor old = new WordActor(spawn(0x38, 0), 0x1357);
        f.manager.addDynamicObjectAtSlot(old, 3);
        old.reads = 0;
        WordActor next = ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 3, () -> {
            WordActor constructed = new WordActor(spawn(0x39, 0), 0xBEEF);
            assertEquals(3, constructed.constructionSlot); assertSame(f.services, constructed.injectedServices());
            assertSame(old, f.owner(3), "Old owner remains registered after the new constructor returns");
            assertEquals(0, old.reads, "Preservation cannot run before the factory completes");
            old.word = 0xBEEF;
            return constructed;
        });
        assertEquals(1, old.reads, "Preservation observes the final factory write");
        assertSame(next, f.owner(3)); assertEquals(0xBEEF, next.word);
        assertEquals(0xBEEF, NativeSstSlotAccess.readWord2C(f.manager, 3));
        assertEquals(0, old.unloads);
    }

    @ParameterizedTest @CsvSource({"2,3", "2,2", "3,2"})
    void prefixReplacementUsesExistingFutureCurrentAndPastExecutionRules(int writerSlot, int target) {
        Fixture f = new Fixture();
        Actor old = writerSlot == target ? null : f.at(target, 0x20);
        Writer writer = new Writer(spawn(0x21, target)); f.manager.addDynamicObjectAtSlot(writer, writerSlot);
        f.step(); Actor next = f.owner(target);
        assertNotSame(writer, next); assertNotSame(old, next);
        assertEquals(writerSlot < target ? 1 : 0, next.ticks);
        if (old != null) assertEquals(writerSlot > target ? 1 : 0, old.ticks);
        assertEquals(0, writer.unloads);
        f.step(); assertEquals(writerSlot < target ? 2 : 1, next.ticks);
        assertEquals(writerSlot == target ? 1 : 2, writer.ticks);
        assertEquals(0, writer.unloads);
        if (old != null) { assertEquals(writerSlot > target ? 1 : 0, old.ticks); assertEquals(0, old.unloads); }
    }

    @ParameterizedTest @CsvSource({"2,3", "2,2", "3,2"})
    void initialPassPublishesLivePrefixPointerInsteadOfRetiredLocalOwner(int writerSlot, int target) {
        Fixture f = new Fixture();
        Actor old = writerSlot == target ? null : f.at(target, 0x30);
        Writer writer = new Writer(spawn(0x31, target)); f.manager.addDynamicObjectAtSlot(writer, writerSlot);
        f.initialPass(); Actor next = f.owner(target);
        assertEquals(writerSlot < target ? 1 : 0, next.ticks);
        List<ObjectInstance> pointers = f.manager.initialCollisionResponseList().playerReadView();
        assertTrue(pointers.contains(next)); assertFalse(pointers.contains(writerSlot == target ? writer : old));
        assertEquals(writerSlot == target ? 1 : 2, pointers.size());
        assertDoesNotThrow(() -> f.manager.rewindSnapshottable().capture());
        f.step(); assertEquals(writerSlot < target ? 2 : 1, next.ticks);
    }

    @Test void p2SharedAddressWriteReplacesP1PendingPairBeforeEitherCardExecutes() {
        Fixture f = new Fixture();
        Actor game = f.replace(2, 0x39), over = f.replace(3, 0x39);
        f.publish(game, over, game);
        Actor p2game = ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2,
                () -> new Actor(spawn(0x39, 1)));
        Actor p2over = ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 3,
                () -> new Actor(spawn(0x39, 1)));
        assertEquals(List.of(p2game, p2over, p2game), f.previous());
        f.step(); assertEquals(0, game.ticks); assertEquals(0, over.ticks);
        assertEquals(1, p2game.ticks); assertEquals(1, p2over.ticks);
        assertEquals(1, f.owner(2).getSpawn().subtype()); assertEquals(0, f.manager.getActiveObjectSlotCount());
        assertEquals(0, game.unloads); assertEquals(0, over.unloads);
    }

    @Test void fullDynamicRamDoesNotBlockEitherPrefixWriteOrAlterReservations() {
        Fixture f = new Fixture(); f.manager.reserveAllButNFreeSlots(0);
        Actor old = f.at(2, 0x40);
        var before = f.manager.rewindSnapshottable().capture();
        assertEquals(-1, f.manager.firstFreeDynamicSlot());
        f.replace(2, 0x39); f.replace(3, 0x39);
        assertEquals(112, f.manager.getActiveObjectSlotCount());
        assertEquals(-1, f.manager.firstFreeDynamicSlot());
        assertArrayEquals(before.usedSlotsBits(), f.manager.rewindSnapshottable().capture().usedSlotsBits());
        assertEquals(0, old.unloads); f.step(); assertEquals(1, f.owner(2).ticks); assertEquals(1, f.owner(3).ticks);
    }

    @Test void throwingNullOrAlreadyRegisteredFactoryRollsBackBeforeRegistrationMutation() {
        Fixture f = new Fixture(); Actor old = f.at(2, 0x50), other = f.at(3, 0x51);
        f.publish(old, other, old);
        var before = f.manager.rewindSnapshottable().capture();
        RuntimeException failure = new IllegalStateException("prefix construction failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> {
                    Actor next = new Actor(spawn(0x39, 0));
                    assertEquals(2, next.constructionSlot); assertSame(f.services, next.injectedServices());
                    assertSame(old, f.owner(2)); throw failure;
                })));
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> null));
        assertThrows(IllegalArgumentException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> other));
        assertSameSnapshotAndOwners(f, before, old, other);
        assertEquals(-1, new Actor(spawn(0x52, 0)).constructionSlot);
    }

    private static void assertSameSnapshotAndOwners(Fixture f, ObjectManagerSnapshot before, Actor old, Actor other) {
        assertSnapshot(before, f.manager.rewindSnapshottable().capture());
        assertSame(old, f.owner(2)); assertSame(other, f.owner(3));
        assertEquals(List.of(old, other, old), f.previous()); assertEquals(0, old.unloads);
    }

    @Test void explicitRosterAndSemanticPrefixBoundsAreCheckedBeforeFactoryRuns() {
        AtomicInteger calls = new AtomicInteger();
        Fixture ordinary = new Fixture(false, ObjectSlotLayout.SONIC_2, List.of());
        assertThrows(IllegalStateException.class, () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(
                ordinary.manager, 2, () -> { calls.incrementAndGet(); return new Actor(spawn(0x39, 0)); }));
        Fixture f = new Fixture();
        for (int slot : new int[]{-1, 0, 1, 16, 56, 127, 128}) {
            assertThrows(IllegalArgumentException.class, () -> ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(
                    f.manager, slot, () -> { calls.incrementAndGet(); return new Actor(spawn(0x39, 0)); }), "slot " + slot);
        }
        assertEquals(0, calls.get()); assertEquals(15, f.replace(15, 0x39).getSlotIndex());
        Fixture s3k = new Fixture(true, ObjectSlotLayout.SONIC_3K, List.of());
        assertEquals(3, s3k.replace(3, 0x39).getSlotIndex());
        for (int slot : new int[]{0, 1, 4, 93, 94, 109, 110}) {
            assertThrows(IllegalArgumentException.class, () -> s3k.replace(slot, 0x39));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(s3k.manager, 3, () -> new Actor(spawn(1, 0))));
        assertEquals(94, ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(s3k.manager, 94,
                () -> new Actor(spawn(1, 0))).getSlotIndex());
    }

    @Test void prefixCollisionAliasesRetainCanonicalOrderDuplicatesCapacityAndDeletedHoles() {
        Fixture f = new Fixture(); Actor first = f.at(2, 0x60), second = f.at(3, 0x61);
        List<ObjectInstance> input = new ArrayList<>();
        for (int i = 0; i < 80; i++) input.add(i % 3 == 1 ? second : first);
        f.publish(input.toArray(ObjectInstance[]::new)); List<ObjectInstance> published = f.previous();
        f.manager.removeDynamicObject(first);
        assertEquals(21, f.previous().size());
        Actor next = ObjectManagerInternalAccess.replaceNativeCompetitionPrefixObject(f.manager, 2, () -> {
            Actor a = new Actor(spawn(0x39, 0)); a.publishes = false; return a;
        });
        assertEquals(input.subList(0, 63).stream().map(a -> a == first ? next : a).toList(), f.previous());
        assertEquals(63, f.previous().size()); assertEquals(input.subList(0, 63), published);
        assertThrows(UnsupportedOperationException.class, () -> f.previous().add(next));
        assertFalse(f.previous().contains(first));
    }

    @Test void destructiveGenericRestoreRelinksBothPrefixSlotsAndReplaysTheFullGraph() {
        Fixture f = new Fixture(); RewindRegistry snapshots = f.snapshots();
        Actor first = f.replace(2, 0x39), second = f.replace(3, 0x3A), dynamic = f.at(16, 0x70);
        first.ticks = 7; second.ticks = 8; f.publish(first, dynamic, second, first);
        CompositeSnapshot checkpoint = snapshots.capture(); f.step();
        CompositeSnapshot expected = snapshots.capture(); List<String> graph = f.graph();
        f.manager.removeDynamicObject(f.owner(2)); f.manager.removeDynamicObject(f.owner(3));
        f.replace(2, 0x71); f.replace(3, 0x72); f.step();
        snapshots.restore(checkpoint);
        assertNotSame(first, f.owner(2)); assertNotSame(second, f.owner(3));
        assertEquals(7, f.owner(2).ticks); assertEquals(8, f.owner(3).ticks);
        assertSame(f.services, f.owner(2).injectedServices());
        assertEquals(List.of(2, 16, 3, 2), f.previous().stream().map(o -> ((Actor) o).getSlotIndex()).toList());
        assertSame(f.owner(2), f.previous().get(0)); assertSame(f.owner(2), f.previous().get(3));
        Actor overwrittenAfterRestore = f.replace(2, 0x73);
        assertSame(overwrittenAfterRestore, f.previous().get(0));
        assertDoesNotThrow(() -> snapshots.capture());
        snapshots.restore(checkpoint); f.step(); assertEquals(graph, f.graph());
        CompositeSnapshot actual = snapshots.capture();
        expected.entries().forEach((key, value) -> assertEquals(List.of(), RewindSnapshotDiff.diffKey(key, value, actual.get(key))));
        assertEquals(1, f.manager.getActiveObjectSlotCount());
    }
}
