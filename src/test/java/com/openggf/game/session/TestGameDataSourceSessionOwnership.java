package com.openggf.game.session;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestGameDataSourceSessionOwnership {
    @AfterEach void clear() { SessionManager.clear(); }

    @Test
    void sourceIsDurableAcrossEditorModeRebuild() {
        GameModule module = mock(GameModule.class);
        var registry = com.openggf.game.PlayableCharacterRegistry.empty();
        when(module.getPlayableCharacterRegistry()).thenReturn(registry);
        GameDataSource source = missingSource("fixture-rom");
        GameplayModeContext gameplay = SessionManager.openGameplaySession(module, module, source, null);
        assertSame(source, gameplay.getWorldSession().getDataSource());
        assertSame(registry, gameplay.getWorldSession().getPlayableCharacterRegistry());
        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(1, 2));
        assertSame(source, editor.getWorldSession().getDataSource());
        assertSame(registry, editor.getWorldSession().getPlayableCharacterRegistry());
        WorldSession resumed = SessionManager.resumeGameplayFromEditor().getWorldSession();
        assertSame(source, resumed.getDataSource());
        assertSame(registry, resumed.getPlayableCharacterRegistry());
    }

    @Test
    void legacyMockModulesPinTheEmptyRegistryWhenMockitoReturnsNull() {
        GameModule module = mock(GameModule.class);

        WorldSession world = new WorldSession(module);

        assertSame(com.openggf.game.PlayableCharacterRegistry.empty(),
                world.getPlayableCharacterRegistry());
    }

    private static GameDataSource missingSource(String identity) {
        return new GameDataSource() {
            @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                return java.util.Optional.empty();
            }
            @Override public java.io.InputStream openAsset(String normalizedPath)
                    throws java.io.IOException {
                throw new java.io.IOException("fixture has no named assets");
            }
            @Override public String identity() { return identity; }
        };
    }
}
