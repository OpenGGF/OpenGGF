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
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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
        assertEquals(4, pedestal(0, progression, runtime).getPriorityBucket());
        assertEquals(1, pedestal(1, progression, runtime).getPriorityBucket());
        assertEquals(1, pedestal(2, progression, runtime).getPriorityBucket());
        assertEquals(4, pedestal(3, progression, runtime).getPriorityBucket());
        assertArrayEquals(new int[]{2, 0, 2, 0, 0, 1, 3},
                java.util.stream.IntStream.range(0, 7)
                        .map(i -> pedestal(i, progression, runtime).completedPaletteLine())
                        .toArray());
        assertArrayEquals(new int[]{2, 1, 2, 1, 0, 0, 3},
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
    void pedestalSelectionAppliesRomPlayerLockAndAnimation() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 2, 0, 0, 0, 0), true);
        S3kSanctuaryRuntimeState runtime = new S3kSanctuaryRuntimeState(progression, true);
        HPZSuperEmeraldObjectInstance pedestal = pedestal(2, progression, runtime);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);

        assertTrue(pedestal.beginSelection(player));

        verify(player).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState
                        .NATIVE_BITS_0_TO_6_CPU_ALLOWED_MOVEMENT_SUPPRESSED);
        verify(player).setAnimationId(5);
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
        master.setOnScreenForTest(true);

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
    void completedMasterEmeraldRunsTheRomPaletteRotationScript() throws Exception {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = {new Palette(), new Palette(), new Palette(), new Palette()};
        ObjectServices services = mock(ObjectServices.class);
        GameStateManager gameState = mock(GameStateManager.class);
        when(gameState.hasAllSuperEmeralds()).thenReturn(true);
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        when(services.gameState()).thenReturn(gameState);
        when(services.romReader()).thenReturn(masterEmeraldPaletteReader());

        HPZMasterEmeraldObjectInstance master = new HPZMasterEmeraldObjectInstance(
                new ObjectSpawn(0x1640, 0x340, 0xB0, 0, 0, false, 0));
        master.setServices(services);
        master.setOnScreenForTest(true);

        registry.beginFrame();
        master.update(0, null);
        registry.resolveInto(palettes, null, null, palettes[0]);
        assertSegaColor(palettes[3], 1, 0x08C0);
        assertSegaColor(palettes[3], 2, 0x0680);

        for (int frame = 1; frame <= 10; frame++) {
            registry.beginFrame();
            master.update(frame, null);
            registry.resolveInto(palettes, null, null, palettes[0]);
        }
        assertSegaColor(palettes[3], 1, 0x0AC0);
        assertSegaColor(palettes[3], 2, 0x0680);
        assertEquals(0x1D, master.glowFrameForTest(),
                "off_914CE entry 2 selects RawAni_90768[$3B/2]");
    }

    @Test
    void completedMasterEmeraldSkipsPaletteWritesDuringReturnTransform()
            throws Exception {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(3, 3, 3, 3, 3, 3, 3), true);
        HPZSSEntryControlObjectInstance controller = controller(progression, true);
        controller.attachRuntimeForTest(
                new S3kSanctuaryRuntimeState(progression, true, 2, true));
        HPZMasterEmeraldObjectInstance master = new HPZMasterEmeraldObjectInstance(
                new ObjectSpawn(0x1640, 0x340, 0xB0, 0, 0, false, 0), controller);
        ObjectServices services = mock(ObjectServices.class);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        when(services.romReader()).thenReturn(masterEmeraldPaletteReader());
        master.setServices(services);
        master.setOnScreenForTest(true);

        registry.beginFrame();
        master.update(0, null);

        assertEquals("none", registry.ownerAt(PaletteSurface.NORMAL, 3, 1));
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
    void ceremonyOnlyIncludesStateOneEmeraldsAndBlinksOnEvenVintFrames() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 1, 2, 3, 1, 0, 2), true);
        HPZSanctuarySmallEmeraldCeremonyObjectInstance ceremony =
                new HPZSanctuarySmallEmeraldCeremonyObjectInstance(progression);

        assertFalse(ceremony.participatesForTest(0));
        assertTrue(ceremony.participatesForTest(1));
        assertFalse(ceremony.participatesForTest(2));
        assertFalse(ceremony.participatesForTest(3));
        assertTrue(ceremony.participatesForTest(4));
        assertTrue(ceremony.shouldDrawForTest(0, 1));
        assertFalse(ceremony.shouldDrawForTest(1, 1));
        assertFalse(ceremony.shouldDrawForTest(0, 2));
    }

    @Test
    void successfulReturnKeepsPedestalGrayUntilTwinStarsCollapse() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 3, 0, 0, 0), true);
        HPZSSEntryControlObjectInstance controller = controller(progression, true);
        S3kSanctuaryRuntimeState runtime =
                new S3kSanctuaryRuntimeState(progression, true, 3, true);
        controller.attachRuntimeForTest(runtime);
        HPZSuperEmeraldObjectInstance pedestal = new HPZSuperEmeraldObjectInstance(
                new ObjectSpawn(0, 0, 0xB4, 3, 0, false, 0), controller);
        HPZSuperEmeraldReturnEffectObjectInstance effect =
                new HPZSuperEmeraldReturnEffectObjectInstance(controller);
        ObjectServices services = mock(ObjectServices.class);
        effect.setServices(services);

        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal.display());
        effect.update(0, null);
        assertEquals(0xE000, effect.displayRadiusForTest());
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8},
                java.util.stream.IntStream.range(0, 8)
                        .map(effect::mappingFrameForTest).toArray());
        verify(services).playSfx(
                com.openggf.game.sonic3k.audio.Sonic3kSfx.SIGNPOST.id);
        for (int i = 1; i < 225; i++) {
            effect.update(i, null);
        }

        assertTrue(runtime.transformationActive());
        assertFalse(effect.drawsCurrentFrameForTest(),
                "loc_2EDAE deletes on borrow without drawing a center frame");
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED, pedestal.display());
        verify(services).playSfx(
                com.openggf.game.sonic3k.audio.Sonic3kSfx.SUPER_EMERALD.id);
        verify(services, never()).playSfx(
                com.openggf.game.sonic3k.audio.Sonic3kSfx.PERFECT.id);
        effect.update(225, null);
        assertFalse(runtime.transformationActive());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED, pedestal.display());
        assertEquals(7, pedestal.mappingFrameForTest(1));
        verify(services).playSfx(
                com.openggf.game.sonic3k.audio.Sonic3kSfx.PERFECT.id);
    }

    @Test
    void conversionPlayerPosesAreIndexedByEmeraldSubtype() {
        int[][] expected = {
                {1, 1}, {1, 0}, {0, 0}, {1, 1}, {0, 1}, {1, 0}, {0, 0}
        };
        for (int ordinal = 0; ordinal < expected.length; ordinal++) {
            assertArrayEquals(expected[ordinal],
                    HPZSSEntryControlObjectInstance.conversionPoseForTest(ordinal),
                    "byte_90BBC row " + ordinal);
        }
        // First ROM scan subtype is 5, so loc_90B32 selects row five.
        assertFalse(java.util.Arrays.equals(
                HPZSSEntryControlObjectInstance.conversionPoseForTest(0),
                HPZSSEntryControlObjectInstance.conversionPoseForTest(5)));
    }

    @Test
    void freshSanctuaryLockRevertsAnActivePoweredFormBeforeUsingNormalMappings() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(1, 1, 1, 1, 1, 1, 1), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        AbstractPlayableSprite sonic = mock(AbstractPlayableSprite.class);
        var superState = mock(com.openggf.sprites.playable.SuperStateController.class);
        when(sonic.getSuperStateController()).thenReturn(superState);
        when(superState.isSuper()).thenReturn(true);

        controller.applyFreshLockForTest(sonic);

        var order = inOrder(superState, sonic);
        order.verify(superState).debugDeactivate();
        order.verify(sonic).setMappingFrame(0);
    }

    @Test
    void terminalCameraPanUsesTheRomRowSevenVisiblePlayerPose() throws Exception {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(2, 2, 2, 2, 2, 2, 2), true);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        AbstractPlayableSprite sonic = mock(AbstractPlayableSprite.class);
        ObjectServices services = mock(ObjectServices.class);
        var players = mock(com.openggf.level.objects.ObjectPlayerQuery.class);
        when(services.playerQuery()).thenReturn(players);
        when(players.playersFor(any())).thenReturn(List.of(sonic));
        controller.setServices(services);

        var method = HPZSSEntryControlObjectInstance.class
                .getDeclaredMethod("applyFinalPlayerMappings");
        method.setAccessible(true);
        method.invoke(controller);

        verify(sonic).setRenderFlips(false, false);
        verify(sonic).setMappingFrame(0xBA);
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
    void conversionMidpointSavesAndKeepsPedestalColoredUntilCrystalAnimationEnds() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 0, 0, 1, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        ObjectServices services = mock(ObjectServices.class);
        controller.setServices(services);
        controller.beginConversionForTest();

        controller.onFallingCrystalMidpoint(5);
        HPZSuperEmeraldObjectInstance pedestal = new HPZSuperEmeraldObjectInstance(
                new ObjectSpawn(0, 0, 0xB4, 5, 0, false, 0), controller);

        assertEquals(2, progression.states().get(5));
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED, pedestal.display());
        verify(services).requestSessionSave(
                com.openggf.game.save.SaveReason.SPECIAL_STAGE_SAVE);

        controller.onFallingCrystalAnimationComplete(5);
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal.display());
    }

    @Test
    void introCrystalMidpointAppliesTheRomTailsFourPixelYAdjustment() {
        GameStateManager gsm = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gsm, List.of(0, 0, 0, 0, 0, 0, 0), false);
        HPZSSEntryControlObjectInstance controller = controller(progression, false);
        ObjectServices services = mock(ObjectServices.class);
        var query = mock(com.openggf.level.objects.ObjectPlayerQuery.class);
        AbstractPlayableSprite tails = mock(AbstractPlayableSprite.class);
        when(tails.getCode()).thenReturn("tails");
        when(tails.getCentreY()).thenReturn((short) 0x300);
        when(services.playerQuery()).thenReturn(query);
        when(query.playersFor(any())).thenReturn(List.of(tails));
        controller.setServices(services);

        controller.onFallingCrystalMidpoint(7);

        verify(tails).setCentreYPreserveSubpixel((short) 0x304);
        verify(tails).setMappingFrame(0xAD);
        verify(tails).setAnimationId(5);
    }

    @Test
    void typedSanctuaryReturnContextDrivesSuccessAndFailurePresentation() {
        GameStateManager successState = new GameStateManager();
        successState.restoreS3kEmeraldProgress(
                List.of(0, 0, 0, 3, 0, 0, 0), true);
        ObjectServices successServices = mock(ObjectServices.class);
        when(successServices.gameState()).thenReturn(successState);
        when(successServices.sanctuaryReturnContext()).thenReturn(java.util.Optional.of(
                new com.openggf.level.SanctuaryReturnContext(3, true)));
        HPZSSEntryControlObjectInstance successController =
                new HPZSSEntryControlObjectInstance(
                        new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0));
        successController.setServices(successServices);
        assertTrue(successController.runtimeForChild().transformationActive());
        assertTrue(successController.runtimeForChild().forceGrayPedestal(3));

        GameStateManager failureState = new GameStateManager();
        failureState.restoreS3kEmeraldProgress(
                List.of(0, 0, 0, 2, 0, 0, 0), true);
        ObjectServices failureServices = mock(ObjectServices.class);
        when(failureServices.gameState()).thenReturn(failureState);
        when(failureServices.sanctuaryReturnContext()).thenReturn(java.util.Optional.of(
                new com.openggf.level.SanctuaryReturnContext(3, false)));
        HPZSSEntryControlObjectInstance failureController =
                new HPZSSEntryControlObjectInstance(
                        new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0));
        failureController.setServices(failureServices);
        assertFalse(failureController.runtimeForChild().transformationActive());
        HPZSuperEmeraldObjectInstance failedPedestal =
                new HPZSuperEmeraldObjectInstance(
                        new ObjectSpawn(0, 0, 0xB4, 3, 0, false, 0),
                        failureController);
        assertTrue(failedPedestal.isSelectable());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY,
                failedPedestal.display());
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
                com.openggf.sprites.playable.ObjectControlState
                        .NATIVE_BITS_0_TO_6_CPU_ALLOWED_MOVEMENT_SUPPRESSED);
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
        teleporter.update(0, null);
        var teleporterSnapshot = teleporter.captureRewindState();
        teleporter.restoreRewindState(teleporterSnapshot);
        assertEquals(0x1640, teleporter.getX());
        assertEquals(0x3C7, teleporter.getY());
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

    private static RomByteReader masterEmeraldPaletteReader() {
        byte[] rom = new byte[0x91518];
        int base = Sonic3kConstants.HPZ_MASTER_EMERALD_PALETTE_SCRIPT_ADDR;
        writeU32(rom, base, 0x914EE);
        writeU16(rom, base + 4, 1);
        int[] script = {
                0, 0xF, 1, 9, 2, 9, 3, 7, 4, 7, 5, 5,
                6, 5, 5, 5, 4, 7, 3, 7, 2, 9, 1, 9
        };
        for (int i = 0; i < script.length; i++) {
            rom[base + 6 + i] = (byte) script[i];
        }
        int[] glowFrames = {
                0x1D, 0x1D, 0x1D, 0xC, 0xD, 0xE,
                0x1D, 0xF, 0x10, 0x11, 0x1D, 0x1D
        };
        for (int i = 0; i < glowFrames.length; i++) {
            rom[Sonic3kConstants.HPZ_MASTER_EMERALD_GLOW_ANIMATION_ADDR + i] =
                    (byte) glowFrames[i];
        }
        int colorsBase = 0x914EE;
        int[] offsets = {0xE, 0x12, 0x16, 0x1A, 0x1E, 0x22, 0x26};
        int[][] colors = {
                {0x6A0, 0x660}, {0x8C0, 0x680}, {0xAC0, 0x680},
                {0xCE0, 0x880}, {0xCE6, 0x6A2}, {0xCE8, 0xAC0},
                {0xEEC, 0xCE8}
        };
        for (int i = 0; i < offsets.length; i++) {
            writeU16(rom, colorsBase + i * 2, offsets[i]);
            writeU16(rom, colorsBase + offsets[i], colors[i][0]);
            writeU16(rom, colorsBase + offsets[i] + 2, colors[i][1]);
        }
        return RomByteReader.fromBytes(rom);
    }

    private static void writeU16(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static void writeU32(byte[] target, int offset, int value) {
        writeU16(target, offset, value >>> 16);
        writeU16(target, offset + 2, value);
    }
}
