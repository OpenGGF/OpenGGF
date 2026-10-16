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
    public static void cacheLevelPalette(GraphicsManager graphics, PaletteView palette, int line) {
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

    /** Displayed CRAM fade generation and whether a later request still waits for its V-int. */
    public record DisplayedFade(PaletteFadePresentation.Mode mode, int steps, int lineMask, boolean pending) { }

    /** The fade CRAM currently holds (one V-int behind the requested fade while latched). */
    public static PaletteFadePresentation displayedFade(GraphicsManager graphics) {
        return graphics.displayedPaletteFade();
    }

    /** Captures the displayed fade generation for a rewind snapshot of the V-int publication. */
    public static DisplayedFade captureDisplayedFade(GraphicsManager graphics) {
        return graphics == null ? null : graphics.captureDisplayedPaletteFade();
    }

    /**
     * Restores the captured displayed generation and holds it until the next V-int publication,
     * so owners that re-request their fade while a snapshot is restored do not show it early.
     */
    public static void restoreDisplayedFade(GraphicsManager graphics, DisplayedFade state) {
        if (graphics != null && state != null) {
            graphics.restoreDisplayedPaletteFade(state);
        }
    }

    /** Uploads anything pending and returns to immediate uploads. */
    public static void release(GraphicsManager graphics) {
        if (graphics != null && graphics.isPaletteUploadLatched()) {
            graphics.setPaletteUploadLatched(false);
        }
    }
}
