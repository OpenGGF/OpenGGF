package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** S3KBadnikProjectile_Init/Main, Sprite_CheckDeleteTouchXY and Go_Delete_Sprite. */
class TestS3kBadnikProjectileNativeLifetime {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void initFallsThroughMovementAndKeepsTheProjectileAboveTheVisibleViewport() {
        camera(0x26C1, 0x47C);
        var projectile = projectile(0x272D, 0x448, -0x200, -0x400, 0x20);
        assertEquals(0, projectile.getCollisionFlags(),
                "CreateChild2_Complex copies mappings/art but collision waits for SetUp_ObjAttributes");
        assertFalse(projectile.publishesTouchResponseListEntryThisFrame());
        projectile.update(1, player());
        assertEquals(0x272B, projectile.getX());
        assertEquals(0x444, projectile.getY(), "Init falls through Main in its own first dispatch");
        assertFalse(projectile.isDestroyed(), "native XY tail admits the actual -56px position");
        assertEquals(0x98, projectile.getCollisionFlags());
        assertTrue(projectile.publishesTouchResponseListEntryThisFrame());
    }

    @ParameterizedTest @CsvSource({"-129,false", "-128,true", "384,true", "385,false"})
    void nativeUnsignedVerticalWindowPublishesOrRetiresAtItsInclusiveBoundaries(int dy, boolean admitted) {
        camera(0x400, 0x500);
        var projectile = projectile(0x500, 0x500 + dy, 0, 0, 0);
        projectile.update(1, player());
        assertFalse(projectile.isDestroyed(), "Go_Delete_Sprite retains this physical record for one dispatch");
        assertEquals(admitted, projectile.publishesTouchResponseListEntryThisFrame());
        assertEquals(admitted ? 0x98 : 0, projectile.getCollisionFlags());
        projectile.update(2, player());
        assertEquals(!admitted, projectile.isDestroyed(), "Delete_Current_Sprite runs on the next dispatch");
    }

    @ParameterizedTest @CsvSource({"768,false", "896,true", "1663,true", "1664,false"})
    void nativeUnsignedCoarseXWindowUsesTheAlignedBackEdge(int x, boolean admitted) {
        camera(0x400, 0x500); // coarse back=$380; last admitted chunk=$600..$67F
        var projectile = projectile(x, 0x500, 0, 0, 0);
        projectile.update(1, player());
        assertFalse(projectile.isDestroyed());
        assertEquals(admitted, projectile.publishesTouchResponseListEntryThisFrame());
        projectile.update(2, player());
        assertEquals(!admitted, projectile.isDestroyed());
    }

    @Test void nativeVerticalWordSubtractionWrapsWithoutUsingTheVisibleBottomEdge() {
        camera(0x400, 0xFF80);
        var projectile = projectile(0x500, 0, 0, 0, 0);
        projectile.update(1, player());
        assertFalse(projectile.isDestroyed());
        assertTrue(projectile.publishesTouchResponseListEntryThisFrame(),
                "word subtraction yields +128 before the unsigned $200 comparison");
    }

    @Test void markedPhysicalRecordRestoresAndClearsWithoutMovingOnTheFollowingDispatch() {
        var h = new TestMonkeyDudeNativeSstGraph.Harness();
        when(h.camera.getX()).thenReturn((short)0x400);
        when(h.camera.getY()).thenReturn((short)0x500);
        h.manager.reset(0x400);
        h.manager.createDynamicObjectAtSlot(() -> projectile(0x500, 0x480, 0x80, 0, 0), 43);
        h.manager.update(0x400, h.main, java.util.List.of(), 1);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        when(h.camera.getY()).thenReturn((short)0x600);
        h.manager.update(0x400, h.main, java.util.List.of(), 2);
        var pendingOwner = NativeSstSlotAccess.currentOwner(h.manager, 43);
        assertNotNull(pendingOwner, "native status7 is not Java destruction");
        assertEquals(0x501, pendingOwner.getX());
        assertFalse(pendingOwner.publishesTouchResponseListEntryThisFrame());
        var pending = rewind.capture();
        h.manager.update(0x400, h.main, java.util.List.of(), 3);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 43));
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before);
            assertSnapshot(before, rewind.capture());
            h.manager.update(0x400, h.main, java.util.List.of(), 2);
            assertSnapshot(pending, rewind.capture());
            rewind.restore(pending);
            assertSnapshot(pending, rewind.capture());
            h.manager.update(0x400, h.main, java.util.List.of(), 3);
            assertSnapshot(after, rewind.capture());
        }
    }

    @Test void actualManagedHandBirthRunsTheHigherSlotProjectileInTheSamePassAboveTheViewport() {
        var h = new TestMonkeyDudeNativeSstGraph.Harness();
        when(h.camera.getY()).thenReturn((short)0x4A2);
        h.body(4, 0x1E58, 1);
        h.step(0); h.step(0);
        for (int frame = 1; frame <= 50; frame++) h.step(frame);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        var rng = h.services.rng().capture();
        Object after = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            if (cycle != 0) { rewind.restore(before); h.services.rng().restore(rng); }
            for (int frame = 51; frame <= 61; frame++) h.step(frame);
            var projectiles = h.manager.getActiveObjects().stream()
                    .filter(o -> !o.isDestroyed()).filter(S3kBadnikProjectileInstance.class::isInstance)
                    .map(S3kBadnikProjectileInstance.class::cast).toList();
            assertEquals(1, projectiles.size(), "the ordinary hand graph admits a real higher-slot coconut");
            var projectile = projectiles.getFirst();
            assertEquals(0x1E64, projectile.getX());
            assertEquals(0x46A, projectile.getY());
            assertEquals(0x98, projectile.getCollisionFlags());
            assertTrue(projectile.publishesTouchResponseListEntryThisFrame());
            if (cycle == 0) after = rewind.capture(); else assertSnapshot(after, rewind.capture());
        }
    }

    @Test void shieldDeflectionDisablesHurtWithoutSuppressingNativeDrawOrListAdmission() {
        camera(0x400, 0x500);
        var render = mock(ObjectRenderManager.class);
        var renderer = mock(PatternSpriteRenderer.class);
        when(render.getRenderer("projectile")).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        var projectile = projectile(0x500, 0x500, 0, 0, 0);
        projectile.setServices(new TestObjectServices() {
            @Override public ObjectRenderManager renderManager() { return render; }
        });
        projectile.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(renderer);
        projectile.update(1, player());
        assertTrue(projectile.onShieldDeflect(player()));
        assertEquals(0, projectile.getCollisionFlags());
        assertTrue(projectile.publishesTouchResponseListEntryThisFrame(),
                "Sprite_CheckDeleteTouchXY runs AddList and Draw independently of collision flags");
        projectile.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(6, 0x500, 0x500, false, false);
    }

    private static void camera(int x, int y) {
        AbstractObjectInstance.updateCameraBounds(x, y, x + 320, y + 224, 0);
    }
    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short)0x500, (short)0x500);
    }
    private static S3kBadnikProjectileInstance projectile(int x, int y, int vx, int vy, int gravity) {
        return new S3kBadnikProjectileInstance(new ObjectSpawn(x, y, 0x8E, 0, 0, false, 0),
                "projectile", 6, x, y, vx, vy, gravity, 0x18, 5, false);
    }
    private static void assertSnapshot(Object expected, Object actual) {
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", expected, actual).isEmpty(),
                () -> RewindSnapshotDiff.diffKey("object-manager", expected, actual).toString());
    }
}
