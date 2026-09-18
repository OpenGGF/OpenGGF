package com.openggf.tests.trace;

import com.openggf.trace.TraceMetadata;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import java.io.IOException;

/** Shared recorded-team session resolution for standalone and chain replay tests. */
public final class TraceReplayTestSession {
    private TraceReplayTestSession() { }

    /**
     * Resolves the recorded team through the engine's built-in patches with
     * the deterministic policy and reopens the session on the patched module
     * when one activates. Stock recordings resolve to the root module and
     * leave the fixture's session untouched.
     */
    public static void reopenForRecordedTeam(TraceMetadata meta) throws IOException {
        var worldSession = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        if (worldSession == null) {
            return;
        }
        com.openggf.game.GameModule root = worldSession.rootGameModule();
        com.openggf.game.session.EngineContext services =
                com.openggf.game.session.EngineServices.current();
        com.openggf.game.GameModule resolved =
                TraceReplaySessionBootstrap.resolveReplayModule(services, root, meta);
        if (resolved == worldSession.resolvedGameModule()) {
            return;
        }
        var reopened = com.openggf.game.session.SessionManager.openGameplaySession(root, resolved,
                com.openggf.game.StockGameDataSources.pinned(services.roms().getRom(), root), null);
        com.openggf.game.session.GameplaySessionFactory.attachManagers(reopened, services);
        com.openggf.game.GameModuleRegistry.setCurrent(resolved);
    }

}
