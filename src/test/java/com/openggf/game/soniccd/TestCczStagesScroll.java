package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.ccz.CczScrollHandler;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load-and-run check for the ten Collision Chaos stage programs: each loads through the shared loader, scrolls
 * for 300 frames of a camera sweep and cycles its palette (the Past stages have no cycle, as shipped). This
 * proves the routines run on real stage data. The native result per stage is in
 * the TestCcz*Picture classes; a stage that fails there is uncertified whatever this says.
 */
class TestCczStagesScroll {
    /** Stages whose PaletteCycle is not the bare rts of Palette Cycle (Act 1 Variant).asm:7-10. */
    private static boolean cycles(CdStagePayload payload) {
        return payload.stage().period() != CdPeriod.PAST;
    }

    private static String paletteImage(Level level) {
        StringBuilder out = new StringBuilder();
        for (int line = 0; line < level.getPaletteCount(); line++) {
            Palette palette = level.getPalette(line);
            for (int color = 0; color < 16; color++) {
                Palette.Color c = palette.getColor(color);
                out.append(c.r & 0xFF).append(',').append(c.g & 0xFF).append(',').append(c.b & 0xFF).append(';');
            }
        }
        return out.toString();
    }

    @Test
    void everyStageScrollsAndCyclesForThreeHundredFrames() throws Exception {
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (CdStagePayload payload : CczNativePictureCheck.STAGES) {
                try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                    var loop = boot.boot(disc, new SonicCdGameModule(),
                            SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(SonicCdZoneRegistry.zoneIndex(payload));
                    assertInstanceOf(CczScrollHandler.class, handler, payload + " scroll handler");
                    Level level = GameServices.level().getCurrentLevel();
                    if (cycles(payload)) {
                        assertNotEquals(null, GameServices.level().getAnimatedPaletteManager(), payload + " palette cycle");
                    } else {
                        assertNull(GameServices.level().getAnimatedPaletteManager(), payload + " has no palette cycle");
                    }
                    String before = paletteImage(level);
                    GameServices.camera().setFrozen(true);
                    int startX = GameServices.camera().getX();
                    int startY = GameServices.camera().getY();
                    int changedLines = 0;
                    int[] previous = null;
                    for (int frame = 1; frame <= 300; frame++) {
                        GameServices.camera().setX((short) (startX + frame * 9));      // a sweep to the right
                        GameServices.camera().setY((short) startY);
                        loop.step();
                        int[] hscroll = GameServices.parallax().getHScroll();
                        assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset(), payload + " offsets at " + frame);
                        if (previous != null && !java.util.Arrays.equals(previous, hscroll)) changedLines++;
                        previous = hscroll.clone();
                    }
                    if (cycles(payload)) assertNotEquals(before, paletteImage(level), payload + " palette never cycled");
                    else assertEquals(before, paletteImage(level), payload + " palette changed without a cycle");
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    report.add(payload + ": " + changedLines + " changing frames");
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
        System.out.println("CCZ stages: " + report);
        assertEquals(10, report.size());
    }
}
