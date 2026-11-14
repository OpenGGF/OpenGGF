package micaandtalon;

import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * StageSpriteSet final-orientation drawings for Blue Sphere and the half-pipe. Binds approved
 * images to native selector and flip combinations while preserving the host's motion, clocks and
 * lighting conventions.
 */
final class OrientedStageArt {
    static Map<StageSpriteSet.Surface, StageSpriteSet> load(
            ModAssetRoot assets,
            String character,
            Map<StageSpriteSet.Surface, StageSpriteSet> original)
            throws IOException {
        var result =
                new EnumMap<StageSpriteSet.Surface, StageSpriteSet>(StageSpriteSet.Surface.class);
        result.putAll(original);
        var table = FrameTable.read(assets, "art/" + character + "/special-stage/frames.txt");
        for (var entry : table.banks().entrySet()) {
            String[] bank = entry.getKey().split("/");
            var surface = StageSpriteSet.Surface.valueOf(bank[0]);
            var set = result.get(surface);
            var drawings = entry.getValue().frames();
            if (bank[1].equals("n")) {
                var frames = new HashMap<>(set.frames());
                frames.putAll(drawings);
                set = new StageSpriteSet(frames);
            } else {
                // These drawings already face the native flip direction; the host keeps its
                // selector and clock.
                set =
                        set.withOrientationFrames(
                                bank[1].contains("h"), bank[1].contains("v"), drawings);
            }
            result.put(surface, set);
        }
        return Map.copyOf(result);
    }
}
