package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestMhzCurledVineObjectInstance {
    private static final int MHZ_CURLED_VINE = 0x09;
    private PatternSpriteRenderer renderer;
    private LevelManager levelManager;

    @BeforeEach
    void setUpRenderer() {
        renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MHZ_CURLED_VINE)).thenReturn(renderer);
        levelManager = mock(LevelManager.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
    }

    @Test
    void registryRoutesSklSlot09ToMhzCurledVineInsteadOfAizTree() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);

        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 0, false, 0));

        assertEquals("MHZCurledVine", vine.getName(),
                "SKL slot $09 is Obj_MHZCurledVine; MHZ must not use the S3KL AIZ1 tree object");
    }

    @Test
    void curledVineExposesRomTopSolidFootprint() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 0, false, 0));

        assertEquals("MHZCurledVine", vine.getName(),
                "SKL slot $09 must construct the MHZ curled vine before solidity can be validated");
        AbstractObjectInstance concreteVine = assertInstanceOf(AbstractObjectInstance.class, vine);
        assertEquals(5, vine.getPriorityBucket(),
                "Obj_MHZCurledVine initializes priority=$280, which maps to render bucket 5");
        assertEquals(0x40, concreteVine.getOnScreenHalfWidth(),
                "Obj_MHZCurledVine initializes the display child width_pixels to $40");
        assertEquals(0x30, concreteVine.getOnScreenHalfHeight(),
                "Obj_MHZCurledVine initializes the display child height_pixels to $30");

        SolidObjectProvider solid = assertInstanceOf(SolidObjectProvider.class, vine,
                "Obj_MHZCurledVine calls its top-solid helper after generating the child segment surface");
        assertEquals(0x20, solid.getSolidParams().halfWidth(),
                "The initial byte_3E8F6 range is $40 pixels, exposed as a $20 standable half-width");
        assertTrue(solid.isTopSolidOnly(),
                "Obj_MHZCurledVine only presents a rideable top surface");
        assertTrue(solid.usesCollisionHalfWidthForTopLanding(),
                "The curled vine helper passes its computed standable range directly");
    }

    @Test
    void standingPlayerNearRightEdgeWidensRangeAndUncurlsOneStep() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance vine = registry.create(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2030, (short) 0x05E0);

        SolidObjectListener listener = assertInstanceOf(SolidObjectListener.class, vine,
                "Obj_MHZCurledVine stores per-player segment indices in $36/$37 while ridden");
        listener.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        vine.update(1, player);

        SolidObjectProvider solid = assertInstanceOf(SolidObjectProvider.class, vine);
        assertEquals(0x40, solid.getSolidParams().halfWidth(),
                "Right-edge standing index 7 selects byte_3E8F6[8]=$80, exposed as a $40 half-width");
        assertTrue(vine.traceDebugDetails().contains("curve=$FFF50000"),
                "The curve state moves one $10000 step from $FFF40000 toward byte index 8's $FFFF0000 target");
        assertTrue(vine.traceDebugDetails().contains("range=$80"),
                "The live standable range mirrors byte_3E8F6 for the selected rider index");
    }

    @Test
    void curledVineRendersRomDisplayChildSegments() {
        MhzCurledVineObjectInstance vine = new MhzCurledVineObjectInstance(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 0, false, 0));
        vine.setServices(new TestObjectServices().withLevelManager(levelManager));

        vine.appendRenderCommands(new ArrayList<>());

        verify(renderer, times(8)).drawFrameIndex(eq(0), anyInt(), anyInt(), eq(false), eq(false));
        verify(renderer).drawFrameIndex(0, 0x1FC8, 0x0600, false, false);
    }

    @Test
    void hFlippedCurledVineMirrorsDisplayChildSegments() {
        MhzCurledVineObjectInstance vine = new MhzCurledVineObjectInstance(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 1, false, 0));
        vine.setServices(new TestObjectServices().withLevelManager(levelManager));

        vine.appendRenderCommands(new ArrayList<>());

        verify(renderer, times(8)).drawFrameIndex(eq(0), anyInt(), anyInt(), eq(true), eq(false));
        verify(renderer).drawFrameIndex(0, 0x2038, 0x0600, true, false);
        assertTrue(vine.traceDebugDetails().contains("hflip=true"),
                "Spawn render flag bit 0 must drive the MHZ curled vine's display-child horizontal flip");
    }

    @Test
    void riderPressureMovesRenderedSegmentsAsCurveUncurls() {
        MhzCurledVineObjectInstance vine = new MhzCurledVineObjectInstance(new ObjectSpawn(
                0x2000, 0x0600, MHZ_CURLED_VINE, 0, 0, false, 0));
        vine.setServices(new TestObjectServices().withLevelManager(levelManager));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x2030, (short) 0x05E0);

        vine.appendRenderCommands(new ArrayList<>());
        List<Integer> initialY = capturedRenderedYPositions();
        clearInvocations(renderer);

        SolidObjectListener listener = assertInstanceOf(SolidObjectListener.class, vine);
        listener.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        for (int frame = 1; frame <= 8; frame++) {
            vine.update(frame, player);
        }
        vine.appendRenderCommands(new ArrayList<>());

        assertNotEquals(initialY, capturedRenderedYPositions(),
                "Obj_MHZCurledVine animates by regenerating the eight child sprite positions from its curve state");
    }

    private List<Integer> capturedRenderedYPositions() {
        ArgumentCaptor<Integer> yCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(renderer, times(8)).drawFrameIndex(eq(0), anyInt(), yCaptor.capture(), eq(false), eq(false));
        return yCaptor.getAllValues();
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }
}
