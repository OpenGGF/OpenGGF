package mica;

import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Locked generated identities and native limb phases; selectors and clocks remain the host's. */
final class OrientedStageArt {
    private record Drawing(String character, StageSpriteSet.Surface surface, int id,
                           String orientation, int hotspotX, int hotspotY) {}
    static Map<StageSpriteSet.Surface, StageSpriteSet> load(ModAssetRoot assets, String character,
            Map<StageSpriteSet.Surface, StageSpriteSet> original) throws IOException {
        var result = new EnumMap<StageSpriteSet.Surface, StageSpriteSet>(StageSpriteSet.Surface.class);
        result.putAll(original);
        for (var drawing : drawings()) {
            if (!drawing.character.equals(character)) continue;
            String game = drawing.surface == StageSpriteSet.Surface.S2_HALF_PIPE ? "s2" : "s3k";
            String file = "art/" + character + "/consistent-stage/" + game + "-"
                    + String.format(java.util.Locale.ROOT, "%02d", drawing.id) + "-" + drawing.orientation + ".png";
            var frame = new StageSpriteSet.Frame(PresentationImages.read(assets, file),
                    drawing.hotspotX, drawing.hotspotY);
            var set = result.get(drawing.surface);
            if (drawing.orientation.equals("n")) {
                var frames = new HashMap<>(set.frames());
                frames.put(drawing.id, frame);
                set = new StageSpriteSet(frames);
            } else {
                set = set.withOrientationFrames(drawing.orientation.contains("h"),
                        drawing.orientation.contains("v"), Map.of(drawing.id, frame));
            }
            result.put(drawing.surface, set);
        }
        return Map.copyOf(result);
    }
    private static Drawing[] drawings() {
        return new Drawing[] {
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 1, "n", 14, 35),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 2, "n", 14, 43),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 3, "n", 15, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 4, "n", 16, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 5, "n", 16, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "n", 15, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "h", 15, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "n", 15, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "h", 15, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "n", 16, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "h", 16, 44),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 9, "n", 15, 37),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 10, "n", 15, 37),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 11, "n", 14, 36),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "n", 13, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "h", 14, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "v", 16, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "hv", 16, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "n", 14, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "h", 15, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "v", 16, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "hv", 15, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "n", 14, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "h", 13, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "v", 16, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "hv", 15, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "n", 15, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "h", 14, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "v", 15, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "hv", 16, 27),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "n", 28, 26),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "h", 17, 36),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "v", 28, 18),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "hv", 26, 18),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "n", 24, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "h", 18, 34),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "v", 24, 22),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "hv", 27, 22),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "n", 21, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "h", 18, 34),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "v", 21, 23),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "hv", 27, 23),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "n", 22, 24),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "h", 18, 33),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "v", 22, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "hv", 27, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "n", 20, 21),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "h", 18, 30),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "v", 20, 25),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "hv", 27, 26),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "n", 23, 23),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "h", 19, 33),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "v", 23, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "hv", 28, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "n", 24, 24),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "h", 20, 33),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "v", 24, 18),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "hv", 29, 18),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "n", 27, 23),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "h", 19, 33),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "v", 27, 21),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "hv", 28, 21),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "n", 24, 15),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "h", 30, 17),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "n", 22, 15),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "h", 27, 15),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "n", 24, 14),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "h", 27, 15),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "n", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "h", 27, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 16, "n", 15, 19),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 17, "n", 15, 19),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 1, "n", 11, 31),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 2, "n", 11, 39),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 3, "n", 12, 40),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 4, "n", 13, 39),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 5, "n", 14, 39),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "n", 15, 39),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "h", 15, 39),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "n", 15, 38),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "h", 15, 38),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "n", 16, 38),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "h", 16, 38),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 9, "n", 13, 36),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 10, "n", 13, 36),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 11, "n", 13, 36),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "n", 11, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "h", 14, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "v", 15, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "hv", 14, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "n", 11, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "h", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "v", 14, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "hv", 15, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "n", 14, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "h", 13, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "v", 14, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "hv", 13, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "n", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "h", 11, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "v", 15, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "hv", 15, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "n", 28, 21),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "h", 12, 28),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "v", 28, 18),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "hv", 21, 18),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "n", 25, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "h", 13, 28),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "v", 25, 21),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "hv", 20, 21),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "n", 21, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "h", 13, 28),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "v", 21, 23),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "hv", 20, 23),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "n", 22, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "h", 13, 29),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "v", 22, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "hv", 20, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "n", 20, 19),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "h", 13, 28),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "v", 20, 26),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "hv", 22, 26),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "n", 23, 18),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "h", 14, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "v", 23, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "hv", 21, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "n", 24, 19),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "h", 15, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "v", 24, 18),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "hv", 22, 18),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "n", 27, 19),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "h", 14, 27),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "v", 27, 21),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "hv", 21, 21),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "n", 24, 15),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "h", 24, 14),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "n", 22, 12),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "h", 24, 14),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "n", 24, 12),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "h", 24, 13),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "n", 24, 14),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "h", 24, 14),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 16, "n", 15, 15),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 17, "n", 15, 15),
        };
    }
}
