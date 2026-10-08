package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.LevelManager;

/** SSZ1 loc_578F0/loc_57916: moving Plane B rows followed by the crumble's literal tile write. */
public final class SszLaunchBackground {
    private SszLaunchBackground() { }

    public static void update(LevelManager level, SszZoneRuntimeState state) {
        var launch = state.launch();
        int x = state.backgroundCameraX() & 0xFFF0;
        int y = state.backgroundCameraY() & 0xFF0;
        if (!launch.hasBackgroundPlane()) {
            // Materialize the already resident 512x256 plane at takeover. Preserve physical
            // VRAM coordinates, not the stateless cache's camera-relative origin.
            for (int row = 0; row < 16; row++) copyRow(level, x, (y + row * 16) & 0xFF0, 32);
        } else {
            // A terrain/art upload may invalidate the renderer's cache. The retained words
            // remain authoritative until DrawBGAsYouMove replaces the entering row.
            if (!level.getTilemapManager().isRetainedBackgroundPlaneAuthoritative()) restore(level, state);
            for (int row : enteringRows(launch.backgroundRoundedY(), y)) copyRow(level, x, row, 0x15);
        }
        launch.setBackgroundRoundedY(y);
        int address = launch.tileRowOffset();
        if (address != 0) {
            // VInt_DrawLevel_Draw calls VInt_VRAMWrite twice, +$80 bytes apart. D1=7
            // writes eight LONGWORDS on each row: 16 cells by two rows, not 32 by one.
            int tileY = ((address & 0xFFF) / 0x80) & 31;
            for (int dy = 0; dy < 2; dy++)
                for (int tx = 0; tx < 16; tx++)
                    level.getTilemapManager().setRetainedBackgroundTileDescriptorAtTilemapCell(
                            tx, (tileY + dy) & 31, 0x6061);
            launch.setTileRowOffset(0);
        }
        level.uploadBackgroundTilemap();
        launch.setBackgroundPlane(level.captureBackgroundVdpPlane());
    }

    private static void copyRow(LevelManager level, int x, int y, int count) {
        // Setup_TileRowDraw splits at the plane's right edge, then resumes at X=0.
        int first = Math.min(count, 32 - ((x >> 4) & 31));
        int address = 0xE000 + ((y & 0xF0) << 4) + ((x >> 2) & 0x7C);
        level.copyBackgroundTileRowFromWorldToVdpPlane(x, y, address, first);
        if (first < count) level.copyBackgroundTileRowFromWorldToVdpPlane(
                x + first * 16, y, address & 0xFF80, count - first);
    }

    /** Draw_TileRow's byte-sign branch and optional double update, including wrapped deltas. */
    static int[] enteringRows(int oldY, int newY) {
        if (oldY == newY) return new int[0];
        int delta = (short) (oldY - newY);
        boolean down = (byte) delta < 0;
        int position = (down ? oldY + 0xF0 : newY) & 0xFF0;
        return ((down ? -delta : delta) & 0x30) == 0x10
                ? new int[]{position} : new int[]{position, (position + 0x10) & 0xFF0};
    }

    public static void restore(LevelManager level, SszZoneRuntimeState state) {
        if (state.launch().hasBackgroundPlane()) level.restoreBackgroundVdpPlane(state.launch().backgroundPlane());
    }
}
