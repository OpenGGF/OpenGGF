package com.openggf;

import com.openggf.editor.EditorNativeLevelView;
import com.openggf.editor.project.EditorProjectSpawnValidator;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;

import java.io.IOException;

/** Native authoring data outlives disposable editor and hosted playtest runtimes. */
final class NativeEditorSession {
    private final GameModule rootModule;
    private final GameModule module;
    private final GameDataSource dataSource;
    private final int zone;
    private final int act;
    private final int apparentAct;
    private final MutableLevel level;
    private EditorCursorState cursor;

    NativeEditorSession(WorldSession source, Level loaded, EditorCursorState cursor) throws IOException {
        if (loaded == null) throw new IOException("No loaded level is available for native editing");
        rootModule = source.rootGameModule();
        module = source.getGameModule();
        dataSource = source.getDataSource();
        zone = source.getCurrentZone();
        act = source.getCurrentAct();
        apparentAct = source.getApparentAct();
        this.cursor = cursor;
        // Even an already mutable gameplay level belongs to the retired runtime.
        // Snapshot its data while retaining native collision delegation and bounds.
        var prepared = EditorNativeLevelView.prepare(loaded, module.getObjectArtProvider());
        level = prepared == loaded ? MutableLevel.snapshot(prepared) : prepared;
    }

    MutableLevel level() { return level; }
    EditorCursorState cursor() { return cursor; }
    void setCursor(EditorCursorState cursor) { this.cursor = cursor; }
    int zone() { return zone; }
    int act() { return act; }

    GameplayModeContext openWorld(GameModule runtimeModule) {
        var gameplay = SessionManager.openGameplaySession(rootModule, runtimeModule, dataSource, null);
        var world = gameplay.getWorldSession();
        world.setCurrentZone(zone);
        world.setCurrentAct(act);
        world.setApparentAct(apparentAct);
        return gameplay;
    }

    void openEditorWorld() {
        openWorld(module).getWorldSession().setCurrentLevel(level);
        SessionManager.enterEditorMode(cursor);
    }

    EditorCursorState start(boolean fromCursor) {
        int[] start = module.getZoneRegistry().getStartPosition(zone, act);
        var spawn = fromCursor ? cursor : new EditorCursorState(start[0], start[1]);
        EditorProjectSpawnValidator.validateNative(level, spawn.x(), spawn.y());
        return spawn;
    }

    GameModule preparePlaytest() {
        // Freeze once at launch. Each load, including death/restart, gets another
        // copy so neither gameplay nor later reloads can mutate authored data.
        MutableLevel frozen = MutableLevel.snapshot(level);
        int levelIndex = module.getZoneRegistry().getLevelDataForZone(zone).get(act).levelIndex();
        return new DelegatingGameModule(module, "native-editor-playtest") {
            @Override public Level loadLevelOverride(int index) throws IOException {
                if (index != levelIndex) throw new IOException("A native editor playtest owns only the edited act");
                return MutableLevel.snapshot(frozen);
            }
            @Override public Level transformDecodedLevel(Level loaded) {
                // The authored source already contains effective-module transforms.
                return loaded;
            }
        };
    }
}
