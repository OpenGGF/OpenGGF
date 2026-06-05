package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.GameMode;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.Objects;

public final class EditorModeContext implements ModeContext {
    private final WorldSession worldSession;
    private EditorCursorState cursor;
    private final EditorPlaytestStash playtestStash;
    private final Camera camera;
    private final SpriteManager spriteManager;
    private final LevelManager levelManager;

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor) {
        this(worldSession, cursor, null);
    }

    public EditorModeContext(WorldSession worldSession, EditorCursorState cursor, EditorPlaytestStash playtestStash) {
        this(worldSession, cursor, playtestStash, null, null, null);
    }

    EditorModeContext(WorldSession worldSession,
                      EditorCursorState cursor,
                      EditorPlaytestStash playtestStash,
                      Camera camera,
                      SpriteManager spriteManager,
                      LevelManager levelManager) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.cursor = Objects.requireNonNull(cursor, "cursor");
        this.playtestStash = playtestStash;
        this.camera = camera;
        this.spriteManager = spriteManager;
        this.levelManager = levelManager;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    public EditorCursorState getCursor() {
        return cursor;
    }

    public void setCursor(EditorCursorState cursor) {
        this.cursor = Objects.requireNonNull(cursor, "cursor");
    }

    public EditorPlaytestStash getPlaytestStash() {
        return playtestStash;
    }

    public boolean hasPlaytestStash() {
        return playtestStash != null;
    }

    public boolean isEditorRuntimeReady() {
        return camera != null
                && spriteManager != null
                && levelManager != null;
    }

    public Camera getCamera() {
        return camera;
    }

    public SpriteManager getSpriteManager() {
        return spriteManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.EDITOR;
    }

    @Override
    public void destroy() {
        if (spriteManager != null) {
            spriteManager.resetState();
        }
        if (camera != null) {
            camera.resetState();
        }
    }
}
