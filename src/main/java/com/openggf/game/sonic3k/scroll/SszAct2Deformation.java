package com.openggf.game.sonic3k.scroll;

import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;

/** SSZ2 encounter sub_58D3E / sub_58FBC, before the excluded ending redraw. */
final class SszAct2Deformation {
    private SszAct2Deformation() { }

    /** loc_58F52: descending four-line ramp, using the old signed16.16 accumulator. */
    static void islandParameters(SszZoneRuntimeState state) {
        int value = state.cloudDrift() & 0xFFFF0000;
        state.setCloudDrift(state.cloudDrift() - 0x10000);
        int step = value >> 5;
        int destination = 0x114;
        for (int group = 0; group < 32; group++) {
            for (int line = 0; line < 4; line++) put(state, destination -= 2, value >> 16);
            value -= step;
        }
        put(state, 0x10, -word(state, 0x14)); put(state, 8, -word(state, 0x14));
        put(state, 0xC, -word(state, 0x1C)); put(state, 0x12, -word(state, 0x24));
        put(state, 6, -word(state, 0x2C)); put(state, 0xA, -word(state, 0x34));
        put(state, 0xE, -word(state, 0x3C));
    }

    /** loc_5906A / ApplyFGDeformation: a negative band advances one table word per scanline. */
    static void composeIsland(SszZoneRuntimeState state, int[] output, int cameraY, int[] bands) {
        int band = 0, source = 4, skip = cameraY & 0xFFFF;
        while (skip >= (bands[band] & 0x7FFF)) {
            int height = bands[band] & 0x7FFF;
            skip -= height;
            source += (bands[band] & 0x8000) == 0 ? 2 : height * 2;
            band++;
        }
        if ((bands[band] & 0x8000) != 0) source += skip * 2;
        int remaining = (bands[band] & 0x7FFF) - skip;
        int bg = -state.backgroundCameraX() & 0xFFFF;
        for (int line = 0; line < 224; line++) {
            output[line] = ((-word(state, source) & 0xFFFF) << 16) | bg;
            if ((bands[band] & 0x8000) != 0) source += 2;
            if (--remaining == 0) {
                if ((bands[band] & 0x8000) == 0) source += 2;
                remaining = bands[++band] & 0x7FFF;
            }
        }
    }

    static void parameters(SszZoneRuntimeState state, int cameraX, int cameraY) {
        int drift = state.cloudDrift();
        state.setCloudDrift(drift + 0x1000);
        put(state, 4, 0);
        int step = (cameraX << 16) >> 5;
        int value = (step >> 1) + drift;
        scatter(state, value >> 16, 0, 4, 8, 0x10, 0x36, 0x3A, 0x3E, 0x46);
        step += drift;
        value += step;
        scatter(state, value >> 16, 6, 0xA, 0xE, 0x14, 0x3C, 0x40, 0x44);
        value += step;
        scatter(state, value >> 16, 2, 0xC, 0x16, 0x34, 0x38, 0x42);
        value += step;
        scatter(state, value >> 16, 0x12);
        for (int offset = 0x1E; offset < 0x30; offset += 2) {
            put(state, offset, value >> 16);
            value += step;
        }
        if (state.foregroundRoutine() <= 4) {
            for (int offset = 0x2A; offset < 0x3C; offset += 2) put(state, offset, cameraX);
        } else {
            // loc_58E3A's ordered in-place remapping, based at HScroll_table+$004.
            put(state, 0x30, word(state, 0x2E));
            int middle = word(state, 0x2C);
            put(state, 0x2E, middle); put(state, 0x32, middle);
            put(state, 0x2C, word(state, 0x2A)); put(state, 0x2A, word(state, 0x28));
            put(state, 0x34, word(state, 0x26)); put(state, 0x36, word(state, 0x22));
            put(state, 0x38, word(state, 0x1E));
        }
        int bgY = cameraY - 0x320 - state.cloudOscillator();
        if (state.specialVIntRoutine() != 4 && state.specialVIntRoutine() != 8) bgY += 8;
        state.setBackgroundCameraY(bgY); state.setBackgroundCameraX(0x5E);
        int swing = (short) state.eventsBgWord(0);
        ramp(state, 0xD0, 64, 128, swing);
        ramp(state, 0x160, 8, 8, swing);
        // loc_58F46 copies forward in the SAME table. The final two reads
        // overlap the first two destination words; snapshotting the source
        // into a separate array before copying would change those edge columns.
        int source = 0x140 + (((0x5E + 0x10) >> 3) & 0xFFFE);
        for (int i = 0; i < 20; i++) put(state, 0x170 + i * 2, word(state, source + i * 2) + bgY + 8);
    }

    private static void scatter(SszZoneRuntimeState state, int value, int... offsets) {
        for (int offset : offsets) put(state, 6 + offset, value);
    }
    private static void ramp(SszZoneRuntimeState state, int centre, int count, int divisor, int swing) {
        int increment = (Math.abs(swing) << 16) / divisor;
        int accumulator = 0;
        for (int i = 0; i < count; i++) {
            accumulator += increment;
            int value = accumulator >> 16;
            if (swing < 0) value = -value;
            put(state, centre - (i + 1) * 2, value);
            put(state, centre + i * 2, -value);
        }
    }

    /** ApplyFGDeformation's constant-height bands plus loc_59018/59036's BG window. */
    static void compose(SszZoneRuntimeState state, int[] output, int cameraY, int[] heights) {
        int band = 0, skip = Math.max(0, (short) cameraY);
        while (band < heights.length - 1 && skip >= heights[band]) skip -= heights[band++];
        int remaining = heights[band] - skip;
        for (int line = 0; line < 224; line++) {
            if (remaining-- == 0) { remaining = heights[++band] - 1; }
            output[line] = ((-word(state, 4 + band * 2) & 0xFFFF) << 16) | 0xFFA2;
        }
        int bgY = (short) state.backgroundCameraY();
        int first = Math.max(0, 0x100 - bgY);
        int end = Math.min(224, 0x180 - bgY);
        int source = 0x50 + Math.max(0, bgY - 0x100) * 2;
        for (int line = first; line < end; line++) {
            int bg = word(state, source) - 0x5E;
            output[line] = (output[line] & 0xFFFF0000) | (bg & 0xFFFF);
            source += 2;
        }
    }

    static short[] columns(SszZoneRuntimeState state, int width) {
        if (state.specialVIntRoutine() == 0) return null;
        short[] columns = new short[(width + 15) / 16];
        // ROM supplies twenty columns. Beyond that native window, continue the
        // final column's vertical offset, rather than reading unrelated work RAM.
        // Native columns and the horizontal wave remain byte-for-byte unchanged.
        for (int i = 0; i < columns.length; i++) columns[i] = (short)
                (word(state, 0x170 + Math.min(i, 19) * 2) - state.backgroundCameraY());
        return columns;
    }
    private static int word(SszZoneRuntimeState state, int offset) { return state.act2ScrollWord(offset); }
    private static void put(SszZoneRuntimeState state, int offset, int value) { state.setAct2ScrollWord(offset, value); }
}
