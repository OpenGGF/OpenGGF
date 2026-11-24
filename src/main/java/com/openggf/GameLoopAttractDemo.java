package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.mode.MenuScreenModeController;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.internal.AttractDemoPlayback;
import com.openggf.game.internal.LevelDemoPlayback;
import com.openggf.game.internal.TitleAttractDemo;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;

import java.util.function.Supplier;

/** The title and demo are different worlds owned by one native-mode visit. */
final class GameLoopAttractDemo {
    private final GameLoop loop;
    private TitleAttractDemo.Launch pending;
    private TitleAttractDemo.Launch running;
    private WorldSession demoWorld;
    private GameModule root;
    private GameModule titleModule;
    private GameStateSnapshot progress;

    GameLoopAttractDemo(GameLoop loop) {
        this.loop = loop;
    }

    void updateTitle(MenuScreenModeController menus, TitleScreenProvider title, InputHandler input, Runnable onExit) {
        int previousTicks = titleTicks(title);
        menus.updateTitleScreen(title, input, () -> onTitleUpdated(title, previousTicks), onExit);
    }

    private static int titleTicks(TitleScreenProvider title) {
        return title instanceof TitleAttractDemo.TitleClock clock ? clock.attractTitleTicks() : 0;
    }

    private boolean onTitleUpdated(TitleScreenProvider title, int previousTicks) {
        int ticks = titleTicks(title);
        if (ticks == previousTicks || ticks == 0) return false;
        var program = GameServices.module().getGameService(TitleAttractDemo.class);
        if (program == null) return false;
        if (ticks == 1) program.enterTitle();
        pending = program.onTitleTick();
        return pending != null;
    }

    static boolean ownsInput(GameMode mode, GameLoopAttractDemo demo) {
        return (mode == GameMode.LEVEL || mode == GameMode.TITLE_CARD || mode == GameMode.COMPETITION)
                && demo.active();
    }

    private boolean active() {
        return running != null && SessionManager.getCurrentWorldSession() == demoWorld;
    }

    AttractDemoPlayback levelPlayback() {
        return active() && running.destination() instanceof TitleAttractDemo.Destination.Level
                ? running.playback() : null;
    }

    LevelFrameResult runLevelFrame(LevelDemoPlayback legacyDemo, InputHandler input,
            GameplayModeContext mode, PlcLifecycleFrame frame, Supplier<LevelFrameResult> ordinary) {
        var playback = levelPlayback();
        if (playback == null || mode.getLevelManager().hasPendingInitialProcessSpritesPass()) {
            return GameLoopLevelDemo.runFrame(legacyDemo, input, ordinary);
        }
        return playback.step(input, GameServices.graphics(),
                mode.getLevelManager()::isLevelInactiveForTransition,
                () -> playback.isFading() ? runClosingFrame(input, mode, frame) : ordinary.get());
    }

    private static LevelFrameResult runClosingFrame(InputHandler input, GameplayModeContext mode,
            PlcLifecycleFrame frame) {
        var sprites = mode.getSpriteManager();
        sprites.publishHeldInputForLevelEvents(input);
        return LevelFrameStep.executeAttractFade(LevelFrameContext.from(mode), frame, () -> sprites.update(input));
    }

    void completeFrame(GameLoopNativeMenus menus) {
        if (pending != null) {
            var launch = pending;
            pending = null;
            if (loop.getCurrentGameMode() != GameMode.TITLE_SCREEN) return;
            var source = SessionManager.getCurrentWorldSession();
            root = source.rootGameModule();
            titleModule = source.resolvedGameModule();
            progress = loop.gameState.capture();
            loop.getTitleScreenProvider().reset();
            menus.startAttractDemo(launch);
            demoWorld = SessionManager.getCurrentWorldSession();
            running = launch;
        } else if (running != null) {
            if (!active()) {
                reset();
            } else if (running.playback().returnTarget() != null) {
                var target = running.playback().returnTarget();
                menus.restoreAttractTitle(root, titleModule, progress);
                reset();
                loop.initializeTitleScreenMode(target == AttractDemoPlayback.ReturnTarget.TITLE);
            }
        }
    }

    void reset() {
        pending = running = null;
        demoWorld = null;
        root = titleModule = null;
        progress = null;
    }
}
