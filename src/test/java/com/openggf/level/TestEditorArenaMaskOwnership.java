package com.openggf.level;

import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorArenaMaskOwnership {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @EnumSource(value = GameId.class, names = {"S1", "S2"})
    void editorDrawingUsesItsInjectedBoundaryStateWithoutAnActiveGameplayMode(GameId game) {
        var module = game == GameId.S1 ? new Sonic1GameModule() : new Sonic2GameModule();
        TestEnvironment.configureGameModuleFixture(module);
        var level = GameServices.level();
        level.gameModule = module;
        var state = GameServices.gameState();
        var camera = GameServices.camera();
        camera.setMinX((short) 0); camera.setMaxX((short) 800); camera.setX((short) 760);
        var before = LevelScrollPresentation.captureArenaMask(level);
        var editor = SessionManager.enterEditorMode(new EditorCursorState(0, 0));
        var editorLevel = editor.getLevelManager();
        editorLevel.gameModule = module;
        var editorCamera = editor.getCamera();
        editorCamera.setMinX((short) 0); editorCamera.setMaxX((short) 800); editorCamera.setX((short) 760);
        assertThrows(IllegalStateException.class, GameServices::gameState);
        assertNotSame(state, editorLevel.boundaryGameState(), "The editor owns fresh state for its own view");
        assertEquals(before, LevelScrollPresentation.captureArenaMask(editorLevel),
                "The same ordinary bounds render while the editor's state owner is active");
    }
}
