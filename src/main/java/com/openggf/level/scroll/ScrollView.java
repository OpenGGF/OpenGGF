package com.openggf.level.scroll;

/** A director camera and its complete raster, independent of native-size composition tiles. */
public record ScrollView(int cameraX, int cameraY, int width, int height) {
    public ScrollView {
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException(
                    "Directed scroll raster must fit within 4096 pixels per side");
        }
    }
}
