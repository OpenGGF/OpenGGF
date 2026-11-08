package mica;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import java.io.IOException;
import java.util.Map;

/** Native selector/hotspot inventory from art/mica/endings/s1/s1.yaml. */
final class MicaEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets) throws IOException {
        return Map.of(EndingSpriteSet.Surface.S1_ENDING, s1(assets),
                EndingSpriteSet.Surface.S2_ENDING, s2normal(assets),
                EndingSpriteSet.Surface.S2_SUPER, s2super(assets),
                EndingSpriteSet.Surface.S2_FLOAT, s2float(assets),
                EndingSpriteSet.Surface.S2_PHOTOS, s2photos(assets));
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
    private static EndingSpriteSet s2normal(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(7, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-07.png"), 32, 32)),
            Map.entry(8, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-08.png"), 24, 25)),
            Map.entry(9, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-09.png"), 20, 19)),
            Map.entry(10, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-0a.png"), 20, 19)),
            Map.entry(11, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-0b.png"), 20, 15)),
            Map.entry(12, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-0c.png"), 0, 0)),
            Map.entry(13, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-0d.png"), 0, 0)),
            Map.entry(14, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/normal-0e.png"), 0, 0)))), PresentationImages.backing(assets,"art/mica/endings/s2/normal-07.png",0).rgb());
    }
    private static EndingSpriteSet s2super(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(18, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-12.png"), 12, 8)),
            Map.entry(19, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-13.png"), 8, 8)),
            Map.entry(20, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-14.png"), 8, 8)),
            Map.entry(21, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-15.png"), 8, 8)),
            Map.entry(22, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-16.png"), 8, 8)),
            Map.entry(23, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/super-17.png"), 0, 0)))), PresentationImages.backing(assets,"art/mica/endings/s2/super-12.png",0).rgb());
    }
    private static EndingSpriteSet s2float(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(84, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/float-54.png"), 20, 12)),
            Map.entry(85, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/float-55.png"), 24, 12)),
            Map.entry(86, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/float-56.png"), 28, 12)),
            Map.entry(87, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/float-57.png"), 28, 12)),
            Map.entry(88, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/float-58.png"), 12, 12)))), PresentationImages.backing(assets,"art/mica/endings/s2/float-54.png",0).rgb());
    }
    private static EndingSpriteSet s2photos(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(0, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/photo-1.png"), 0, 0)),
            Map.entry(1, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/photo-2.png"), 0, 0)),
            Map.entry(2, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/photo-3.png"), 0, 0)),
            Map.entry(3, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/endings/s2/photo-4.png"), 0, 0)))), PresentationImages.backing(assets,"art/mica/endings/s2/photo-1.png",0).rgb());
    }
}
