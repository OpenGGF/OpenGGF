package com.openggf.level;

import java.util.Objects;

/** Generic ROM-style retained Plane-B strip redraw scheduler (fixed 64x32 VDP cells). */
public final class StagedBackgroundPlaneRedrawController {
    public enum Direction { TOP_DOWN, BOTTOM_UP, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

    public interface Surface {
        void copyRow(int sourceWorldX, int sourceWorldY, int destPlaneY);
        void copyColumn(int sourceWorldX, int sourceWorldY, int destPlaneX);
        default void finishBatch() { }
    }

    private final Surface surface;

    public StagedBackgroundPlaneRedrawController(Surface surface) {
        this.surface = Objects.requireNonNull(surface, "surface");
    }

    /** Applies one frame: one 16px row or two 16px columns, exactly as S3K draw helpers do. */
    public int step(Direction direction, int completedFrames, int sourceOffsetX, int effectiveBackgroundY) {
        if (completedFrames < 0 || completedFrames >= 16) throw new IllegalArgumentException("completedFrames");
        return switch (direction) {
            case TOP_DOWN -> {
                int p = ((effectiveBackgroundY & 0xFF0) + completedFrames * 0x10) & 0xFF0;
                surface.copyRow(sourceOffsetX, p, p & 0xFF);
                surface.finishBatch(); yield p;
            }
            case BOTTOM_UP -> {
                int p = ((effectiveBackgroundY & 0xFF0) + 0xF0 - completedFrames * 0x10) & 0xFF0;
                surface.copyRow(sourceOffsetX, p, p & 0xFF);
                surface.finishBatch(); yield p;
            }
            case LEFT_TO_RIGHT -> {
                int p = completedFrames * 0x20;
                if (p >= sourceOffsetX && p <= sourceOffsetX + 0x1F0) {
                    surface.copyColumn(p, effectiveBackgroundY, p);
                    surface.copyColumn(p + 0x10, effectiveBackgroundY, p + 0x10);
                }
                surface.finishBatch(); yield p;
            }
            case RIGHT_TO_LEFT -> {
                int p = 0x3F0 - completedFrames * 0x20;
                if (p >= sourceOffsetX && p <= sourceOffsetX + 0x1F0) {
                    surface.copyColumn(p, effectiveBackgroundY, p);
                    surface.copyColumn(p - 0x10, effectiveBackgroundY, p - 0x10);
                }
                surface.finishBatch(); yield p;
            }
        };
    }

    /** Replays the retained nametable prefix after rewind/full-cache reconstruction. */
    public void replay(Direction direction, int completedFrames, int sourceOffsetX, int effectiveBackgroundY) {
        for (int i = 0; i < completedFrames; i++) step(direction, i, sourceOffsetX, effectiveBackgroundY);
    }
}
