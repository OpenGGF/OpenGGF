package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectManager;
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
    void fullSolidIncludesExactRightBoundaryLikeRomBhiCheck() {
        // Obj_FBZFlamethrower passes d1=$1B to SolidObjectFull. The shared
        // S3K routine rejects only when relX is unsigned-HIGH, so relX=d1*2
        // remains a grounded side contact (sonic3k.asm:41399-41407).
        assertTrue(flame(0).getSolidRoutineProfile().inclusiveRightEdge());
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
    void offscreenP2StandingBitKeepsTrapTimerAliveUntilNativeLaunch() {
        FbzFlamethrowerObjectInstance flame = flame(0x40);
        PlayableEntity sidekick = org.mockito.Mockito.mock(PlayableEntity.class);
        ObjectManager manager = org.mockito.Mockito.mock(ObjectManager.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        org.mockito.Mockito.when(services.playerQuery())
                .thenReturn(new ObjectPlayerQuery(() -> null, () -> List.of(sidekick)));
        org.mockito.Mockito.when(manager.hasObjectStandingBit(sidekick, flame)).thenReturn(true);
        flame.setServices(services);

        PlayerSolidContactResult standing = org.mockito.Mockito.mock(PlayerSolidContactResult.class);
        org.mockito.Mockito.when(standing.standingNow()).thenReturn(true);
        PlayerSolidContactResult skipped = org.mockito.Mockito.mock(PlayerSolidContactResult.class);
        org.mockito.Mockito.when(skipped.standingNow()).thenReturn(false);
        flame.applyStandingCheckpoint(new SolidCheckpointBatch(flame, Map.of(sidekick, standing)));

        // SolidObjectFull skips offscreen Player_2 before SolidObjectFull_1P,
        // but the object's p2_standing_bit remains set. sub_3CE1A reads that
        // persistent bit, not the current helper result, for all sixty ticks.
        for (int frame = 0; frame < 60; frame++) {
            flame.applyStandingCheckpoint(new SolidCheckpointBatch(flame, Map.of(sidekick, skipped)));
        }

        assertEquals(0, flame.standingTimer());
        org.mockito.Mockito.verify(manager).releaseRidingObject(sidekick, flame);
        org.mockito.Mockito.verify(sidekick).setYSpeed((short) -0x1000);
        org.mockito.Mockito.verify(sidekick).setAir(true);
        org.mockito.Mockito.verify(sidekick).setOnObject(false);
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
    void firstRotatingPairUsesGetSineCosineD1CosineComponent() {
        ObjectSpawn spawn = new ObjectSpawn(0x0990, 0x06D4, 0xE4, 0, 0, false, 2);
        FbzFlameObjectInstance first = FbzFlameObjectInstance.rotating(spawn, 0x7C, 0);
        FbzFlameObjectInstance opposite = FbzFlameObjectInstance.rotating(spawn, 0xFC, 0);

        // sub_3CEC0 consumes d1 from GetSineCosine. At the first rotating
        // emission ($7C/$FC), d1 is cosine: -254/+254. The allocation entry
        // offsets x by d1>>4, then loc_3CF90 moves once by (d1<<2)/$100.
        assertAll(
                () -> assertEquals(0x0980, first.getX()),
                () -> assertEquals(-0x3F8, first.xVelocity()),
                () -> assertEquals(0x099F, opposite.getX()),
                () -> assertEquals(0x3F8, opposite.xVelocity()));

        first.update(0, null);
        opposite.update(0, null);
        assertEquals(0x097C, first.getX(), "ROM complete-run f4262 slot 12");
        assertEquals(0x09A2, opposite.getX(), "ROM complete-run f4262 slot 13");
    }

    @Test
    void lateralFlameAlsoUsesGetSineCosineD1CosineComponent() {
        ObjectSpawn spawn = new ObjectSpawn(0x1000, 0x0700, 0xE4, 0x40, 0, false, 2);
        FbzFlameObjectInstance flame = FbzFlameObjectInstance.lateral(spawn, 0, 0, false);

        // loc_3CF4C applies d1 + d1/2 + $280. At angle zero d1=$100.
        assertEquals(0x0400, flame.xVelocity());
        assertEquals(0x1010, flame.getX());
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

    @Test
    void parentUsesLevelFrameCounterPlusOneInsteadOfObjectManagerClock() {
        com.openggf.level.objects.ObjectManager manager =
                org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        com.openggf.level.LevelManager levelManager =
                org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        org.mockito.Mockito.when(services.levelManager()).thenReturn(levelManager);
        org.mockito.Mockito.when(services.solidExecution())
                .thenReturn(com.openggf.game.solid.ObjectSolidExecutionContext.inert());
        FbzFlamethrowerObjectInstance rotating = onScreenFlame(0);
        rotating.setServices(services);

        // loc_3CD4C reads the low byte at (Level_frame_counter+1).w.  The
        // supplied object clock is deliberately divisible by four here, but
        // native counter+1 is 3, so this frame must not allocate flames.
        org.mockito.Mockito.when(levelManager.getFrameCounter()).thenReturn(2);
        rotating.update(4, null);
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.never())
                .addDynamicObjectAfterCurrent(org.mockito.Mockito.any());

        // On the following native cadence point, counter+1 is 4 and both
        // rotating children are allocated after the parent.
        org.mockito.Mockito.when(levelManager.getFrameCounter()).thenReturn(3);
        rotating.update(5, null);
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.times(2))
                .addDynamicObjectAfterCurrent(org.mockito.Mockito.any());
    }

    @Test
    void parentEmitsWhenItsRenderBoxOverlapsTheLeftViewportEdge() {
        // Render_Sprites sets render_flags bit 7 from width_pixels=$10. At the
        // complete-run f5820 approach, the parent centre is still just left of
        // the viewport while its 16-pixel render box is already visible. The
        // loc_3CD4C gate must therefore emit before the centre itself enters.
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                0x0AEF, 0x300, 0x0C2F, 0x400, 0);
        com.openggf.level.objects.ObjectManager manager =
                org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        com.openggf.level.LevelManager levelManager =
                org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        org.mockito.Mockito.when(services.levelManager()).thenReturn(levelManager);
        org.mockito.Mockito.when(levelManager.getFrameCounter()).thenReturn(3);
        org.mockito.Mockito.when(services.solidExecution())
                .thenReturn(com.openggf.game.solid.ObjectSolidExecutionContext.inert());
        FbzFlamethrowerObjectInstance rotating = new FbzFlamethrowerObjectInstance(
                new ObjectSpawn(0x0AE0, 0x0378, 0xE4, 0x02, 0, false, 1));
        rotating.setServices(services);

        rotating.update(4, null);

        org.mockito.Mockito.verify(manager, org.mockito.Mockito.times(2))
                .addDynamicObjectAfterCurrent(org.mockito.Mockito.any());
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
