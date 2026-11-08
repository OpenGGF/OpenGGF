package mica;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import java.io.IOException;
import java.util.Map;

/** Native selector/hotspot inventory from art/mica/endings/s1/s1.yaml. */
final class MicaEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets) throws IOException {
        return Map.of(EndingSpriteSet.Surface.S1_ENDING, s1(assets));
    }
    private static EndingSpriteSet s1(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(0, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-00.png"), 16, 20)),
            Map.entry(1, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-01.png"), 16, 20)),
            Map.entry(2, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-02.png"), 16, 20)),
            Map.entry(3, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-03.png"), 16, 20)),
            Map.entry(4, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-04.png"), 16, 20)),
            Map.entry(5, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-05.png"), 16, 20)),
            Map.entry(6, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-06.png"), 16, 78)),
            Map.entry(7, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s1/ending-07.png"), 56, 128)))), PresentationImages.backing(assets,"art/mica/endings/s1/ending-00.png",0).rgb());
    }
}
