package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A capture that omits title drawing must still finish the real destination assembly. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureFreshLoad {
    @Test
    void omittedDestinationTitleFinishesDeferredAssemblyAndReleasesTheLivePlayer() throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            session.player().setRingCount(28);
            GameServices.level().requestZoneAndAct(4, 0);
            boolean destinationSeen = false;
            boolean pendingAssemblySeen = false;
            boolean releasedCounterSeen = false;
            for (int frame = 0; frame < 400; frame++) {
                session.step(null);
                // Prepared resources publish through the real render-thread queue.
                session.renderFrame();
                var level = GameServices.level();
                if (level.getCurrentZone() != 4) continue;
                destinationSeen = true;
                pendingAssemblySeen |= level.hasPendingFreshLevelTransitionBoundary();
                if (!level.hasPendingFreshLevelTransitionBoundary() && !releasedCounterSeen) {
                    // loc_63B4's ordinary destination count must replace the
                    // source count retained only for the locked title loop.
                    assertEquals(0, session.player().getRingCount());
                    releasedCounterSeen = true;
                }
                if (!level.hasPendingFreshLevelTransitionBoundary()
                        && !GameServices.fade().hasPendingCompletion()
                        && level.getFrameCounter() > 0) {
                    var livePlayer = GameServices.sprites().getMainPlayable();
                    assertSame(livePlayer, session.player(), "capture reports the actual live leader");
                    assertSame(livePlayer, GameServices.camera().getFocusedSprite());
                    assertNotEquals(0, livePlayer.getCentreX(), "destination player is assembled");
                    assertFalse(livePlayer.isObjectControlled());
                    assertFalse(livePlayer.isControlLocked());
                    assertTrue(pendingAssemblySeen, "the test crosses the production deferred boundary");
                    assertTrue(releasedCounterSeen);
                    return;
                }
            }
            assertTrue(destinationSeen, "the production zone load executes");
            fail("omitting title presentation stranded the fresh destination assembly");
        }
    }
}
