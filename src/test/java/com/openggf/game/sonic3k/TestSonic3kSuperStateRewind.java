package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PowerUpSpawner;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.Palette;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestSonic3kSuperStateRewind {

    @Test
    void hyperFlashUploadNeverMutatesCyclingSoftwarePalette() {
        Palette live = new Palette();
        live.setColor(2, new Palette.Color((byte) 1, (byte) 2, (byte) 3));

        Palette firstUpload = Sonic3kSuperStateController.buildHyperFlashUpload(live, 0);
        live.setColor(2, new Palette.Color((byte) 4, (byte) 5, (byte) 6));
        Palette secondUpload = Sonic3kSuperStateController.buildHyperFlashUpload(live, 2);

        assertEquals(4, live.getColor(2).r);
        assertEquals(5, live.getColor(2).g);
        assertEquals(6, live.getColor(2).b);
        assertEquals(255, firstUpload.getColor(2).r & 0xFF);
        assertEquals(255, secondUpload.getColor(2).r & 0xFF);
        assertEquals(255, firstUpload.getColor(0).r & 0xFF,
                "line 0 backdrop is white during the Hyper flash");
        assertEquals(0, secondUpload.getColor(0).r & 0xFF,
                "only line 2 color 0 remains black");
        assertEquals(255, Sonic3kSuperStateController.buildHyperFlashUpload(live, 3)
                .getColor(0).r & 0xFF);
    }

    @Test
    void constructionMayDispatchResetBeforeSubclassFieldsInitialize() {
        assertDoesNotThrow(() -> new Sonic3kSuperStateController(
                new Sonic("sonic", (short) 0, (short) 0)));
    }

    @Test
    void hyperDashAppliesThePoweredScreenAttack() throws Exception {
        Sonic sonic = new Sonic("sonic", (short) 0, (short) 0);
        PowerUpSpawner powerUps = mock(PowerUpSpawner.class);
        sonic.setPowerUpSpawner(powerUps);
        Sonic3kSuperStateController controller = new Sonic3kSuperStateController(sonic);
        setField(controller, "activeFormTier", S3kFormTier.HYPER);
        setField(SuperStateController.class, controller, "state", SuperState.SUPER);
        ObjectManager objects = mock(ObjectManager.class);
        var poweredAttacks = mock(com.openggf.level.objects.PoweredAttackSurface.class);
        when(objects.poweredAttacks()).thenReturn(poweredAttacks);

        controller.triggerPoweredAirDashEffects(objects);

        verify(poweredAttacks).apply(sonic);
        verify(powerUps).registerObject(any(
                com.openggf.game.sonic3k.objects.HyperSonicStarsObjectInstance.class));
        assertEquals(4, getIntField(controller, "hyperFlashFrames"));
        controller.update();
        controller.update();
        controller.update();
        assertEquals(4, getIntField(controller, "hyperFlashFrames"),
                "gameplay updates must not consume the VInt-owned flash timer");
        for (int i = 0; i < 4; i++) {
            controller.onPaletteUploadVInt();
        }
        assertEquals(0, getIntField(controller, "hyperFlashFrames"),
                "ROM flash must last exactly four V-Int updates");
        assertTrue(getBooleanField(controller, "hyperFlashRestorePending"));
        controller.onPaletteUploadVInt();
        assertTrue(getBooleanField(controller, "hyperFlashRestorePending"),
                "a headless VInt with no level palette must retain the pending restore");
        assertEquals(0, getIntField(controller, "hyperFlashFrames"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        setField(target.getClass(), target, name, value);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getIntField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private Sonic sonic;
    private Sonic3kSuperStateController controller;

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetLevelGamestate(GameModuleRegistry.getCurrent().createLevelState());
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markEmeraldCollected(i);
        }
        sonic = new Sonic("sonic", (short) 0, (short) 0);
        sonic.setRingCount(50);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(sonic, "sonic");
        controller = new Sonic3kSuperStateController(sonic);
        sonic.setSuperStateController(controller);
    }

    @Test
    void midTransformationControllerTimingRoundTripsThroughPlayerSnapshot() {
        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 5; i++) {
            controller.update();
        }
        PerObjectRewindSnapshot snapshot = sonic.captureRewindState();
        SuperStateController.RewindState expected =
                snapshot.playerExtra().controllerState().superStateState();
        assertNotNull(expected);

        for (int i = 0; i < 7; i++) {
            controller.update();
        }
        sonic.restoreRewindState(snapshot);

        assertEquals(expected, controller.captureRewindState());
        assertEquals(SuperState.TRANSFORMING, controller.getState());
        assertEquals(50, sonic.getRingCount(), "restore must not replay ring drain");
    }

    @Test
    void activeSuperRestoreReconcilesPhysicsWithoutReplayingActivation() {
        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) {
            controller.update();
        }
        assertEquals(SuperState.SUPER, controller.getState());
        PerObjectRewindSnapshot snapshot = sonic.captureRewindState();

        controller.debugDeactivate();
        assertEquals(PhysicsProfile.SONIC_2_SONIC, sonic.getPhysicsProfile());
        sonic.restoreRewindState(snapshot);

        assertEquals(SuperState.SUPER, controller.getState());
        assertEquals(PhysicsProfile.SONIC_3K_SUPER_SONIC, sonic.getPhysicsProfile());
        assertEquals(50, sonic.getRingCount());
    }

    @Test
    void activeHyperTierRoundTripsWithoutReInferringFromEmeraldState() {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        assertTrue(controller.activateFromAirAbility());
        assertEquals(S3kFormTier.HYPER, controller.getActiveFormTier());
        assertTrue(controller.isHyperFormActive());
        PerObjectRewindSnapshot snapshot = sonic.captureRewindState();

        controller.debugDeactivate();
        sonic.restoreRewindState(snapshot);

        assertEquals(S3kFormTier.HYPER, controller.getActiveFormTier());
        assertTrue(controller.isHyperFormActive());
    }

    @Test
    void deathAndLifecycleResetClearTierAndSuperFlag() {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        assertTrue(controller.activateFromAirAbility());
        sonic.setDead(true);

        controller.update();

        assertEquals(S3kFormTier.NORMAL, controller.getActiveFormTier());
        assertEquals(SuperState.NORMAL, controller.getState());
        assertTrue(!sonic.isSuperSonic());

        sonic.setDead(false);
        sonic.setInvincibleFrames(0);
        assertTrue(controller.activateFromAirAbility());
        controller.reset();
        assertEquals(S3kFormTier.NORMAL, controller.getActiveFormTier());
        assertTrue(!sonic.isSuperSonic());
    }

    @Test
    void deathRestoresNormalRendererAndAnimationResources() throws Exception {
        PlayerSpriteRenderer normalRenderer = mock(PlayerSpriteRenderer.class);
        PlayerSpriteRenderer poweredRenderer = mock(PlayerSpriteRenderer.class);
        SpriteAnimationSet normalAnimations = new SpriteAnimationSet();
        SpriteAnimationSet poweredAnimations = new SpriteAnimationSet();
        sonic.setSpriteRenderer(normalRenderer);
        sonic.setAnimationSet(normalAnimations);
        setControllerField("superRenderer", poweredRenderer);
        setControllerField("superAnimSet", poweredAnimations);

        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) {
            controller.update();
        }
        assertSame(poweredRenderer, sonic.getSpriteRenderer());
        assertSame(poweredAnimations, sonic.getAnimationSet());

        sonic.setDead(true);
        controller.update();

        assertSame(normalRenderer, sonic.getSpriteRenderer());
        assertSame(normalAnimations, sonic.getAnimationSet());
    }

    @Test
    void hyperSonicStartsWithTheNormalSuperFadeBeforeItsHyperCycle() throws Exception {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        assertTrue(controller.activateFromAirAbility());

        assertEquals(1, controller.captureRewindState().paletteState());
        assertEquals(4, controller.activePaletteReloadForTest());
        assertEquals(java.util.List.of(2, 3, 4), controller.activePaletteColorIndicesForTest());
        assertPaletteWrap(controller, 11 * 6, 4);
    }

    @Test
    void hyperSonicUsesTheTimedSonicReverseFade() throws Exception {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        assertTrue(controller.activateFromAirAbility());

        controller.debugDeactivate();

        assertEquals(2, controller.captureRewindState().paletteState(),
                "Hyper Sonic follows SuperHyper_PalCycle_Revert's Sonic branch");
        assertEquals(0x1E, controller.captureRewindState().paletteFrame());
    }

    @Test
    void tailsAndKnucklesPublishPoweredPaletteOnFirstTransformationTick() {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }

        Tails tails = new Tails("tails", (short) 0, (short) 0);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(tails, "tails");
        tails.setRingCount(50);
        Sonic3kSuperStateController tailsController = new Sonic3kSuperStateController(tails);
        tails.setSuperStateController(tailsController);
        assertTrue(tailsController.activateFromAirAbility());
        tailsController.update();
        assertEquals(SuperState.SUPER, tailsController.getState());
        assertEquals(0xB, tailsController.activePaletteReloadForTest());
        assertEquals(java.util.List.of(8, 9, 11), tailsController.activePaletteColorIndicesForTest());
        assertPaletteWrap(tailsController, 5 * 6, 0xB);
        assertEquals(0, tailsController.superTailsCompanionPaletteFrameForTest());
        assertEquals(1, tailsController.superTailsCompanionPaletteTimerForTest());
        tailsController.update();
        tailsController.update();
        assertEquals(6, tailsController.superTailsCompanionPaletteFrameForTest(),
                "Super Tails also advances the shared Super-Sonic/Flicky palette");
        assertEquals(6, tailsController.superTailsCompanionPaletteTimerForTest());
        tailsController.debugDeactivate();
        assertEquals(0, tailsController.captureRewindState().paletteState(),
                "Tails uses the ROM one-step revert branch");

        Knuckles knuckles = new Knuckles("knuckles", (short) 0, (short) 0);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(knuckles, "knuckles");
        knuckles.setRingCount(50);
        Sonic3kSuperStateController knucklesController = new Sonic3kSuperStateController(knuckles);
        knuckles.setSuperStateController(knucklesController);
        assertTrue(knucklesController.activateFromAirAbility());
        knucklesController.update();
        assertEquals(SuperState.SUPER, knucklesController.getState());
        assertEquals(2, knucklesController.activePaletteReloadForTest());
        assertEquals(0xE, knucklesController.activePaletteWrapReloadForTest());
        assertEquals(10, knucklesController.activePaletteFrameCountForTest());
        assertEquals(java.util.List.of(2, 3, 4), knucklesController.activePaletteColorIndicesForTest());
        assertPaletteWrap(knucklesController, 9 * 6, 0xE);
        knucklesController.debugDeactivate();
        assertEquals(0, knucklesController.captureRewindState().paletteState(),
                "Knuckles uses the dedicated one-frame ROM revert palette");
    }

    @Test
    void superTailsTransformationRegistersExactlyOneFlickyFlock() {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        PowerUpSpawner powerUps = mock(PowerUpSpawner.class);
        tails.setPowerUpSpawner(powerUps);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(tails, "tails");
        tails.setRingCount(50);
        Sonic3kSuperStateController tailsController = new Sonic3kSuperStateController(tails);
        tails.setSuperStateController(tailsController);

        assertTrue(tailsController.activateFromAirAbility());

        assertEquals(S3kFormTier.SUPER_TAILS, tailsController.getActiveFormTier());
        verify(powerUps, times(1)).registerObject(any(
                com.openggf.game.sonic3k.objects.SuperTailsFlickyFlockObjectInstance.class));
    }

    @Test
    void debugActivationSelectsSuperTailsAndRegistersItsFlockWithoutProgression() {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        PowerUpSpawner powerUps = mock(PowerUpSpawner.class);
        tails.setPowerUpSpawner(powerUps);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(tails, "tails");
        Sonic3kSuperStateController tailsController = new Sonic3kSuperStateController(tails);
        tails.setSuperStateController(tailsController);

        tailsController.debugActivate();

        assertEquals(S3kFormTier.SUPER_TAILS, tailsController.getActiveFormTier());
        verify(powerUps).registerObject(any(
                com.openggf.game.sonic3k.objects.SuperTailsFlickyFlockObjectInstance.class));
    }

    @Test
    void debugActivationDoesNotGiveSecondaryTailsASuperTailsFlock() {
        Tails main = new Tails("tails", (short) 0, (short) 0);
        Tails secondary = new Tails("tails_p2", (short) 0, (short) 0);
        PowerUpSpawner powerUps = mock(PowerUpSpawner.class);
        secondary.setPowerUpSpawner(powerUps);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(main, "tails");
        Sonic3kSuperStateController secondaryController =
                new Sonic3kSuperStateController(secondary);
        secondary.setSuperStateController(secondaryController);

        secondaryController.debugActivate();

        assertEquals(S3kFormTier.NORMAL, secondaryController.getActiveFormTier());
        verify(powerUps, never()).registerObject(any(
                com.openggf.game.sonic3k.objects.SuperTailsFlickyFlockObjectInstance.class));
    }

    @Test
    void activeSuperTailsFlockReconciliationCreatesOnceAndThenDeduplicates() {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(tails, "tails");
        Sonic3kSuperStateController tailsController = new Sonic3kSuperStateController(tails);
        ObjectManager objects = mock(ObjectManager.class);
        var active = new ArrayList<com.openggf.level.objects.ObjectInstance>();
        when(objects.getActiveObjects()).thenReturn(active);
        when(objects.createDynamicObject(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<com.openggf.level.objects.ObjectInstance> factory =
                    invocation.getArgument(0);
            var created = factory.get();
            active.add(created);
            return created;
        });

        var first = tailsController.ensureSuperTailsFlickies(objects);
        var second = tailsController.ensureSuperTailsFlickies(objects);

        assertSame(first, second);
        verify(objects, times(1)).createDynamicObject(any());
    }

    @Test
    void superTailsCompanionPaletteTimingRoundTripsThroughRewind() throws Exception {
        for (int i = 0; i < 7; i++) {
            GameServices.gameState().markSuperEmeraldCollected(i);
        }
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(tails, "tails");
        tails.setRingCount(50);
        Sonic3kSuperStateController tailsController = new Sonic3kSuperStateController(tails);
        tails.setSuperStateController(tailsController);
        assertTrue(tailsController.activateFromAirAbility());
        tailsController.update();
        tailsController.update();
        tailsController.update();
        PerObjectRewindSnapshot snapshot = tails.captureRewindState();
        int expectedFrame = tailsController.superTailsCompanionPaletteFrameForTest();
        int expectedTimer = tailsController.superTailsCompanionPaletteTimerForTest();

        for (int i = 0; i < 5; i++) tailsController.update();
        tails.restoreRewindState(snapshot);

        assertEquals(expectedFrame, tailsController.superTailsCompanionPaletteFrameForTest());
        assertEquals(expectedTimer, tailsController.superTailsCompanionPaletteTimerForTest());
    }

    @Test
    void capturedBaseSurfaceAndWaterColorsRoundTripExplicitly() throws Exception {
        byte[] surface = {0x02, 0x22, 0x04, 0x44, 0x06, 0x66};
        byte[] underwater = {0x00, 0x02, 0x02, 0x24, 0x04, 0x46};
        setControllerField("savedNormalPalette", surface.clone());
        setControllerField("savedNormalUnderwaterPalette", underwater.clone());
        SuperStateController.RewindState snapshot = controller.captureRewindState();

        setControllerField("savedNormalPalette", new byte[6]);
        setControllerField("savedNormalUnderwaterPalette", null);
        controller.restoreRewindState(snapshot);

        assertEquals(snapshot.savedNormalPalette(), controller.captureRewindState().savedNormalPalette());
        assertEquals(snapshot.savedNormalUnderwaterPalette(),
                controller.captureRewindState().savedNormalUnderwaterPalette());
    }

    private static void assertPaletteWrap(Sonic3kSuperStateController controller,
                                          int finalFrameOffset, int expectedReload) {
        try {
            java.lang.reflect.Field frame =
                    Sonic3kSuperStateController.class.getDeclaredField("paletteFrame");
            frame.setAccessible(true);
            frame.setInt(controller, finalFrameOffset);
            java.lang.reflect.Method advance =
                    Sonic3kSuperStateController.class.getDeclaredMethod("advanceActivePaletteFrame");
            advance.setAccessible(true);
            advance.invoke(controller);
            assertEquals(0, controller.captureRewindState().paletteFrame());
            assertEquals(expectedReload, controller.captureRewindState().paletteTimer());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void setControllerField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = Sonic3kSuperStateController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    void knucklesRestoreUsesCharacterSpecificNormalAndSuperProfiles() {
        Knuckles knuckles = new Knuckles("knuckles", (short) 0, (short) 0);
        knuckles.setRingCount(50);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(knuckles, "knuckles");
        Sonic3kSuperStateController knucklesController = new Sonic3kSuperStateController(knuckles);
        knuckles.setSuperStateController(knucklesController);

        assertTrue(knucklesController.activateFromAirAbility());
        for (int i = 0; i < 30; i++) {
            knucklesController.update();
        }
        PerObjectRewindSnapshot snapshot = knuckles.captureRewindState();
        assertEquals(PhysicsProfile.SONIC_3K_SUPER_KNUCKLES, knuckles.getPhysicsProfile());

        knucklesController.debugDeactivate();
        assertEquals(PhysicsProfile.SONIC_3K_KNUCKLES, knuckles.getPhysicsProfile());
        knuckles.restoreRewindState(snapshot);

        assertEquals(SuperState.SUPER, knucklesController.getState());
        assertEquals(PhysicsProfile.SONIC_3K_SUPER_KNUCKLES, knuckles.getPhysicsProfile());
    }

    @Test
    void normalUnderwaterPhysicsSurvivesRoundTripFromSuperState() {
        sonic.setInWater(true);
        PerObjectRewindSnapshot normalUnderwater = sonic.captureRewindState();
        assertEquals(0x380, sonic.getJump());

        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) {
            controller.update();
        }
        sonic.restoreRewindState(normalUnderwater);

        assertEquals(SuperState.NORMAL, controller.getState());
        assertEquals(0x380, sonic.getJump());
        assertEquals(PhysicsProfile.SONIC_2_SONIC, sonic.getPhysicsProfile());
    }

    @Test
    void normalSpeedShoesPhysicsSurvivesRoundTripFromSuperState() {
        sonic.giveSpeedShoes();
        PerObjectRewindSnapshot normalWithShoes = sonic.captureRewindState();
        short expectedMax = sonic.getMax();

        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) {
            controller.update();
        }
        sonic.restoreRewindState(normalWithShoes);

        assertEquals(SuperState.NORMAL, controller.getState());
        assertEquals(expectedMax, sonic.getMax());
        assertEquals(PhysicsProfile.SONIC_2_SONIC, sonic.getPhysicsProfile());
    }
}
