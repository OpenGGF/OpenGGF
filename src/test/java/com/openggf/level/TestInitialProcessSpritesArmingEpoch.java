package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.InitialObjectSetupLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestInitialProcessSpritesArmingEpoch {

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void successfulPublicFreshFullLoadResetsPlayableEpochAtLifecyclePublication() throws Exception {
        LevelManager manager = managerWithRequestingProfile();
        SpriteManager sprites = TestEnvironment.activeGameplayMode().getSpriteManager();
        sprites.setFrameCounter(37);
        LevelLoadContext fresh = new LevelLoadContext();
        fresh.setIncludePostLoadAssembly(true);
        fresh.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);

        manager.loadLevel(0, LevelLoadMode.FULL, fresh);

        assertEquals(0, sprites.getFrameCounter());
    }

    @Test
    void loadWithoutFreshSetupAuthorityDoesNotResetPlayableEpoch() throws Exception {
        LevelManager manager = managerWithRequestingProfile();
        SpriteManager sprites = TestEnvironment.activeGameplayMode().getSpriteManager();
        sprites.setFrameCounter(37);
        LevelLoadContext restoration = new LevelLoadContext();
        restoration.setIncludePostLoadAssembly(true);
        restoration.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);

        manager.loadLevel(0, LevelLoadMode.FULL, restoration);

        assertEquals(37, sprites.getFrameCounter());
    }

    private static LevelManager managerWithRequestingProfile() {
        LevelInitProfile profile = new LevelInitProfile() {
            @Override
            public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
                return List.of(new InitStep(
                        "RequestInitialObjectSetup", "test",
                        () -> ctx.requestInitialObjectSetupFromProfile(
                                InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE)));
            }

            @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
            @Override public List<InitStep> perTestResetSteps() { return List.of(); }
            @Override public List<com.openggf.game.StaticFixup> postTeardownFixups() {
                return List.of();
            }
        };
        Sonic3kGameModule real = new Sonic3kGameModule();
        GameModule module = mock(GameModule.class, delegatesTo(real));
        when(module.getLevelInitProfile()).thenReturn(profile);
        TestEnvironment.configureGameModuleFixture(module);
        return TestEnvironment.activeGameplayMode().getLevelManager();
    }
}
