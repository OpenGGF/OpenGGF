package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeArenaCameraFraming;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIZ2_DoShipLoop (sonic3k.asm:105251-105272) writes the moving camera X to both native
 * bounds every frame of the battleship loop, so the S3K zone policy reports them as an
 * auto-scroll lock for exactly that loop and the widescreen bounds mask leaves the margins
 * showing the scenery about to scroll in. TestLevelBoundsMaskGeometry covers the mask.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAiz2ShipLoopWidescreenMask {

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void shipLoopReportsItsNativeBoundsAsAnAutoScrollLockOnlyWhileItRuns() {
        HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var framing = assertInstanceOf(NativeArenaCameraFraming.class,
                GameServices.level().getZoneFeatureProvider());
        Sonic3kAIZEvents events = ((Sonic3kLevelEventManager) GameServices.module()
                .getLevelEventProvider()).getAizEvents();

        assertFalse(framing.nativeBoundsFollowAutoScroll(), "ordinary AIZ2 bounds stay a masked arena");
        events.setBattleshipAutoScrollActiveRaw(true);
        assertTrue(framing.nativeBoundsFollowAutoScroll(), "AIZ2_DoShipLoop drives the bounds with the camera");
        events.setBattleshipAutoScrollActiveRaw(false);
        assertFalse(framing.nativeBoundsFollowAutoScroll(), "the boss lock after the loop is masked again");
    }
}
