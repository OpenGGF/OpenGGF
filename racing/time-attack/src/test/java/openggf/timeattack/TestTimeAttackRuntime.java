package openggf.timeattack;

import openggf.racing.ghost.GhostFrame;
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
        assertEquals(openggf.racing.identity.PlayerIdentity.loadOrCreate(identityDir)
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
        byte[] frames = new byte[openggf.racing.ghost.GhostFrameCodec.BYTES];
        var wrongTrack = new openggf.timeattack.ghost.GhostRecording(
                new openggf.timeattack.ghost.GhostHeader(1, "s3k", 1, 0, "sonic", "x", 0, 100,
                        new int[0], new byte[32]), frames);
        var rightTrack = new openggf.timeattack.ghost.GhostRecording(
                new openggf.timeattack.ghost.GhostHeader(1, "s3k", 0, 0, "tails", "y", 0, 100,
                        new int[0], new byte[32]), frames);
        Path wrong = importDir.resolve("wrong.ggfghost");
        Path right = importDir.resolve("right.ggfghost");
        openggf.timeattack.ghost.GhostFileCodec.write(wrongTrack, wrong);
        openggf.timeattack.ghost.GhostFileCodec.write(rightTrack, right);

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
        for (int i = 0; i < openggf.timeattack.ghost.GhostFileCodec.MAX_FRAMES + 10; i++) {
            runtime.tickForTest(0x08, false, false, -1, frame(10));
        }
        runtime.tickForTest(0x08, false, true, -1, frame(10)); // signpost after cap — ignored
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
        assertFalse(runtime.hudState().finished());
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
    void runSpecIsAnIsolatedActForTheArmedLaunch(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 1, 1, "knuckles", java.util.List.of()));
        var spec = runtime.runSpec();
        assertEquals("s3k", spec.gameId());
        assertEquals(1, spec.zone());
        assertEquals(1, spec.act());
        assertEquals("knuckles", spec.character());
        assertEquals(com.openggf.game.session.GameplayRunPolicy.isolatedAct(), spec.policy());
    }

    @Test
    void retryKeyVoidsTheAttemptAndAsksTheRunToRetry(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        boolean[] retried = new boolean[1];
        runtime.attachHandle(new com.openggf.game.run.RunHandle() {
            @Override public void retry() { retried[0] = true; }
            @Override public void leave() { }
            @Override public void spectate(boolean active, int dx, int dy) { }
            @Override public boolean isActive() { return true; }
        });
        runtime.setRetryKey(() -> 82);
        runtime.onLevelReady(new com.openggf.game.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0));
        assertTrue(runtime.isAttemptActive());

        assertTrue(runtime.admitStep(keys(82)));

        assertFalse(runtime.isAttemptActive(), "retry voids the running attempt");
        assertTrue(retried[0], "retry is a run command, applied by the engine at the next boundary");
    }

    @Test
    void debugAssistedLevelStartTaintsTheAttempt(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.onLevelReady(new com.openggf.game.run.RunLevelStart(runtime.runSpec(), "fp", true, 0, 0));
        com.openggf.game.run.PlayerPose pose = new com.openggf.game.run.PlayerPose(10, 20, 0, false, false, 2, false);
        runtime.afterStep(new com.openggf.game.run.RunStep(0, 0x08, false, pose, false, -1, false));
        runtime.afterStep(new com.openggf.game.run.RunStep(1, 0x08, false, pose, true, -1, false));
        assertTrue(runtime.hudState().finished());
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty(), "a tainted finish is never saved");
    }

    @Test
    void endingTheRunDeactivatesTheRuntime(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertTrue(runtime.isActive());
        runtime.onRunEnded(com.openggf.game.run.RunEndReason.ACT_COMPLETED);
        assertFalse(runtime.isActive());
    }

    private static com.openggf.game.run.RunInput keys(int pressedKey) {
        return new com.openggf.game.run.RunInput() {
            @Override public com.openggf.control.LogicalInputSnapshot input() { return null; }
            @Override public boolean keyDown(int key) { return key == pressedKey; }
            @Override public boolean keyPressed(int key) { return key == pressedKey; }
        };
    }
}
