package com.openggf.graphics;

/**
 * Engine presentation bridge (not part of the compiled-mod API): the owner of a
 * foreground tilemap image that is still being drawn column by column.
 *
 * <p>{@link TilemapGpuRenderer} calls it before every draw that samples the foreground
 * texture, with the world X range that draw can sample, so the owner draws any
 * part of that range that is not drawn yet before the texture is uploaded. Every
 * foreground sample therefore comes from a drawn region, whatever the camera,
 * per-line scroll, picture width or caller.
 */
public interface ForegroundCoverage {

    /**
     * Draws every undrawn part of {@code data} between world X {@code firstWorldX} and
     * {@code lastWorldX} (inclusive; either may lie outside the image, which repeats
     * horizontally as the shader samples it). A call for another array does nothing.
     */
    void coverColumns(byte[] data, int firstWorldX, int lastWorldX);

    /** Draws every undrawn part of {@code data}; a call for another array does nothing. */
    void coverAll(byte[] data);
}
