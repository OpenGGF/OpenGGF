package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidExecutionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzFlamethrower {
    @BeforeEach
    void bounds() {
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                0x0E00, 0x600, 0x1200, 0x800, 0);
    }

    @AfterEach
    void resetBounds() {
        com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void subtypeBitsSelectLateralAndInvertedFamilies() {
        assertFalse(flame(0).isLateral());
        assertTrue(flame(0x40).isLateral());
        assertFalse(flame(0).isInverted());
        assertTrue(flame(0x80).isInverted());
        assertEquals(3, flame(0).baseSpriteCount());
        assertEquals(2, flame(0x40).baseSpriteCount());
        assertEquals(3, flame(0x80).mappingFrame());
        assertEquals(3, flame(0xC0).mappingFrame());
    }

    @Test
    void flameIsIndependentAndDeletesOnItsExactSeventeenthUpdate() {
        FbzFlameObjectInstance flame = FbzFlameObjectInstance.rotating(
                new ObjectSpawn(0x1000, 0x700, 0xE4, 0, 0, false, 2), 0, 0);
        for (int frame = 0; frame < 16; frame++) {
            flame.update(frame, null);
            assertFalse(flame.isDestroyed(), "flame remains through update " + (frame + 1));
        }
        flame.update(16, null);
        assertTrue(flame.isDestroyed());
        assertFalse(java.util.Arrays.stream(FbzFlameObjectInstance.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == FbzFlamethrowerObjectInstance.class));
    }

    @Test
    void standingTrapConsumesSameCheckpointForLandingLeaveAndExactSixtyFrameLaunch() {
        FbzFlamethrowerObjectInstance flame = flame(0);
        PlayableEntity player = org.mockito.Mockito.mock(PlayableEntity.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery())
                .thenReturn(new ObjectPlayerQuery(() -> player, List::of));
        flame.setServices(services);
        PlayerSolidContactResult standing = org.mockito.Mockito.mock(PlayerSolidContactResult.class);
        org.mockito.Mockito.when(standing.standingNow()).thenReturn(true);
        SolidCheckpointBatch on = new SolidCheckpointBatch(flame, Map.of(player, standing));
        SolidCheckpointBatch off = new SolidCheckpointBatch(flame, Map.of());

        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT, flame.solidExecutionMode());
        flame.applyStandingCheckpoint(on);
        assertEquals(2, flame.mappingFrame());
        assertEquals(0x3C, flame.standingTimer());

        for (int frame = 0; frame < 59; frame++) {
            flame.applyStandingCheckpoint(on);
            assertEquals(0x3B - frame, flame.standingTimer());
        }
        flame.applyStandingCheckpoint(on);
        assertEquals(0, flame.standingTimer());

        flame.applyStandingCheckpoint(off);
        assertEquals(1, flame.mappingFrame());
        assertEquals(0, flame.standingTimer());
    }

    @Test
    void placementFlipAppliesToBaseNozzlesAndIndependentFlameMotionAndRender() {
        ObjectSpawn leftSpawn = new ObjectSpawn(0x1000, 0x700, 0xE4, 0x40, 1, false, 1);
        FbzFlamethrowerObjectInstance leftParent = new FbzFlamethrowerObjectInstance(leftSpawn);
        FbzFlameObjectInstance leftFlame = FbzFlameObjectInstance.lateral(leftSpawn, 0, 0, true);

        assertTrue(leftParent.renderFlipX());
        assertTrue(leftFlame.renderFlipX());
        assertEquals(0x0FF0, leftFlame.getX());
        assertTrue(leftFlame.xVelocity() < 0);
        assertFalse(flame(0x40).renderFlipX());
    }

    @Test
    void parentUsesFourFrameCadenceAndContinuesBothRotatingAllocationsAfterFailure() {
        com.openggf.level.objects.ObjectManager manager =
                org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((com.openggf.level.objects.AbstractObjectInstance) invocation.getArgument(0))
                    .setDestroyed(true);
            return null;
        }).when(manager).addDynamicObjectAfterCurrent(org.mockito.Mockito.any());
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        org.mockito.Mockito.when(services.solidExecution())
                .thenReturn(com.openggf.game.solid.ObjectSolidExecutionContext.inert());
        FbzFlamethrowerObjectInstance rotating = onScreenFlame(0);
        rotating.setServices(services);

        rotating.update(1, null);
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.never())
                .addDynamicObjectAfterCurrent(org.mockito.Mockito.any());
        rotating.update(4, null);
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.times(2))
                .addDynamicObjectAfterCurrent(org.mockito.Mockito.any());

        org.mockito.Mockito.reset(manager);
        FbzFlamethrowerObjectInstance lateral = onScreenFlame(0x40);
        lateral.setServices(services);
        lateral.update(8, null);
        org.mockito.Mockito.verify(manager).addDynamicObjectAfterCurrent(org.mockito.Mockito.any());
    }

    private static FbzFlamethrowerObjectInstance flame(int subtype) {
        return new FbzFlamethrowerObjectInstance(
                new ObjectSpawn(0x1000, 0x700, 0xE4, subtype, 0, false, 1));
    }

    private static FbzFlamethrowerObjectInstance onScreenFlame(int subtype) {
        return new FbzFlamethrowerObjectInstance(
                new ObjectSpawn(0x1000, 0x700, 0xE4, subtype, 0x80, false, 1));
    }
}
