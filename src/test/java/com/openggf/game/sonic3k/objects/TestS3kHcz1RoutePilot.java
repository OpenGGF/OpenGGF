package com.openggf.game.sonic3k.objects;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

/** Native HCZ1 representative route; broader entry/reset axes are checked independently. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("slow-suite")
class TestS3kHcz1RoutePilot {
    @Test
    void recordedOpeningAndLiveRouteReachTheAct2Reload() throws Exception {
        Hcz1Route.withConfiguration(320, "off", Hcz1Route::complete);
    }
}
