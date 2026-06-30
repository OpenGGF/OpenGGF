package com.openggf.game.session;

import com.openggf.architecture.CompositionRoot;
import com.openggf.game.GameModule;
import com.openggf.game.save.SaveSessionContext;

import java.util.Objects;

@CompositionRoot
public final class SessionManager {
    private static volatile WorldSession currentWorldSession;
    private static volatile GameplayModeContext currentGameplayMode;
    private static volatile EditorModeContext currentEditorMode;
    private static final EditorSessionFactory EDITOR_SESSION_FACTORY = new EditorSessionFactory();

    private SessionManager() {
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module) {
        return openGameplaySession(module, null);
    }

    public static synchronized GameplayModeContext openGameplaySession(GameModule module,
                                                                       SaveSessionContext saveSessionContext) {
        Objects.requireNonNull(module, "module");
        destroyCurrentMode();
        currentWorldSession = new WorldSession(module, saveSessionContext);
        currentGameplayMode = new GameplayModeContext(currentWorldSession);
        return currentGameplayMode;
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor) {
        return enterEditorMode(cursor, null);
    }

    public static synchronized EditorModeContext enterEditorMode(EditorCursorState cursor,
                                                                 EditorPlaytestStash playtestStash) {
        Objects.requireNonNull(cursor, "cursor");
        if (currentWorldSession == null) {
            throw new IllegalStateException("Cannot enter editor mode without an active world session.");
        }
        destroyCurrentMode();
        currentEditorMode = EDITOR_SESSION_FACTORY.create(
                currentWorldSession, EngineServices.current(), cursor, playtestStash);
        return currentEditorMode;
    }

    public static synchronized GameplayModeContext exitEditorMode() {
        return resumeGameplayFromEditor();
    }

    public static synchronized GameplayModeContext resumeGameplayFromEditor() {
        if (currentEditorMode == null) {
            throw new IllegalStateException("Cannot exit editor mode without an active editor mode.");
        }
        EditorCursorState cursor = currentEditorMode.getCursor();
        EditorPlaytestStash playtestStash = currentEditorMode.getPlaytestStash();
        WorldSession worldSession = currentEditorMode.getWorldSession();
        destroyCurrentMode();
        currentGameplayMode = new GameplayModeContext(worldSession, cursor.x(), cursor.y(), playtestStash);
        return currentGameplayMode;
    }

    public static synchronized GameplayModeContext restartGameplayFromBeginning() {
        if (currentEditorMode == null) {
            throw new IllegalStateException("Cannot restart gameplay without an active editor mode.");
        }
        WorldSession worldSession = currentEditorMode.getWorldSession();
        destroyCurrentMode();
        currentGameplayMode = new GameplayModeContext(worldSession);
        return currentGameplayMode;
    }

    public static synchronized void clear() {
        destroyCurrentMode();
        currentWorldSession = null;
    }

    public static synchronized void closeGameplaySession() {
        destroyCurrentMode();
        currentWorldSession = null;
    }

    public static synchronized GameModule requireCurrentGameModule() {
        if (currentWorldSession == null) {
            throw new IllegalStateException("No active WorldSession");
        }
        return currentWorldSession.getGameModule();
    }

    private static void destroyCurrentMode() {
        boolean destroyedRuntimeMode = false;
        if (currentGameplayMode != null) {
            currentGameplayMode.destroy();
            currentGameplayMode = null;
            destroyedRuntimeMode = true;
        }
        if (currentEditorMode != null) {
            currentEditorMode.destroy();
            currentEditorMode = null;
            destroyedRuntimeMode = true;
        }
        if (destroyedRuntimeMode) {
            EngineServices.current().graphics().clearRuntimeManagedReferences();
        }
    }

    public static WorldSession getCurrentWorldSession() {
        return currentWorldSession;
    }

    public static GameplayModeContext getCurrentGameplayMode() {
        return currentGameplayMode;
    }

    public static EditorModeContext getCurrentEditorMode() {
        return currentEditorMode;
    }

}
