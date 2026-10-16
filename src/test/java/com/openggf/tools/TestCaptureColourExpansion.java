package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A saved Limited colour expansion must reach captured frames through the
 * shared display owner, not only the windowed engine. Renders the same EHZ1
 * boot frame under Full and Limited and reads the actual framebuffer.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestCaptureColourExpansion {
    @Test
    void savedLimitedExpansionReachesCapturedFramebufferPixels() throws Exception {
        assumeTrue(SurfacelessEglContext.requested(), "requires owned surfaceless EGL");
        Path rom = RomTestUtils.ensureSonic2RomAvailable().toPath();
        RgbaImage full = capture(rom, DisplayColorExpansion.FULL);
        RgbaImage limited = capture(rom, DisplayColorExpansion.LIMITED);

        int fullMax = 0;
        int limitedMax = 0;
        int limitedTop = 0;
        int exact = 0;
        int[] f = full.pixels();
        int[] l = limited.pixels();
        assertEquals(f.length, l.length);
        for (int i = 0; i < f.length; i++) {
            boolean pixelExact = true;
            for (int shift = 0; shift <= 16; shift += 8) {
                int fc = (f[i] >>> shift) & 0xFF;
                int lc = (l[i] >>> shift) & 0xFF;
                fullMax = Math.max(fullMax, fc);
                limitedMax = Math.max(limitedMax, lc);
                if (lc == 238) limitedTop++;
                pixelExact &= lc == (fc * 238 + 127) / 255;
            }
            if (pixelExact) exact++;
        }
        assertEquals(255, fullMax, "control: the Full frame must contain a canonical 255 channel");
        assertTrue(limitedMax <= 238, "Limited capture must not show any channel above 238, was " + limitedMax);
        assertTrue(limitedTop > 0, "canonical 255 channels must read 238 under Limited");
        assertTrue(exact >= f.length * 99L / 100,
                "the Limited frame must be the forward 238/255 gain of the Full frame: "
                        + exact + "/" + f.length + " pixels exact");
    }

    private static RgbaImage capture(Path rom, DisplayColorExpansion expansion) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(
                    SonicConfiguration.DISPLAY_COLOR_EXPANSION, expansion.name());
            session.boot(rom, 0, 0, settings);
            return session.render().copy();
        }
    }
}
