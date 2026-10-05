package com.openggf.level.render;

import java.util.List;

/**
 * A stock game's zone pictures for presentation (mod scenes' {@code SceneRomArt.zoneBackdrop}
 * and {@code levelOverview}, which convert these values to the API types). Engine-internal.
 * Implementations read only the ROM, on the calling thread, and never touch the running level,
 * graphics, session or settings. Results are not cached here.
 */
public interface ZonePictureSource {
    /** The zone's background with its parallax bands, or null when unsupported. */
    Backdrop backdrop(int zone, int act);

    /**
     * The act's foreground over its background, cropped to the playable area and shrunk so it
     * is at most {@code maxHeight} pixels tall and 4096 wide, or null when unsupported.
     */
    Picture overview(int zone, int act, int maxHeight);

    /** Opaque {@code 0xAARRGGBB} pixels, row by row from the top. */
    record Picture(int width, int height, int[] argb) {
    }

    /**
     * Picture rows {@code top .. top + height - 1} scrolling at {@code speed} times the camera
     * plus {@code drift} pixels per frame.
     */
    record Band(int top, int height, double speed, double drift) {
    }

    /** A background picture whose rows repeat at its width, with bands covering every row. */
    record Backdrop(Picture picture, List<Band> bands) {
    }
}
