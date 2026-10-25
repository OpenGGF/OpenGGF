package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.openggf.level.objects.NativeSstSlotAccess.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent word arithmetic and real manager registration/replacement/rewind controls. */
class TestNativeSstWord2C {
    private GraphicsManager graphics;

    @BeforeEach void setUp() { graphics = GraphicsManager.getInstance(); graphics.initHeadless(); }
    @AfterEach void tearDown() { graphics.resetState(); }

    @Test void unknownEmptyWordIsNotAnInventedZero() {
        ObjectManager manager = manager();
        assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
        assertThrows(IllegalStateException.class, () -> addWord2C(manager, 40, -1));
        assertTrue(manager.nativeSstWord2C().capture().cells().isEmpty());
    }

    @Test void unknownUntypedOverlapDoesNotBecomeACaptureAuthority() {
        ObjectManager manager = manager();
        for (int i = 0; i < 2; i++) {
            ForeignOwner owner = ObjectConstructionContext.with(manager.services(), 40,
                    () -> new ForeignOwner(spawn()));
            manager.addDynamicObject(owner);
        }
        writeWord2C(manager, 50, 0xABCD);
        RewindRegistry registry = new RewindRegistry();
        registerWord2CRewind(manager, registry);
        var snapshot = registry.capture();
        assertEquals(new ObjectNativeSstWord2C.State(List.of(new ObjectNativeSstWord2C.Cell(50, 0xABCD))),
                snapshot.get(WORD_2C_REWIND_KEY));
        assertThrows(IllegalStateException.class, () -> currentOwner(manager, 40));
        assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
        assertThrows(IllegalStateException.class, () -> writeWord2C(manager, 40, 1));
        assertThrows(IllegalStateException.class, () -> addWord2C(manager, 40, -1));
        assertThrows(IllegalStateException.class, () -> clearWord2C(manager, 40));
        assertEquals(snapshot.get(WORD_2C_REWIND_KEY), registry.capture().get(WORD_2C_REWIND_KEY));
    }

    @Test void knownWordCaptureStillRejectsDistinctLiveReceivers() {
        ObjectManager manager = manager();
        add(manager, 40, 5);
        WordOwner second = ObjectConstructionContext.with(manager.services(), 40, () -> {
            WordOwner owner = new WordOwner(spawn());
            owner.word = 5;
            return owner;
        });
        manager.addDynamicObject(second);
        assertThrows(IllegalStateException.class, () -> manager.nativeSstWord2C().capture());
        assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
    }

    @Test void explicitClearThenOrphanSubtractionsWrapAndDoNotAllocate() {
        ObjectManager manager = manager();
        clearWord2C(manager, 40);
        assertEquals(0, readWord2C(manager, 40));
        assertEquals(0xFFFF, addWord2C(manager, 40, -1));
        assertEquals(0xFFFE, addWord2C(manager, 40, -1));
        assertNull(currentOwner(manager, 40));
        assertEquals(0, manager.getAllocatedSlotCount());
        clearWord2C(manager, 40);
        assertEquals(0, readWord2C(manager, 40));
    }

    @Test void literalWritesAndAdditionAreWordWidth() {
        ObjectManager manager = manager();
        writeWord2C(manager, 40, 0x12345);
        assertEquals(0x2345, readWord2C(manager, 40));
        assertEquals(0x2344, addWord2C(manager, 40, 0xFFFF));
        writeWord2C(manager, 40, -1);
        assertEquals(0, addWord2C(manager, 40, 1));
    }

    @Test void typedLiveWritesAndFullClearReachTheActualReceiver() {
        ObjectManager manager = manager();
        WordOwner owner = add(manager, 40, 0x1234);
        assertEquals(0x1233, addWord2C(manager, 40, -1));
        assertEquals(0x1233, owner.word);
        owner.word = 0x8123; // A proven owner's own native field write is the live projection.
        assertEquals(0x8123, readWord2C(manager, 40));
        clearWord2C(manager, 40);
        assertEquals(0, owner.word);
        assertSame(owner, currentOwner(manager, 40), "The word port does not retire the ID or allocator");
    }

    @Test void supportedReuseImportsRetainedOrphanWordInsteadOfConstructorZero() {
        ObjectManager manager = manager();
        clearWord2C(manager, 40);
        addWord2C(manager, 40, -1);
        WordOwner next = imported(manager, 40);
        manager.addDynamicObject(next);
        assertEquals(0xFFFF, next.word);
        assertEquals(0xFFFE, addWord2C(manager, 40, -1));
        assertEquals(0xFFFE, next.word);
    }

    @Test void captureOfAnExistingIdentitySamplesItsLiveFieldWithoutRepeatingBirthValidation() {
        ObjectManager manager = manager();
        WordOwner owner = add(manager, 40, 5);
        owner.word = 0x8765;
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registerWord2CRewind(manager, registry);
        var frame = registry.capture();
        writeWord2C(manager, 40, 9);
        registry.restore(frame);
        assertEquals(0x8765, readWord2C(manager, 40));
    }

    @Test void defaultZeroPublicationCannotOverwriteOrphanResidue() {
        ObjectManager manager = manager();
        writeWord2C(manager, 40, 0xFFFF);
        WordOwner wrong = ObjectConstructionContext.with(manager.services(), 40, () -> new WordOwner(spawn()));
        assertThrows(IllegalStateException.class, () -> manager.addDynamicObject(wrong));
        assertNull(currentOwner(manager, 40));
        assertEquals(0xFFFF, readWord2C(manager, 40));
    }

    @Test void unsupportedPublicationInvalidatesKnowledgeAndDoesNotMutateForeignFields() {
        ObjectManager manager = manager();
        writeWord2C(manager, 40, 0x4567);
        ForeignOwner foreign = ObjectConstructionContext.with(manager.services(), 40, () -> new ForeignOwner(spawn()));
        manager.addDynamicObject(foreign);
        assertThrows(UnsupportedOperationException.class, () -> readWord2C(manager, 40));
        assertThrows(UnsupportedOperationException.class, () -> writeWord2C(manager, 40, 1));
        assertThrows(UnsupportedOperationException.class, () -> addWord2C(manager, 40, -1));
        assertEquals(0xABCD, foreign.unrelated);
        manager.removeDynamicObject(foreign);
        assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
    }

    @Test void explicitErasureCanEstablishZeroForAnUnsupportedOwnerBeingRetired() {
        ObjectManager manager = manager();
        ForeignOwner foreign = ObjectConstructionContext.with(manager.services(), 40, () -> new ForeignOwner(spawn()));
        manager.addDynamicObject(foreign);
        clearWord2C(manager, 40);
        assertThrows(UnsupportedOperationException.class, () -> readWord2C(manager, 40));
        manager.removeDynamicObject(foreign);
        assertEquals(0, readWord2C(manager, 40));
    }

    @Test void freeSlotPartialWriterPreservesOrphanMemory() {
        ObjectManager manager = manager();
        int slot = manager.firstDynamicSlot();
        writeWord2C(manager, slot, 0xFFFE);
        WordOwner next = createUnchecked(manager, ignored -> imported(manager, slot));
        assertEquals(slot, next.getSlotIndex());
        assertEquals(0xFFFE, readWord2C(manager, slot));
    }

    @Test void occupiedPartialWriterPreservesWordIdentityAndDoesNotUnloadOldOwner() {
        ObjectManager manager = manager();
        WordOwner old = null;
        for (int slot = manager.firstDynamicSlot(); slot < manager.getLastDynamicSlotExclusive(); slot++)
            old = add(manager, slot, slot * 17);
        int last = manager.getLastDynamicSlotExclusive() - 1;
        WordOwner displaced = old;
        var before = manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(e -> e.slotIndex() == last).findFirst().orElseThrow().objectId();
        WordOwner replacement = createUnchecked(manager, ignored -> imported(manager, last));
        assertSame(replacement, currentOwner(manager, last));
        assertEquals(last * 17, readWord2C(manager, last));
        assertEquals(0, displaced.unloads);
        var after = manager.rewindSnapshottable().capture().dynamicObjects().stream()
                .filter(e -> e.slotIndex() == last).findFirst().orElseThrow().objectId();
        assertEquals(before, after, "A partial code write retains the physical rewind identity");
        clearWord2C(manager, last);
        assertEquals(0, replacement.word);
    }

    @Test void failedOrNullFactoryDoesNotDetachOwnerOrChangeItsWord() {
        ObjectManager manager = manager();
        for (int slot = manager.firstDynamicSlot(); slot < manager.getLastDynamicSlotExclusive(); slot++)
            add(manager, slot, 0x1280 + slot);
        int last = manager.getLastDynamicSlotExclusive() - 1;
        ObjectInstance owner = currentOwner(manager, last);
        var captured = manager.nativeSstWord2C().capture();
        WordOwner.reads = 0;
        assertThrows(IllegalArgumentException.class, () -> createUnchecked(manager, ignored -> { throw new IllegalArgumentException("factory"); }));
        assertThrows(IllegalStateException.class, () -> createUnchecked(manager, ignored -> null));
        assertEquals(0, WordOwner.reads, "Failed factories must not sample a $2C preservation callback");
        assertSame(owner, currentOwner(manager, last));
        assertEquals(captured, manager.nativeSstWord2C().capture());
    }

    @Test void successfulFactorySamplesTheOldWordAfterConstructionBeforeDetachingItsOwner() {
        ObjectManager manager = manager();
        WordOwner old = null;
        for (int slot = manager.firstDynamicSlot(); slot < manager.getLastDynamicSlotExclusive(); slot++)
            old = add(manager, slot, slot);
        int last = manager.getLastDynamicSlotExclusive() - 1;
        WordOwner displaced = old;
        WordOwner next = createUnchecked(manager, ignored -> {
            assertSame(displaced, currentOwner(manager, last));
            displaced.word = 0xABCD;
            // A proven direct old-field copy deliberately avoids readWord2C's cache.
            WordOwner replacement = new WordOwner(spawn());
            replacement.word = displaced.word;
            return replacement;
        });
        assertEquals(0xABCD, readWord2C(manager, last));
        assertSame(next, currentOwner(manager, last));
        assertEquals(0, displaced.unloads);
    }

    @Test void typedProjectionReadWriteAndVerificationUseTheReceiverCallbackBoundary() {
        List<String> calls = new ArrayList<>();
        ObjectCallbackBoundary boundary = new ObjectCallbackBoundary() {
            @Override public <T> T call(String owner, Supplier<T> action) {
                calls.add(owner);
                return action.get();
            }
        };
        ObjectCallbackRouter router = new ObjectCallbackRouter(new CallbackRegistry(boundary));
        WordOwner owner = new WordOwner(spawn());
        router.registerOwnerIfAbsent(owner, "receiver");
        ObjectNativeSstWord2C state = new ObjectNativeSstWord2C(128,
                slot -> slot == 40 ? owner : null, router, () -> false);
        state.clear(40);
        calls.clear();
        assertEquals(0xFFFF, state.add(40, -1));
        assertEquals(0xFFFF, owner.word);
        assertEquals(List.of("receiver", "receiver", "receiver"), calls);
    }

    @Test void probesAndActualReconstructionCannotAccessTheLiveWordTimeline() {
        ObjectManager manager = manager();
        writeWord2C(manager, 40, 0xFFFF);
        for (boolean probe : new boolean[] {true, false}) {
            Runnable access = () -> {
                assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
                assertThrows(IllegalStateException.class, () -> clearWord2C(manager, 40));
                assertThrows(IllegalStateException.class, () -> writeWord2C(manager, 40, 3));
                assertThrows(IllegalStateException.class, () -> addWord2C(manager, 40, -1));
                preserveWord2C(manager, 40);
            };
            if (probe) ObjectConstructionContext.withProbeConstruction(() -> { access.run(); return null; });
            else ObjectConstructionContext.withRewindActiveRestore(() -> { access.run(); return null; });
        }
        assertEquals(0xFFFF, readWord2C(manager, 40));
    }

    @Test void registeredDestructiveRestoreRetainsEmptyAndRecreatedLiveWordsAndForwardReplay() {
        ObjectManager manager = manager();
        WordOwner owner = add(manager, 40, 5);
        writeWord2C(manager, 50, 0xFFFF);
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registerWord2CRewind(manager, registry);
        var snapshot = registry.capture();
        manager.reset(0);
        add(manager, 40, 0x8123);
        writeWord2C(manager, 50, 12);
        registry.restore(snapshot);
        WordOwner restored = (WordOwner) currentOwner(manager, 40);
        assertNotSame(owner, restored);
        assertEquals(5, restored.word);
        assertEquals(0xFFFF, readWord2C(manager, 50));
        assertEquals(4, addWord2C(manager, 40, -1));
        assertEquals(0xFFFE, addWord2C(manager, 50, -1));
        var forward = registry.capture();
        registry.restore(snapshot);
        addWord2C(manager, 40, -1); addWord2C(manager, 50, -1);
        assertEquals(forward.get(WORD_2C_REWIND_KEY), registry.capture().get(WORD_2C_REWIND_KEY));
        assertEquals(4, ((WordOwner) currentOwner(manager, 40)).word);
    }

    @Test void missingAdapterStateInvalidatesUnownedResidueWithoutInventingZero() {
        ObjectManager manager = manager();
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        var old = registry.capture();
        registerWord2CRewind(manager, registry);
        writeWord2C(manager, 40, 0xFFFF);
        registry.restore(old);
        assertThrows(IllegalStateException.class, () -> readWord2C(manager, 40));
    }

    @Test void snapshotsCopyValuesAndRejectMalformedStateBeforeMutation() {
        ObjectManager manager = manager();
        writeWord2C(manager, 40, 0xFFFF);
        var state = manager.nativeSstWord2C();
        List<ObjectNativeSstWord2C.Cell> cells = new ArrayList<>(state.capture().cells());
        var copied = new ObjectNativeSstWord2C.State(cells);
        cells.clear();
        assertEquals(1, copied.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> copied.cells().clear());
        assertThrows(IllegalArgumentException.class, () -> state.restore(new ObjectNativeSstWord2C.State(List.of(
                new ObjectNativeSstWord2C.Cell(40, 1), new ObjectNativeSstWord2C.Cell(40, 2)))));
        assertThrows(IllegalArgumentException.class, () -> state.restore(new ObjectNativeSstWord2C.State(List.of(
                new ObjectNativeSstWord2C.Cell(40, -1)))));
        assertThrows(IllegalArgumentException.class, () -> readWord2C(manager, manager.getLastProcessSlotExclusive()));
        assertEquals(0xFFFF, readWord2C(manager, 40));
    }

    private ObjectManager manager() {
        Camera camera = new Camera();
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null, graphics, camera, services);
        return holder[0];
    }

    private static WordOwner add(ObjectManager manager, int slot, int word) {
        writeWord2C(manager, slot, word);
        WordOwner owner = imported(manager, slot);
        manager.addDynamicObject(owner);
        return owner;
    }

    private static WordOwner imported(ObjectManager manager, int slot) {
        return ObjectConstructionContext.with(manager.services(), slot, () -> {
            WordOwner owner = new WordOwner(spawn());
            owner.word = readWord2C(manager, slot);
            return owner;
        });
    }

    private static ObjectSpawn spawn() { return new ObjectSpawn(100, 100, 1, 0, 0, false, 0); }

    public static class WordOwner extends AbstractObjectInstance implements SpawnRewindRecreatable,
            NativeSstWord2CSource, NativeSstStateSource {
        private static int reads;
        private int word;
        private int unloads;
        public WordOwner(ObjectSpawn spawn) { super(spawn, "WordOwner"); }
        @Override public int nativeWord2C() { reads++; return word; }
        @Override public void writeNativeWord2C(int value) { word = value & 0xFFFF; }
        @Override public NativeSstState nativeSstState() { return NativeSstState.CLEARED; }
        @Override public void onUnload() { unloads++; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private record CallbackRegistry(ObjectCallbackBoundary boundary) implements ObjectRegistry, Supplier<Object> {
        @Override public Object get() { return Map.entry(boundary, (Supplier<String>) () -> "caller"); }
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "fixture"; }
    }

    public static final class ForeignOwner extends AbstractObjectInstance {
        private int unrelated = 0xABCD;
        ForeignOwner(ObjectSpawn spawn) { super(spawn, "Unprojected"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
