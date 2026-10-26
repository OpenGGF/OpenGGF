package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.timer.TimerManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owns real worlds and core-only gameplay contexts for registry contract tests.
 * Callers must be isolated tests: resetAll replaces the global test session and
 * creates a baseline session, which this fixture also closes. Do not nest fixtures.
 * Local contexts are never published into SessionManager or given level managers.
 */
public final class GameplayRewindFixture implements AutoCloseable {
    private final List<WorldSession> worlds = new ArrayList<>();
    private final List<GameplayModeContext> contexts = new ArrayList<>();
    private boolean closed;

    public GameplayRewindFixture() {
        try {
            TestEnvironment.resetAll();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public WorldSession newWorld() {
        return newWorld(null);
    }

    /** The supplied controller is the actual module controller seen during attachment. */
    public WorldSession newWorld(GameplayFrameController controller) {
        requireOpen();
        var module = new DelegatingGameModule(new Sonic2GameModule(), "rewind-fixture") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
        };
        var world = new WorldSession(module);
        worlds.add(world);
        return world;
    }

    /**
     * Composes a fresh registry through GameplayModeContext, then removes only its
     * registered adapters/callbacks through public operations. Retaining the real
     * world across calls retains its scope; the opaque scope itself is never read.
     */
    public RewindRegistry emptyRegistry(WorldSession world) {
        requireOpen();
        if (!worlds.contains(world)) throw new IllegalArgumentException("World is not owned by this fixture");
        var context = new GameplayModeContext(world);
        contexts.add(context); // Track before attachment so partial setup is also torn down.
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        RewindRegistry registry = context.getRewindRegistry();
        var composed = registry.capture();
        assertTrue(composed.containsKey("camera"), "The registry must come from actual core attachment");
        // Core attachment's directed-player callback uses its adapter key. No
        // level/shared registries or their additional callbacks are attached here.
        for (String key : composed.entries().keySet()) {
            registry.deregister(key);
            registry.deregisterPostRestoreCallback(key);
        }
        assertTrue(registry.capture().entries().isEmpty());
        assertTrue(registry.captureCourse().entries().isEmpty());
        return registry;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Gameplay rewind fixture is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List<Runnable> cleanup = new ArrayList<>();
        for (int i = contexts.size() - 1; i >= 0; i--) cleanup.add(contexts.get(i)::tearDownManagers);
        // resetAll creates this baseline even though the local contexts aren't global.
        cleanup.add(SessionManager::clear);
        Throwable failure = null;
        for (Runnable step : cleanup) {
            try {
                step.run();
            } catch (RuntimeException | Error next) {
                if (failure == null) failure = next;
                else if (failure != next) failure.addSuppressed(next);
            }
        }
        contexts.clear();
        worlds.clear();
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure instanceof Error error) throw error;
    }
}
