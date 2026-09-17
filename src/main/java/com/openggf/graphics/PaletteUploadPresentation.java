package com.openggf.graphics;

/**
 * Engine-internal bridge for the V-int CRAM upload model. Games that publish their sprite table
 * and scroll buffers at V-int (S3K VInt_8: {@code dma68kToVDP Normal_palette} beside the SAT and
 * H-scroll uploads) latch palette line uploads between publications, so a palette written by
 * frame N's logic is displayed on the same frame as frame N's sprites instead of one frame early.
 */
public final class PaletteUploadPresentation {
    private PaletteUploadPresentation() { }

    /**
     * Level palette pipeline write (zone palette writers, cycles, ownership resolution). Other
     * scenes (special stages, menus, loads) keep calling {@link GraphicsManager#cachePaletteTexture}
     * directly and upload immediately.
     */
    public static void cacheLevelPalette(GraphicsManager graphics, com.openggf.level.Palette palette, int line) {
        if (graphics != null) {
            graphics.cacheLatchablePaletteTexture(palette, line);
        }
    }

    /** Uploads everything written since the previous publication and keeps latching. */
    public static void publishAndLatch(GraphicsManager graphics) {
        if (graphics != null) {
            graphics.setPaletteUploadLatched(true);
        }
    }

    /** Uploads anything pending and returns to immediate uploads. */
    public static void release(GraphicsManager graphics) {
        if (graphics != null && graphics.isPaletteUploadLatched()) {
            graphics.setPaletteUploadLatched(false);
        }
    }
}
