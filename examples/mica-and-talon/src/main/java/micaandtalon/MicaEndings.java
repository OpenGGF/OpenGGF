package micaandtalon;

import com.openggf.game.EndingSpriteSet;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * EndingSpriteSet for MICA's Sonic 1 and Sonic 2 ending actors. Supplies indexed poses, native
 * selector origins and backing palettes while the host owns sequence timing.
 */
final class MicaEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets)
            throws IOException {
        var result =
                new EnumMap<EndingSpriteSet.Surface, EndingSpriteSet>(
                        EndingSpriteSet.Surface.class);
        result.putAll(FrameTable.read(assets, "art/mica/endings/s1/frames.txt").endings());
        result.putAll(FrameTable.read(assets, "art/mica/endings/s2/frames.txt").endings());
        return Map.copyOf(result);
    }
}
