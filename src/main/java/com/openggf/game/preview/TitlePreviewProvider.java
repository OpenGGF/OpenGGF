package com.openggf.game.preview;

import com.openggf.data.Rom;
import com.openggf.game.ModApi;
import java.io.IOException;
import java.util.Optional;

/**
 * CPU-only title art decoded from an explicitly supplied ROM. Implementations must
 * finish ROM reads before returning and must not change gameplay, audio or GL state.
 */
@ModApi
@FunctionalInterface
public interface TitlePreviewProvider {
    /** Unsupported modules produce no preview and need no ROM fallback. */
    TitlePreviewProvider NONE = rom -> Optional.empty();

    /** The caller owns the ROM and may close it immediately after this call. */
    Optional<Sequence> load(Rom rom) throws IOException;

    /**
     * Detached preview with fixed dimensions. Negative ticks behave as zero;
     * equal frame tokens identify identical images within this sequence.
     * Consumers must treat returned pixels as read-only. Menu use is single-threaded.
     */
    @ModApi
    interface Sequence {
        int width();
        int height();
        int stillTick();
        int frameTokenAt(int tick);
        Image imageAt(int tick);
    }

    /** Top-to-bottom RGBA pixels; the supplied array belongs to this image. */
    @ModApi
    record Image(int width, int height, byte[] rgba) {
        public Image {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Preview image dimensions must be positive");
            }
            if (rgba == null || rgba.length != (long) width * height * 4) {
                throw new IllegalArgumentException("RGBA data must be width*height*4 bytes");
            }
        }

        public boolean hasVisiblePixels() {
            for (int i = 3; i < rgba.length; i += 4) {
                if ((rgba[i] & 0xFF) != 0) return true;
            }
            return false;
        }
    }
}
