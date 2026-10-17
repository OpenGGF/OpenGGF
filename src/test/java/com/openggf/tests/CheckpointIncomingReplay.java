package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import static org.junit.jupiter.api.Assertions.*;

/** Full incoming-world replay after the actual reload/title/fade release, task S3K20261014. */
public final class CheckpointIncomingReplay {
    private CheckpointIncomingReplay() { }

    public static void verify(HeadlessTestFixture fixture, GameLoop loop, InputHandler input,
                       int checkpoint, Runnable assertOwners) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var objects = GameServices.level().getObjectManager();
        var runtime = GameServices.zoneRuntimeRegistry().current();
        int lives = GameServices.gameState().getLives();
        var before = registry.capture();
        Runnable advance = () -> {
            var neutral = new Bk2FrameInput(0, 0, 0, false, "");
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral, neutral));
            for (int frame = 0; frame < 45; frame++) {
                loop.step();
                GameServices.graphics().runPendingRenderThreadTasks();
                assertEquals(GameMode.LEVEL, loop.getCurrentGameMode(), "incoming world stays active");
                assertFalse(GameServices.sprites().getMainPlayable().getDead(), "incoming leader stays alive");
                assertEquals(lives, GameServices.gameState().getLives(), "no hidden restart");
                assertEquals(checkpoint, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                assertSame(objects, GameServices.level().getObjectManager(), "raw replay never crosses a load");
                assertSame(runtime, GameServices.zoneRuntimeRegistry().current(), "incoming runtime remains owned");
                assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
                assertOwners.run();
            }
        };
        advance.run();
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture(), "incoming before " + cycle);
            assertOwners.run();
            advance.run();
            same(after, registry.capture(), "incoming forward " + cycle);
            registry.restore(before);
            same(before, registry.capture(), "incoming reverse " + cycle);
            assertOwners.run();
            registry.restore(after);
            same(after, registry.capture(), "incoming after " + cycle);
            assertOwners.run();
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
