package com.openggf.mods.scene.host;

import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunSpec;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertSame(launcher, scene.context.gameplay());
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
}
