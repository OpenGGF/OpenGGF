package micaandtalon;

import com.openggf.game.EndingSpriteSet;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.Map;

/**
 * EndingSpriteSet for MICA's Sonic 3 & Knuckles ending actors. Supplies native pose selectors and
 * local palettes while the host owns the ending sequence and vehicle pieces.
 */
final class MicaS3kEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets)
            throws IOException {
        return FrameTable.read(assets, "art/mica/endings/s3k/frames.txt").endings();
    }
}
