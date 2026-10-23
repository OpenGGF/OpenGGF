package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectStreaming;
import com.openggf.graphics.GLCommand;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static com.openggf.level.objects.NativeSstSlotAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual native ID/owner deletion; no allocator-bit inference or fabricated $2C birth. */
class TestNativeCompetitionOccupiedFullDelete {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void bindingResetCaptureAndReconstructionDoNotInventAFreshClear() {
        var f = new Rig(List.of(), false);
        assertThrows(IllegalStateException.class, () -> readWord2C(f.manager, 56));
        f.manager.reset(0);
        f.manager.nativeSstWord2C().capture();
        ObjectConstructionContext.withProbeConstruction(() -> {
            assertThrows(IllegalStateException.class, () -> clearWord2C(f.manager, 56));
            return null;
        });
        ObjectConstructionContext.withRewindActiveRestore(() -> {
            assertThrows(IllegalStateException.class, () -> clearWord2C(f.manager, 56));
            return null;
        });
        assertThrows(IllegalStateException.class, () -> readWord2C(f.manager, 56));
        assertTrue(f.clears.isEmpty());
    }

    @Test void occupiedTypedAndUntypedCellsClearButEmptyAndIdZeroCellsSurvive() {
        var f = new Rig(List.of(), false);
        var typed = f.word(56, 0x1234);
        var untyped = f.actor(58);
        var idZero = f.word(59, 0xFFFF); idZero.liveId = 0;
        writeWord2C(f.manager, 57, 0xFFFF);
        assertTrue(f.manager.reserveDynamicSlot(60), "a reservation does not prove a live ID");
        writeWord2C(f.manager, 60, 0xFFFE);
        f.delete(56, 12);
        assertEquals(List.of(56, 58), f.clears);
        assertEquals(0, typed.word);
        assertEquals(1, typed.unloads); assertEquals(1, untyped.unloads);
        assertEquals(0, readWord2C(f.manager, 56));
        assertEquals(0, readWord2C(f.manager, 58));
        assertEquals(0xFFFF, readWord2C(f.manager, 57));
        assertEquals(0xFFFF, readWord2C(f.manager, 59));
        assertEquals(0xFFFE, readWord2C(f.manager, 60));
        assertSame(idZero, currentOwner(f.manager, 59));
        assertEquals(0, idZero.unloads);
        assertFalse(f.manager.reserveDynamicSlot(59));
        assertFalse(f.manager.reserveDynamicSlot(60), "an ID-empty reservation was not released");
    }

    @Test void liveIdZeroSkipsTheRespawnLatchAsWellAsTheWord() {
        var f = new Rig(List.of(spawn(null)), false);
        Actor owner = (Actor) f.manager.getActiveObjects().iterator().next();
        int slot = owner.getSlotIndex(); owner.liveId = 0;
        assertEquals(0x80, f.respawn(1) & 0x80);
        f.delete(slot, 1);
        assertEquals(0x80, f.respawn(1) & 0x80);
        assertTrue(f.clears.isEmpty());
        assertSame(owner, currentOwner(f.manager, slot));
    }

    @Test void respawnAndWordClearPrecedeExactRetirementAndBothNotifications() {
        var f = new Rig(List.of(spawn(null)), false);
        Actor owner = (Actor) f.manager.getActiveObjects().iterator().next();
        int slot = owner.getSlotIndex();
        var id = f.manager.objectQuery().identityOf(owner).orElseThrow();
        f.beforeClear = selected -> {
            assertEquals(slot, selected);
            assertEquals(0, f.respawn(owner.getRespawnStateIndex()) & 0x80);
            assertSame(owner, currentOwner(f.manager, slot));
            assertFalse(f.manager.reserveDynamicSlot(slot));
            f.events.add("clear");
        };
        owner.removed = () -> {
            assertNull(currentOwner(f.manager, slot));
            assertEquals(-1, owner.getSlotIndex());
            assertEquals(0, readWord2C(f.manager, slot));
            assertTrue(f.manager.objectQuery().resolve(id).isEmpty());
            assertTrue(f.manager.reserveDynamicSlot(slot), "allocation is retired before onRemoved");
            f.manager.releaseDynamicSlot(slot);
            f.events.add("removed");
        };
        owner.unloading = () -> { assertNull(currentOwner(f.manager, slot)); f.events.add("unload"); };
        f.delete(slot, 1);
        assertEquals(List.of("clear", "removed", "unload"), f.events);
    }

    @Test void physicalScanVisitsNewHigherSlotsAndPreservesNewCurrentAndLowerSlots() {
        var f = new Rig(List.of(), false);
        Actor selected = f.actor(56);
        Actor[] created = new Actor[3];
        selected.unloading = () -> {
            created[0] = f.word(55, 5);
            created[1] = f.word(56, 6);
            created[2] = f.word(58, 8);
        };
        f.delete(54, 14);
        assertEquals(List.of(56, 58), f.clears);
        assertSame(created[0], currentOwner(f.manager, 55));
        assertSame(created[1], currentOwner(f.manager, 56));
        assertNull(currentOwner(f.manager, 58));
        assertEquals(5, readWord2C(f.manager, 55));
        assertEquals(6, readWord2C(f.manager, 56));
        assertEquals(0, readWord2C(f.manager, 58));
        assertEquals(1, created[2].unloads);
    }

    @Test void onRemovedCanReloadTheSamePlacementAndItsNewIdentitySurvives() {
        var placed = spawn(null);
        var f = new Rig(List.of(placed), false);
        Actor selected = (Actor) f.manager.getActiveObjects().iterator().next();
        int slot = selected.getSlotIndex();
        var oldId = f.manager.objectQuery().identityOf(selected).orElseThrow();
        Actor[] replacement = new Actor[1];
        selected.removed = () -> {
            assertTrue(f.manager.nativeCompetitionStreaming().load(placed, 1, slot, 1));
            replacement[0] = (Actor) currentOwner(f.manager, slot);
            assertNotSame(selected, replacement[0]);
        };
        selected.unloading = () -> assertSame(replacement[0], currentOwner(f.manager, slot));
        f.delete(slot, 1);
        assertSame(replacement[0], currentOwner(f.manager, slot));
        assertNotEquals(oldId, f.manager.objectQuery().identityOf(replacement[0]).orElseThrow());
        assertEquals(0x80, f.respawn(1) & 0x80, "stale cleanup must not clear the replacement's latch");
        assertEquals(0, replacement[0].unloads);
        assertFalse(f.manager.reserveDynamicSlot(slot));
    }

    @Test void destroyedAliasCannotClearOrUnloadTheCurrentReplacementTwice() {
        var f = new Rig(List.of(), false);
        var stale = f.word(56, 7); stale.setDestroyed(true);
        var current = f.aliasWord(56, 7);
        WordActor[] next = new WordActor[1];
        current.unloading = () -> next[0] = f.word(56, 0xBEEF);
        f.delete(56, 12);
        assertEquals(List.of(56), f.clears);
        assertEquals(0, stale.unloads); assertEquals(1, current.unloads);
        assertSame(next[0], currentOwner(f.manager, 56));
        assertEquals(0xBEEF, readWord2C(f.manager, 56));
        assertEquals(0, next[0].unloads);
    }

    @Test void ambiguousOwnersAndUnknownNativeIdsFailBeforeAnyClear() {
        var ambiguous = new Rig(List.of(), false);
        var first = ambiguous.word(56, 7);
        ambiguous.aliasWord(56, 7);
        assertThrows(IllegalStateException.class, () -> ambiguous.delete(56, 12));
        assertTrue(ambiguous.clears.isEmpty()); assertEquals(7, first.word);
        var unknown = new Rig(List.of(), false);
        var owner = unknown.word(56, 9); owner.liveId = -1;
        assertThrows(IllegalStateException.class, () -> unknown.delete(56, 12));
        assertTrue(unknown.clears.isEmpty()); assertEquals(9, owner.word);
        assertSame(owner, currentOwner(unknown.manager, 56));
    }

    @Test void failedClearKeepsEarlierRespawnEffectAndDoesNotRetireOrVisitLaterSlots() {
        var f = new Rig(List.of(spawn(null)), false);
        Actor selected = (Actor) f.manager.getActiveObjects().iterator().next();
        int slot = selected.getSlotIndex();
        f.word(slot + 1, 8);
        var failure = new IllegalStateException("full-clear writer fixture");
        f.beforeClear = ignored -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.delete(slot, 2)));
        assertEquals(0, f.respawn(1) & 0x80);
        assertSame(selected, currentOwner(f.manager, slot));
        assertFalse(f.manager.reserveDynamicSlot(slot));
        assertEquals(0, selected.unloads);
        assertEquals(8, readWord2C(f.manager, slot + 1));
        assertEquals(List.of(slot), f.clears);
    }

    @Test void writerReentryCannotUseACapturedSpawnToRetireItsNewReplacement() {
        var placed = spawn(null);
        var f = new Rig(List.of(placed), false);
        Actor selected = (Actor) f.manager.getActiveObjects().iterator().next();
        int slot = selected.getSlotIndex();
        Actor[] replacement = new Actor[1];
        f.beforeClear = ignored -> {
            assertTrue(f.manager.nativeCompetitionStreaming().load(placed, 1, slot + 1, 1));
            replacement[0] = (Actor) currentOwner(f.manager, slot + 1);
            assertSame(selected, currentOwner(f.manager, slot), "the former placement is now dynamic");
        };
        var failure = assertThrows(IllegalStateException.class, () -> f.delete(slot, 2));
        assertTrue(failure.getMessage().contains("placement owner changed"));
        assertSame(selected, currentOwner(f.manager, slot));
        assertSame(replacement[0], currentOwner(f.manager, slot + 1));
        assertThrows(UnsupportedOperationException.class, () -> readWord2C(f.manager, slot),
                "a surviving untyped owner remains outside the live read projection");
        assertEquals(0x80, f.respawn(1) & 0x80, "the reentrant birth owns the new latch");
        assertEquals(0, selected.unloads); assertEquals(0, replacement[0].unloads);
        assertFalse(f.manager.reserveDynamicSlot(slot));
        assertFalse(f.manager.reserveDynamicSlot(slot + 1));
        assertEquals(List.of(slot), f.clears);
        f.manager.removeDynamicObject(selected); // Ordinary removal does not clear WORD2C.
        assertEquals(0, readWord2C(f.manager, slot), "the prior completed clear was retained");
        assertSame(replacement[0], currentOwner(f.manager, slot + 1));
    }

    @Test void removalAndUnloadFaultsRetainOrderedEffectsAndTheSelectedOwnerBoundary() {
        for (boolean failOnRemoved : new boolean[] {true, false}) {
            var f = new Rig(List.of(spawn("receiver")), false);
            Actor selected = (Actor) f.manager.getActiveObjects().iterator().next();
            int slot = selected.getSlotIndex();
            f.word(slot + 1, 8);
            var failure = new IllegalArgumentException("notification fixture");
            WordActor[] replacement = new WordActor[1];
            Runnable notification = () -> {
                assertNull(currentOwner(f.manager, slot));
                assertEquals(0, readWord2C(f.manager, slot));
                replacement[0] = f.word(slot, 0xCAFE);
                throw failure;
            };
            if (failOnRemoved) selected.removed = notification;
            else selected.unloading = notification;
            var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> f.delete(slot, 2));
            assertEquals("receiver", aborted.owner()); assertSame(failure, aborted.getCause());
            assertEquals(Set.of("receiver"), f.disabled);
            assertSame(replacement[0], currentOwner(f.manager, slot));
            assertEquals(0xCAFE, readWord2C(f.manager, slot));
            assertFalse(f.manager.reserveDynamicSlot(slot));
            assertEquals(failOnRemoved ? 0 : 1, selected.unloads);
            assertEquals(8, readWord2C(f.manager, slot + 1));
            assertEquals(List.of(slot), f.clears);
            // Dynamic birth inside notification still inherits the retained router owner.
            var inherited = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> ObjectCallbackDispatch.run(f.manager, replacement[0], () -> { throw new IllegalStateException("child fixture"); }));
            assertEquals("receiver", inherited.owner());
        }
    }

    @Test void registeredDestructiveRestoreKeepsBindingCellsOwnerIdsAndForwardReplay() {
        var f = new Rig(List.of(), false);
        WordActor original = f.word(56, 7);
        writeWord2C(f.manager, 57, 0xFFFF);
        var id = f.manager.objectQuery().identityOf(original).orElseThrow();
        var registry = new RewindRegistry();
        registry.register(f.manager.rewindSnapshottable());
        var streaming = ObjectManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(f.manager);
        registry.register(streaming); registerWord2CRewind(f.manager, registry);
        var saved = registry.capture();
        assertEquals(7, readWord2C(f.manager, 56));
        assertEquals(0xFFFF, readWord2C(f.manager, 57));
        f.delete(56, 12); var expected = registry.capture();
        f.manager.reset(0); f.word(56, 9);
        registry.restore(saved);
        var restored = (WordActor) currentOwner(f.manager, 56);
        assertNotSame(original, restored);
        assertEquals(id, f.manager.objectQuery().identityOf(restored).orElseThrow());
        assertEquals(1, restored.getLiveObjectId()); assertEquals(7, restored.word);
        assertEquals(0xFFFF, readWord2C(f.manager, 57));
        f.clears.clear(); f.delete(56, 12);
        var replay = registry.capture();
        for (String key : List.of("object-manager", streaming.key(), WORD_2C_REWIND_KEY))
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), replay.get(key)), key);
        assertEquals(List.of(56), f.clears, "the original structural writer remains bound after restore");
    }

    @Test void legacyTwoArgumentBindingRetainsItsOriginalNotificationAndResidueSemantics() {
        var f = new Rig(List.of(), true);
        var owner = f.word(56, 0xFFFF);
        owner.unloading = () -> {
            assertSame(owner, currentOwner(f.manager, 56));
            assertFalse(f.manager.reserveDynamicSlot(56));
        };
        f.delete(56, 12);
        assertEquals(1, owner.unloads); assertTrue(f.clears.isEmpty());
        assertEquals(0xFFFF, readWord2C(f.manager, 56));
    }

    @Test void successfulLegacyRebindClearsOnlyTheOptionalWriterAndFailedEnableRetainsIt() {
        var failed = new Rig(List.of(), false);
        var retained = failed.word(56, 7);
        assertThrows(NullPointerException.class,
                () -> ObjectManagerInternalAccess.enableNativeCompetitionStreaming(failed.manager, null));
        failed.delete(56, 12);
        assertEquals(List.of(56), failed.clears);
        assertEquals(0, retained.word);

        var rebound = new Rig(List.of(), false);
        var owner = rebound.word(56, 0xFFFF);
        ObjectManagerInternalAccess.enableNativeCompetitionStreaming(rebound.manager, new Sonic2CompetitionObjectStreaming());
        owner.unloading = () -> {
            assertSame(owner, currentOwner(rebound.manager, 56));
            assertFalse(rebound.manager.reserveDynamicSlot(56));
        };
        rebound.delete(56, 12);
        assertTrue(rebound.clears.isEmpty());
        assertEquals(0xFFFF, readWord2C(rebound.manager, 56));
        assertEquals(1, owner.unloads);
    }

    private static ObjectSpawn spawn(String owner) {
        return new ObjectSpawn(0x20, 0x100, 1, 0, 0, true, 0x100, 0, owner,
                owner == null ? null : owner + ":fixture");
    }

    public static class Actor extends AbstractObjectInstance implements SpawnRewindRecreatable {
        int liveId, unloads;
        @RewindTransient(reason = "test-owned structural notification binding") Runnable removed = () -> { };
        @RewindTransient(reason = "test-owned structural notification binding") Runnable unloading = () -> { };
        public Actor(ObjectSpawn spawn) { super(spawn, "native full-delete fixture"); liveId = spawn.objectId() & 0xFF; }
        @Override public int getLiveObjectId() { return liveId; }
        @Override protected void onRemovedFromObjectManager() { removed.run(); }
        @Override public void onUnload() { unloads++; unloading.run(); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    public static final class WordActor extends Actor implements NativeSstWord2CSource {
        int word;
        public WordActor(ObjectSpawn spawn) { super(spawn); }
        @Override public int nativeWord2C() { return word; }
        @Override public void writeNativeWord2C(int value) { word = value & 0xFFFF; }
    }

    private static final class Rig {
        final Set<String> disabled = new HashSet<>();
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), disabled::addAll);
        final List<Integer> clears = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        IntConsumer beforeClear = slot -> { };
        final ObjectManager manager;
        Rig(List<ObjectSpawn> spawns, boolean legacy) {
            Camera camera = new Camera(SonicConfigurationService.getInstance());
            var roster = mock(NativeCompetitionRoster.class);
            when(roster.camera(0)).thenReturn(camera); when(roster.camera(1)).thenReturn(camera);
            var module = mock(GameModule.class);
            var policy = mock(WorldSessionPolicyState.class);
            when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
            var world = new WorldSession(module);
            ObjectManager[] holder = new ObjectManager[1];
            manager = new ObjectManager(spawns, new Registry(boundary), -1, null, null, null, camera,
                    new StubObjectServices() {
                        @Override public ObjectManager objectManager() { return holder[0]; }
                        @Override public Camera camera() { return camera; }
                        @Override public WorldSession worldSession() { return world; }
                    });
            holder[0] = manager;
            if (legacy) ObjectManagerInternalAccess.enableNativeCompetitionStreaming(manager, new Sonic2CompetitionObjectStreaming());
            else ObjectManagerInternalAccess.enableNativeCompetitionStreaming(manager, new Sonic2CompetitionObjectStreaming(), slot -> {
                clears.add(slot); beforeClear.accept(slot); clearWord2C(manager, slot);
            });
            manager.reset(0);
        }
        void delete(int first, int count) { manager.nativeCompetitionStreaming().unload(first, count); }
        int respawn(int index) { return manager.rewindSnapshottable().capture().placement().objState()[index] & 0xFF; }
        Actor actor(int slot) {
            Actor actor = manager.createDynamicObjectAtSlot(() -> new Actor(spawn(null)), slot);
            assertNotNull(actor); return actor;
        }
        WordActor word(int slot, int value) {
            writeWord2C(manager, slot, value);
            WordActor actor = manager.createDynamicObjectAtSlot(() -> imported(slot), slot);
            assertNotNull(actor); return actor;
        }
        WordActor aliasWord(int slot, int value) {
            WordActor actor = ObjectConstructionContext.with(manager.services(), slot, () -> {
                WordActor next = new WordActor(spawn(null)); next.word = value; return next;
            });
            manager.addDynamicObject(actor); return actor;
        }
        WordActor imported(int slot) {
            WordActor actor = new WordActor(spawn(null)); actor.word = readWord2C(manager, slot); return actor;
        }
    }
    private record Registry(ObjectCallbackBoundary boundary) implements ObjectRegistry, Supplier<Object> {
        @Override public Object get() { return boundary; }
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Actor(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "full-delete fixture"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
    }
}
