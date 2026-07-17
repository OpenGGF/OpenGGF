package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestFbzTechnoSqueek {
    @BeforeEach void bounds() { AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0); }
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    @Test void subtypesSelectExactAxisAndPresentation() {
        TechnoSqueekBadnikInstance normal = new TechnoSqueekBadnikInstance(spawn(0, 0));
        TechnoSqueekBadnikInstance inverted = new TechnoSqueekBadnikInstance(spawn(2, 0));
        TechnoSqueekBadnikInstance vertical = new TechnoSqueekBadnikInstance(spawn(4, 1));
        assertFalse(normal.verticalMotion());
        assertFalse(inverted.verticalMotion());
        assertTrue(inverted.verticalPresentation());
        assertTrue(vertical.verticalMotion());
        assertEquals(0x400, normal.maximumVelocity());
        assertEquals(0x20, normal.acceleration());
    }

    @Test void attachedChildOffsetTablesAreExact() {
        assertArrayEquals(new int[]{0x14,4,0xC,4,0,4}, TechnoSqueekAttachmentObjectInstance.horizontalOffsets());
        assertArrayEquals(new int[]{-4,0x14,-4,0xC,-4,0}, TechnoSqueekAttachmentObjectInstance.verticalOffsets());
    }

    @Test void verticalSubtypePreservesPlacementXFlipWhileForcingYFlip() {
        TechnoSqueekBadnikInstance normalX = new TechnoSqueekBadnikInstance(spawn(4, 0));
        TechnoSqueekBadnikInstance flippedX = new TechnoSqueekBadnikInstance(spawn(4, 1));
        assertTrue(normalX.badnikFacingLeft());
        assertFalse(flippedX.badnikFacingLeft());
        assertTrue(normalX.verticalPresentation());
        assertTrue(flippedX.verticalPresentation());
    }

    @Test void fallingEntryUsesExactVelocityAndOwnAttachment() {
        TechnoSqueekBadnikInstance falling = TechnoSqueekBadnikInstance.falling(spawn(0, 0), true);
        assertEquals(-0x200, falling.xVelocityRaw());
        assertEquals(-0x300, falling.yVelocityRaw());
        assertTrue(falling.fallingEntry());
        ObjectManager manager = mock(ObjectManager.class);
        falling.setServices(new Services(manager));
        int x = falling.getX(), y = falling.getY();
        falling.update(0, null);
        assertEquals(x, falling.getX());
        assertEquals(y, falling.getY());
        verify(manager).addDynamicObjectAfterCurrent(any(TechnoSqueekAttachmentObjectInstance.class));
    }

    @Test void fallingEntryIntegratesLightGravitySnapsAndConvertsWithItsExistingChild() {
        ObjectManager manager = mock(ObjectManager.class);
        TechnoSqueekBadnikInstance falling = TechnoSqueekBadnikInstance.falling(spawn(0, 0), true);
        falling.setServices(new Services(manager));
        int startX = falling.getX(), startY = falling.getY();
        falling.update(0, null);
        TechnoSqueekAttachmentObjectInstance child = falling.attachment();
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(7)))
                    .thenReturn(new TerrainCheckResult(-3, (byte) 0, 1));
            for (int frame = 1; frame <= 24; frame++) falling.update(frame, null);
        }
        assertEquals("MOVING", falling.stateName());
        assertEquals(startX - 0x30, falling.getX());
        assertEquals(startY - 0x29, falling.getY());
        assertEquals(-0x400, falling.xVelocityRaw());
        assertEquals(0, falling.yVelocityRaw());
        assertSame(child, falling.attachment());
        verify(manager, times(1)).addDynamicObjectAfterCurrent(any(TechnoSqueekAttachmentObjectInstance.class));
    }

    @Test void fallingLandingDirectionComesFromSignedLaunchVelocityOnBothSides() {
        for (boolean launchLeft : new boolean[]{true, false}) {
            ObjectManager manager = mock(ObjectManager.class);
            TechnoSqueekBadnikInstance falling = TechnoSqueekBadnikInstance.falling(spawn(0, 0), launchLeft);
            falling.setServices(new Services(manager));
            falling.update(0, null);
            try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
                terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(7)))
                        .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
                for (int frame = 1; frame <= 24; frame++) falling.update(frame, null);
            }
            assertEquals(launchLeft ? -0x400 : 0x400, falling.xVelocityRaw());
            assertEquals(launchLeft, falling.badnikFacingLeft());
        }
    }

    @Test void childCreationIsRawThenNextUpdateAppliesBothPlacementFlipAxes() {
        ObjectManager manager = mock(ObjectManager.class);
        var parent = new TechnoSqueekBadnikInstance(spawn(2, 0));
        parent.setServices(new Services(manager));
        parent.update(0, null);
        parent.update(1, null);
        ArgumentCaptor<AbstractObjectInstance> capture = ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(manager).addDynamicObjectAfterCurrent(capture.capture());
        var child = assertInstanceOf(TechnoSqueekAttachmentObjectInstance.class, capture.getValue());
        child.setServices(new Services(manager));
        assertEquals(0x1014, child.getX());
        assertEquals(0x804, child.getY());
        child.update(1, null); // creation tick: frame 2 and raw table position.
        assertEquals(2, child.mappingFrame());
        child.update(2, null);
        assertEquals(3, child.mappingFrame());
        assertEquals(0x0FEC, child.getX(), "forced X flip mirrors +$14");
        assertEquals(0x7FC, child.getY(), "subtype 2 forced Y flip mirrors +4");
    }

    @Test void rawTurnLastsThirtyThreeUpdatesAndObjWaitReleasesChildOnMovingUpdateSeventeen() {
        ObjectManager manager = mock(ObjectManager.class);
        var parent = new TechnoSqueekBadnikInstance(spawn(0, 0));
        parent.setServices(new Services(manager));
        parent.update(0, null);
        parent.update(1, null);

        for (int frame = 2; frame <= 33; frame++) parent.update(frame, null);
        assertEquals("TURNING", parent.stateName());
        for (int frame = 34; frame <= 65; frame++) parent.update(frame, null);
        assertEquals("TURNING", parent.stateName());
        parent.update(66, null);
        assertEquals("MOVING", parent.stateName());
        assertTrue(parent.childFrozen());

        for (int frame = 67; frame <= 82; frame++) parent.update(frame, null);
        assertTrue(parent.childFrozen(), "$2E has reached zero but has not underflowed");
        parent.update(83, null);
        assertFalse(parent.childFrozen(), "Obj_Wait underflow invokes loc_89926 before 89B24 updates");
    }

    @Test void movingRawScriptSetsTerminalOffsetAndAxisFlipOnUpdateTwentySeven() {
        ObjectManager manager = mock(ObjectManager.class);
        var parent = new TechnoSqueekBadnikInstance(spawn(0, 0));
        parent.setServices(new Services(manager));
        parent.update(0, null);
        parent.update(1, null);
        for (int frame = 2; frame <= 27; frame++) parent.update(frame, null);
        assertFalse(parent.childUsesTerminalOffset());
        assertFalse(parent.badnikFacingLeft());
        parent.update(28, null);
        assertTrue(parent.childUsesTerminalOffset());
        assertTrue(parent.badnikFacingLeft());
    }

    @Test void verticalRawFlipMutatesOnlyYAndAttachmentConsumesTheLiveAxes() {
        ObjectManager manager = mock(ObjectManager.class);
        var parent = new TechnoSqueekBadnikInstance(spawn(4, 1));
        parent.setServices(new Services(manager));
        parent.update(0, null);
        parent.update(1, null);
        var child = parent.attachment();
        child.setServices(new Services(manager));
        child.update(1, null);
        child.update(2, null);
        assertFalse(parent.badnikFacingLeft(), "placement X flip remains set");
        assertTrue(parent.verticalPresentation());
        for (int frame = 2; frame <= 28; frame++) parent.update(frame, null);
        child.update(29, null);
        assertFalse(parent.badnikFacingLeft(), "FlipY raw commands must not mutate X flip");
        assertFalse(parent.verticalPresentation(), "movement raw offset 6 toggles live render bit 1");
        assertEquals(parent.getX() + 4, child.getX());
        assertEquals(parent.getY(), child.getY());
    }

    @Test void waitOffscreenAndRestoreDispatchRenderNothingUntilInitializationFrame() {
        Services services = new Services(mock(ObjectManager.class));
        var parent = new TechnoSqueekBadnikInstance(spawn(0, 0));
        parent.setServices(services);
        parent.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(services.renderManager);
        parent.update(0, null);
        parent.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(services.renderManager);
        parent.update(1, null);
        parent.appendRenderCommands(new ArrayList<>());
        verify(services.renderManager).getRenderer(anyString());
    }

    private static final class Services extends StubObjectServices {
        private final ObjectManager manager;
        private final Camera camera = mock(Camera.class);
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        Services(ObjectManager manager) {
            this.manager = manager;
            when(camera.getX()).thenReturn((short) 0xF00);
        }
        @Override public ObjectManager objectManager() { return manager; }
        @Override public Camera camera() { return camera; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
    }

    private static ObjectSpawn spawn(int subtype, int flags) {
        return new ObjectSpawn(0x1000, 0x800, 0xA9, subtype, flags, true, 3);
    }
}
