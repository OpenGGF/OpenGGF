package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import java.io.IOException;
import java.io.UncheckedIOException;

/** SOZ1 normal desert: sub_55D56 + loc_55DF2 / ApplyFGandBGDeformation.
 * Act 2 normally uses sub_566D2; event-selected sand and arena modes are separate.
 */
public final class SwScrlSoz extends SwScrlS3kDefault {
    private final short[] shimmer = new short[32];
    private final int[] bandHeights = new int[7];
    private final Rom rom;
    private boolean tablesLoaded;
    private int bgX;
    private boolean desert;

    public SwScrlSoz(Rom rom) { this.rom = rom; }

    private void ensureTablesLoaded() {
        if (tablesLoaded) return;
        try {
            loadTables();
            tablesLoaded = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load SOZ desert deformation tables", e);
        }
    }

    private void loadTables() throws IOException {
        // Locked-on AIZ2_SOZ1_LRZ3_FGDeformDelta and word_560DC.
        byte[] wave = rom.readBytes(0x5077E, 64);
        byte[] bands = rom.readBytes(0x560DC, 14);
        for (int i = 0; i < shimmer.length; i++)
            shimmer[i] = (short) (((wave[i * 2] & 255) << 8) | (wave[i * 2 + 1] & 255));
        for (int i = 0; i < bandHeights.length; i++)
            bandHeights[i] = ((bands[i * 2] & 255) << 8) | (bands[i * 2 + 1] & 255);
    }

    public static int desertBackgroundX(int cameraX) { return (short) cameraX >> 4; }
    public static int desertTilePhase(int cameraX) {
        return (((short) cameraX >> 5) - desertBackgroundX(cameraX)) & 31;
    }

    @Override public void init(int actId, int cameraX, int cameraY) {
        desert = actId == 0;
        if (desert) ensureTablesLoaded();
        bgX = desert ? desertBackgroundX(cameraX) : (short) cameraX >> 1;
        vscrollFactorBG = (short) ((short) cameraY >> (desert ? 4 : 1));
    }

    @Override public void update(int[] buffer, int cameraX, int cameraY,
                                 int levelFrameCounter, int actId) {
        desert = actId == 0;
        if (!desert) {
            // sub_566D2 + PlainDeformation: shift camera words before negation.
            resetScrollTracking();
            bgX = (short) cameraX >> 1;
            vscrollFactorBG = (short) ((short) cameraY >> 1);
            var state = com.openggf.game.GameServices.hasRuntime()
                    ? com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                            com.openggf.game.GameServices.zoneRuntimeRegistry()).orElse(null) : null;
            if (state != null && state.actIndex() == 1) {
                var events = state.events();
                if (events.backgroundRoutine() == 0x14 || events.backgroundRoutine() == 0x18) {
                    bgX = (short) (cameraX - 0x1930);
                    vscrollFactorBG = (short) (cameraY + 0x2E0 + events.sandHeight());
                } else if (events.backgroundRoutine() == 0x1C && events.savedBackgroundX() != 0) {
                    bgX = events.savedBackgroundX();
                    vscrollFactorBG = (short) events.savedBackgroundY();
                }
            }
            short fg = (short) -cameraX;
            short bg = (short) -bgX;
            java.util.Arrays.fill(buffer, 0, 224, ((fg & 0xFFFF) << 16) | (bg & 0xFFFF));
            trackOffset(fg, bg);
            return;
        }
        ensureTablesLoaded();
        resetScrollTracking();
        bgX = desertBackgroundX(cameraX);
        vscrollFactorBG = (short) ((short) cameraY >> 4);
        // Preserve fractions through all seven additions, as sub_55D56 does.
        int base = (((short) cameraX) << 16) >> 4;
        int increment = base >> 2;
        int fgPhase = (((short) levelFrameCounter >> 1) + 2 * (short) cameraY) & 0x3E;
        int bgPhase = (((short) levelFrameCounter >> 1) + 2 * vscrollFactorBG) & 0x3E;
        int band = 0;
        int remaining = bandHeights[0] - vscrollFactorBG;
        while (remaining <= 0 && band < bandHeights.length - 1)
            remaining += bandHeights[++band];
        for (int line = 0; line < 224; line++) {
            short fg = (short) (-cameraX + shimmer[((fgPhase >> 1) + line) & 31]);
            short bg = (short) (-((base + increment * band) >> 16)
                    + shimmer[((bgPhase >> 1) + line) & 31]);
            buffer[line] = ((fg & 0xFFFF) << 16) | (bg & 0xFFFF);
            trackOffset(fg, bg);
            if (--remaining == 0 && band < bandHeights.length - 1)
                remaining = bandHeights[++band];
        }
    }

    @Override public int getBgCameraX() { return bgX; }
}
