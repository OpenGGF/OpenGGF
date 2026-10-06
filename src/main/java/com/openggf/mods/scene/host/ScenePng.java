package com.openggf.mods.scene.host;

import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.mods.scene.SceneImage;
import java.io.IOException;

/** PNG decoding for scene images through the engine's pure-Java codec. Engine-internal. */
final class ScenePng {
    private ScenePng() {
    }

    static SceneImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty PNG data");
        }
        try {
            PixelImage img = PngCodec.decode(bytes);
            return new SceneImage(img.getWidth(), img.getHeight(), img.pixels());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unreadable PNG: " + e.getMessage(), e);
        }
    }
}
