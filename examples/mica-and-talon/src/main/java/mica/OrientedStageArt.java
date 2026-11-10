package mica;

import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Original final-orientation drawings; native selectors and clocks remain the host's. */
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
            String file = "art/" + character + "/oriented-stage/" + game + "-"
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
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "h", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "hv", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "n", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "v", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "h", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "hv", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "n", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "v", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "h", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "hv", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "n", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "v", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "h", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "hv", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "n", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "v", 16, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "hv", 20, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "n", 28, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "v", 28, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "hv", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "n", 26, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "v", 26, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "hv", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "n", 25, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "v", 25, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "hv", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "n", 28, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "v", 28, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "hv", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "n", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "v", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "hv", 20, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "n", 24, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "v", 24, 20),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "h", 22, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "hv", 22, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "n", 25, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "v", 25, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "h", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "hv", 20, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "n", 28, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "v", 28, 28),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "h", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "n", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "h", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "n", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "h", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "n", 24, 16),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "h", 24, 17),
        new Drawing("mica", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "n", 24, 17),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "h", 12, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "hv", 12, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "n", 12, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 0, "v", 12, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "h", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "hv", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "n", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 1, "v", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "h", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "hv", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "n", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 2, "v", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "h", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "hv", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "n", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 3, "v", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "h", 24, 31),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "hv", 24, 17),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 4, "v", 20, 17),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "h", 23, 31),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "hv", 23, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 5, "v", 17, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "h", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "hv", 24, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 6, "v", 16, 20),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "h", 24, 23),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "hv", 24, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 7, "v", 16, 25),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "h", 24, 30),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "hv", 24, 26),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 8, "v", 16, 26),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "h", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "hv", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 9, "v", 16, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "h", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "hv", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 10, "v", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "h", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "hv", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 11, "v", 24, 24),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 12, "h", 20, 16),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 13, "h", 20, 16),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 14, "h", 20, 16),
        new Drawing("talon", StageSpriteSet.Surface.S2_HALF_PIPE, 15, "h", 20, 16),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "h", 17, 43),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "h", 17, 43),
        new Drawing("mica", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "h", 16, 43),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 1, "n", 12, 31),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 2, "n", 13, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 3, "n", 12, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 4, "n", 11, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 5, "n", 12, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 6, "h", 20, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 7, "h", 13, 35),
        new Drawing("talon", StageSpriteSet.Surface.S3K_BLUE_SPHERE, 8, "h", 20, 35),
        };
    }
}
