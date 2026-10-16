package com.openggf.level;

/**
 * Engine-side entry for a picture width change while a level is loaded (a game resumed from the
 * master title at a new aspect). Kept out of the {@code @ModApi} {@link LevelManager} and
 * {@link ParallaxManager} surfaces: creators never resize the display.
 */
public final class LevelDisplayWidth {
    private LevelDisplayWidth() { }

    /** Re-reads the resolved width into the level, its parallax tables and the background buffer. */
    public static void refresh(LevelManager level) {
        level.refreshDisplayDimensions();
    }

    /** Per-column VScroll entries the parallax currently holds (one per 16 pixels). */
    public static int parallaxColumns(ParallaxManager parallax) {
        return parallax.vScrollColumnCount();
    }
}
