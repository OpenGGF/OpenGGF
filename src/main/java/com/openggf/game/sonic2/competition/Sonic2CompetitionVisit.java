package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.sonic2.competition.state.Sonic2NativeControlState;
import com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionViews;
import com.openggf.game.sonic2.options.Sonic2OptionsState;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import java.io.IOException;
import java.util.function.IntPredicate;

/** One native visit owns its world, logical controls, display and competitive halfpipe. */
public final class Sonic2CompetitionVisit implements NativeMenuScreen {
    private final Sonic2CompetitionViews views;
    private final Sonic2NativeCompetitionHost host;
    private final Sonic2CompetitionScreen screen;
    private final Sonic2CompetitiveSpecialStageAdapter halfpipe;
    private final Sonic2NativeControlState controls = new Sonic2NativeControlState();
    private RewindRegistry registry;
    private boolean registeredSessionDirectly;
    private boolean closed;
    private boolean initialized;
    private final com.openggf.game.GameStateManager sourceProgress;

    public Sonic2CompetitionVisit(NativeMenuHostContext context, Sonic2OptionsState options,
            boolean overseas, boolean fromOptions, IntPredicate qualified, Sonic2TeleportPorts.ObjectFields objects,
            com.openggf.game.GameStateManager sourceProgress)
            throws IOException {
        this.sourceProgress = java.util.Objects.requireNonNull(sourceProgress, "native entry progress owner");
        var session = new Sonic2CompetitionSession();
        views = new Sonic2CompetitionViews(context.engine().graphics());
        try {
            halfpipe = Sonic2CompetitiveSpecialStageAdapter.create(session, context.engine().roms().getRom(),
                    context.engine().graphics(), SpecialStageViewport.nativeViewport(), context.engine().audio(),
                    () -> (int) EngineTiming.vIntRunCounter(context.engine()).value());
            var qualifiedViews = new Sonic2NativeCompetitionHost.Views() {
                public boolean qualifiesZone(int zone) { return qualified.test(zone); }
                public void prepare(com.openggf.game.session.GameplayModeContext mode, Sonic2CompetitionSession state,
                        Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) {
                    views.prepare(mode, state, profile, art);
                }
                public void draw(com.openggf.game.session.GameplayModeContext mode, Sonic2CompetitionSession state) {
                    views.draw(mode, state);
                }
            };
            host = Sonic2NativeCompetitionHost.production(context.rootModule(), context.engine(), mode -> {
                context.publishMode().accept(mode);
                bindRegistry(mode.getRewindRegistry(), false);
            }, qualifiedViews, options.teleportOnlyMonitors(), overseas, objects);
            host.bindNativeWaitInput(NativeWaitInput.from(context.input()), controls);
            screen = new Sonic2CompetitionScreen(session, host, halfpipe, context.engine().roms().getRom(), () -> {
                var module = GameServices.module();
                return module == null ? null : module.getGameService(Sonic2PlcService.class);
            });
            screen.setEntryProgress(sourceProgress, fromOptions);
        } catch (IOException | RuntimeException | Error failure) {
            views.close();
            throw failure;
        }
    }
    public Sonic2NativeCompetitionHost host() { return host; }
    public Sonic2CompetitionSession session() { return screen.session(); }
    public Sonic2NativeControlState controls() { return controls; }
    @Override public GameMode mode() { return screen.mode(); }
    @Override public void initialize() {
        if (closed) throw new IllegalStateException("Competition visit closed");
        // TitleScreen's Camera_RAM clear is the actual menu-entry event. A new
        // visit starts cold; later acts retain this same host/module/plane owner.
        host.clearMenuCameraRam();
        screen.initialize();
        initialized = true;
        var mode = java.util.Objects.requireNonNull(
                com.openggf.game.session.SessionManager.getCurrentGameplayMode(), "live native menu registry owner");
        bindRegistry(mode.getRewindRegistry(), true);
    }
    private void releaseRegistry() {
        if (registry == null) return;
        for (var adapter : halfpipe.rewindAdapters()) registry.deregister(adapter.key());
        registry.deregister(controls.key());
        if (registeredSessionDirectly) registry.deregister(session().key());
        registry = null;
        registeredSessionDirectly = false;
    }
    private void bindRegistry(RewindRegistry target, boolean standalone) {
        if (registry == target && registeredSessionDirectly == standalone) return;
        releaseRegistry();
        registry = java.util.Objects.requireNonNull(target);
        // Before an act opens, the visit owns its session directly. The native
        // world's policy graph owns that same session after the transfer.
        if (standalone) registry.registerOrRefresh(session());
        registeredSessionDirectly = standalone;
        registry.registerOrRefresh(controls);
        for (var adapter : halfpipe.rewindAdapters()) registry.registerOrRefresh(adapter);
    }
    private void advanceBoundWaitClock() {
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var objects = mode == null ? null : mode.getObjectManager();
        // The outer engine already ticks the unbound global clock. A bound
        // object clock must still tick on menu/results/halfpipe waits with no LevelLoop.
        if (objects != null) objects.advanceVblaCounter();
    }
    @Override public void update(InputHandler input) {
        if (session().phase() == Sonic2CompetitionSession.Phase.SPECIAL_STAGE) {
            var frame = com.openggf.game.competition.NativeCompetitionIterationAccess.currentFrame();
            com.openggf.game.competition.NativeCompetitionIterationAccess.runLoop(frame, () -> {
                var phase = com.openggf.game.resources.PlcLifecyclePhase.SPECIAL_STAGE;
                if (!frame.claim(phase)) throw new IllegalStateException("Competitive halfpipe wait already owned");
                advanceBoundWaitClock();
                screen.update(input);
                frame.prepareAfterLoop(phase);
                return null;
            });
        } else {
            if (session().phase() != Sonic2CompetitionSession.Phase.ACT) advanceBoundWaitClock();
            screen.update(input);
        }
    }
    @Override public void afterFrame() { screen.afterFrame(); }
    @Override public void draw() { screen.draw(); }
    @Override public void setClearColor() { screen.setClearColor(); }
    @Override public Exit consumeExit() { return screen.consumeExit(); }
    @Override public void reset() {
        if (closed) return;
        closed = true;
        try {
            releaseRegistry();
            // Return the native P1 bank to the caller's persistent progress owner
            // before retiring/resetting the competition world.
            if (initialized) sourceProgress.restore(screen.session().gameState(0).capture());
            screen.reset();
        } finally {
            try { host.close(); } finally { views.close(); }
        }
    }
}
