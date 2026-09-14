package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** HCZ1's actual reload dispatch must sever the old timeline and retain rewind in HCZ2. */
@RequiresRom(SonicGame.SONIC_3K)
@org.junit.jupiter.api.Tag("slow-suite")
class TestS3kHcz1ReloadRewind {
    @Test
    void seamlessReloadIsolatesHistoryAndNewActReplays() throws Exception {
        Hcz1Route.withConfiguration(320, "off", TestS3kHcz1ReloadRewind::checkReload);
    }

    private static void checkReload(Hcz1Route route) throws Exception {
        var config = GameServices.configuration();
        Object savedRewind = config.getConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED);
        LiveRewindManager rewind = null;
        try {
            var fixture = route.fixture;
            var firstObjects = GameServices.level().getObjectManager();
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            rewind = new LiveRewindManager(config);
            fixture.gameplayMode().setRewindBoundaryReporter(rewind::markBoundary);
            var input = new InputHandler();
            rewind.handleRealtimeRewindInput(GameMode.LEVEL, false, input);
            Bk2FrameInput previous = new Bk2FrameInput(0, 0, 0, false, "");
            int frame = 0;
            boolean boundarySeen = false;
            while (frame < 20000) {
                int mask = route.step();
                frame++;
                var current = new Bk2FrameInput(frame, mask, (mask & 0x10) == 0 ? 0 : 1, false, "");
                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
                // Same end-of-frame admission as GameLoop: never capture the reload mid-frame.
                boolean boundary = GameServices.level().consumeActTransitionRewindBoundaryDuringFrame();
                rewind.recordExternalFrame(GameMode.LEVEL, false, input, boundary);
                input.update();
                input.clearLogicalOverride();
                previous = current;
                boundarySeen |= boundary;
                assertFalse(fixture.sprite().getDead(), "P1 died before reload at " + frame);
                if (GameServices.level().getCurrentAct() == 1) break;
            }
            assertEquals(1, GameServices.level().getCurrentAct(), "HCZ2 reload missing");
            assertTrue(boundarySeen, "production reload did not publish its rewind boundary");
            assertNotSame(firstObjects, GameServices.level().getObjectManager());
            var controller = fixture.gameplayMode().getRewindController();
            assertNotNull(controller);
            int newRoot = controller.currentFrame();
            assertTrue(newRoot > 0, "seamless boundary retains the current clock origin");
            assertEquals(newRoot, controller.earliestAvailableFrame());
            controller.seekTo(newRoot - 1);
            assertEquals(newRoot, controller.currentFrame(), "seek must clamp to the new act's root");
            assertEquals(1, GameServices.level().getCurrentAct(), "old act must not be restored");
            assertEquals(320, fixture.camera().getWidth());
            assertEquals(1, GameServices.sprites().getSidekicks().size());

            var registry = fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            fixture.stepIdleFrames(30);
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                fixture.runner().primeInputState(previous);
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(before, registry.capture(), "HCZ2 restore", frame);
                fixture.stepIdleFrames(30);
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(expected, registry.capture(), "HCZ2 replay", frame);
                assertEquals(1, GameServices.level().getCurrentAct());
                assertFalse(fixture.sprite().getDead());
            }
            System.out.printf("HCZRELOAD frame=%d root=%d replay=30 cycles=2%n", frame, newRoot);
        } finally {
            if (rewind != null) rewind.markBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
            if (savedRewind != null) config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, savedRewind);
        }
    }
}
