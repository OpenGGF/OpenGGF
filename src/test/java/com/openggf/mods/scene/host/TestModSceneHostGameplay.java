package com.openggf.mods.scene.host;

import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneGameplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Scene-side contract of gameplay runs: access to the launcher and resumption after a run. */
class TestModSceneHostGameplay {
    @TempDir
    Path temp;

    private final ModSceneHost host = new ModSceneHost();

    @AfterEach
    void close() {
        host.close();
    }

    static class RunScene implements ModScene {
        final List<String> calls = new ArrayList<>();
        SceneContext context;

        @Override public void enter(SceneContext ctx) { context = ctx; calls.add("enter"); }
        @Override public void update(SceneContext ctx) { calls.add("update"); }
        @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
        @Override public void resumed(SceneContext ctx, RunEndReason reason) { calls.add("resumed:" + reason); }
    }

    private static OwnedSceneFactory owned(ModScene scene) {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        return ModContextTestAccess.ownedScene("racer", () -> scene, boundary);
    }

    @Test
    void sceneWithoutALauncherCannotLaunchRuns() {
        RunScene scene = new RunScene();
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
        assertThrows(UnsupportedOperationException.class, () -> scene.context.gameplay());
    }

    @Test
    void sceneReachesTheEngineLauncher() {
        RunScene scene = new RunScene();
        SceneGameplay launcher = new SceneGameplay() {
            @Override public List<String> availableGames() { return List.of("s2"); }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) { throw new AssertionError(); }
        };
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }, null, launcher),
                320, 224);
        assertNotSame(launcher, scene.context.gameplay());
        assertEquals(List.of("s2"), scene.context.gameplay().availableGames());
    }

    @Test
    void retainedGameplayCapabilityIsRevokedBeforeExitAndAcrossReopenedScenes() {
        java.util.concurrent.atomic.AtomicInteger launches = new java.util.concurrent.atomic.AtomicInteger();
        SceneGameplay launcher = new SceneGameplay() {
            @Override public List<String> availableGames() { return List.of("s2"); }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) { launches.incrementAndGet(); return null; }
        };
        RunScene scene = new RunScene() {
            @Override public void exit(SceneContext ctx) {
                assertThrows(IllegalStateException.class, () -> ctx.gameplay().launch(null, null));
            }
        };
        SceneServices services = new SceneServices(null, null, temp, null, () -> { }, () -> { }, null, launcher);
        host.open(owned(scene), services, 320, 224);
        SceneGameplay retained = scene.context.gameplay();
        retained.launch(null, null);
        assertEquals(1, launches.get());
        scene.context.exitToMasterTitle();
        assertThrows(IllegalStateException.class, () -> retained.launch(null, null));
        host.close();
        host.open(owned(new RunScene()), services, 320, 224);
        assertThrows(IllegalStateException.class, () -> retained.launch(null, null));
        assertThrows(IllegalStateException.class, retained::availableGames);
        assertEquals(1, launches.get(), "the old visit must never reach a new scene's launcher");
    }

    @Test
    void workerThreadCannotLaunchIntoTheEngine() throws Exception {
        RunScene scene = new RunScene();
        java.util.concurrent.atomic.AtomicInteger launches = new java.util.concurrent.atomic.AtomicInteger();
        SceneGameplay launcher = new SceneGameplay() {
            @Override public List<String> availableGames() { return List.of("s2"); }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) { launches.incrementAndGet(); return null; }
        };
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }, null, launcher),
                320, 224);
        SceneGameplay capability = scene.context.gameplay();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try { capability.launch(null, null); } catch (Throwable thrown) { failure.set(thrown); }
        });
        worker.join();
        assertTrue(failure.get() instanceof IllegalStateException);
        assertEquals(0, launches.get());
    }

    @Test
    void resumeDeliversTheEndReasonThroughTheOwnerBoundary() {
        RunScene scene = new RunScene();
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
        host.resume(RunEndReason.ACT_COMPLETED);
        assertEquals(List.of("enter", "resumed:ACT_COMPLETED"), scene.calls);
    }

    @Test
    void aSceneThatFailsToResumeIsClosed() {
        ModScene failing = new RunScene() {
            @Override public void resumed(SceneContext ctx, RunEndReason reason) {
                throw new IllegalStateException("creator bug");
            }
        };
        host.open(owned(failing), new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
        assertThrows(RuntimeException.class, () -> host.resume(RunEndReason.LEFT));
        assertFalse(host.isOpen());
    }

    @Test
    void textInputClaimIsForwardedFromTheCreatorScene() {
        boolean[] typing = {false};
        RunScene scene = new RunScene() {
            @Override public boolean capturesTextInput() { return typing[0]; }
        };
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
        assertFalse(host.capturesTextInput());
        typing[0] = true;
        org.junit.jupiter.api.Assertions.assertTrue(host.capturesTextInput());
    }
}
