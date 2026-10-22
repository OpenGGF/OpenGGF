package com.openggf.graphics;


/** Engine presentation bridge; not part of the compiled-mod API. */
public final class TilemapGpuRendererInternalAccess {
    private TilemapGpuRendererInternalAccess() { }

    public static void withCinematicBackground(TilemapGpuRenderer renderer,
            CinematicBackgroundResidency residency, Runnable draw) {
        renderer.withCinematicBackground(residency, draw);
    }

    public static void setCinematicColumnPhase(TilemapGpuRenderer renderer, int phase) {
        renderer.setCinematicColumnPhase(phase);
    }

    public static void setForegroundVerticalScrollSplit(TilemapGpuRenderer renderer,
                                                        ForegroundScrollSplit split) {
        renderer.setForegroundVerticalScrollSplit(split);
    }

    public static void setClipHorizontal(TilemapGpuRenderer renderer, boolean clip) {
        renderer.setClipHorizontal(clip);
    }
}
