package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.rewind.LiveRewindManager;

/** Routes a completed level frame to the rewind host that owns the session. */
final class LevelRewindFrameRecorder {
    private LevelRewindFrameRecorder() {
    }

    /** The active module's own rewind for the live rewind host, if the session has one. */
    static com.openggf.game.rewind.ScriptedRewind activeScriptedRewind() {
        var session = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        var module = session == null ? null : session.getGameModule();
        return module == null ? null : module.scriptedRewind();
    }

    static void record(
            TraceSessionLauncher traceSession,
            LiveRewindManager liveRewindManager,
            GameMode gameMode,
            boolean nonRewindableTransitionPending,
            InputHandler input,
            boolean seamlessTransitionCompleted) {
        if (traceSession != null) {
            traceSession.recordExternalRewindFrame(seamlessTransitionCompleted);
            return;
        }
        liveRewindManager.recordExternalFrame(
                gameMode,
                nonRewindableTransitionPending,
                input,
                seamlessTransitionCompleted);
    }
}
