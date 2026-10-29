package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.nativemenu.NativeMenuController;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.startup.DataSelectPresentationResolution;
import com.openggf.game.startup.StartupRouteResolver;
import com.openggf.game.startup.TitleActionRoute;
import com.openggf.configuration.SonicConfiguration;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Title destination selection and native menu dispatch, owned by one game loop. */
final class GameLoopNativeMenus {
    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final GameLoop loop;
    private final NativeMenuController nativeMenus;
    private final EngineContext engineServices;
    private final AudioManager audioManager;
    private final StartupRouteResolver startupRouteResolver;
    private final Supplier<GameplayTeamBootstrapContext> bootstrap;
    private final Supplier<DataSelectProvider> dataSelectProvider;
    private final Runnable initializeDataSelect;
    private final Runnable enterLevelSelect;
    private final Runnable enterLevelSelectFromTitle;
    private final BiConsumer<Integer, Integer> startLevel;
    private final Consumer<GameplayModeContext> publisher;
    private final Supplier<InputHandler> input;
    private final Supplier<GameMode> currentMode;

    GameLoopNativeMenus(GameLoop loop, NativeMenuController nativeMenus, EngineContext engineServices,
            AudioManager audioManager, StartupRouteResolver startupRouteResolver,
            Supplier<GameplayTeamBootstrapContext> bootstrap, Supplier<DataSelectProvider> dataSelectProvider,
            Runnable initializeDataSelect, Runnable enterLevelSelect, Runnable enterLevelSelectFromTitle,
            BiConsumer<Integer, Integer> startLevel, Consumer<GameplayModeContext> publisher,
            Supplier<InputHandler> input, Supplier<GameMode> currentMode) {
        this.loop = loop;
        this.nativeMenus = nativeMenus;
        this.engineServices = engineServices;
        this.audioManager = audioManager;
        this.startupRouteResolver = startupRouteResolver;
        this.bootstrap = bootstrap;
        this.dataSelectProvider = dataSelectProvider;
        this.initializeDataSelect = initializeDataSelect;
        this.enterLevelSelect = enterLevelSelect;
        this.enterLevelSelectFromTitle = enterLevelSelectFromTitle;
        this.startLevel = startLevel;
        this.publisher = publisher;
        this.input = input;
        this.currentMode = currentMode;
    }

    void exitTitleScreen() {
        TitleScreenProvider titleScreen = loop.getTitleScreenProviderLazy();
        if (titleScreen == null) {
            return;
        }

        TitleActionRoute route = resolveTitleActionRoute(titleScreen);

        // Sonic 1 level select: immediate transition, no fade, music continues.
        // The original game loads Pal_LevelSel and clears the BG plane instantly.
        // Title screen art data is kept loaded so it can be rendered behind the
        // level select text with the brown/sepia palette tint.
        if (route == TitleActionRoute.LEVEL_SELECT && titleScreen.supportsLevelSelectOverlay()) {
            enterLevelSelectFromTitle.run();
            return;
        }

        if (shouldFadeTitleScreenExit(route) && loop.fadeManager.isActive()) {
            return;
        }

        // Fade out title music
        audioManager.fadeOutMusic();

        if (shouldFadeTitleScreenExit(route)) {
            GameLoopPlcLifecycle.startToBlack(loop.resolveGameplayModeContext(), loop.fadeManager,
                    () -> doExitTitleScreen(route));
            LOGGER.info("Title screen exit fading to " + route);
            return;
        }

        doExitTitleScreen(route);
        LOGGER.info("Title screen exit routed directly to " + route);
    }

    void doExitTitleScreen() {
        TitleScreenProvider titleScreen = loop.getTitleScreenProviderLazy();
        doExitTitleScreen(resolveTitleActionRoute(titleScreen));
    }

    void doExitTitleScreen(TitleActionRoute route) {
        TitleScreenProvider titleScreen = loop.getTitleScreenProviderLazy();
        // reset() may discard a creator title's selection. Resolve the complete
        // destination while the exiting provider still owns that selection.
        int zone = titleStartZone();
        int act = titleStartAct(zone);
        if (titleScreen != null) {
            titleScreen.reset();
        }

        executeTitleActionRoute(route, zone, act);
    }

    int titleStartAct(int zone) {
        var provider = loop.getTitleScreenProviderLazy();
        var module = GameServices.module();
        var registry = module != null ? module.getZoneRegistry() : null;
        if (provider == null || registry == null) {
            return 0;
        }
        int count = Math.max(1, registry.getActCount(zone));
        return Math.clamp(provider.startActIndex(), 0, count - 1);
    }

    int titleStartZone() {
        TitleScreenProvider titleScreen = loop.getTitleScreenProviderLazy();
        var module = GameServices.module();
        var registry = module != null ? module.getZoneRegistry() : null;
        if (titleScreen == null || registry == null) {
            return 0;
        }
        int zone = titleScreen.startZoneIndex();
        return zone >= 0 && zone < registry.getZoneCount() ? zone : 0;
    }

    void handleTitleScreenExitFromProvider() {
        TitleScreenProvider titleScreen = loop.getTitleScreenProviderLazy();
        if (titleScreen == null) {
            return;
        }

        TitleActionRoute route = resolveTitleActionRoute(titleScreen);
        // Provider callbacks fire after the provider has already finished its
        // own staged exit, so this path must hand off directly.
        if (route == TitleActionRoute.LEVEL_SELECT && titleScreen.supportsLevelSelectOverlay()) {
            enterLevelSelectFromTitle.run();
            return;
        }

        if (shouldFadeTitleScreenExit(route) && loop.fadeManager.isActive()) {
            return;
        }

        if (shouldFadeTitleScreenExit(route)) {
            GameLoopPlcLifecycle.startToBlack(loop.resolveGameplayModeContext(), loop.fadeManager,
                    () -> doExitTitleScreen(route));
            return;
        }

        doExitTitleScreen(route);
    }

    TitleActionRoute resolveTitleActionRoute(TitleScreenProvider titleScreen) {
        var gameModule = GameServices.module();
        if (gameModule == null || titleScreen == null) {
            return TitleActionRoute.LEVEL;
        }
        TitleScreenProvider.TitleScreenAction exitAction = titleScreen.consumeExitAction();
        if (exitAction == null) {
            exitAction = TitleScreenProvider.TitleScreenAction.OTHER;
        }
        boolean levelSelectAllowed = !gameModule.suppressesLevelSelect();
        if (!levelSelectAllowed && exitAction == TitleScreenProvider.TitleScreenAction.LEVEL_SELECT) {
            exitAction = TitleScreenProvider.TitleScreenAction.ONE_PLAYER;
        }
        return startupRouteResolver.resolveTitleAction(
                gameModule,
                resolveDataSelectPresentation(),
                true,
                levelSelectAllowed && loop.configService.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP),
                exitAction);
    }

    DataSelectPresentationResolution resolveDataSelectPresentation() {
        var gameModule = GameServices.module();
        if (gameModule == null) {
            return new DataSelectPresentationResolution(false, null);
        }

        DataSelectProvider dataSelectProvider = this.dataSelectProvider.get();
        boolean dataSelectEligible = dataSelectProvider != null
                && !(dataSelectProvider instanceof NoOpDataSelectProvider);
        boolean crossGameEnabled = loop.configService.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED);
        boolean s3kConfiguredDonor = "s3k".equalsIgnoreCase(
                loop.configService.getString(SonicConfiguration.CROSS_GAME_SOURCE));
        // All modules that expose a DataSelectPresentationProvider use the
        // S3K presentation manager as their delegate. Resolve the presentation
        // game as S3K so the startup router recognises the donated screen for
        // S1/S2 hosts.
        GameId presentationId = gameModule.getGameId();
        if (dataSelectEligible
                && dataSelectProvider instanceof com.openggf.game.dataselect.DataSelectPresentationProvider) {
            boolean nativeS3kDataSelect = gameModule.getGameId() == GameId.S3K;
            boolean donatedS3kDataSelect = crossGameEnabled && s3kConfiguredDonor;
            if (nativeS3kDataSelect || donatedS3kDataSelect) {
                presentationId = GameId.S3K;
            } else {
                dataSelectEligible = false;
            }
        }
        return new DataSelectPresentationResolution(dataSelectEligible, presentationId);
    }

    boolean shouldFadeTitleScreenExit(TitleActionRoute route) {
        return route == TitleActionRoute.DATA_SELECT || route == TitleActionRoute.LEVEL;
    }

    void executeTitleActionRoute(TitleActionRoute route, int zone, int act) {
        switch (route) {
            case DATA_SELECT -> initializeDataSelect.run();
            case LEVEL_SELECT -> enterLevelSelect.run();
            case TWO_PLAYER -> openNativeTitleMode(false);
            case OPTIONS -> openNativeTitleMode(true);
            case LEVEL -> {
                var module = GameServices.module();
                var modes = module == null ? null : module.getGameService(
                        com.openggf.game.nativemenu.NativeTitleModeProvider.class);
                var nativeExit = modes == null ? com.openggf.game.nativemenu.NativeMenuScreen.Exit.NONE
                        : modes.singlePlayerTitleExit();
                if (nativeExit.destination() == com.openggf.game.nativemenu.NativeMenuScreen.Destination.LEVEL) {
                    startNativeMenuLevel(nativeExit);
                } else startLevel.accept(zone, act);
            }
            case OTHER -> startLevel.accept(zone, act);
        }
    }

    private NativeMenuHostContext nativeMenuHostContext() {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) {
            throw new IllegalStateException("Native menu visit requires an attached root world");
        }
        return new NativeMenuHostContext(engineServices, world.rootGameModule(),
                input.get(), publisher);
    }

    void openNativeTitleMode(boolean options) {
        var module = GameServices.module();
        var provider = module == null ? null : module.getGameService(
                com.openggf.game.nativemenu.NativeTitleModeProvider.class);
        var screen = provider == null ? null
                : options ? provider.optionsScreen() : provider.competitionScreen(nativeMenuHostContext());
        if (!nativeMenus.open(screen)) {
            // An action token is not permission to boot an ordinary one-player
            // world as competition. Only a connected native provider can launch it.
            loop.setGameMode(GameMode.TITLE_SCREEN);
            var title = loop.getTitleScreenProviderLazy();
            if (title != null) title.initialize();
            return;
        }
        loop.camera.setX((short) 0);
        loop.camera.setY((short) 0);
        loop.setGameMode(screen.mode());
    }

    void updateNativeMenuMode() {
        var exit = nativeMenus.update(input.get(), loop.resolveFadeManager().isActive());
        var screen = nativeMenus.screen();
        if (screen == null) return;
        switch (exit.destination()) {
            case NONE -> { }
            case TITLE_SCREEN -> {
                nativeMenus.close();
                loop.initializeTitleScreenMode();
            }
            case COMPETITION -> {
                var module = GameServices.module();
                var provider = module == null ? null : module.getGameService(
                        com.openggf.game.nativemenu.NativeTitleModeProvider.class);
                var competition = provider == null ? null : provider.competitionScreen(nativeMenuHostContext());
                if (competition == null) {
                    screen.initialize();
                } else if (nativeMenus.open(competition)) {
                    loop.setGameMode(competition.mode());
                }
            }
            case LEVEL -> startNativeMenuLevel(exit);
        }
    }

    void completeFrame() {
        if (!nativeMenus.completeFrame()) return;
        var screen = nativeMenus.screen();
        if (screen != null && NativeMenuController.handles(currentMode.get())
                && screen.mode() != currentMode.get()) loop.setGameMode(screen.mode());
    }

    void startNativeMenuLevel(com.openggf.game.nativemenu.NativeMenuScreen.Exit exit) {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null || exit.team() == null) {
            throw new IllegalStateException("Native level launch requires a world and exact team");
        }
        var progress = exit.resetProgress() ? null : GameServices.gameState().capture();
        var root = world.rootGameModule();
        var module = world.getGameModule();
        nativeMenus.close();
        try {
            bootstrap.get().openNativeMenuLaunch(root, module, engineServices,
                    loop.configService, exit.zone(), exit.act(), publisher, exit.team(), progress);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to load native menu launch", failure);
        }
        loop.setGameMode(GameMode.LEVEL);
        GameLoopPlcLifecycle.startFromBlack(loop.resolveGameplayModeContext(), loop.fadeManager, null);
    }
}
