package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Declared native eye -> independent FlickerMove -> Go_Delete -> clear boundaries. */
class TestDezMinibossEyeRetirement {
    @AfterEach void resetBounds() { AbstractObjectInstance.resetCameraBoundsForTests(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void conversionFinishesEyeTrackingThenRetiresTheLinkBeforeTheFirstDebrisMove(boolean left) throws Exception {
        var h = new Harness(left);
        h.step(0);
        assertSame(h.parent(), h.eye().parentForTest(), "live eye retains its actual owner");
        h.parent().status = 0x80;
        h.parent().writeX(0x3748);
        h.parent().writeY(0x2C8);
        h.step(1);
        var eye = h.eye();
        assertEquals(0x3748 + (left ? -12 : 12), eye.getX());
        assertEquals(0x2C8, eye.getY(), "conversion draws final eye position without moving");
        assertTrue(eye.visible);
        assertEquals(left ? -0x300 : 0x300, eye.xVelocity);
        assertEquals(-0x200, eye.yVelocity);
        assertEquals(0, eye.getCollisionFlags());
        assertFalse(eye.publishesTouchResponseListEntryThisFrame());
        assertNull(eye.parentForTest(), "Obj_FlickerMove never reads parent3 again");
        int x = eye.getX(), y = eye.getY();
        h.step(2);
        assertEquals(x + (left ? -3 : 3), eye.getX());
        assertEquals(y - 2, eye.getY());
        assertEquals(-0x1C8, eye.yVelocity);
        assertFalse(eye.visible, "bchg tests the old bit, first debris pass is hidden");
        h.step(3);
        assertTrue(eye.visible);
    }

    @Test void detachedDebrisRecreatesAndReplaysWithADifferentParentSlotOccupant() throws Exception {
        var h = new Harness(false);
        h.step(0);
        var rewind = h.manager.rewindSnapshottable();
        var live = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(live); same(live, rewind.capture());
            assertSame(h.parent(), h.eye().parentForTest());
            h.parent().status = 0x80;
            h.step(1);
            h.manager.removeDynamicObject(h.parent());
            var decoy = h.manager.createDynamicObjectAtSlot(() -> new Parent(
                    new ObjectSpawn(0x3000,0x400,0xA6,0,0,false,0)), 4);
            decoy.collisionProperty = 123;
            assertNull(h.eye().parentForTest());
            var detached = rewind.capture();
            for (int entry = 2; entry <= 12; entry++) h.step(entry);
            var forward = rewind.capture();
            var oldEye = h.eye();
            rewind.restore(detached); same(detached, rewind.capture());
            assertNotSame(oldEye, h.eye(), "forced recreation cannot reuse a stale local eye");
            assertNull(h.eye().parentForTest());
            assertEquals(123, h.parent().collisionProperty);
            for (int entry = 2; entry <= 12; entry++) h.step(entry);
            same(forward, rewind.capture());
        }
    }

    @Test void debrisCullMarksThenClearsOnTheFollowingDispatchAndRewindsBothBoundaries() throws Exception {
        var h = new Harness(false);
        h.step(0); h.parent().status = 0x80; h.step(1);
        h.manager.removeDynamicObject(h.parent());
        h.eye().writeY(0x28C + 0x200);
        var rewind = h.manager.rewindSnapshottable();
        var beforeCull = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(beforeCull); same(beforeCull, rewind.capture());
            h.step(2);
            assertNotNull(h.eye()); assertTrue(h.eye().pendingDelete);
            assertFalse(h.eye().isDestroyed()); assertFalse(h.eye().visible);
            var marker = rewind.capture();
            h.step(3); assertNull(h.eye());
            var cleared = rewind.capture();
            rewind.restore(marker); same(marker, rewind.capture());
            assertNull(h.eye().parentForTest());
            h.step(3); assertNull(h.eye()); same(cleared, rewind.capture());
        }
    }

    @Test void strictClosureStillRejectsAnUnregisteredOwnerWhileTheEyeRoutineIsLive() throws Exception {
        var h = new Harness(false);
        h.step(0);
        var actualParent = h.parent();
        h.manager.removeDynamicObject(actualParent);
        h.manager.createDynamicObjectAtSlot(() -> new Parent(
                new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)), 4);
        assertSame(actualParent, h.eye().parentForTest(), "a live link cannot silently adopt a decoy");
        var failure = assertThrows(IllegalStateException.class, () -> h.manager.rewindSnapshottable().capture());
        assertTrue(failure.getMessage().contains("DezMinibossEye#parent"), failure::getMessage);
    }

    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }

    static final class Parent extends DezMinibossSprite implements RewindRecreatable {
        Parent(ObjectSpawn spawn) { super(spawn,"DeclaredEyeOwner"); codePointer=0x7DE6E; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
    }

    private static final class Harness {
        final ObjectManager manager;
        final TestPlayableSprite player = new TestPlayableSprite();
        Harness(boolean left) throws Exception {
            var camera = mock(Camera.class);
            when(camera.getX()).thenReturn((short)0x3680);
            when(camera.getY()).thenReturn((short)0x28C);
            when(camera.getXCopy()).thenReturn((short)0x3680);
            when(camera.getYCopy()).thenReturn((short)0x28C);
            when(camera.getWidth()).thenReturn((short)320);
            when(camera.getHeight()).thenReturn((short)224);
            player.setCentreXPreserveSubpixel((short)(0x3740 + (left ? -0x100 : 0x100)));
            player.setCentreYPreserveSubpixel((short)0x2C0);
            var rom = mock(RomByteReader.class);
            // sub_7ED6C's capped table entry and Set_IndexedVelocity d0=$14/subtype=0.
            when(rom.readU8(0x7EDBA+20)).thenReturn(12);
            when(rom.readU8(0x7EDBB+20)).thenReturn(5);
            when(rom.readU16BE(0x852F4+0x14)).thenReturn(0x300);
            when(rom.readU16BE(0x852F4+0x16)).thenReturn(0xFE00);
            var services = new TestObjectServices() {
                @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player,List::of); }
            }.withCamera(camera).withRomReader(rom).withGameModule(new Sonic3kGameModule());
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0x3680);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObjectAtSlot(() -> new Parent(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)),4);
            manager.createDynamicObjectAtSlot(() -> new DezMinibossEye(parent()),5);
        }
        Parent parent() { return (Parent)NativeSstSlotAccess.currentOwner(manager,4); }
        DezMinibossEye eye() { return (DezMinibossEye)NativeSstSlotAccess.currentOwner(manager,5); }
        void step(int vIntRunCount) { manager.update(0x3680,player,List.of(),vIntRunCount); }
    }
}
