package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
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
 * Smoke check that the ten Quartz Quadrant stages load through the shared loader, scroll for 300 frames
 * and cycle their palettes without error. The native comparison is {@code TestQqzBackgroundPicture_*};
 * this proves the routines run on every stage file, not that they match the original.
 */
class TestQqzPeriodsScroll {
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
            for (CdStagePayload payload : new CdStagePayload[] {
                    CdStagePayload.R51A, CdStagePayload.R51B, CdStagePayload.R51C, CdStagePayload.R51D,
                    CdStagePayload.R52A, CdStagePayload.R52B, CdStagePayload.R52C, CdStagePayload.R52D,
                    CdStagePayload.R53C, CdStagePayload.R53D}) {
                try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                    var loop = boot.boot(disc, new SonicCdGameModule(),
                            SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(SonicCdZoneRegistry.zoneIndex(payload));
                    assertInstanceOf(QqzScrollHandler.class, handler, payload + " scroll handler");
                    Level level = GameServices.level().getCurrentLevel();
                    assertNotEquals(null, GameServices.level().getAnimatedPaletteManager(), payload + " palette cycle");
                    String before = paletteImage(level);
                    GameServices.camera().setFrozen(true);
                    int startX = GameServices.camera().getX();
                    int startY = GameServices.camera().getY();
                    int changedLines = 0;
                    boolean cycled = false;
                    int[] previous = null;
                    for (int frame = 1; frame <= 300; frame++) {
                        GameServices.camera().setX((short) (startX + frame * 5));
                        GameServices.camera().setY((short) startY);
                        loop.step();
                        int[] hscroll = GameServices.parallax().getHScroll();
                        assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset(), payload + " offsets at " + frame);
                        if (previous != null && !java.util.Arrays.equals(previous, hscroll)) changedLines++;
                        previous = hscroll.clone();
                        cycled |= !before.equals(paletteImage(level));
                    }
                    assertTrue(cycled, payload + " palette never cycled in 300 frames");
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    report.add(payload + ": " + changedLines + " changing frames");
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
        System.out.println("QQZ periods: " + report);
        assertEquals(10, report.size());
    }
}
