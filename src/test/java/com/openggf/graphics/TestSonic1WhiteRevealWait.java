package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic1WhiteRevealWait {
    @Test void paletteWhiteInReturnsAfterItsTwentySecondCountedWait() {
        var fade = new FadeManager();
        int[] completed = {0};
        fade.startFadeFromWhite(() -> completed[0]++,
                PaletteFadePresentation.ROM_FADE_FRAMES - PaletteFadePresentation.COLOR_STEPS);
        for (int wait = 0; wait < PaletteFadePresentation.COLOR_STEPS; wait++) fade.update();
        assertEquals(0, completed[0]);
        assertTrue(fade.isActive());
        fade.update();
        assertEquals(1, completed[0]);
        assertFalse(fade.isActive());
    }
}
