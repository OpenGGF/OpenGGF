package com.openggf.game.sonic2.credits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic2EndingWidescreenLayout {

    @Test
    void nativeEndingCoordinatesFollowTheLiveViewport() {
        assertEquals(160, Sonic2CreditsTextRenderer.centerXForWidth(320));
        assertEquals(176, Sonic2CreditsTextRenderer.centerXForWidth(352));
        assertEquals(200, Sonic2CreditsTextRenderer.centerXForWidth(400));
        assertEquals(112, Sonic2EndingCutsceneManager.screenXForWidth(112, 320));
        assertEquals(152, Sonic2EndingCutsceneManager.screenXForWidth(112, 400));
        assertEquals(200, Sonic2LogoFlashManager.centerXForWidth(400));
    }
}
