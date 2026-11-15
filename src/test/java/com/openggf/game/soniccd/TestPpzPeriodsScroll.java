package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.ppz.PpzScrollHandler;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke check that every Palmtree Panic stage (acts 1 to 3) loads through the shared loader, scrolls for
 * 300 frames and cycles its palette without error. This proves the routines run on real stage data, not
 * that they match the original: the native checks are {@code TestPpzStageFixturePictures} (every stage) and
 * {@code TestPpzR11aBackgroundCamera} / {@code TestPpzR11aBackgroundPicture} (act 1 Present).
 */
class TestPpzPeriodsScroll {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R11A, CdStagePayload.R11B, CdStagePayload.R11C, CdStagePayload.R11D,
            CdStagePayload.R12A, CdStagePayload.R12B, CdStagePayload.R12C, CdStagePayload.R12D,
            CdStagePayload.R13C, CdStagePayload.R13D};

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
    void everyPeriodScrollsAndCyclesForThreeHundredFrames() throws Exception {
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (CdStagePayload payload : PAYLOADS) {
                try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                    var loop = boot.boot(disc, new SonicCdGameModule(),
                            SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(SonicCdZoneRegistry.zoneIndex(payload));
                    assertInstanceOf(PpzScrollHandler.class, handler, payload + " scroll handler");
                    Level level = GameServices.level().getCurrentLevel();
                    assertNotEquals(null, GameServices.level().getAnimatedPaletteManager(), payload + " palette cycle");
                    // The load already ran the first PaletteCycle (CdFirstMainLoopIteration), and a cycle whose period
                    // divides 300 returns to that phase, so every frame's palette is sampled.
                    java.util.Set<String> palettes = new java.util.HashSet<>();
                    palettes.add(paletteImage(level));
                    GameServices.camera().setFrozen(true);
                    int startX = GameServices.camera().getX();
                    int startY = GameServices.camera().getY();
                    int changedLines = 0;
                    int[] previous = null;
                    for (int frame = 1; frame <= 300; frame++) {
                        // A camera sweep to the right, ending past the first screens of the act.
                        GameServices.camera().setX((short) (startX + frame * 9));
                        GameServices.camera().setY((short) startY);
                        loop.step();
                        int[] hscroll = GameServices.parallax().getHScroll();
                        assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset(), payload + " offsets at " + frame);
                        if (previous != null && !java.util.Arrays.equals(previous, hscroll)) changedLines++;
                        previous = hscroll.clone();
                        palettes.add(paletteImage(level));
                    }
                    assertTrue(palettes.size() > 1, payload + " palette never cycled in 300 frames");
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    report.add(payload + ": " + changedLines + " changing frames");
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
        System.out.println("PPZ periods: " + report);
        assertEquals(PAYLOADS.length, report.size());
    }
}
