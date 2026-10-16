package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent actual restart pilots; entry replay and physical post contact are separate contracts. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCanonicalDeathLifecycle {
    static Stream<TestS3kCanonicalEntryLifecycle.Act> nativeActs() {
        return Stream.of(new TestS3kCanonicalEntryLifecycle.Act("AIZ2", 0, 1),
                new TestS3kCanonicalEntryLifecycle.Act("HCZ2", 1, 1),
                new TestS3kCanonicalEntryLifecycle.Act("MGZ2", 2, 1),
                new TestS3kCanonicalEntryLifecycle.Act("CNZ1", 3, 0),
                new TestS3kCanonicalEntryLifecycle.Act("CNZ2", 3, 1),
                new TestS3kCanonicalEntryLifecycle.Act("ICZ1", 5, 0),
                new TestS3kCanonicalEntryLifecycle.Act("ICZ2", 5, 1));
    }

    @ParameterizedTest(name = "actual native death/reload twice {0}")
    @MethodSource("nativeActs")
    void nativeMainRestartsThroughTheProductionLoopAndReplaysTheReleasedWorldTwice(
            TestS3kCanonicalEntryLifecycle.Act act) {
        verifyNativeAct(act);
    }

    @Test
    void mgzActOneActualDeathRestartsAndReplaysItsReleasedWorldTwice() {
        verifyNativeAct(new TestS3kCanonicalEntryLifecycle.Act("MGZ1", 2, 0));
    }

    private void verifyNativeAct(TestS3kCanonicalEntryLifecycle.Act act) {
        var entry = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() { return act; }
        };
        entry.saveConfiguration();
        try {
            verifyDeaths(entry, new CanonicalEntryMatrixSupport.Row("sonic",
                    WidescreenAspect.NATIVE_4_3, "off", ""));
        } finally {
            entry.restoreConfiguration();
        }
    }

    static void verifyDeaths(CanonicalEntryMatrixSupport entry, CanonicalEntryMatrixSupport.Row row) {
        var fixture = entry.boot(row);
        var neutral = new Bk2FrameInput(0, 0, 0, false, "");
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle(), "production logical iteration owns fade service");
        try {
            for (int death = 0; death < 2; death++) {
                advance(fixture, loop, entry, row, 45, true);
                assertFalse(GameServices.level().getCheckpointState().isActive(),
                        "this independent scenario has not touched a physical post");
                var oldManager = GameServices.level().getObjectManager();
                var oldRuntime = GameServices.zoneRuntimeRegistry().current();
                int lives = GameServices.gameState().getLives();
                var history = fixture.gameplayMode().getRewindController();
                assertNotNull(history, "actual production live history is enabled");
                int outgoing = history.currentFrame();
                // Invoke the same semantic Kill_Character owner as pit contact; do not
                // write routine, countdown, f_restart, checkpoint or destination state.
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                String killed = label(entry, row, loop);
                int elapsed = 0;
                while (GameServices.level().getObjectManager() == oldManager && elapsed++ < 1800) {
                    advance(fixture, loop, entry, row, 1, false);
                    var current = fixture.gameplayMode().getRewindController();
                    if (current != null) outgoing = Math.max(outgoing, current.currentFrame());
                }
                assertNotSame(oldManager, GameServices.level().getObjectManager(),
                        "death=" + death + " killed=[" + killed + "] final=["
                                + label(entry, row, loop) + "] actual death did not reload");
                assertNotSame(oldRuntime, GameServices.zoneRuntimeRegistry().current());
                assertTrue(outgoing > 10, "the outgoing world has recorded live history");
                var incoming = fixture.gameplayMode().getRewindController();
                assertTrue(incoming == null || incoming.currentFrame() < outgoing,
                        "full reload retires outgoing history");
                boolean released = false;
                for (int frame = 0; frame < 1800 && !released; frame++) {
                    advance(fixture, loop, entry, row, 1, false);
                    var title = GameServices.module().getTitleCardProvider();
                    released = loop.getCurrentGameMode() == GameMode.LEVEL
                            && (title == null || title.isComplete())
                            && !GameServices.fade().isActive()
                            && !GameServices.fade().hasPendingCompletion()
                            && !GameServices.level().hasPendingFreshLevelTransitionBoundary();
                }
                assertTrue(released, () -> label(entry, row, loop) + " title/assembly not released");
                assertEquals(lives - 1, GameServices.gameState().getLives(), "one actual life per death");
                assertFalse(GameServices.level().getCheckpointState().isActive());
                assertFalse(GameServices.sprites().getMainPlayable().getDead());
                entry.assertOwners(row);
                replayReleasedTwice(fixture, loop, entry, row, death);
            }
        } finally {
            loop.closePresence();
        }
    }

    private static void advance(HeadlessTestFixture fixture, GameLoop loop,
                                CanonicalEntryMatrixSupport entry, CanonicalEntryMatrixSupport.Row row,
                                int frames, boolean alive) {
        int lives = GameServices.gameState().getLives();
        for (int frame = 0; frame < frames; frame++) {
            // GameLoop's production logical iteration services the fade once.
            loop.step();
            GameServices.graphics().runPendingRenderThreadTasks();
            if (alive) {
                assertFalse(GameServices.sprites().getMainPlayable().getDead(),
                        () -> label(entry, row, loop));
                assertEquals(lives, GameServices.gameState().getLives(), "no hidden P1 restart");
                entry.assertOwners(row);
            }
        }
    }

    private static void replayReleasedTwice(HeadlessTestFixture fixture, GameLoop loop,
                                            CanonicalEntryMatrixSupport entry,
                                            CanonicalEntryMatrixSupport.Row row, int death) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        advance(fixture, loop, entry, row, 45, true);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                    entry.binding() + "/" + row + " death=" + death + " restore=" + cycle, 45);
            entry.assertOwners(row);
            advance(fixture, loop, entry, row, 45, true);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                    entry.binding() + "/" + row + " death=" + death + " replay=" + cycle, 45);
        }
    }

    private static String label(CanonicalEntryMatrixSupport entry, CanonicalEntryMatrixSupport.Row row,
                                GameLoop loop) {
        var p1 = GameServices.sprites().getMainPlayable();
        return entry.binding() + "/" + row + " mode=" + loop.getCurrentGameMode()
                + " p1=" + p1.getCentreX() + "/" + p1.getCentreY()
                + " dead=" + p1.getDead() + " yvel=" + p1.getYSpeed()
                + " countdown=" + p1.getDeathCountdown() + " restart=" + p1.isInDeathRestartRoutine()
                + " lives=" + GameServices.gameState().getLives()
                + " cameraY=" + GameServices.camera().getY()
                + " cameraMinMaxY=" + GameServices.camera().getMinY() + "/" + GameServices.camera().getMaxY()
                + " cameraFrozen=" + GameServices.camera().getFrozen()
                + " objectControl=" + p1.isObjectControlled()
                + " pendingFresh=" + GameServices.level().hasPendingFreshLevelTransitionBoundary();
    }
}
