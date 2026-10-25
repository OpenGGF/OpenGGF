package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shipped Saw_Type3/4 .hide skips deletion, while an untracked OPL_SpawnObj
 * always uses FindFreeObj on cursor re-entry. These synthetic placements use
 * the real S1 counter loader and object walk, independently of trace rows.
 */
class TestSonic1SawPlacementReentry {
    private static final int SAW_X = 0x500;
    private static final int SAW_Y = 0x100;
    // (SAW_X & $FF80) - ((camera - $80) & $FF80) = $280 exactly.
    private static final int IN_RANGE_CAMERA = 0x300;
    private static final int OUT_OF_RANGE_CAMERA = 0x280;

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"3,320", "4,320", "3,528", "4,528"})
    void hiddenUntrackedSawSurvivesWhileCursorReentryUsesLowerHoleAndRewinds(int subtype, int width) {
        Fixture fixture = new Fixture(subtype, false, width);
        ObjectManager manager = fixture.manager;
        Sonic1SawObjectInstance original = fixture.placed();
        ObjectRefId originalId = id(manager, original);
        assertEquals(33, original.getSlotIndex());
        assertEquals(0, original.getCollisionFlags());

        // Its first execution is in range, at the inclusive native boundary.
        // Merely being hidden must not drop the placement-to-instance link.
        fixture.step(IN_RANGE_CAMERA);
        assertSame(original, fixture.placed());
        assertEquals(1, fixture.saws().size());

        // X failure skips Saw_Action's out_of_range; a Y failure does not.
        // This is the first out-of-range execution, not the first update.
        fixture.step(OUT_OF_RANGE_CAMERA);
        assertNull(fixture.placed(), "the old SST must stop suppressing a later untracked load");
        assertSame(original, fixture.saws().getFirst(), "release must preserve the actual saw SST");
        assertEquals(33, original.getSlotIndex());
        assertEquals(originalId, id(manager, original));
        assertFalse(original.isDestroyed());
        assertEquals(0, manager.getPlacementCursorState()[0], "the forward cursor really retreated");
        assertEquals(1, manager.getAllocatedSlotCount());
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        ObjectManagerSnapshot beforeRecross = manager.rewindSnapshottable().capture();
        int beforeFrame = fixture.frame;

        runRecrossAndActivate(fixture, subtype, originalId);
        List<SawState> expected = state(fixture);
        ObjectManagerSnapshot after = manager.rewindSnapshottable().capture();
        ObjectRefId newId = id(manager, fixture.placed());
        assertNotEquals(originalId, newId, "two copies of one placement require distinct rewind identities");

        manager.rewindSnapshottable().restore(beforeRecross);
        fixture.frame = beforeFrame;
        fixture.camera.x = OUT_OF_RANGE_CAMERA;
        fixture.playerAt(SAW_X, SAW_Y);
        Sonic1SawObjectInstance restored = fixture.withId(originalId);
        assertNotSame(original, restored, "destructive restore must recreate the detached SST");
        assertNull(fixture.placed());
        assertEquals(33, restored.getSlotIndex());
        assertEquals(0, restored.getCollisionFlags(), "the later shot state must actually rewind");
        assertArrayEquals(beforeRecross.usedSlotsBits(),
                manager.rewindSnapshottable().capture().usedSlotsBits());

        runRecrossAndActivate(fixture, subtype, originalId);
        assertEquals(expected, state(fixture),
                "restore before cursor recross must reproduce slots, identities and real saw movement");
        assertEquals(newId, id(manager, fixture.placed()));
        assertArrayEquals(after.usedSlotsBits(), manager.rewindSnapshottable().capture().usedSlotsBits());
    }

    private static void runRecrossAndActivate(Fixture fixture, int subtype, ObjectRefId originalId) {
        Sonic1SawObjectInstance old = fixture.withId(originalId);
        fixture.step(IN_RANGE_CAMERA);
        assertEquals(1, fixture.manager.getPlacementCursorState()[0], "ObjPosLoad must cross the entry again");
        assertEquals(2, fixture.saws().size());
        assertEquals(32, fixture.placed().getSlotIndex(), "FindFreeObj must consume the genuine lower hole");
        assertSame(old, fixture.withId(originalId));
        assertEquals(33, old.getSlotIndex(), "the surviving saw never transfers its SST");
        assertEquals(2, fixture.manager.getAllocatedSlotCount());

        for (int i = 0; i < 3; i++) {
            fixture.step(IN_RANGE_CAMERA);
            assertEquals(2, fixture.saws().size(), "no third copy may appear without another cursor crossing");
            assertEquals(32, fixture.placed().getSlotIndex());
        }

        // Both survivors continue through the actual saw activation and motion,
        // rather than keeping only a placeholder/reservation for the first SST.
        fixture.playerAt(subtype == 3 ? SAW_X + 0xC0 : SAW_X - 0xE1, SAW_Y);
        fixture.step(IN_RANGE_CAMERA);
        for (Sonic1SawObjectInstance saw : fixture.saws()) {
            assertEquals(0xA2, saw.getCollisionFlags());
            assertEquals(SAW_X, saw.getX(), "activation skips the SpeedToPos branch");
        }
        fixture.step(IN_RANGE_CAMERA);
        for (Sonic1SawObjectInstance saw : fixture.saws()) {
            assertEquals(SAW_X + (subtype == 3 ? 6 : -6), saw.getX());
            assertEquals(SAW_Y, saw.getY());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void trackedHiddenPlacementKeepsItsLoadedLatchAndCannotDuplicate(int subtype) {
        Fixture fixture = new Fixture(subtype, true, 320);
        Sonic1SawObjectInstance original = fixture.placed();
        ObjectRefId originalId = id(fixture.manager, original);
        fixture.step(OUT_OF_RANGE_CAMERA);
        assertSame(original, fixture.placed(), "OPL_SpawnObj's bset gate still owns tracked entries");
        assertEquals(0, fixture.manager.getPlacementCursorState()[0]);
        fixture.step(IN_RANGE_CAMERA);
        fixture.step(IN_RANGE_CAMERA);
        assertEquals(1, fixture.saws().size());
        assertSame(original, fixture.placed());
        assertEquals(33, original.getSlotIndex());
        assertEquals(originalId, id(fixture.manager, original));
        assertEquals(32, fixture.manager.allocateDynamicSlot(), "the lower hole remains free");
        fixture.manager.releaseDynamicSlot(32);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void pizzaCuttersDeleteNormallyAndReenterAsOneFreshSst(int subtype) {
        Fixture fixture = new Fixture(subtype, false, 320);
        assertNormalDeleteAndReload(fixture);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void dormantYFailureReturnsToNormalDeleteInsteadOfLeaking(int subtype) {
        Fixture fixture = new Fixture(subtype, false, 320);
        // Pass X, fail Y: .return retains Saw_Action's return address.
        fixture.playerAt(subtype == 3 ? SAW_X + 0xC0 : SAW_X - 0xE1, SAW_Y + 0x100);
        assertNormalDeleteAndReload(fixture);
    }

    private static void assertNormalDeleteAndReload(Fixture fixture) {
        Sonic1SawObjectInstance original = fixture.placed();
        ObjectRefId originalId = id(fixture.manager, original);
        fixture.step(OUT_OF_RANGE_CAMERA);
        assertTrue(fixture.saws().isEmpty(), "the normal Saw_Action tail must delete out-of-range SSTs");
        assertEquals(0, fixture.manager.getAllocatedSlotCount());
        fixture.step(IN_RANGE_CAMERA);
        fixture.step(IN_RANGE_CAMERA);
        assertEquals(1, fixture.saws().size());
        assertNotSame(original, fixture.placed());
        assertNotEquals(originalId, id(fixture.manager, fixture.placed()));
        assertEquals(32, fixture.placed().getSlotIndex());
    }

    private static ObjectRefId id(ObjectManager manager, ObjectInstance instance) {
        ObjectRefId id = manager.captureIdentityContext().requireIdentityTable().idFor(instance);
        assertNotNull(id);
        return id;
    }

    private static List<SawState> state(Fixture fixture) {
        return fixture.saws().stream().sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .map(saw -> new SawState(id(fixture.manager, saw), saw.getSlotIndex(),
                        saw.getX(), saw.getY(), saw.getCollisionFlags()))
                .toList();
    }

    private record SawState(ObjectRefId id, int slot, int x, int y, int collisionFlags) { }

    private static final class Fixture {
        final MutableCamera camera;
        final AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        final ObjectSpawn spawn;
        final ObjectManager manager;
        int frame;

        Fixture(int subtype, boolean tracked, int width) {
            camera = new MutableCamera(width);
            spawn = new ObjectSpawn(SAW_X, SAW_Y, 0x6A, subtype, 0, tracked, SAW_Y, 0);
            playerAt(SAW_X, SAW_Y);
            ObjectManager[] holder = new ObjectManager[1];
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new Sonic1SawObjectInstance(spawn); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "Saw"; }
            };
            manager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.enableCounterBasedRespawn();
            manager.reset(camera.x);
            assertTrue(saws().isEmpty(), "the saw begins beyond the real load cursor");

            HoleMarker lower = new HoleMarker(new ObjectSpawn(0x180, SAW_Y, 0x7E, 0, 0, false, 0));
            manager.addDynamicObject(lower);
            assertEquals(32, lower.getSlotIndex());
            // Advance only native $80 chunks: no teleport refresh shortcut.
            for (int x = 0x180; x <= IN_RANGE_CAMERA; x += 0x80) {
                step(x);
            }
            assertEquals(33, placed().getSlotIndex());
            lower.setDestroyed(true);
            step(IN_RANGE_CAMERA);
            assertFalse(manager.getActiveObjects().contains(lower));
            assertEquals(1, manager.getAllocatedSlotCount());
        }

        void step(int cameraX) {
            camera.x = cameraX;
            manager.update(cameraX, player, List.of(), ++frame, false);
            manager.postCameraPlacementUpdate(cameraX);
        }

        void playerAt(int x, int y) {
            when(player.getCentreX()).thenReturn((short) x);
            when(player.getCentreY()).thenReturn((short) y);
        }

        Sonic1SawObjectInstance placed() {
            return (Sonic1SawObjectInstance) manager.getActiveObjectForRewind(spawn);
        }

        List<Sonic1SawObjectInstance> saws() {
            return manager.activeObjectsOfType(Sonic1SawObjectInstance.class);
        }

        Sonic1SawObjectInstance withId(ObjectRefId identity) {
            return saws().stream().filter(saw -> identity.equals(id(manager, saw))).findFirst().orElseThrow();
        }
    }

    private static final class MutableCamera extends Camera {
        int x = 0x100;
        final short width;
        MutableCamera(int width) { this.width = (short) width; }
        @Override public short getX() { return (short) x; }
        @Override public short getY() { return 0; }
        @Override public short getWidth() { return width; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }

    public static final class HoleMarker extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public HoleMarker(ObjectSpawn spawn) { super(spawn, "lower-hole"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
