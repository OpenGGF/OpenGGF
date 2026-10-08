package com.openggf.graphics;


/** Engine presentation bridge; not part of the compiled-mod API. */
public final class TilemapGpuRendererInternalAccess {
    private TilemapGpuRendererInternalAccess() { }

    public static void setForegroundVerticalScrollSplit(TilemapGpuRenderer renderer,
                                                        ForegroundScrollSplit split) {
        renderer.setForegroundVerticalScrollSplit(split);
    }

    public static void setClipHorizontal(TilemapGpuRenderer renderer, boolean clip) {
        renderer.setClipHorizontal(clip);
    }
}
