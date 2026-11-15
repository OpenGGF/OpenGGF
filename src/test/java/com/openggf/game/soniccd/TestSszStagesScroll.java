package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.ssz.SszScrollHandler;
import com.openggf.game.soniccd.zones.ssz.SszZone;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke check that the ten Stardust Speedway stages load through the shared loader, scroll for 300 frames and cycle
 * their palettes where the shipped routine does (Present and Bad Future; Past and Good Future begin with {@code rts},
 * r7/palette_cycle_{b,c}.asm:4), without error on real stage data. Stardust Speedway has no animated tiles
 * (r7/stage_gfx_animate_*.asm:3-4). Native pixel equality is {@code TestSszNativePictures}.
 */
class TestSszStagesScroll {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R71A, CdStagePayload.R71B, CdStagePayload.R71C, CdStagePayload.R71D,
            CdStagePayload.R72A, CdStagePayload.R72B, CdStagePayload.R72C, CdStagePayload.R72D,
            CdStagePayload.R73C, CdStagePayload.R73D};

    /** Every colour of the level's palette lines as RGB, at index line * 16 + colour. */
    private static int[] colours(Level level) {
        int[] out = new int[level.getPaletteCount() * 16];
        for (int line = 0; line < level.getPaletteCount(); line++) {
            Palette palette = level.getPalette(line);
            for (int color = 0; color < 16; color++) {
                Palette.Color c = palette.getColor(color);
                out[line * 16 + color] = ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
            }
        }
        return out;
    }

    /** Whether a colour outside {@code excluded} differs. */
    private static boolean changed(int[] before, int[] after, java.util.Set<Integer> excluded) {
        for (int i = 0; i < Math.min(before.length, after.length); i++) {
            if (!excluded.contains(i) && before[i] != after[i]) return true;
        }
        return before.length != after.length;
    }

    /**
     * The colours the act 3 boss loads: EggmanObject_0_Routine0's {@code move.w #6,d0; jsr LoadPalette}
     * (objects/r7/boss.asm:780-783), the stage palette table's entry 6 (destination word and long count, as
     * {@code CdBossPart.loadPalette} reads it). The boss is a layout object: the spawner places it when the camera
     * reaches it and it loads these colours on its first frame, as native does at that camera position. From the frame
     * it is live they are not the palette cycle's and are left out; every other colour, and every other stage, is
     * compared whole.
     */
    private static java.util.Set<Integer> bossColours(CdStagePayload payload, com.openggf.game.soniccd.level.CdStageImage image) {
        int table = com.openggf.game.soniccd.level.CdStageCatalogue.entry(payload).paletteTable();
        int entry = table + 6 * 8;                                       // move.w #6,d0
        int first = (image.u16(entry + 4) - image.u16(table + 4)) / 2;
        int count = (image.u16(entry + 6) + 1) * 2;
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) out.add(first + i);
        return out;
    }

    private static boolean bossLive() {
        for (var o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof com.openggf.game.soniccd.objects.boss.ssz.SszBossEggman && !o.isDestroyed()) return true;
        }
        return false;
    }

    private static boolean cycles(CdStagePayload payload) {
        return payload.stage().period() == com.openggf.game.soniccd.CdPeriod.PRESENT
                || payload.stage().period() == com.openggf.game.soniccd.CdPeriod.BAD_FUTURE;
    }

    @Test
    void everyStageScrollsAndCyclesWhereTheRoutineDoesForThreeHundredFrames() throws Exception {
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (CdStagePayload payload : PAYLOADS) {
                try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                    int zone = SonicCdZoneRegistry.zoneIndex(payload);
                    var loop = boot.boot(disc, new SonicCdGameModule(), zone, SonicCdZoneRegistry.actIndex(payload));
                    ZoneScrollHandler handler = GameServices.parallax().getHandler(zone);
                    assertInstanceOf(SszScrollHandler.class, handler, payload + " scroll handler");
                    Level level = GameServices.level().getCurrentLevel();
                    var game = (SonicCdGame) GameServices.level().getGame();
                    assertNull(new SszZone().createAnimatedTiles(payload, level, game.currentImage()), payload + " animated tiles");
                    if (cycles(payload)) {
                        assertNotNull(GameServices.level().getAnimatedPaletteManager(), payload + " palette cycle");
                    } else {
                        assertNull(GameServices.level().getAnimatedPaletteManager(), payload + " has no palette cycle");
                    }
                    int[] lastColours = colours(level);
                    int bossFrame = -1;
                    java.util.Set<Integer> bossColours = java.util.Set.of();
                    int paletteChanges = 0;
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
                        if (bossFrame < 0 && bossLive()) {
                            bossFrame = frame;
                            bossColours = bossColours(payload, game.currentImage());
                        }
                        int[] now = colours(level);
                        if (changed(lastColours, now, bossColours)) paletteChanges++;
                        lastColours = now;
                    }
                    if (cycles(payload)) assertTrue(paletteChanges > 0, payload + " palette never cycled in 300 frames");
                    else assertEquals(0, paletteChanges, payload + " palette changed though its routine returns first");
                    assertTrue(changedLines > 250, payload + " scroll output barely changed: " + changedLines);
                    report.add(payload + ": " + changedLines + " changing frames, " + paletteChanges + " palette changes"
                            + (bossFrame < 0 ? "" : ", boss live from frame " + bossFrame + " (colours " + bossColours + " left out)"));
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
        System.out.println("SSZ stages: " + report);
        assertEquals(PAYLOADS.length, report.size());
    }
}
