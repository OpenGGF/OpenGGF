package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the expanded ObjectServices methods delegate to the correct singletons.
 */
class TestObjectServicesExpansion {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @Test
    void defaultObjectServices_camera_returnsSingleton() {
        DefaultObjectServices services = sessionServices();
        assertSame(GameServices.camera(), services.camera(),
                "camera() should delegate to GameServices.camera()");
    }

    @Test
    void defaultObjectServices_levelManager_returnsRuntimeLevelManager() {
        DefaultObjectServices services = sessionServices();
        assertSame(GameServices.level(), services.levelManager(),
                "levelManager() should delegate to the runtime-owned level manager");
    }

    @Test
    void defaultObjectServices_gameState_returnsSingleton() {
        DefaultObjectServices services = sessionServices();
        assertSame(GameServices.gameState(), services.gameState(),
                "gameState() should delegate to GameServices.gameState()");
    }

    @Test
    void defaultObjectServices_worldSession_returnsRuntimeWorldSession() {
        DefaultObjectServices services = sessionServices();
        assertSame(GameServices.worldSession(), services.worldSession(),
                "worldSession() should delegate to the runtime-owned world session");
    }

    @Test
    void defaultObjectServices_gameModule_returnsRuntimeModule() {
        DefaultObjectServices services = sessionServices();
        assertSame(GameServices.module(), services.gameModule(),
                "gameModule() should delegate to the runtime-owned module");
    }

    @Test
    void defaultObjectServices_processServices_returnRuntimeEngineServicesMembers() {
        DefaultObjectServices services = sessionServices();
        EngineContext engineServices = EngineServices.current();

        assertSame(engineServices, services.engineServices());
        assertSame(engineServices.configuration(), services.configuration());
        assertSame(engineServices.debugOverlay(), services.debugOverlay());
        assertSame(engineServices.roms(), services.romManager());
        assertSame(engineServices.crossGameFeatures(), services.crossGameFeatures());
    }

    @Test
    void defaultObjectServices_sidekicks_returnsUnmodifiableList() {
        DefaultObjectServices services = sessionServices();
        var sidekicks = services.sidekicks();
        assertNotNull(sidekicks);
        assertThrows(UnsupportedOperationException.class, () -> sidekicks.add(null));
    }

    @Test
    void defaultObjectServices_requiresGameplayMode() {
        assertThrows(NullPointerException.class,
                () -> new DefaultObjectServices(null, EngineServices.current()));
    }

    @Test
    void defaultObjectServices_bootstrapConstructor_requiresActiveRuntime() {
        LevelManager levelManager = GameServices.level();
        Camera camera = GameServices.camera();
        GameStateManager gameState = GameServices.gameState();
        SpriteManager spriteManager = GameServices.sprites();
        FadeManager fadeManager = GameServices.fade();
        WaterSystem waterSystem = GameServices.water();
        ParallaxManager parallaxManager = GameServices.parallax();

        SessionManager.clear();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DefaultObjectServices(
                        levelManager,
                        camera,
                        gameState,
                        spriteManager,
                        fadeManager,
                        waterSystem,
                        parallaxManager));
        assertTrue(ex.getMessage().contains("active gameplay runtime"),
                "legacy constructor should fail before fabricating detached runtime-owned services");
    }

    @Test
    void bootstrapObjectServices_delegatesExpandedApiToGameServices() {
        BootstrapObjectServices services = new BootstrapObjectServices();

        assertSame(GameServices.level(), services.levelManager());
        assertSame(GameServices.camera(), services.camera());
        assertSame(GameServices.gameState(), services.gameState());
        assertSame(SonicConfigurationService.getInstance(), services.configuration());
        assertSame(DebugOverlayManager.getInstance(), services.debugOverlay());
        assertSame(RomManager.getInstance(), services.romManager());
        assertSame(CrossGameFeatureProvider.getInstance(), services.crossGameFeatures());
        assertNotNull(services.engineServices());
    }

    @Test
    void bootstrapObjectServices_usesRuntimeOwnedMutationPipeline() {
        BootstrapObjectServices services = new BootstrapObjectServices();

        assertSame(GameServices.zoneLayoutMutationPipeline(), services.zoneLayoutMutationPipeline(),
                "bootstrap object services must not create a private mutation pipeline when runtime exists");
    }

    @Test
    void defaultObjectServices_bonusStageActionsUseInjectedGameplayProviderNotActiveSession() {
        // After the activeBonusStageProvider migration to GameplayModeContext,
        // the provider is gameplay-scoped. DefaultObjectServices
        // captures the provider snapshot at construction time. This test verifies
        // that the captured snapshot is used by bonus-stage forwarding methods,
        // even after the active session's provider is changed afterwards.
        GameplayModeContext gameplayA = TestEnvironment.activeGameplayMode();
        CountingBonusStageProvider providerA = new CountingBonusStageProvider();
        gameplayA.setActiveBonusStageProvider(providerA);

        // Capture providerA into the services BEFORE switching the active session.
        DefaultObjectServices servicesFromGameplayA = sessionServices(gameplayA);

        GameplayModeContext gameplayB = SessionManager.openGameplaySession(GameServices.module());
        CountingBonusStageProvider providerB = new CountingBonusStageProvider();
        gameplayB.setActiveBonusStageProvider(providerB);

        servicesFromGameplayA.requestBonusStageExit();
        servicesFromGameplayA.addBonusStageRings(7);
        servicesFromGameplayA.setBonusStageShield(com.openggf.game.ShieldType.LIGHTNING);

        assertEquals(1, providerA.requestExitCount,
                "requestBonusStageExit should call provider captured at construction");
        assertEquals(7, providerA.ringsAdded,
                "addBonusStageRings should add rings on the captured provider");
        assertEquals(1, providerA.shieldsSet,
                "setBonusStageShield should forward to the captured provider");
        assertEquals(0, providerB.requestExitCount,
                "later-swapped session provider must not receive captured-services calls");
        assertEquals(0, providerB.ringsAdded,
                "later-swapped session provider must not receive captured-services calls");
        assertEquals(0, providerB.shieldsSet,
                "later-swapped session provider must not receive captured-services calls");
    }

    private static final class CountingBonusStageProvider implements BonusStageProvider {
        int requestExitCount;
        int ringsAdded;
        int shieldsSet;

        @Override
        public boolean hasBonusStages() {
            return true;
        }

        @Override
        public BonusStageType selectBonusStage(int ringCount) {
            return null;
        }

        @Override
        public void onEnter(BonusStageType type, com.openggf.game.BonusStageState savedState) {
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onFrameUpdate() {
        }

        @Override
        public boolean isStageComplete() {
            return false;
        }

        @Override
        public void requestExit() {
            requestExitCount++;
        }

        @Override
        public int getZoneId(BonusStageType type) {
            return -1;
        }

        @Override
        public int getMusicId(BonusStageType type) {
            return -1;
        }

        @Override
        public com.openggf.game.BonusStageState getSavedState() {
            return null;
        }

        @Override
        public com.openggf.game.BonusStageProvider.BonusStageRewards getRewards() {
            return com.openggf.game.BonusStageProvider.BonusStageRewards.none();
        }

        @Override
        public void addRings(int count) {
            ringsAdded += count;
        }

        @Override
        public void addLife() {
        }

        @Override
        public void setAwardedShield(com.openggf.game.ShieldType type) {
            shieldsSet++;
        }
    }

    private DefaultObjectServices sessionServices() {
        return sessionServices(TestEnvironment.activeGameplayMode());
    }

    private DefaultObjectServices sessionServices(GameplayModeContext gameplayMode) {
        return new DefaultObjectServices(gameplayMode, EngineServices.current());
    }
}


