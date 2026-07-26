package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.InitialObjectSetupLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.OscillationManager;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageComparisonState;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kInitialObjectSetupLifecycle {

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void firstAdmittedInvocationReturnsSetupOnlyAndTheRetryRunsGameplayFrame()
            throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int objectBefore = manager.getObjectManager().getFrameCounter();
            int levelBefore = manager.getFrameCounter();

            LevelFrameResult setup = LevelFrameStep.executeWithPause(
                    LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                        throw new AssertionError("SETUP_ONLY must not enter ordinary physics");
                    }, false, LevelFrameStep.DIRECT_WRAPPER);

            assertEquals(LevelFrameResult.SETUP_ONLY, setup);
            assertEquals(objectBefore + 1, manager.getObjectManager().getFrameCounter());
            assertEquals(levelBefore, manager.getFrameCounter());
            assertFalse(manager.hasPendingInitialObjectSetupPass());

            LevelFrameResult gameplay = LevelFrameStep.executeWithPause(
                    LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                    }, false, LevelFrameStep.DIRECT_WRAPPER);

            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, gameplay);
            assertEquals(objectBefore + 2, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void pausedAdmissionReturnsPausedWithoutConsumingSetupAuthority() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int objectBefore = manager.getObjectManager().getFrameCounter();

            LevelFrameResult result = LevelFrameStep.executeWithPause(
                    LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                        throw new AssertionError("PAUSED must not enter ordinary physics");
                    }, true, LevelFrameStep.DIRECT_WRAPPER);

            assertEquals(LevelFrameResult.PAUSED, result);
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(objectBefore, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
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
    void productionConsumerExecutesPendingSetupExactlyOnce() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int before = manager.getObjectManager().getFrameCounter();

            assertTrue(manager.consumePendingInitialObjectSetupPass());
            assertEquals(before + 1, manager.getObjectManager().getFrameCounter());
            assertFalse(manager.hasPendingInitialObjectSetupPass());

            assertFalse(manager.consumePendingInitialObjectSetupPass());
            assertEquals(before + 1, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void nonLevelTitleCardReleaseRetainsFreshSetupAuthority() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int before = manager.getObjectManager().getFrameCounter();
            GameLoop loop = releasingTitleCardLoop("BONUS_STAGE");

            assertTrue((boolean) invoke(loop, "updateTitleCardMode",
                    new Class<?>[] { boolean.class }, false));

            assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void specialStageEntryPausedTickAndResultsRetainPendingSetup() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int before = manager.getObjectManager().getFrameCounter();
            GameLoop loop = new GameLoop(new InputHandler());
            loop.setGameplayMode(TestEnvironment.activeGameplayMode());
            SpecialStageProvider provider = GameServices.module().getSpecialStageProvider();

            loop.enterSpecialStage();
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter());

            invoke(loop, "doEnterSpecialStage",
                    new Class<?>[] { SpecialStageProvider.class, int.class, boolean.class },
                    provider, 0, false);
            Sonic3kSpecialStageManager specialStage =
                    ((Sonic3kSpecialStageProvider) provider).getManager();
            Sonic3kSpecialStageComparisonState specialStateBefore =
                    specialStage.captureComparisonState();
            loop.pause();
            assertTrue(loop.isPaused());
            loop.step();

            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
            assertEquals(specialStateBefore, specialStage.captureComparisonState(),
                    "the loop-owned pause gate must skip the special-stage provider tick");
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter(),
                    "a paused special-stage tick must not dispatch level objects");
            loop.resume();
            assertFalse(loop.isPaused());

            invoke(loop, "doEnterResultsScreen", new Class<?>[0]);
            loop.step();

            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter(),
                    "results ticks must not consume or dispatch fresh-level setup");
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void genuineSpecialStageReturnReloadArmsAndLevelReleaseConsumesExactlyOnce()
            throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            assertTrue(manager.consumePendingInitialObjectSetupPass());
            GameLoop loop = new GameLoop(new InputHandler());
            loop.setGameplayMode(TestEnvironment.activeGameplayMode());
            setField(loop, "currentGameMode", GameMode.SPECIAL_STAGE_RESULTS);

            invoke(loop, "doExitResultsScreen", new Class<?>[0]);

            assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
            assertTrue(manager.hasPendingInitialObjectSetupPass(),
                    "the genuine return load publishes one fresh setup token");
            int beforeRelease = manager.getObjectManager().getFrameCounter();
            installReleasingTitleProvider(loop);
            assertTrue((boolean) invoke(loop, "updateTitleCardMode",
                    new Class<?>[] { boolean.class }, false));

            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            assertEquals(beforeRelease + 1, manager.getObjectManager().getFrameCounter());
            assertFalse(manager.consumePendingInitialObjectSetupPass());
            assertEquals(beforeRelease + 1, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void representedSpecialStageReturnRestorationDiscardsBeforeLevelRelease()
            throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            assertTrue(manager.consumePendingInitialObjectSetupPass());
            GameLoop loop = new GameLoop(new InputHandler());
            loop.setGameplayMode(TestEnvironment.activeGameplayMode());
            setField(loop, "currentGameMode", GameMode.SPECIAL_STAGE_RESULTS);
            invoke(loop, "doExitResultsScreen", new Class<?>[0]);
            assertTrue(manager.hasPendingInitialObjectSetupPass());

            manager.discardPendingInitialObjectSetupForStateRestoration();
            int beforeRelease = manager.getObjectManager().getFrameCounter();
            installReleasingTitleProvider(loop);
            assertTrue((boolean) invoke(loop, "updateTitleCardMode",
                    new Class<?>[] { boolean.class }, false));

            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            assertEquals(beforeRelease, manager.getObjectManager().getFrameCounter(),
                    "represented return state must not replay the native setup dispatch");
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void pausedFirstOrdinaryFrameRetainsSetupUntilGameplayResumes() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int before = manager.getObjectManager().getFrameCounter();

            LevelFrameResult paused = LevelFrameStep.executeWithPause(
                    LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                    }, true, LevelFrameStep.DIRECT_WRAPPER);

            assertEquals(LevelFrameResult.PAUSED, paused);
            assertTrue(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter());

            LevelFrameResult resumed = LevelFrameStep.executeWithPause(
                    LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                    }, true, LevelFrameStep.DIRECT_WRAPPER);

            assertEquals(LevelFrameResult.SETUP_ONLY, resumed);
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            assertEquals(before + 1, manager.getObjectManager().getFrameCounter(),
                    "resume runs setup only; the caller retries for the ordinary frame");
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void representedStateRestorationDiscardsWithoutExecutingFreshSetup() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            int before = manager.getObjectManager().getFrameCounter();

            manager.discardPendingInitialObjectSetupForStateRestoration();

            assertFalse(manager.hasPendingInitialObjectSetupPass());
            assertFalse(manager.consumePendingInitialObjectSetupPass());
            assertEquals(before, manager.getObjectManager().getFrameCounter());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void setupExceptionConsumesTokenAndLeavesSolidRegistryBalanced() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            LevelManager manager = GameServices.level();
            ObjectInstance throwing = mock(ObjectInstance.class);
            doThrow(new IllegalStateException("setup boom"))
                    .when(throwing).update(org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.any());
            manager.getObjectManager().addDynamicObject(throwing);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    manager::consumePendingInitialObjectSetupPass);

            assertEquals("setup boom", failure.getMessage());
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            assertFalse(manager.consumePendingInitialObjectSetupPass());
        } finally {
            sharedLevel.dispose();
        }
    }

    @Test
    void visibleTitleReleaseAndNoTitleFirstFrameConvergeForFreshCnz() throws Exception {
        ConvergenceState visible = loadCnzThroughVisibleTitleRelease();
        TestEnvironment.resetAll();
        ConvergenceState noTitle = loadCnzThroughNoTitleFirstFrame();

        assertEquals(visible, noTitle);
        assertTrue(visible.runtime().objects().stream()
                        .anyMatch(row -> row.className().contains("CnzBalloon")),
                "the initial CNZ spawn window must include a balloon initialized by the setup pass");
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

    private static ConvergenceState loadCnzThroughVisibleTitleRelease() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 3, 0);
        try {
            LevelManager manager = GameServices.level();
            OscillatorState before = captureOscillator();
            GameLoop loop = releasingTitleCardLoop("LEVEL");
            assertTrue((boolean) invoke(loop, "updateTitleCardMode",
                    new Class<?>[] { boolean.class }, false));
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            LevelFrameStep.execute(LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                    });
            return new ConvergenceState(captureRuntimeState(manager),
                    before, captureOscillator());
        } finally {
            sharedLevel.dispose();
        }
    }

    private static ConvergenceState loadCnzThroughNoTitleFirstFrame() throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 3, 0);
        try {
            LevelManager manager = GameServices.level();
            OscillatorState before = captureOscillator();
            LevelFrameStep.execute(LevelFrameContext.from(TestEnvironment.activeGameplayMode()),
                    manager, GameServices.camera(), () -> {
                    });
            assertFalse(manager.hasPendingInitialObjectSetupPass());
            return new ConvergenceState(captureRuntimeState(manager),
                    before, captureOscillator());
        } finally {
            sharedLevel.dispose();
        }
    }

    private static OscillatorState captureOscillator() {
        return new OscillatorState(
                Arrays.stream(OscillationManager.valuesForTest()).boxed().toList(),
                Arrays.stream(OscillationManager.deltasForTest()).boxed().toList(),
                OscillationManager.controlForTest());
    }

    private static GameLoop releasingTitleCardLoop(String destination) throws Exception {
        GameLoop loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(TestEnvironment.activeGameplayMode());
        installReleasingTitleProvider(loop);
        setField(loop, "currentGameMode", GameMode.TITLE_CARD);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object target = Enum.valueOf(
                (Class<? extends Enum>) Class.forName("com.openggf.PostTitleCardDestination"),
                destination);
        setField(loop, "postTitleCardDestination", target);
        return loop;
    }

    private static void installReleasingTitleProvider(GameLoop loop) throws Exception {
        TitleCardProvider provider = mock(TitleCardProvider.class);
        when(provider.shouldReleaseControl()).thenReturn(true);
        setField(loop, "titleCardProvider", provider);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String name, Class<?>[] types,
                                 Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static RuntimeState captureRuntimeState(LevelManager manager) {
        List<ObjectRow> objects = new ArrayList<>();
        for (ObjectInstance object : manager.getObjectManager().getActiveObjects()) {
            int slot = object instanceof AbstractObjectInstance instance
                    ? instance.getSlotIndex() : -1;
            objects.add(new ObjectRow(slot, object.getClass().getName(),
                    object.getX(), object.getY()));
        }
        objects.sort(Comparator.comparingInt(ObjectRow::slot)
                .thenComparing(ObjectRow::className));
        AbstractPlayableSprite player = GameServices.sprites().getMainPlayable();
        List<PlayerState> sidekicks = GameServices.sprites().getSidekicks().stream()
                .map(TestS3kInitialObjectSetupLifecycle::capturePlayer)
                .toList();
        return new RuntimeState(List.copyOf(objects), objects.size(),
                manager.getObjectManager().getFrameCounter(),
                manager.getObjectManager().getVblaCounter(), manager.getFrameCounter(),
                GameServices.camera().getX(), GameServices.camera().getY(),
                GameServices.rng().getSeed(), capturePlayer(player), sidekicks);
    }

    private static PlayerState capturePlayer(AbstractPlayableSprite player) {
        return new PlayerState(player.getCentreX(), player.getCentreY(),
                player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                TraceCharacterState.statusByteFromSprite(player),
                player.getAnimationId(), player.getMappingFrame());
    }

    private record ObjectRow(int slot, String className, int x, int y) {
    }

    private record PlayerState(int centreX, int centreY, int xSpeed, int ySpeed,
                               int groundSpeed, int status, int animation, int mapping) {
    }

    private record RuntimeState(List<ObjectRow> objects, int activeSlotCount,
                                int objectFrame, int vbla, int levelFrame,
                                int cameraX, int cameraY, long rngSeed,
                                PlayerState player, List<PlayerState> sidekicks) {
    }

    private record OscillatorState(List<Integer> values, List<Integer> deltas,
                                   int control) {
    }

    private record ConvergenceState(RuntimeState runtime, OscillatorState before,
                                    OscillatorState after) {
    }

    private record AuthorityCase(boolean expected, LevelLoadMode mode, boolean postLoad,
                                 LevelAssemblyKind assemblyKind) {
    }
}
