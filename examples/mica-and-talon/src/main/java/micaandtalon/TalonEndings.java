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
        return FrameTable.read(assets, "art/talon/endings/frames.txt").endings();
    }
}
