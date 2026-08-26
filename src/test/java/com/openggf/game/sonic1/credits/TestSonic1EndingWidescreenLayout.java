package com.openggf.game.sonic1.credits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic1EndingWidescreenLayout {

    @Test
    void nativeCreditCenterFollowsTheLiveViewport() {
        assertEquals(160, Sonic1CreditsTextRenderer.centerXForWidth(320));
        assertEquals(176, Sonic1CreditsTextRenderer.centerXForWidth(352));
        assertEquals(200, Sonic1CreditsTextRenderer.centerXForWidth(400));
        assertEquals(200, TryAgainEndManager.centerXForWidth(400));
    }
}
