package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.wwz.WwzScrollHandler;
import com.openggf.game.soniccd.zones.wwz.WwzZone;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke check that the ten Wacky Workbench stages load through the shared loader, scroll for 300 frames, cycle their
 * palettes and animate their tiles without error on real stage data. Native pixel equality is
 * {@code TestWwzNativePictures}; this proves the routines run on every stage and that their tile destinations lie
 * inside the loaded pattern table.
 */
class TestWwzStagesScroll {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R61A, CdStagePayload.R61B, CdStagePayload.R61C, CdStagePayload.R61D,
            CdStagePayload.R62A, CdStagePayload.R62B, CdStagePayload.R62C, CdStagePayload.R62D,
            CdStagePayload.R63C, CdStagePayload.R63D};

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

    private static int tilesImage(Level level) {
        int hash = 1;
        for (int i = 0; i < level.getPatternCount(); i++) {
            Pattern pattern = level.getPattern(i);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) hash = hash * 31 + pattern.getPixel(x, y);
        }
        return hash;
    }

    @Test
    void everyStageScrollsCyclesAndAnimatesForThreeHundredFrames() throws Exception {
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (CdStagePayload payload : PAYLOADS) {
                try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                    int zone = SonicCdZoneRegistry.zoneIndex(payload);
                    var loop = boot.boot(disc, new SonicCdGameModule(), zone, SonicCdZoneRegistry.actIndex(payload));
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(zone);
                    assertInstanceOf(WwzScrollHandler.class, handler, payload + " scroll handler");
                    Level level = GameServices.level().getCurrentLevel();
                    assertNotNull(GameServices.level().getAnimatedPaletteManager(), payload + " palette cycle");
                    var game = (SonicCdGame) GameServices.level().getGame();
                    AnimatedPatternManager tiles = new WwzZone().createAnimatedTiles(payload, level, game.currentImage());
                    assertNotNull(tiles, payload + " animated tiles");
                    String lastPalette = paletteImage(level);
                    int lastTiles = tilesImage(level);
                    int paletteChanges = 0;
                    int tileChanges = 0;
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
                        tiles.update();
                        int[] hscroll = GameServices.parallax().getHScroll();
                        assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset(), payload + " offsets at " + frame);
                        if (previous != null && !java.util.Arrays.equals(previous, hscroll)) changedLines++;
                        previous = hscroll.clone();
                        String palette = paletteImage(level);
                        if (!palette.equals(lastPalette)) paletteChanges++;
                        lastPalette = palette;
                        if (frame % 3 == 0) {       // every animator period divides into a 3-frame sampling grid often enough
                            int tilesNow = tilesImage(level);
                            if (tilesNow != lastTiles) tileChanges++;
                            lastTiles = tilesNow;
                        }
                    }
                    assertTrue(paletteChanges > 0, payload + " palette never cycled in 300 frames");
                    assertTrue(tileChanges > 0, payload + " tiles never animated in 300 frames");
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    report.add(payload + ": " + changedLines + " changing frames");
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
        System.out.println("WWZ stages: " + report);
        assertEquals(PAYLOADS.length, report.size());
    }
}
