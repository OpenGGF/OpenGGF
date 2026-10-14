package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal loc_85AD2/loc_85B02 dispatch and recreation; component cameras, not cold routes. */
class TestRhinobotNativeWaitGraph {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void verticalDormancyAndPriorRenderReleaseRecreateInBothDirections(int width) {
        var h = new Harness(width, 0x3FA);
        assertEquals(width, h.camera.getWidth());
        var rewind = h.manager.rewindSnapshottable();
        h.step(0);
        for (int frame = 1; frame <= 45; frame++) {
            h.step(frame);
            assertDormant(h, "X is visible, but the native $20-height placeholder is above the viewport");
        }
        var waiting = rewind.capture();
        h.cameraY = 0x300;
        h.step(46);
        assertDormant(h, "loc_85AD2 reads the prior render sign, not new camera bounds");
        var published = rewind.capture();
        // The saved sign survives a camera move until this object's next dispatch.
        h.cameraY = 0x3FA;
        h.step(47);
        assertDormant(h, "loc_85B02 restores the operation pointer and returns, without Init");
        var released = rewind.capture();
        h.step(48);
        assertEquals(0x0B, h.owner().getCollisionFlags(), "SetUp_ObjAttributesSlotted writes collision only in Init");
        assertEquals(0x2820, h.owner().getX(), "Init does not fall through to MoveSprite2");
        assertEquals(0x320, h.owner().getY());
        var initialized = rewind.capture();
        h.step(49);
        verify(h.services, times(1)).playSfx(anyInt());
        assertEquals(0x2820, h.owner().getX(), "first patrol selects the in-front player and enters charge prep");
        var active = rewind.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(waiting);
            same(waiting, rewind.capture());
            h.cameraY = 0x300;
            h.step(46);
            same(published, rewind.capture());
            h.cameraY = 0x3FA;
            h.step(47);
            same(released, rewind.capture());
            h.step(48);
            same(initialized, rewind.capture());
            h.step(49);
            same(active, rewind.capture());
            rewind.restore(waiting);
            same(waiting, rewind.capture());
            rewind.restore(published);
            same(published, rewind.capture());
            h.step(47);
            same(released, rewind.capture());
            rewind.restore(initialized);
            same(initialized, rewind.capture());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void initiallyVisiblePlacementNeedsDrawThenReleaseThenInit(int flip) {
        var h = new Harness(320, 0x300, flip);
        h.step(0);
        assertDormant(h, "Obj_WaitOffscreen installs Map_Offscreen and queues its first Draw_Sprite");
        h.step(1);
        assertDormant(h, "only the prior sign releases the saved operation, without routine dispatch");
        h.step(2);
        assertEquals(0x0B, h.owner().getCollisionFlags());
        assertEquals(0x2820, h.owner().getX());
        assertEquals(0x320, h.owner().getY());
    }

    @ParameterizedTest @CsvSource({
            "32,0,true", "33,0,false", "-352,0,false", "-351,0,true",
            "0,32,true", "0,33,false", "0,-256,false", "0,-255,true"
    })
    void placeholderUsesLiteralInclusiveLeftTopAndExclusiveRightBottom(int dx, int dy, boolean visible) {
        var h = new Harness(320, 0x320 + dy);
        h.cameraX = 0x2820 + dx;
        h.step(0);
        assertEquals(32, h.owner().getOnScreenHalfWidth());
        assertEquals(32, h.owner().getOnScreenHalfHeight());
        h.step(1);
        assertEquals(0, h.owner().getCollisionFlags(), "release is not Init");
        h.step(2);
        assertEquals(visible ? 0x0B : 0, h.owner().getCollisionFlags());
        assertEquals(visible ? 20 : 32, h.owner().getOnScreenHalfWidth());
        assertEquals(visible ? 16 : 32, h.owner().getOnScreenHalfHeight());
        assertEquals(0x2820, h.owner().getX());
        assertEquals(0x320, h.owner().getY());
        assertEquals(6, h.owner().getSlotIndex(), "the native waiter and continuation retain the exact SST");
    }

    @Test void unpublishedPlacementCannotBorrowACameraOnlySign() {
        var h = new Harness(320, 0x300);
        h.manager.refreshPostCameraRenderState();
        h.manager.refreshPostCameraRenderState();
        h.step(0);
        assertDormant(h, "a never-dispatched SST has not queued Map_Offscreen for Render_Sprites");
        h.step(1);
        assertDormant(h, "the first actual published placeholder releases only its continuation");
        h.step(2);
        assertEquals(0x0B, h.owner().getCollisionFlags());
    }

    @Test void mapOffscreenQueuesNoRhinobotBodyUntilInit() {
        var h = new Harness(320, 0x300);
        h.owner().appendRenderCommands(List.of());
        verifyNoInteractions(h.renderer);
        h.step(0);
        h.owner().appendRenderCommands(List.of());
        verifyNoInteractions(h.renderer);
        h.step(1);
        h.owner().appendRenderCommands(List.of());
        verifyNoInteractions(h.renderer);
        h.step(2);
        h.owner().appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(0, 0x2820, 0x320, false, false);
    }

    @Test void waitingHeaderKeepsClearedPriorityUntilInit() {
        var h = new Harness(320, 0x300);
        assertEquals(0, h.owner().getPriorityBucket(), "fresh placement has a cleared priority word");
        h.step(0);
        assertEquals(0, h.owner().getPriorityBucket(), "Obj_WaitOffscreen writes dimensions, not priority");
        h.step(1);
        assertEquals(0, h.owner().getPriorityBucket(), "loc_85B02 does not execute the attribute loader");
        h.step(2);
        assertEquals(5, h.owner().getPriorityBucket(), "ObjSlot_Rhinobot publishes $280 in Init");
    }

    @Test void releaseSkipsNewCoarseCutoffThenInitRetainsNativeDeleteMarker() {
        var h = new Harness(320, 0x300);
        h.step(0);
        var rewind = h.manager.rewindSnapshottable();
        var published = rewind.capture();
        h.cameraX = 0x2C00;
        h.step(1);
        assertNotNull(h.owner(), "loc_85B02 returns before either coarse-X deletion tail");
        assertEquals(0, h.owner().getCollisionFlags());
        var released = rewind.capture();
        h.step(2);
        assertNotNull(h.owner(), "Go_Delete_SpriteSlotted publishes status7/code, not an immediate clear");
        assertEquals(0, h.owner().getCollisionFlags(), "the native delete marker does not AddList");
        assertEquals(5, h.owner().getPriorityBucket());
        var marked = rewind.capture();
        clearInvocations(h.renderer);
        h.owner().appendRenderCommands(List.of());
        verifyNoInteractions(h.renderer);
        h.step(3);
        assertNull(h.owner(), "the following Delete_Current_Sprite clears the physical SST");
        var deleted = rewind.capture();
        for (int frame = 4; frame <= 48; frame++) h.step(frame);
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(published);
            same(published, rewind.capture());
            h.step(1);
            same(released, rewind.capture());
            h.step(2);
            same(marked, rewind.capture());
            h.step(3);
            same(deleted, rewind.capture());
            for (int frame = 4; frame <= 48; frame++) h.step(frame);
            same(after, rewind.capture());
            rewind.restore(published);
            same(published, rewind.capture());
            rewind.restore(marked);
            same(marked, rewind.capture());
            h.step(3);
            same(deleted, rewind.capture());
            rewind.restore(released);
            same(released, rewind.capture());
        }
    }

    @Test void undrawnWaiterOutsideCoarseWindowClearsImmediately() {
        var h = new Harness(320, 0x3FA);
        h.step(0);
        var rewind = h.manager.rewindSnapshottable();
        var waiting = rewind.capture();
        h.cameraX = 0x2C00;
        h.step(1);
        assertNull(h.owner(), "loc_85AFC jumps to Delete_Current_Sprite in this dispatch");
        var deleted = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(waiting);
            same(waiting, rewind.capture());
            h.step(1);
            same(deleted, rewind.capture());
            rewind.restore(waiting);
            same(waiting, rewind.capture());
        }
    }

    private static void assertDormant(Harness h, String reason) {
        assertNotNull(h.owner(), reason);
        assertEquals(0x2820, h.owner().getX(), reason);
        assertEquals(0x320, h.owner().getY(), reason);
        assertEquals(0, h.owner().getCollisionFlags(), reason);
    }

    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }

    static final class Harness {
        final Camera camera = mock(Camera.class);
        final TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x2800, (short) 0x320);
        final com.openggf.level.render.PatternSpriteRenderer renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        final TestObjectServices services;
        final ObjectManager manager;
        int cameraX = 0x2725;
        int cameraY;
        Harness(int width, int initialY) { this(width, initialY, 0); }
        Harness(int width, int initialY, int flip) {
            cameraY = initialY;
            main.setCentreX((short) 0x2800);
            main.setCentreY((short) 0x320);
            when(camera.getX()).thenAnswer(i -> (short) cameraX);
            when(camera.getXCopy()).thenAnswer(i -> (short) cameraX);
            when(camera.getY()).thenAnswer(i -> (short) cameraY);
            when(camera.getYCopy()).thenAnswer(i -> (short) cameraY);
            when(camera.getWidth()).thenReturn((short) width);
            when(camera.getHeight()).thenReturn((short) 224);
            when(camera.getFocusedSprite()).thenReturn(main);
            var render = mock(ObjectRenderManager.class);
            when(render.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.RHINOBOT)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            var query = new ObjectPlayerQuery(() -> main, List::of);
            services = spy(new TestObjectServices() {
                @Override public ObjectPlayerQuery playerQuery() { return query; }
                @Override public ObjectRenderManager renderManager() { return render; }
            }.withCamera(camera).withGameModule(new Sonic3kGameModule())
                    .withGraphicsManager(GraphicsManager.getInstance()));
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0x2725);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObjectAtSlot(() -> new RhinobotBadnikInstance(
                    new ObjectSpawn(0x2820, 0x320, 0x8D, 0, flip, false, 0)), 6);
        }
        RhinobotBadnikInstance owner() {
            return (RhinobotBadnikInstance) NativeSstSlotAccess.currentOwner(manager, 6);
        }
        void step(int frame) {
            manager.update(cameraX, main, List.of(), frame);
            manager.refreshPostCameraRenderState();
        }
    }
}
