package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.ttz.TtzScrollHandler;
import com.openggf.game.soniccd.zones.ttz.TtzZone;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs all ten Tidal Tempest stage programs through the shared loader for 300 frames: the scroll handler
 * deforms, the palette cycle moves exactly where the original's gates allow, the water provider hands the
 * shared water system the level {@code WaterHeights} gives, and R41A's animated tiles change. This proves
 * the routines run on real stage data; the native pictures are in {@code TestTtzPictures}, which owns the one GL boot this runs in.
 * Act 1 Past, Good Future and Bad Future must not cycle ({@code palette_cycle_b/c/d.asm:5-7}), which is the
 * negative control for the act gate.
 */
final class TtzStagesRun {
    private TtzStagesRun() { }

    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R41A, CdStagePayload.R41B, CdStagePayload.R41C, CdStagePayload.R41D,
            CdStagePayload.R42A, CdStagePayload.R42B, CdStagePayload.R42C, CdStagePayload.R42D,
            CdStagePayload.R43C, CdStagePayload.R43D};
    /**
     * {@code water_y} ($FFF646) on row 0 of each {@code rehost-fixtures} stage, in PAYLOADS order (native
     * post-frame RAM, LAG lane probe {@code tools/soniccd/lag_native_probe.cs}). The load stands for level frame 1,
     * so it includes iteration 1's V-INT, VBlankStage (r4/main.asm:1078-1082), whose water call has already run the act's
     * water routine: act 1 keeps the table height (r4/main.asm:604-607), acts 2 and 3 start at the camera's
     * {@code fgY + $E8} (r4/main.asm:680-698, 751-757).
     */
    private static final int[] START = {0x280, 0x300, 0x1D0, 0x1D0, 0x574, 0x574, 0x574, 0x574, 0x1F4, 0x1F4};

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

    private static String patterns(Level level, int first, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = first; i < first + count; i++) {
            Pattern pattern = level.getPattern(i);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) out.append(pattern.getPixel(x, y));
        }
        return out.toString();
    }

    static void run(TtzPicture.Session session) throws Exception {
                List<String> report = new ArrayList<>();
        {
            CdDiscDataSource disc = session.disc;
            for (CdStagePayload payload : PAYLOADS) {
                {
                    session.load(payload, true);
                    var loop = session.loop;
                    SonicCdGameModule base = session.base;
                    int zone = SonicCdZoneRegistry.zoneIndex(payload);
                    int act = SonicCdZoneRegistry.actIndex(payload);
                    int start = START[java.util.Arrays.asList(PAYLOADS).indexOf(payload)];
                    assertTrue(GameServices.water().hasWater(zone, act), payload + " has water");
                    assertEquals(start, GameServices.water().getGameplayWaterLevelY(zone, act),
                            payload + " water line after the load (native row 0)");
                    assertEquals(4, GameServices.water().getUnderwaterPalette(zone, act).length, payload + " underwater lines");
                    GameServices.camera().setFrozen(true);
                    loop.step();                    // the first scroll pass creates this stage's handler
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(zone);
                    assertInstanceOf(TtzScrollHandler.class, handler, payload + " scroll handler");
                    assertEquals(payload == CdStagePayload.R41A, ((TtzScrollHandler) handler).isLayered(),
                            payload + " layered background");
                    Level level = GameServices.level().getCurrentLevel();
                    boolean act1Quiet = payload.stage().actNumber() == 1 && payload != CdStagePayload.R41A;
                    assertNotEquals(null, GameServices.level().getAnimatedPaletteManager(), payload + " palette manager");
                    AnimatedPatternManager animator = new TtzZone().createAnimatedTiles(payload, level,
                            ((SonicCdGame) base.createGame(disc)).currentImage());
                    assertEquals(payload == CdStagePayload.R41A, animator != null, payload + " animated tiles");
                    java.util.Set<String> palettes = new java.util.HashSet<>();
                    palettes.add(paletteImage(level));
                    String tilesBefore = animator == null ? "" : patterns(level, 0x228, 16);
                    GameServices.camera().setFrozen(true);
                    int startX = GameServices.camera().getX();
                    int startY = GameServices.camera().getY();
                    int changedLines = 0;
                    int bobs = 0;
                    int lastWater = Integer.MIN_VALUE;
                    int[] previous = null;
                    for (int frame = 1; frame <= 300; frame++) {
                        GameServices.camera().setX((short) (startX + frame));
                        GameServices.camera().setY((short) startY);
                        if (animator != null) animator.update();
                        loop.step();
                        int[] hscroll = GameServices.parallax().getHScroll();
                        assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset(), payload + " offsets at " + frame);
                        if (previous != null && !java.util.Arrays.equals(previous, hscroll)) changedLines++;
                        previous = hscroll.clone();
                        if (frame % 5 == 0) palettes.add(paletteImage(level));
                        int water = GameServices.water().getVisualWaterLevelY(zone, act);
                        if (lastWater != Integer.MIN_VALUE && water != lastWater) bobs++;
                        lastWater = water;
                    }
                    if (act1Quiet) {
                        assertEquals(1, palettes.size(), payload + " must not cycle in act 1");
                    } else {
                        assertTrue(palettes.size() > 1, payload + " palette never cycled in 300 frames");
                    }
                    if (animator != null) {
                        assertNotEquals(tilesBefore, patterns(level, 0x228, 16), payload + " tiles never animated");
                    }
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    assertTrue(bobs > 20, payload + " water level never bobbed: " + bobs);
                    report.add(payload + ": " + changedLines + " changing frames, " + bobs + " water moves");
                }
            }
        }
        System.out.println("TTZ stages: " + report);
        assertEquals(PAYLOADS.length, report.size());
    }
}
