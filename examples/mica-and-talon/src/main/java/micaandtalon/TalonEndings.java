package micaandtalon;

import com.openggf.game.EndingSpriteSet;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.Map;

/**
 * EndingSpriteSet for TALON's ending actors, search photos and Tornado pilot crops. Replaces
 * character-owned pieces while retaining native vehicle art, selectors and sequence timing.
 */
final class TalonEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets)
            throws IOException {
        var result = new java.util.EnumMap<EndingSpriteSet.Surface, EndingSpriteSet>(EndingSpriteSet.Surface.class);
        result.putAll(FrameTable.read(assets, "art/talon/endings/frames.txt").endings());
        result.putAll(FrameTable.read(assets, "art/talon/endings/s1/frames.txt").endings());
        var companion = FrameTable.read(assets, "art/mica/endings/pilot/frames.txt").endings();
        result.put(EndingSpriteSet.Surface.S2_SOLO_TORNADO_PILOT,
                companion.get(EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        result.put(EndingSpriteSet.Surface.S3K_SOLO_TORNADO_PILOT,
                companion.get(EndingSpriteSet.Surface.S3K_TORNADO_PILOT));
        result.put(EndingSpriteSet.Surface.S3K_SOLO_MINI_TORNADO_PILOT,
                companion.get(EndingSpriteSet.Surface.S3K_MINI_TORNADO_PILOT));
        // MICA takes the Sonic seat beside TALON on the final S3 graphics card.
        result.put(EndingSpriteSet.Surface.S3K_SOLO_EYECATCH,
                FrameTable.read(assets, "art/mica/endings/s3k/frames.txt").endings()
                        .get(EndingSpriteSet.Surface.S3K_EYECATCH));
        result.putAll(FrameTable.read(assets, "art/talon/endings/final-pilot/frames.txt").endings());
        result.put(EndingSpriteSet.Surface.S2_SOLO_FINAL_TORNADO_PILOT,
                FrameTable.read(assets, "art/mica/endings/final-pilot/frames.txt").endings()
                        .get(EndingSpriteSet.Surface.S2_FINAL_TORNADO_PILOT));
        return Map.copyOf(result);
    }
}
