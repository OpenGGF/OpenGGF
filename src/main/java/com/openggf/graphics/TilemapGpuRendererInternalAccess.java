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

    /** Republishes a row span of the resident foreground tilemap array. */
    public static void setForegroundTilemapRows(TilemapGpuRenderer renderer, byte[] data,
            int widthTiles, int heightTiles, int firstRow, int rowCount) {
        renderer.setForegroundTilemapRows(data, widthTiles, heightTiles, firstRow, rowCount);
    }

    public static void setClipHorizontal(TilemapGpuRenderer renderer, boolean clip) {
        renderer.setClipHorizontal(clip);
    }
}
