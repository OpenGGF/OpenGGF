package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import java.io.IOException;
import java.io.UncheckedIOException;

/** LRZ3 sub_59D82/59DA2/59DBC and sub_59DDE: camera, lava columns and heat shimmer. */
public final class SwScrlLrz3 extends SwScrlS3kDefault {
    private final Rom rom;
    private short[] shimmer;
    private short[] lavaColumns;
    private boolean columnMode;
    private int backgroundX;
    private short foregroundY;

    public SwScrlLrz3(Rom rom) { this.rom = rom; }

    private void loadShimmer() {
        if (shimmer != null) return;
        try {
            byte[] bytes = rom.readBytes(0x5077E, 64); // AIZ2_SOZ1_LRZ3_FGDeformDelta, repeated 32-word period.
            shimmer = new short[32];
            for (int i = 0; i < 32; i++) shimmer[i] = (short) ((bytes[2*i] & 255) << 8 | bytes[2*i+1] & 255);
        } catch (IOException failure) {
            throw new UncheckedIOException("LRZ3 heat deformation table", failure);
        }
    }

    private LrzZoneRuntimeState state() {
        return GameServices.hasRuntime()
                ? S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElse(null) : null;
    }

    @Override public void update(int[] buffer, int cameraX, int cameraY, int levelFrameCounter, int act) {
        var state = state();
        if (state != null) {
            cameraX = GameServices.camera().getXCopy();
            cameraY = GameServices.camera().getYCopy();
        }
        render(buffer, cameraX, cameraY, levelFrameCounter, state,
                GameServices.hasRuntime() ? GameServices.camera().getWidth() : 320,
                GameServices.hasRuntime() && GameServices.gameState().isEndOfLevelFlag());
    }

    /** Pure rendering boundary also used by source-table tests. Event stages have a separate owner. */
    void render(int[] buffer, int cameraX, int cameraY, int frame, LrzZoneRuntimeState state,
                int width, boolean endOfLevel) {
        loadShimmer(); resetScrollTracking();
        foregroundY = (short) cameraY;
        int stage = state == null ? 0 : state.backgroundRoutine();
        boolean distant = stage == 0 || stage == 8;
        backgroundX = distant ? (short) cameraX >> 4 : (short) (cameraX - 0x700);
        int bgY = distant ? ((short) cameraY >> 4) + 0x10 : (short) (cameraY - 0x500);
        if (state != null) {
            int phase = distant ? (short) backgroundX >> 1 : state.animationPhaseX1();
            // loc_59C72 retains the old camera while the top-down refill is in flight.
            if (distant && state.savedBackgroundCameraX() != 0) {
                backgroundX = (short) state.savedBackgroundCameraX();
                bgY = (short) state.savedBackgroundCameraY();
            }
            state.publishDeformationWords(backgroundX, bgY, state.animationPhaseX0(), phase);
        }
        vscrollFactorBG = (short) bgY;
        boolean heat = state != null && state.bossAct().foregroundRoutine() >= 8
                && !state.bossAct().capsuleOpened() && !endOfLevel;
        int fgPhase = ((cameraY + 2 * frame) & 0x3E) >> 1;
        int bgPhase = ((bgY + frame) & 0x3E) >> 1;
        for (int line = 0; line < 224; line++) {
            short fg = (short) (-cameraX + (heat ? shimmer[(fgPhase + line) & 31] : 0));
            short bg = (short) (-backgroundX + (heat ? shimmer[(bgPhase + line) & 31] : 0));
            buffer[line] = (fg & 65535) << 16 | bg & 65535;
            trackOffset(fg, bg);
        }
        columnMode = stage == 12;
        if (columnMode) {
            int count = (width + 15) / 16;
            if (lavaColumns == null || lavaColumns.length != count) lavaColumns = new short[count];
            // sub_59DBC samples $113 + 8*n; API takes deltas from the common BG Y.
            // The ROM samples from its native left edge. Centre projection shifts
            // the world under the viewport, so shift this two-pixels-per-byte
            // slope lookup too; otherwise the drawn pool disagrees with its solid.
            int inset = state.centerNativeArenaCamera()
                    ? com.openggf.camera.NativeViewportFraming.inset(width) : 0;
            for (int i = 0; i < count; i++) lavaColumns[i] = (short) (
                    state.bossAct().lavaHeight(19 + 8*i - inset / 2) - 0x30);
        }
    }

    @Override public short[] getPerColumnVScrollBG() { return columnMode ? lavaColumns : null; }
    @Override public short getVscrollFactorFG() { return foregroundY; }
    @Override public int getBgCameraX() { return backgroundX - 1; }
    @Override public int getBgPeriodWidth() {
        int width = GameServices.hasRuntime() ? GameServices.camera().getWidth() : 320;
        return Math.max(512, (width + 31) & ~15);
    }
}
