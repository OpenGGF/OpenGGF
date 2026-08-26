package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLegalDisclaimerWidescreenLayout {

    @Test
    void legalBackdropAndNativeTextUseTheResolvedLogicalWidth() {
        assertEquals(0, LegalDisclaimerScreen.nativeOriginForWidth(320));
        assertEquals(320, LegalDisclaimerScreen.backdropWidthForWidth(320));
        assertEquals(115, LegalDisclaimerScreen.centeredTextXForWidth(320, 90));

        assertEquals(16, LegalDisclaimerScreen.nativeOriginForWidth(352));
        assertEquals(352, LegalDisclaimerScreen.backdropWidthForWidth(352));
        assertEquals(131, LegalDisclaimerScreen.centeredTextXForWidth(352, 90));

        assertEquals(40, LegalDisclaimerScreen.nativeOriginForWidth(400));
        assertEquals(400, LegalDisclaimerScreen.backdropWidthForWidth(400));
        assertEquals(155, LegalDisclaimerScreen.centeredTextXForWidth(400, 90));
    }
}
