package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Native expectations from immutable skdisasm 8b2cf3c4178f2382f844da67798de4befc50c5e0:
 * loc_1B892 / loc_1B8A4 (sonic3k.asm:37649-37681),
 * loc_1B8D2 (sonic3k.asm:37685-37718), CreateNewSprite4 (sonic3k.asm:37934-37944).
 * Historical 2026-10-26 negative: expected two B, observed one. The corrected
 * fixture declares a preceding entry so the retry never reads before the ORT.
 */
class TestTasS3kOrtExhaustion {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void leftwardMidPassExhaustionDuplicatesOnlyAfterConsumingTheLastFreeSlot(int freeSlots) {
        var f = new Fixture(false);
        f.fill(freeSlots);
        f.step(0x380);
        assertEquals(Math.min(freeSlots, 1), f.live(f.b).size());
        assertEquals(freeSlots == 2 ? 1 : 0, f.live(f.a).size());
        assertEquals(ObjectSlotLayout.SONIC_3K.dynamicSlotCount(), f.manager.getAllocatedSlotCount(),
                "the pass actually exhausted SST capacity");
        var afterExhaustion = f.capture();
        assertEquals(freeSlots == 1 ? 4 : 3,
                afterExhaustion.placement().leftCursorIndex());
        assertEquals(freeSlots == 2 ? 2 : 3, afterExhaustion.placement().bwdCounter(),
                "only the reached mid-pass failure separates layout and ORT cursors");
        if (freeSlots > 0) {
            assertEquals(ObjectSlotLayout.SONIC_3K.firstDynamicSlot(), f.live(f.b).getFirst().getSlotIndex());
        }
        f.free(2);
        f.step(0x300);
        assertEquals(freeSlots == 2 ? 2 : 1, f.live(f.a).size(), "a later left crossing actually resumes placement");
        assertEquals(freeSlots == 1 ? 2 : 1, f.live(f.b).size());
        assertTrue(f.live(f.c).isEmpty(), "the mispaired C entry observes B's already-set byte");
        if (freeSlots == 1) {
            var pair = f.live(f.b);
            assertNotSame(pair.get(0), pair.get(1));
            assertNotSame(pair.get(0).getSpawn(), pair.get(1).getSpawn());
            assertEquals(pair.get(0).getSpawn(), pair.get(1).getSpawn(), "immutable canonical source data remains equal");
            assertEquals(List.of(1, 2), pair.stream().map(Dummy::getRespawnStateIndex).sorted().toList());
            assertEquals(List.of(ObjectSlotLayout.SONIC_3K.firstDynamicSlot(),
                            ObjectSlotLayout.SONIC_3K.firstDynamicSlot() + 1),
                    pair.stream().map(Dummy::getSlotIndex).sorted().toList());
        }
    }

    @Test
    void logicalFallbackActorDoesNotPretendToConsumeTheLastNativeSlot() {
        var f = new Fixture(false, false, source -> source.objectId() == 0x42
                ? new LogicalActor(source) : new Dummy(source));
        f.fill(1);
        f.step(0x380);
        assertInstanceOf(LogicalActor.class, f.manager.getActiveObjectForRewind(f.b));
        assertEquals(1, f.live(f.a).size(), "the bare actor returned its loaned SST for A to consume");
        assertEquals(ObjectSlotLayout.SONIC_3K.dynamicSlotCount(), f.manager.getAllocatedSlotCount());
        var placement = f.capture().placement();
        assertEquals(3, placement.leftCursorIndex(), "actual last-slot consumption occurred at A, not B");
        assertEquals(2, placement.bwdCounter());
    }

    @Test
    void forwardExhaustionParksOnTheNextEntryWithoutBackwardDrift() {
        var f = new Fixture(true);
        f.fill(1);
        f.step(0x480);
        assertEquals(1, f.live(f.a).size());
        assertTrue(f.live(f.b).isEmpty());
        var full = f.capture().placement();
        assertEquals(2, full.cursorIndex());
        assertEquals(2, full.fwdCounter());
        f.free(3);
        f.step(0x500);
        assertEquals(1, f.live(f.a).size());
        assertEquals(1, f.live(f.b).size());
        assertEquals(1, f.live(f.c).size());
        var resumed = f.capture().placement();
        assertEquals(4, resumed.cursorIndex());
        assertEquals(4, resumed.fwdCounter());
    }

    @Test
    void multiplePendingEntriesResumeInNativeDescendingOrder() {
        var f = new Fixture(false, true);
        f.fill(1);
        f.step(0x380);
        f.free(4);
        f.step(0x300);
        assertEquals(2, f.live(f.b).size());
        assertEquals(1, f.live(f.middle).size());
        assertEquals(1, f.live(f.a).size());
        assertTrue(f.live(f.c).isEmpty());
        assertEquals(List.of(f.b, f.middle, f.a), f.manager.getActiveObjects().stream()
                .filter(Dummy.class::isInstance).map(Dummy.class::cast)
                .filter(o -> o.getSlotIndex() > ObjectSlotLayout.SONIC_3K.firstDynamicSlot()
                        && o.getSlotIndex() < ObjectSlotLayout.SONIC_3K.firstDynamicSlot() + 4)
                .sorted(Comparator.comparingInt(Dummy::getSlotIndex)).map(Dummy::getSpawn).toList());
    }

    @Test
    void restoreBeforeAndAfterExhaustionReplaysDistinctCyclicOccurrenceGraph() {
        var f = new Fixture(false);
        f.fill(1);
        var before = f.capture();
        f.step(0x380);
        var exhausted = f.capture();
        f.restore(before, 0x400);
        f.step(0x380);
        assertWorld(exhausted, f.capture());
        f.free(3);
        var retry = f.capture();
        f.step(0x300);
        var first = f.capture();
        f.restore(retry, 0x380);
        f.step(0x300);
        assertWorld(first, f.capture());
        var pair = f.live(f.b);
        assertEquals(2, pair.size());
        pair.get(0).linkedRef = pair.get(1);
        pair.get(1).linkedRef = pair.get(0);
        assertEquals(2, pair.get(0).ticks, "the earlier birth has executed two object passes");
        assertEquals(1, pair.get(1).ticks, "the recovered birth has executed one object pass");
        f.manager.setSpawnStateBit(pair.get(0).getSpawn(), 1);
        f.manager.setSpawnStateBit(pair.get(1).getSpawn(), 2);
        assertFalse(f.manager.isSpawnStateBitSet(pair.get(0).getSpawn(), 2));
        assertFalse(f.manager.isSpawnStateBitSet(pair.get(1).getSpawn(), 1));
        f.manager.validateRewindReferenceClosure();
        var graph = f.capture();
        assertEquals(graph.slots().size(), graph.slots().stream().map(ObjectManagerSnapshot.PerSlotEntry::objectId)
                .distinct().count(), "same source never aliases an ObjectRefId");
        f.step(0x300);
        var forward = f.capture();
        f.restore(graph, 0x300);
        var restored = f.live(f.b);
        assertSame(restored.get(1), restored.get(0).linkedRef);
        assertSame(restored.get(0), restored.get(1).linkedRef);
        assertWorld(graph, f.capture());
        f.step(0x300);
        assertWorld(forward, f.capture());
        restored.get(0).linkedRef = new Dummy(null);
        assertThrows(RuntimeException.class, f.manager::validateRewindReferenceClosure,
                "unregistered object links must still fail strictly");
    }

    @Test
    void retiringOneOccurrenceClearsOnlyItsActualRespawnByteAndReloadUsesLowestSlot() {
        var f = new Fixture(false);
        f.fill(1);
        f.step(0x380);
        f.free(3);
        f.step(0x300);
        var pair = f.live(f.b);
        var original = pair.get(0);
        var duplicate = pair.get(1);
        assertEquals(2, original.getRespawnStateIndex());
        assertEquals(1, duplicate.getRespawnStateIndex());
        int freedSlot = original.getSlotIndex();
        original.setDestroyedByOffscreen();
        f.step(0x300);
        assertEquals(List.of(duplicate), f.live(f.b));
        var state = f.capture().placement().objState();
        assertEquals(0, state[2] & 0x80);
        assertEquals(0x80, state[1] & 0x80, "the remaining live occurrence retains its own latch");
        f.step(0x480);
        f.step(0x380);
        assertEquals(List.of(duplicate), f.live(f.b), "ordinary recrossing does not duplicate a latched occurrence");
        duplicate.setDestroyedByOffscreen();
        f.step(0x380);
        f.step(0x480);
        f.step(0x380);
        var fresh = f.live(f.b);
        assertEquals(1, fresh.size());
        assertNotSame(original, fresh.getFirst());
        assertNotSame(duplicate, fresh.getFirst());
        assertEquals(freedSlot, fresh.getFirst().getSlotIndex());
        var unloaded = f.capture();
        f.manager.reset(0x400);
        assertTrue(f.manager.getActiveObjects().isEmpty(), "a load resets both SST and cursor mismatch");
        f.restore(unloaded, 0x380);
        assertWorld(unloaded, f.capture());
    }

    @Test
    void placementHandoffRewindsBothNativeOccurrenceOwnersWithoutMovingSlotsOrIds() {
        var f = new Fixture(false);
        f.fill(1);
        f.step(0x380);
        f.free(4);
        f.step(0x300);
        var parents = f.live(f.b);
        assertEquals(2, parents.size(), "real exhausted placement produces both owners");
        var children = new ArrayList<Dummy>();
        for (var parent : parents) {
            var child = f.manager.createDynamicObject(() -> new Dummy(parent.getSpawn()));
            assertNotNull(child, "the handoff target actually consumes another SST");
            parent.linkedRef = child;
            child.linkedRef = parent;
            children.add(child);
        }
        var allocated = f.capture();
        for (int i = 0; i < parents.size(); i++) {
            var parent = parents.get(i);
            var child = children.get(i);
            int parentSlot = parent.getSlotIndex(), childSlot = child.getSlotIndex();
            assertEquals(-1, child.getRespawnStateIndex());
            assertTrue(f.manager.transferPlacementOwnership(parent, child));
            assertSame(child, f.manager.getActiveObjectForRewind(parent.getSpawn()),
                    "canonical and noncanonical placement lookups retain their exact keys");
            assertEquals(parent.getRespawnStateIndex(), child.getRespawnStateIndex(),
                    "native handoff copies the selected respawn address to the child");
            assertEquals(parentSlot, parent.getSlotIndex());
            assertEquals(childSlot, child.getSlotIndex());
            assertFalse(f.manager.transferPlacementOwnership(parent, child),
                    "the former placement owner cannot transfer it a second time");
        }
        var handed = f.capture();
        assertEquals(allocated.dynamicObjectIdCounter(), handed.dynamicObjectIdCounter());
        for (var parent : parents) {
            int slot = parent.getSlotIndex();
            assertEquals(allocated.slots().stream().filter(e -> e.slotIndex() == slot)
                            .findFirst().orElseThrow().objectId(),
                    handed.dynamicObjects().stream().filter(e -> e.slotIndex() == slot)
                            .findFirst().orElseThrow().objectId());
        }
        for (var child : children) {
            int slot = child.getSlotIndex();
            assertEquals(allocated.dynamicObjects().stream().filter(e -> e.slotIndex() == slot)
                            .findFirst().orElseThrow().objectId(),
                    handed.slots().stream().filter(e -> e.slotIndex() == slot)
                            .findFirst().orElseThrow().objectId());
        }
        f.step(0x300);
        var forward = f.capture();
        f.restore(handed, 0x300);
        assertWorld(handed, f.capture());
        f.step(0x300);
        assertWorld(forward, f.capture());
        var owners = children.stream().map(child -> (Dummy)
                f.manager.getActiveObjectForRewind(child.getSpawn())).toList();
        for (var owner : owners) {
            assertSame(owner, ((Dummy) owner.linkedRef).linkedRef,
                    "each restored column resolves its own former parent");
        }
        var retiredParent = (Dummy) owners.getFirst().linkedRef;
        owners.getFirst().linkedRef = null;
        retiredParent.linkedRef = null;
        retiredParent.setDestroyed(true);
        f.step(0x300);
        assertSame(owners.getFirst(), f.manager.getActiveObjectForRewind(owners.getFirst().getSpawn()),
                "retiring transformed debris never removes its replacement");
        owners.getFirst().setDestroyedByOffscreen();
        f.step(0x300);
        var state = f.capture().placement().objState();
        assertEquals(0, state[owners.getFirst().getRespawnStateIndex()] & 0x80,
                "restored child unload clears its actual selected ORT byte");
        assertEquals(0x80, state[owners.get(1).getRespawnStateIndex()] & 0x80,
                "the other live placement occurrence keeps its own loaded latch");
        assertSame(owners.get(1), f.manager.getActiveObjectForRewind(owners.get(1).getSpawn()));
        f.manager.validateRewindReferenceClosure();
    }

    @Test
    void duplicateSolidStandingPushingAndRetirementKeepIndependentPlayerBits() {
        var f = new Fixture(false, false, source -> source.objectId() == 0x42
                ? new SolidDummy(source) : new Dummy(source));
        f.fill(1);
        f.step(0x380);
        f.free(3);
        f.step(0x300);
        var pair = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .sorted(Comparator.comparingInt(SolidDummy::getSlotIndex)).toList();
        assertEquals(2, pair.size(), "real placement exhaustion produced both solid occurrences");
        var original = pair.get(0);
        var duplicate = pair.get(1);
        Sonic p1 = new Sonic("sonic", (short) 0, (short) 0);
        Sonic p2 = new Sonic("tails", (short) 0, (short) 0);
        f.camera.setFocusedSprite(p1);
        // Focus binding relocates the camera to the player's initial position.
        // Restore this component fixture's declared native loading/contact window.
        f.camera.setX((short) 0x300);
        f.camera.setY((short) 0x80);
        f.services.withSidekicks(List.of(p2));
        f.manager.solidContacts().beginInlineFrame(p1, List.of(p2), false);
        seat(f.manager, original, p1);
        seat(f.manager, duplicate, p2);
        assertTrue(f.manager.hasObjectStandingBit(p1, original));
        assertFalse(f.manager.hasObjectStandingBit(p1, duplicate));
        assertTrue(f.manager.hasObjectStandingBit(p2, duplicate));
        assertFalse(f.manager.hasObjectStandingBit(p2, original));
        var standing = f.capture();
        // S3K preserves a newly seated bit for the remainder of that frame;
        // exercise the real air-unseat branch on the following contact frame.
        f.manager.solidContacts().beginInlineFrame(p1, List.of(p2), false);
        p1.setAir(true);
        f.manager.solidContacts().processParticipantCheckpoint(original, p1, false);
        assertFalse(f.manager.hasObjectStandingBit(p1, original));
        assertTrue(f.manager.hasObjectStandingBit(p2, duplicate), "unseating P1 leaves P2's other SST untouched");
        f.restore(standing, 0x300);
        assertWorld(standing, f.capture());
        original = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .min(Comparator.comparingInt(SolidDummy::getSlotIndex)).orElseThrow();
        duplicate = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .max(Comparator.comparingInt(SolidDummy::getSlotIndex)).orElseThrow();
        p1.setAir(false);
        p1.setOnObject(true);
        original.setDestroyed(true);
        f.step(0x300);
        assertTrue(f.manager.hasObjectStandingBit(p2, duplicate), "removing the canonical owner cannot clear the duplicate");
        assertTrue(p1.isOnObject(), "deleting the object slot does not rewrite the player slot");

        f.restore(standing, 0x300);
        original = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .min(Comparator.comparingInt(SolidDummy::getSlotIndex)).orElseThrow();
        duplicate = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .max(Comparator.comparingInt(SolidDummy::getSlotIndex)).orElseThrow();
        f.manager.solidContacts().reset();
        f.manager.solidContacts().beginInlineFrame(p1, List.of(p2), false);
        push(f.manager, original, p1);
        push(f.manager, duplicate, p2);
        assertTrue(f.manager.hasObjectPushingBit(p1));
        assertTrue(f.manager.hasObjectPushingBit(p2));
        var pushing = f.capture();
        assertEquals(pushing.slots().size(), pushing.slots().stream()
                .map(ObjectManagerSnapshot.PerSlotEntry::objectId).distinct().count());
        assertTrue(f.manager.solidContacts().releaseObjectPushLatch(p1, original));
        assertTrue(f.manager.hasObjectPushingBit(p2), "clearing P1's bit does not clear the other occurrence");
        f.restore(pushing, 0x300);
        assertWorld(pushing, f.capture());
        duplicate = f.manager.activeObjectsOfType(SolidDummy.class).stream()
                .max(Comparator.comparingInt(SolidDummy::getSlotIndex)).orElseThrow();
        duplicate.setDestroyed(true);
        f.step(0x300);
        assertTrue(f.manager.hasObjectPushingBit(p1), "duplicate removal does not evict the equal canonical source key");
        assertFalse(f.manager.hasObjectPushingBit(p2), "the removed occurrence's own bit expires");
    }

    private static void seat(ObjectManager manager, SolidDummy solid, Sonic player) {
        assertTrue(solid.isWithinSolidContactBounds(), "copied render-camera bounds reach the solid checkpoint");
        player.setCentreX((short) solid.getX());
        player.setCentreY((short) (solid.getY() - 4 - solid.getSolidParams().groundHalfHeight()
                - player.getYRadius() + 8));
        player.setAir(true);
        player.setOnObject(false);
        player.setYSpeed((short) 0x100);
        manager.solidContacts().processParticipantCheckpoint(solid, player, false);
        assertTrue(manager.isRidingObject(player, solid), "actual SolidObject checkpoint seats the participant");
    }

    private static void push(ObjectManager manager, SolidDummy solid, Sonic player) {
        assertTrue(solid.isWithinSolidContactBounds(), "declared camera bounds reach the side-contact checkpoint");
        player.setCentreX((short) (solid.getX() - 15));
        player.setCentreY((short) (solid.getY() - 19));
        player.setAir(false);
        player.setOnObject(false);
        player.setYSpeed((short) 0);
        player.setXSpeed((short) 0x100);
        player.setGSpeed((short) 0x100);
        var contact = manager.solidContacts().processParticipantCheckpoint(solid, player, false);
        assertTrue(player.getPushing(), () -> "actual side contact publishes Status_Push: "
                + contact.perPlayer().get(player) + " actor=" + solid.getX() + "," + solid.getY()
                + " visible=" + solid.isWithinSolidContactBounds() + " player=" + player.getCentreX()
                + "," + player.getCentreY() + " radius=" + player.getYRadius());
    }

    private static void assertWorld(ObjectManagerSnapshot expected, ObjectManagerSnapshot actual) {
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", expected, actual),
                () -> RewindSnapshotDiff.diffKey("object-manager", expected, actual).toString());
    }

    private static final class Fixture {
        final Camera camera;
        final ObjectSpawn a, b, c, middle;
        final ObjectManager manager;
        final TestObjectServices services;
        int frame;
        Fixture(boolean forward) { this(forward, false); }
        Fixture(boolean forward, boolean multiple) { this(forward, multiple, Dummy::new); }
        Fixture(boolean forward, boolean multiple, java.util.function.Function<ObjectSpawn, ObjectInstance> factory) {
            TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
            var viewport = mock(SonicConfigurationService.class);
            when(viewport.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) 320);
            when(viewport.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short) 224);
            camera = new Camera(viewport);
            camera.setX((short) 0x400);
            // Keep the declared actors at Y=$100 inside the 224px viewport
            // as well as Load_Sprites' broader admission band.
            camera.setY((short) 0x80);
            camera.setMinY((short) 0);
            var entries = new ArrayList<ObjectSpawn>();
            entries.add(spawn(0x100, 0x40, 0));
            a = spawn(forward ? 0x690 : 0x310, 0x41, 1);
            entries.add(a);
            middle = multiple ? spawn(0x315, 0x44, 2) : null;
            if (multiple) entries.add(middle);
            b = spawn(forward ? 0x6A0 : 0x320, 0x42, entries.size());
            entries.add(b);
            c = spawn(forward ? 0x6B0 : 0x1000, 0x43, entries.size());
            entries.add(c);
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn entry) { return factory.apply(entry); }
                @Override public void reportCoverage(List<ObjectSpawn> entries) { }
                @Override public String getPrimaryName(int id) { return "persistent actor"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            services = new TestObjectServices().withCamera(camera);
            manager = new ObjectManager(entries, registry, -1, null, null, null, camera, services);
            services.withDirectObjectManager(manager);
            manager.enableExecThenLoadPlacement();
            manager.enablePermanentDestroyLatch();
            manager.reset(0x400);
            assertTrue(manager.getActiveObjects().isEmpty(), "all declared entries initially outside load window");
        }
        void fill(int free) {
            for (int slot = ObjectSlotLayout.SONIC_3K.firstDynamicSlot() + free;
                 slot < ObjectSlotLayout.SONIC_3K.lastDynamicSlotExclusive(); slot++) {
                manager.addDynamicObjectAtSlot(new Dummy(null), slot);
            }
        }
        void free(int count) {
            manager.getActiveObjects().stream().filter(o -> o.getSpawn() == null)
                    .map(Dummy.class::cast).sorted(Comparator.comparingInt(Dummy::getSlotIndex))
                    .limit(count).toList().forEach(manager::removeDynamicObject);
        }
        void step(int x) {
            camera.setX((short) x);
            camera.setXCopy((short) x);
            camera.setYCopy(camera.getY());
            manager.update(x, null, List.of(), ++frame, false);
        }
        List<Dummy> live(ObjectSpawn source) {
            return manager.getActiveObjects().stream().filter(o -> source.equals(o.getSpawn()))
                    .map(Dummy.class::cast).sorted(Comparator.comparingInt(Dummy::getSlotIndex)).toList();
        }
        ObjectManagerSnapshot capture() { return manager.rewindSnapshottable().capture(); }
        void restore(ObjectManagerSnapshot state, int x) {
            camera.setX((short) x);
            camera.setXCopy((short) x);
            camera.setYCopy(camera.getY());
            manager.rewindSnapshottable().restore(state);
        }
    }

    private static ObjectSpawn spawn(int x, int id, int index) {
        return new ObjectSpawn(x, 0x100, id, 0, 0, false, 0x100, index);
    }

    private record LogicalActor(ObjectSpawn spawn) implements ObjectInstance {
        @Override public ObjectSpawn getSpawn() { return spawn; }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean isDestroyed() { return false; }
        @Override public boolean isHighPriority() { return false; }
        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static final class SolidDummy extends AbstractObjectInstance
            implements SpawnRewindRecreatable, SolidObjectProvider {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        public SolidDummy(ObjectSpawn spawn) { super(spawn, "persistent solid"); }
        @Override public boolean isPersistent() { return true; }
        @Override public SolidObjectParams getSolidParams() { return params; }
        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static final class Dummy extends AbstractObjectInstance implements SpawnRewindRecreatable {
        int ticks;
        AbstractObjectInstance linkedRef;
        public Dummy(ObjectSpawn spawn) { super(spawn, "persistent actor"); }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int frame, PlayableEntity player) { ticks++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
