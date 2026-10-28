package com.openggf.game.session;

import java.util.Objects;

/** Rebuilds disposable owners against the same pinned game source, without saving gameplay. */
public final class SessionResetLifecycle {
    private SessionResetLifecycle() { }

    public static GameplayModeContext restart(EngineContext services, NativeSessionResetPolicy.Kind kind,
            Runnable retireLoopState) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(kind, "kind");
        GameplayModeContext outgoing = SessionManager.getCurrentGameplayMode();
        if (outgoing == null || !outgoing.isGameplayRuntimeReady()) {
            throw new IllegalStateException("Session reset requires an active native gameplay runtime");
        }
        WorldSession world = outgoing.getWorldSession();
        var module = world.getGameModule();
        var policy = module.getGameService(NativeSessionResetPolicy.class);
        if (policy == null) {
            throw new UnsupportedOperationException("Native session reset is unavailable for " + module.getIdentifier());
        }
        var retained = Objects.requireNonNull(policy.capture(outgoing, kind), "retained reset state");
        var admission = outgoing.hardwareTiming().admissionPolicy();
        // Capture precedes teardown; none of these steps reads comparison traces.
        retireLoopState.run();
        var title = module.getTitleScreenProvider();
        if (title != null) title.reset();
        SessionManager.clear();
        module.resetModuleScopedState();
        services.audio().resetState();
        services.graphics().resetPatternAndPaletteState();
        if (kind == NativeSessionResetPolicy.Kind.COLD_POWER) {
            EngineTiming.vIntRunCounter(services).seed(0);
        }
        GameplayModeContext replacement = SessionManager.openGameplaySession(
                world.rootGameModule(), module, world.getDataSource(), null, admission);
        try {
            GameplaySessionFactory.attachManagers(replacement, services);
            retained.restore(replacement);
            services.audio().setAudioProfile(module.getAudioProfile());
            services.audio().setRom(world.getDataSource().rom().orElseThrow(
                    () -> new IllegalStateException("Session reset lost its pinned ROM")));
            services.audio().ensurePresentationSink();
            return replacement;
        } catch (RuntimeException | Error failure) {
            try { SessionManager.clear(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }
}
