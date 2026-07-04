package com.openggf.game.timeattack;

import com.openggf.game.GameStateManager;
import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostRenderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackRuntime {
    private static GhostFrame frame(int x) {
        return new GhostFrame(x, 100, 1, false, false, false, 2, false);
    }

    @Test
    void armTickFinishSavesBestGhostAndInputs(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        Path identityDir = root.resolve("identity");
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, identityDir, () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertTrue(java.nio.file.Files.exists(identityDir.resolve("player-identity.key"))); // identity wired at arm
        runtime.beginAttemptForTest("0.6:cafe");   // package-visible spawn hook used by onLevelReady
        runtime.tickForTest(0, false, false, -1, frame(10));      // spawn idle
        runtime.tickForTest(0x08, false, false, -1, frame(11));   // first input
        runtime.tickForTest(0x08, false, false, 1, frame(12));    // checkpoint 1
        runtime.tickForTest(0x08, false, true, 1, frame(13));     // signpost
        assertTrue(runtime.hudState().finished());
        assertTrue(runtime.hudState().newBest());
        var best = store.loadBest("s3k", 0, 0, "sonic").orElseThrow();
        assertEquals(4, best.frameCount());
        assertEquals(1, best.header().firstInputFrame());
        assertEquals(3, best.header().finishFrame());
        assertEquals(2, best.header().finalTimeFrames());
        assertArrayEquals(new int[] {2}, best.header().splitFrames());
        assertEquals(32, best.header().inputRecordingHash().length);
        assertEquals(8, best.header().displayName().length()); // fingerprint prefix from PlayerIdentity
        assertEquals(com.openggf.net.identity.PlayerIdentity.loadOrCreate(identityDir)
                .fingerprint().substring(0, 8), best.header().displayName());
    }

    @Test
    void taintedAttemptIsNeverSaved(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.markTainted();
        runtime.tickForTest(0x08, false, true, -1, frame(10));
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
        assertFalse(runtime.hudState().newBest());
    }

    @Test
    void incompatibleImportsAreSkipped(@TempDir Path root, @TempDir Path importDir) throws Exception {
        // A ghost recorded for a DIFFERENT track must never race in this one.
        byte[] frames = new byte[com.openggf.game.ghost.GhostFrameCodec.BYTES];
        var wrongTrack = new com.openggf.game.ghost.GhostRecording(
                new com.openggf.game.ghost.GhostHeader(1, "s3k", 1, 0, "sonic", "x", 0, 100,
                        new int[0], new byte[32]), frames);
        var rightTrack = new com.openggf.game.ghost.GhostRecording(
                new com.openggf.game.ghost.GhostHeader(1, "s3k", 0, 0, "tails", "y", 0, 100,
                        new int[0], new byte[32]), frames);
        Path wrong = importDir.resolve("wrong.ggfghost");
        Path right = importDir.resolve("right.ggfghost");
        com.openggf.game.ghost.GhostFileCodec.write(wrongTrack, wrong);
        com.openggf.game.ghost.GhostFileCodec.write(rightTrack, right);

        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic",
                java.util.List.of(wrong, right)));
        runtime.beginAttemptForTest("0.6:cafe");
        assertEquals(1, runtime.opponents().size()); // only the matching-track import raced
    }

    @Test
    void refusesToArmWhenTraceOrTestModeActive(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> true); // guard says blocked
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertFalse(runtime.isActive());
    }

    @Test
    void attemptVoidsAtMaxFramesAndNeverSaves(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        for (int i = 0; i < com.openggf.game.ghost.GhostFileCodec.MAX_FRAMES + 10; i++) {
            runtime.tickForTest(0x08, false, false, -1, frame(10));
        }
        runtime.tickForTest(0x08, false, true, -1, frame(10)); // signpost after cap — ignored
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
        assertFalse(runtime.hudState().finished());
    }

    @Test
    void reattachingRendererDoesNotStackDuplicateRegistrations(@TempDir Path root) {
        // A retry re-enters onLevelReady() on the same GameplayModeContext, so the ghost
        // layer renderer must be detached before re-registering — otherwise it draws N+1
        // times. Two attaches must net exactly one registration: after deactivate()
        // unregisters once, the registry must be empty.
        GhostRenderRegistry registry = new GhostRenderRegistry();
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.attachRenderer(registry);
        runtime.attachRenderer(registry);
        assertFalse(registry.isEmpty());
        runtime.deactivate();
        assertTrue(registry.isEmpty(), "duplicate registration left the ghost rendering after deactivate");
    }

    @Test
    void deactivateClearsOpponentGhosts(@TempDir Path root) throws Exception {
        // A frozen ghost must not keep rendering after a level-ended deactivate.
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        // Bank a best ghost, then re-arm so it loads as an opponent to race.
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.tickForTest(0x08, false, true, -1, frame(11));
        runtime.beginAttemptForTest("0.6:cafe");
        assertEquals(1, runtime.opponents().size());
        runtime.deactivate();
        assertTrue(runtime.opponents().isEmpty());
    }

    @Test
    void retryVoidsAttemptWithoutSaving(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.requestRetry();
        assertTrue(runtime.consumeRetryRequested());
        assertFalse(runtime.consumeRetryRequested());
        runtime.tickForTest(0x08, false, true, -1, frame(11)); // finish after void — ignored
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
    }

    @Test
    void applyTimeAttackActiveFlagSetsAndClearsGameState(@TempDir Path root) {
        // Engine-free seam for onLevelReady()/deactivate(): GameServices isn't
        // reachable headless, so exercise the flag lifecycle directly against
        // a plain GameStateManager instead of faking a GameplayModeContext.
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        GameStateManager gameState = new GameStateManager();
        assertFalse(gameState.isTimeAttackActive());

        runtime.applyTimeAttackActiveFlag(gameState, true); // mirrors onLevelReady()
        assertTrue(gameState.isTimeAttackActive());

        runtime.applyTimeAttackActiveFlag(gameState, false); // mirrors deactivate()
        assertFalse(gameState.isTimeAttackActive());
    }

    @Test
    void applyTimeAttackActiveFlagToleratesNullGameState(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        assertDoesNotThrow(() -> runtime.applyTimeAttackActiveFlag(null, true));
    }
}
