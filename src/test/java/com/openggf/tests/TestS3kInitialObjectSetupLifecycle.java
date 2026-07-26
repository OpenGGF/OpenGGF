package com.openggf.tests;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.InitialObjectSetupLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kInitialObjectSetupLifecycle {

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void contextAuthorityMatrixAllowsAndPublishesOnlyFreshFullPostLoadAssembly() throws Exception {
        LevelManager manager = managerWithProfile(requestingProfile());
        List<AuthorityCase> cases = List.of(
                new AuthorityCase(true, LevelLoadMode.FULL, true,
                        LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY),
                new AuthorityCase(false, LevelLoadMode.PREVIEW_CAPTURE, true,
                        LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY),
                new AuthorityCase(false, LevelLoadMode.FULL, false,
                        LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY),
                new AuthorityCase(false, LevelLoadMode.FULL, true,
                        LevelAssemblyKind.DECODE_ONLY),
                new AuthorityCase(false, LevelLoadMode.FULL, true,
                        LevelAssemblyKind.STATE_RESTORATION));

        for (AuthorityCase authorityCase : cases) {
            LevelLoadContext ctx = context(
                    authorityCase.mode(), authorityCase.postLoad(), authorityCase.assemblyKind());
            manager.loadLevel(0, authorityCase.mode(), ctx);
            assertTrue(manager.hasPendingInitialObjectSetupPass() == authorityCase.expected(),
                    authorityCase.toString());
            assertTrue((ctx.requestedInitialObjectSetupLifecycle()
                            == InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE)
                            == authorityCase.expected(),
                    authorityCase.toString());
        }
    }

    @Test
    void successfulLoadPublishesOnlyAfterRequestedProfileCompletes() throws Exception {
        LevelLoadContext ctx = freshContext();
        LevelManager manager = managerWithProfile(requestingProfile());

        assertFalse(manager.hasPendingInitialObjectSetupPass());
        manager.loadLevel(0, LevelLoadMode.FULL, ctx);

        assertTrue(manager.hasPendingInitialObjectSetupPass());
    }

    @Test
    void reusedContextCannotRepublishPriorFreshLoadAuthorityAndCanRetryFresh() throws Exception {
        LevelManager manager = managerWithProfile(requestingProfile());
        LevelLoadContext reused = freshContext();
        manager.loadLevel(0, LevelLoadMode.FULL, reused);
        assertTrue(manager.hasPendingInitialObjectSetupPass());

        List<AuthorityCase> deniedReuseCases = List.of(
                new AuthorityCase(false, LevelLoadMode.FULL, true,
                        LevelAssemblyKind.STATE_RESTORATION),
                new AuthorityCase(false, LevelLoadMode.FULL, true,
                        LevelAssemblyKind.DECODE_ONLY),
                new AuthorityCase(false, LevelLoadMode.PREVIEW_CAPTURE, true,
                        LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY),
                new AuthorityCase(false, LevelLoadMode.FULL, false,
                        LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY));

        for (AuthorityCase denied : deniedReuseCases) {
            reused.setIncludePostLoadAssembly(denied.postLoad());
            reused.setAssemblyKind(denied.assemblyKind());
            manager.loadLevel(0, denied.mode(), reused);

            assertFalse(manager.hasPendingInitialObjectSetupPass(), denied.toString());
            assertEquals(InitialObjectSetupLifecycle.NONE,
                    reused.requestedInitialObjectSetupLifecycle(), denied.toString());

            reused.setIncludePostLoadAssembly(true);
            reused.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
            manager.loadLevel(0, LevelLoadMode.FULL, reused);
            assertTrue(manager.hasPendingInitialObjectSetupPass(),
                    "a fresh authorized retry must publish after " + denied);
        }
    }

    @Test
    void genuineProductionLoadCurrentLevelPublishesRequest() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            assertTrue(GameServices.level().hasPendingInitialObjectSetupPass());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void requestThenThrowClearsOldTokenPreservesCauseAndRetryCanPublish() throws Exception {
        LevelManager manager = managerWithProfile(requestingProfile());
        manager.loadLevel(0, LevelLoadMode.FULL, freshContext());
        assertTrue(manager.hasPendingInitialObjectSetupPass());

        IllegalStateException startupFailure = new IllegalStateException("synthetic startup failure");
        installProfile(requestingProfileThenThrow(startupFailure));
        IOException wrapped = assertThrows(IOException.class,
                () -> manager.loadLevel(0, LevelLoadMode.FULL, freshContext()));

        assertSame(startupFailure, wrapped.getCause());
        assertFalse(manager.hasPendingInitialObjectSetupPass(),
                "entry must clear the old token and failure must not publish the request");

        installProfile(requestingProfile());
        manager.loadLevel(0, LevelLoadMode.FULL, freshContext());
        assertTrue(manager.hasPendingInitialObjectSetupPass(),
                "a genuine successful retry must publish a fresh request");
    }

    @Test
    void sharedReuseAndProductionSeamlessTransitionCannotArmButFreshReloadCan() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            LevelLoadContext restoration = context(
                    LevelLoadMode.FULL, true, LevelAssemblyKind.STATE_RESTORATION);
            manager.loadLevel(0, LevelLoadMode.FULL, restoration);
            assertFalse(manager.hasPendingInitialObjectSetupPass());

            var reusedLevel = manager.getCurrentLevel();
            HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
            assertSame(reusedLevel, manager.getCurrentLevel(),
                    "the SharedLevel fixture must reuse the live level without another load");
            assertFalse(manager.hasPendingInitialObjectSetupPass());

            manager.executeActTransition(SeamlessLevelTransitionRequest
                    .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                    .targetZoneAct(0, 0)
                    .preserveMusic(true)
                    .build());
            assertFalse(manager.hasPendingInitialObjectSetupPass(),
                    "the production in-place transition bypasses fresh-load authority");

            manager.loadCurrentLevel();
            assertTrue(manager.hasPendingInitialObjectSetupPass(),
                    "the next genuine playable-runtime assembly publishes one request");
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void teardownClearsPendingLifecycle() throws Exception {
        LevelManager manager = managerWithProfile(requestingProfile());
        manager.loadLevel(0, LevelLoadMode.FULL, freshContext());
        assertTrue(manager.hasPendingInitialObjectSetupPass());

        manager.resetState();

        assertFalse(manager.hasPendingInitialObjectSetupPass());
    }

    private static LevelLoadContext context(LevelLoadMode mode,
                                            boolean postLoad, LevelAssemblyKind assemblyKind) {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setLoadMode(mode);
        ctx.setIncludePostLoadAssembly(postLoad);
        ctx.setAssemblyKind(assemblyKind);
        return ctx;
    }

    private static LevelLoadContext freshContext() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        ctx.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        return ctx;
    }

    private static LevelInitProfile requestingProfile() {
        return profileWithFactory(ctx -> List.of(new InitStep(
                "RequestInitialObjectSetup", "test",
                () -> ctx.requestInitialObjectSetupFromProfile(
                        InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE))));
    }

    private static LevelInitProfile requestingProfileThenThrow(RuntimeException failure) {
        return profileWithFactory(ctx -> List.of(
                new InitStep("RequestInitialObjectSetup", "test",
                        () -> ctx.requestInitialObjectSetupFromProfile(
                                InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE)),
                new InitStep("ThrowAfterRequest", "test", () -> {
                    throw failure;
                })));
    }

    private static LevelInitProfile profileWithFactory(
            java.util.function.Function<LevelLoadContext, List<InitStep>> factory) {
        return new LevelInitProfile() {
            @Override
            public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
                return factory.apply(ctx);
            }

            @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
            @Override public List<InitStep> perTestResetSteps() { return List.of(); }
            @Override public List<com.openggf.game.StaticFixup> postTeardownFixups() { return List.of(); }
        };
    }

    private static LevelManager managerWithProfile(LevelInitProfile profile) {
        Sonic3kGameModule real = new Sonic3kGameModule();
        GameModule module = mock(GameModule.class, delegatesTo(real));
        when(module.getLevelInitProfile()).thenReturn(profile);
        TestEnvironment.configureGameModuleFixture(module);
        return TestEnvironment.activeGameplayMode().getLevelManager();
    }

    private static void installProfile(LevelInitProfile profile) {
        when(GameModuleRegistry.getCurrent().getLevelInitProfile()).thenReturn(profile);
    }

    private record AuthorityCase(boolean expected, LevelLoadMode mode, boolean postLoad,
                                 LevelAssemblyKind assemblyKind) {
    }
}
