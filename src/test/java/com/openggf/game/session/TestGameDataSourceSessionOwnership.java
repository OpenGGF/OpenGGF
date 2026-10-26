package com.openggf.game.session;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestGameDataSourceSessionOwnership {
    @BeforeEach void resetState() { com.openggf.tests.TestEnvironment.resetAll(); }

    @AfterEach void clear() {
        SessionManager.clear();
        com.openggf.game.GameModuleRegistry.reset();
        com.openggf.tests.TestEnvironment.resetAll();
    }

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

    @ParameterizedTest
    @ValueSource(strings = {"resume", "restart", "reopen"})
    void retainedWorldAdmitsCompatibleRawSnapshotsAfterActualContextTransition(String transition) {
        var module = new com.openggf.game.sonic2.Sonic2GameModule();
        GameDataSource source = missingSource("same-source-through-rebuild");
        var first = SessionManager.openGameplaySession(module, module, source, null);
        attachCoreManagers(first);
        var world = first.getWorldSession();
        var registry = first.getRewindRegistry();
        first.getCamera().setX((short) 71);
        var full = registry.capture();
        first.getCamera().setX((short) 82);
        var course = registry.captureCourse();

        GameplayModeContext rebuilt;
        if (transition.equals("reopen")) {
            rebuilt = SessionManager.reopenGameplaySession(
                    com.openggf.game.timing.HardwareReadinessAdmissionPolicy.LIVE);
        } else {
            SessionManager.enterEditorMode(new EditorCursorState(3, 4));
            rebuilt = transition.equals("resume") ? SessionManager.resumeGameplayFromEditor()
                    : SessionManager.restartGameplayFromBeginning();
        }
        attachCoreManagers(rebuilt);
        assertSame(world, rebuilt.getWorldSession());
        assertSame(source, rebuilt.getWorldSession().getDataSource());
        assertNotSame(first, rebuilt);
        assertNotSame(registry, rebuilt.getRewindRegistry());
        assertEquals(0, rebuilt.getCamera().getX(), "new managers still begin with fresh state");
        rebuilt.getRewindRegistry().restore(full);
        assertEquals(71, rebuilt.getCamera().getX());
        rebuilt.getRewindRegistry().restoreCourse(course);
        assertEquals(82, rebuilt.getCamera().getX());
    }

    private static void attachCoreManagers(GameplayModeContext context) {
        context.attachGameplayManagers(new com.openggf.camera.Camera(), new com.openggf.timer.TimerManager(),
                new com.openggf.game.GameStateManager(), new com.openggf.graphics.FadeManager(),
                new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2),
                new com.openggf.game.solid.DefaultSolidExecutionRegistry());
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
