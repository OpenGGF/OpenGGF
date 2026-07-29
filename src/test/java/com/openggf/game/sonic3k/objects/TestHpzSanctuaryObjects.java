package com.openggf.game.sonic3k.objects;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.GameStateManager;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestHpzSanctuaryObjects {
    private static final int[][] POSITIONS = {
            {0x1640, 0x368}, {0x15E0, 0x3A0}, {0x16A0, 0x3A0},
            {0x15A0, 0x350}, {0x16E0, 0x350}, {0x1550, 0x390},
            {0x1730, 0x390}
    };

    @Test
    void pedestalStatesUseRomCentrePositionsAndFourStateBehavior() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 1, 2, 3, 0, 0, 0), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);

        for (int i = 0; i < 4; i++) {
            HPZSuperEmeraldObjectInstance pedestal = pedestal(i, progression, runtime);
            assertEquals(POSITIONS[i][0], pedestal.getX());
            assertEquals(POSITIONS[i][1], pedestal.getY());
        }
        assertTrue(pedestal(0, progression, runtime).isDestroyed());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY,
                pedestal(1, progression, runtime).display());
        assertFalse(pedestal(1, progression, runtime).isSelectable());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY,
                pedestal(2, progression, runtime).display());
        assertTrue(pedestal(2, progression, runtime).isSelectable());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED,
                pedestal(3, progression, runtime).display());
        assertFalse(pedestal(3, progression, runtime).isSelectable());
        assertArrayEquals(new int[]{2, 0, 2, 0, 0, 1, 3},
                java.util.stream.IntStream.range(0, 7)
                        .map(i -> pedestal(i, progression, runtime).completedPaletteLine())
                        .toArray());
        assertArrayEquals(new int[]{2, 1, 2, 2, 0, 0, 3},
                java.util.stream.IntStream.range(0, 7)
                        .map(i -> pedestal(i, progression, runtime)
                                .completedPaletteLine(
                                        HPZSuperEmeraldObjectInstance.SanctuaryPlayerMode.KNUCKLES))
                        .toArray());
    }

    @Test
    void selectablePedestalPublishesExactSuperEmeraldRequestOnSixteenthUpdateOnly() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 0, 2, 0, 0), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);
        HPZSuperEmeraldObjectInstance pedestal = pedestal(4, progression, runtime);
        ObjectServices services = mock(ObjectServices.class);
        pedestal.setServices(services);

        assertTrue(pedestal.beginSelection());
        for (int i = 0; i < 15; i++) {
            pedestal.updateSelection();
            verify(services, never()).requestSpecialStageEntry(any());
        }
        pedestal.updateSelection();
        verify(services).requestSpecialStageEntry(
                new SpecialStageEntryRequest(4, EmeraldRewardKind.SUPER_EMERALD));
        pedestal.updateSelection();
        verifyNoMoreInteractions(services);
    }

    @Test
    void controllerPublishesRomSpawnGraphAndReentrySpawnsAllPedestals() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 1, 2, 3, 0, 0, 0), true);

        HPZSSEntryControlObjectInstance fresh = controller(progression, false);
        assertEquals(List.of(HPZMasterEmeraldObjectInstance.class,
                        SSZHPZTeleporterObjectInstance.class,
                        HPZSuperEmeraldObjectInstance.class,
                        HPZSuperEmeraldObjectInstance.class),
                fresh.initialChildTypes());
        assertEquals(List.of(2, 3), fresh.initialPedestalSubtypes());

        HPZSSEntryControlObjectInstance reentry = controller(progression, true);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), reentry.initialPedestalSubtypes());
    }

    @Test
    void sanctuaryVisualAttributesMatchRomObjectData() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(2, 2, 2, 2, 2, 2, 2), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);

        HPZMasterEmeraldObjectInstance master = new HPZMasterEmeraldObjectInstance(
                new ObjectSpawn(0x1640, 0x340, 0xB0, 0, 0, false, 0));
        assertEquals(0xB, master.mappingFrameForTest());
        assertEquals(3, master.renderPaletteLineForTest());
        assertEquals(4, master.getPriorityBucket());
        assertFalse(master.isHighPriority());

        SSZHPZTeleporterObjectInstance teleporter = new SSZHPZTeleporterObjectInstance(
                new ObjectSpawn(0x1640, 0x3C7, 0x79, 0, 0, false, 0));
        assertEquals(0xA, teleporter.mappingFrameForTest());
        assertEquals(0, teleporter.renderPaletteLineForTest());
        assertEquals(3, teleporter.getPriorityBucket());
        assertFalse(teleporter.isHighPriority());

        for (int subtype = 0; subtype < 7; subtype++) {
            assertTrue(pedestal(subtype, progression, runtime).isHighPriority(),
                    "Obj_HPZSuperEmerald art_tile priority bit for subtype " + subtype);
        }
        assertTrue(new HPZSanctuaryFallingCrystalObjectInstance(
                controller(progression, false), 7).isHighPriority());
    }

    @Test
    void incompleteMasterEmeraldOwnsTheRomGreenColors() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = {new Palette(), new Palette(), new Palette(), new Palette()};
        ObjectServices services = mock(ObjectServices.class);
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        when(services.gameState()).thenReturn(new GameStateManager());

        HPZMasterEmeraldObjectInstance master = new HPZMasterEmeraldObjectInstance(
                new ObjectSpawn(0x1640, 0x340, 0xB0, 0, 0, false, 0));
        master.setServices(services);

        registry.beginFrame();
        master.update(0, null);
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals(S3kPaletteOwners.HPZ_MASTER_EMERALD,
                registry.ownerAt(PaletteSurface.NORMAL, 3, 1));
        assertEquals(S3kPaletteOwners.HPZ_MASTER_EMERALD,
                registry.ownerAt(PaletteSurface.NORMAL, 3, 2));
        assertSegaColor(palettes[3], 1, 0x06A0);
        assertSegaColor(palettes[3], 2, 0x0660);
    }

    @Test
    void completedMasterEmeraldRunsTheRomPaletteRotationScript() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = {new Palette(), new Palette(), new Palette(), new Palette()};
        ObjectServices services = mock(ObjectServices.class);
        GameStateManager gameState = mock(GameStateManager.class);
        when(gameState.hasAllSuperEmeralds()).thenReturn(true);
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        when(services.gameState()).thenReturn(gameState);

        HPZMasterEmeraldObjectInstance master = new HPZMasterEmeraldObjectInstance(
                new ObjectSpawn(0x1640, 0x340, 0xB0, 0, 0, false, 0));
        master.setServices(services);

        for (int frame = 0; frame < 16; frame++) {
            registry.beginFrame();
            master.update(frame, null);
            registry.resolveInto(palettes, null, null, palettes[0]);
            assertSegaColor(palettes[3], 1, 0x06A0);
            assertSegaColor(palettes[3], 2, 0x0660);
        }

        registry.beginFrame();
        master.update(16, null);
        registry.resolveInto(palettes, null, null, palettes[0]);
        assertSegaColor(palettes[3], 1, 0x08C0);
        assertSegaColor(palettes[3], 2, 0x0680);
    }

    @Test
    void smallEmeraldCeremonyParentUsesTheRomHalfPixelRiseFor128Updates() {
        HPZSanctuarySmallEmeraldCeremonyObjectInstance ceremony =
                new HPZSanctuarySmallEmeraldCeremonyObjectInstance();

        assertEquals(0x3AC, ceremony.getY());
        for (int frame = 0; frame < 128; frame++) {
            ceremony.update(frame, null);
        }
        assertEquals(0x36C, ceremony.getY());
        ceremony.update(128, null);
        assertEquals(0x36C, ceremony.getY(),
                "loc_90DDC stops MoveSprite2 after the $7F timer expires");
    }

    @Test
    void conversionPlayerPosesAreIndexedByScanOrdinalNotEmeraldSubtype() {
        int[][] expected = {
                {1, 1}, {1, 0}, {0, 0}, {1, 1}, {0, 1}, {1, 0}, {0, 0}
        };
        for (int ordinal = 0; ordinal < expected.length; ordinal++) {
            assertArrayEquals(expected[ordinal],
                    HPZSSEntryControlObjectInstance.conversionPoseForTest(ordinal),
                    "byte_90BBC row " + ordinal);
        }
        // First ROM scan subtype is 5, but its pose must still be row zero.
        assertFalse(java.util.Arrays.equals(
                HPZSSEntryControlObjectInstance.conversionPoseForTest(0),
                HPZSSEntryControlObjectInstance.conversionPoseForTest(5)));
    }

    @Test
    void controllerConversionCountdownsUseSubqBplBoundaries() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(1, 1, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        controller.beginConversionForTest();

        for (int i = 0; i < 0x220 - 1; i++) {
            assertEquals(-1, controller.updateConversionTimerForTest());
        }
        assertEquals(1, controller.updateConversionTimerForTest());
        assertEquals(2, progression.states().get(1));
        for (int i = 0; i < 0x20 - 1; i++) {
            assertEquals(-1, controller.updateConversionTimerForTest());
        }
        assertEquals(0, controller.updateConversionTimerForTest());
        assertEquals(List.of(2, 2, 0, 0, 0, 0, 0), progression.states());
    }

    @Test
    void subtypeSevenFallingCrystalPublishesTheProductionIntroSignalAtNativeBoundary() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        HPZSanctuaryFallingCrystalObjectInstance crystal =
                new HPZSanctuaryFallingCrystalObjectInstance(controller, 7);
        assertTrue(crystal.shouldDrawForTest(0));
        assertFalse(crystal.shouldDrawForTest(1));
        assertEquals(0, crystal.renderPaletteLineForTest());
        assertEquals(0,
                new HPZSanctuaryFallingCrystalObjectInstance(controller, 2)
                        .renderPaletteLineForTest());

        for (int i = 0; i < 101; i++) {
            crystal.update(i, null);
            assertFalse(controller.introSignalReceivedForTest());
            if (i == 54) {
                assertTrue(crystal.midpointPublishedForTest());
            }
        }
        crystal.update(101, null);
        assertTrue(controller.introSignalReceivedForTest());
    }

    @Test
    void fallingCrystalStartsFromCameraMinusEightyAndPublishesLandingEffects() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        HPZSanctuaryFallingCrystalObjectInstance crystal =
                new HPZSanctuaryFallingCrystalObjectInstance(controller, 7, 0x1C0);
        assertEquals(0x1C0, crystal.getY());
        ObjectServices services = mock(ObjectServices.class);
        when(services.gameState()).thenReturn(gsm);
        crystal.setServices(services);

        for (int i = 0; i < 24; i++) crystal.update(i, null);
        assertEquals(8, crystal.screenShakeTimerForTest());
        assertTrue(gsm.isScreenShakeActive());
        verify(services).playSfx(
                com.openggf.game.sonic3k.audio.Sonic3kSfx.BOSS_LASER.id);
    }

    @Test
    void freshCeremonyLocksCentresAndRestoresPrimaryControl() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(1, 0, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);

        controller.applyFreshLockForTest(player);
        verify(player).setCentreXPreserveSubpixel((short) 0x1640);
        verify(player).setCentreYPreserveSubpixel((short) 0x3A3);
        verify(player).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState.NATIVE_BIT_7_FULL_CONTROL);
        verify(player).setObjectControlAllowsCpu(true);
        verify(player).setObjectControlSuppressesMovement(true);
        verify(player).setObjectMappingFrameControl(true);
        verify(player).setMappingFrame(0);
        verify(player).setAnimationId(0x1C);

        controller.releaseFreshLockForTest(player);
        verify(player).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState.NONE);
        verify(player).setObjectMappingFrameControl(false);
    }

    @Test
    void cameraPanMovesExactlyTenPixelsHexPerUpdateAndCallsBackInsideFinalStep() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(1, 0, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x1500, (short) 0x1510, (short) 0x1520);

        assertFalse(controller.panCameraForTest(camera, 0x1520));
        verify(camera).setX((short) 0x1510);
        assertFalse(controller.panCameraForTest(camera, 0x1520));
        verify(camera).setX((short) 0x1520);
        assertTrue(controller.panCameraForTest(camera, 0x1520));
        verify(camera, times(3)).getX();
        verifyNoMoreInteractions(camera);
    }

    @Test
    void rewindRelinksPedestalToTheSingleRestoredControllerRuntime() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 2, 0, 0, 0, 0), true);
        HPZSSEntryControlObjectInstance controller = controller(progression, true);
        HPZSuperEmeraldObjectInstance pedestal = new HPZSuperEmeraldObjectInstance(
                new ObjectSpawn(0, 0, 0xB4, 2, 0, false, 0), controller);
        HPZSanctuaryFallingCrystalObjectInstance falling =
                new HPZSanctuaryFallingCrystalObjectInstance(controller, 2);
        RewindIdentityTable capturedIds = new RewindIdentityTable();
        ObjectRefId controllerId = ObjectRefId.dynamic(4, 1, 40);
        ObjectRefId pedestalId = ObjectRefId.dynamic(5, 1, 41);
        ObjectRefId fallingId = ObjectRefId.dynamic(6, 1, 42);
        capturedIds.registerObject(controller, controllerId);
        capturedIds.registerObject(pedestal, pedestalId);
        capturedIds.registerObject(falling, fallingId);
        var controllerSnapshot = controller.captureRewindState();
        var pedestalSnapshot = pedestal.captureRewindState(
                RewindCaptureContext.withIdentityTable(capturedIds));
        var fallingSnapshot = falling.captureRewindState(
                RewindCaptureContext.withIdentityTable(capturedIds));

        ObjectServices services = mock(ObjectServices.class);
        when(services.gameState()).thenReturn(gsm);
        when(services.sanctuaryReentryStage()).thenReturn(java.util.OptionalInt.of(2));
        HPZSSEntryControlObjectInstance restoredController =
                new HPZSSEntryControlObjectInstance(
                        new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0));
        HPZSuperEmeraldObjectInstance restoredPedestal =
                new HPZSuperEmeraldObjectInstance(
                        new ObjectSpawn(0, 0, 0xB4, 2, 0, false, 0));
        HPZSanctuaryFallingCrystalObjectInstance restoredFalling =
                falling.recreateForRewind(new com.openggf.level.objects.RewindRecreateContext(
                        falling.getSpawn(), fallingSnapshot, services));
        restoredController.setServices(services);
        restoredPedestal.setServices(services);
        restoredFalling.setServices(services);
        RewindIdentityTable restoredIds = new RewindIdentityTable();
        restoredIds.registerObject(restoredController, controllerId);
        restoredIds.registerObject(restoredPedestal, pedestalId);
        restoredIds.registerObject(restoredFalling, fallingId);
        RewindCaptureContext restoreContext =
                RewindCaptureContext.withIdentityTable(restoredIds);

        restoredController.restoreRewindState(controllerSnapshot);
        restoredPedestal.restoreRewindState(pedestalSnapshot, restoreContext);
        assertTrue(restoredPedestal.sharesRuntimeWithForTest(restoredController));
        restoredFalling.restoreRewindState(fallingSnapshot, restoreContext);
        assertSame(restoredController, restoredFalling.parentForTest());
        assertEquals(falling.getY(), restoredFalling.getY());
        assertEquals(falling.landingTimerForTest(), restoredFalling.landingTimerForTest());
        assertEquals(falling.rawAnimationTimerForTest(), restoredFalling.rawAnimationTimerForTest());
    }

    @Test
    void centreExitRequiresLeaveAndReenterBandTeleporterReadyAndNoIncompleteEmeralds() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(3, 3, 3, 3, 3, 3, 3), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);
        HPZSSEntryControlObjectInstance controller = controller(progression, true);
        ObjectServices services = mock(ObjectServices.class);
        controller.setServices(services);
        controller.attachRuntimeForTest(runtime);

        controller.updateExitBand(0x1640, true);
        verify(services, never()).requestSanctuaryExit();
        controller.updateExitBand(0x1660, true);
        controller.updateExitBand(0x1640, false);
        verify(services, never()).requestSanctuaryExit();
        controller.updateExitBand(0x1660, true);
        controller.updateExitBand(0x1640, true);
        verify(services).requestSanctuaryExit();
        controller.updateExitBand(0x1660, true);
        controller.updateExitBand(0x1640, true);
        verifyNoMoreInteractions(services);
    }

    @Test
    void pedestalAndTeleporterMutablePhasesRoundTripThroughObjectRewind() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 2, 0, 0, 0, 0), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);
        HPZSuperEmeraldObjectInstance pedestal = pedestal(2, progression, runtime);
        ObjectServices services = mock(ObjectServices.class);
        pedestal.setServices(services);
        assertTrue(pedestal.beginSelection());
        for (int i = 0; i < 5; i++) pedestal.updateSelection();
        var pedestalSnapshot = pedestal.captureRewindState();
        for (int i = 0; i < 11; i++) pedestal.updateSelection();
        pedestal.restoreRewindState(pedestalSnapshot);
        clearInvocations(services);
        for (int i = 0; i < 10; i++) pedestal.updateSelection();
        verify(services, never()).requestSpecialStageEntry(any());
        pedestal.updateSelection();
        verify(services).requestSpecialStageEntry(
                new SpecialStageEntryRequest(2, EmeraldRewardKind.SUPER_EMERALD));

        SSZHPZTeleporterObjectInstance teleporter = new SSZHPZTeleporterObjectInstance(
                new ObjectSpawn(0x1640, 0x3C7, 0x79, 0, 0, false, 0));
        for (int i = 0; i < 7; i++) teleporter.update(i, null);
        var teleporterSnapshot = teleporter.captureRewindState();
        for (int i = 0; i < 20; i++) teleporter.update(i, null);
        assertTrue(teleporter.isReady());
        teleporter.restoreRewindState(teleporterSnapshot);
        assertFalse(teleporter.isReady());
    }

    private static HPZSuperEmeraldObjectInstance pedestal(
            int subtype, S3kEmeraldProgression progression, S3kSanctuaryRuntimeState runtime) {
        return new HPZSuperEmeraldObjectInstance(
                new ObjectSpawn(0, 0, 0xB4, subtype, 0, false, 0), progression, runtime);
    }

    private static HPZSSEntryControlObjectInstance controller(
            S3kEmeraldProgression progression, boolean reentry) {
        return new HPZSSEntryControlObjectInstance(
                new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0), progression, reentry);
    }

    private static void assertSegaColor(Palette palette, int colorIndex, int segaWord) {
        byte[] encoded = {(byte) (segaWord >>> 8), (byte) segaWord};
        Palette expected = new Palette();
        expected.getColor(colorIndex).fromSegaFormat(encoded, 0);
        assertEquals(expected.getColor(colorIndex).r, palette.getColor(colorIndex).r);
        assertEquals(expected.getColor(colorIndex).g, palette.getColor(colorIndex).g);
        assertEquals(expected.getColor(colorIndex).b, palette.getColor(colorIndex).b);
    }
}
