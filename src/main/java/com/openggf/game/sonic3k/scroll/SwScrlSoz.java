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
    private short foregroundY;

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
        // SOZ1/2_ScreenEvent applies the previous shake offset to these copies
        // before background deformation. Sprites consume the same published copy.
        var runtime = com.openggf.game.GameServices.hasRuntime()
                ? com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                        com.openggf.game.GameServices.zoneRuntimeRegistry()).orElse(null) : null;
        if (runtime != null && runtime.actIndex() == actId) {
            cameraX = com.openggf.game.GameServices.camera().getXCopy();
            cameraY = com.openggf.game.GameServices.camera().getYCopy();
        }
        foregroundY = (short) cameraY;
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
                if (events.backgroundRoutine() == 0x24 || events.backgroundRoutine() == 0x28) {
                    bgX = (short) (cameraX + 0x1240 - events.bossX());
                    vscrollFactorBG = (short) (cameraY + 0x488 - events.bossY());
                    for (int line = 0; line < 224; line++) {
                        int worldY = vscrollFactorBG + line;
                        int row = worldY < 0x440 ? 0 : Math.min(12, 1 + ((worldY - 0x440) >> 4));
                        short fg = (short) -cameraX;
                        short bg = (short) -(bgX + events.bossWall().rowOffset(row));
                        buffer[line] = ((fg & 0xFFFF) << 16) | (bg & 0xFFFF);
                        trackOffset(fg, bg);
                    }
                    return;
                } else if (events.backgroundRoutine() >= 0x2C) {
                    bgX = (short) (((short) cameraX >> 1) + 0x200);
                    if (events.savedBackgroundX() != 0) bgX = events.savedBackgroundX();
                } else if (events.backgroundRoutine() == 0x14 || events.backgroundRoutine() == 0x18) {
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
        var arenaState=com.openggf.game.GameServices.hasRuntime()
                ?com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(com.openggf.game.GameServices.zoneRuntimeRegistry()).orElse(null):null;
        if(arenaState!=null && arenaState.actIndex()==0 && arenaState.events().backgroundRoutine()!=0){
            // sub_55DB6 + sub_55E4C: equal foreground/background shimmer phase.
            bgX=(short)(cameraX-0x3CD0);
            vscrollFactorBG=(short)(cameraY-0x900+arenaState.events().sandHeight());
            int phase=(((short)levelFrameCounter>>1)+2*(short)cameraY)&0x3E;
            for(int line=0;line<224;line++){
                int wave=shimmer[((phase>>1)+line)&31];short fg=(short)(-cameraX+wave),bg=(short)(-bgX+wave);
                buffer[line]=((fg&65535)<<16)|(bg&65535);trackOffset(fg,bg);
            }
            return;
        }
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

    @Override public int getBgPeriodWidth() {
        var state = com.openggf.game.GameServices.hasRuntime()
                ? com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                        com.openggf.game.GameServices.zoneRuntimeRegistry()).orElse(null) : null;
        if (state != null && state.actIndex() == 0 && state.events().backgroundRoutine() != 0) {
            // sub_55DB6 streams a continuous pyramid layout through the native
            // 512px plane. Widescreen needs a larger residency window, not a
            // repeated copy of those first 512 pixels. Include column alignment
            // and the per-line shimmer at the right edge.
            int width = com.openggf.game.GameServices.camera().getWidth();
            return Math.max(512, (width + 31) & ~15);
        }
        return 512;
    }

    @Override public short getVscrollFactorFG() { return foregroundY; }

    @Override public int getBgCameraX() {
        var state = com.openggf.game.GameServices.hasRuntime()
                ? com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                        com.openggf.game.GameServices.zoneRuntimeRegistry()).orElse(null) : null;
        // The SOZ1 ROM shimmer table contains 0/+1 offsets. On an aligned
        // camera column, select the preceding column as well: the first pixel
        // samples bgX-1. HScroll keeps the unmodified world coordinate; this
        // accessor selects residency (as it does for the post-boss band below).
        if (state != null && state.actIndex() == 0 && state.events().backgroundRoutine() != 0) return bgX - 1;
        // loc_56676/566A8 draw the post-boss Plane-B source band at fixed X=$200.
        // HScroll still uses bgX; this accessor selects the tilemap source window.
        if (state != null && state.actIndex() == 1 && state.events().backgroundRoutine() >= 0x2C) return 0x200;
        return bgX;
    }
}
