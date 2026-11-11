package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.Map;

/** EndingSpriteSet for TALON's ending actors, search photos and Tornado pilot crops. Replaces character-owned pieces while retaining native vehicle art, selectors and sequence timing. */
final class TalonEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets) throws IOException {
        return Map.of(
            EndingSpriteSet.Surface.S2_ENDING, s2(assets),
            EndingSpriteSet.Surface.S2_PHOTOS, photos(assets),
            EndingSpriteSet.Surface.S2_TORNADO_PILOT, pilot(assets),
            EndingSpriteSet.Surface.S3K_TORNADO_PILOT, new EndingSpriteSet(new StageSpriteSet(Map.of(
                12, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-10.png"),12,8))),
                PresentationImages.backing(assets,"art/talon/endings/pilot-10.png",0).rgb()),
            EndingSpriteSet.Surface.S3K_NORMAL_POSE, s3k(assets),
            EndingSpriteSet.Surface.S3K_POWERED_POSE, s3k(assets));
    }
    private static EndingSpriteSet s2(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(9, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-09.png"), 5, 19)),
            Map.entry(10, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-0a.png"), 8, 19)),
            Map.entry(11, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-0b.png"), 9, 15)),
            Map.entry(15, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-0f.png"), 0, 0)),
            Map.entry(16, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-10.png"), 0, -8)),
            Map.entry(17, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-11.png"), 0, 0)),
            Map.entry(24, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-18.png"), -6, 32)),
            Map.entry(25, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s2-19.png"), -4, 25)))), PresentationImages.backing(assets,"art/talon/endings/s2-09.png",0).rgb());
    }
    private static EndingSpriteSet photos(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(1, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/photos-01.png"), 0, 0)),
            Map.entry(2, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/photos-02.png"), 0, 0)),
            Map.entry(3, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/photos-03.png"), 0, 0)))), PresentationImages.backing(assets,"art/talon/endings/photos-01.png",0).rgb());
    }
    private static EndingSpriteSet pilot(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(1, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-01.png"), 48, 8)),
            Map.entry(2, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-02.png"), 48, 8)),
            Map.entry(3, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-03.png"), 48, 8)),
            Map.entry(4, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-04.png"), 48, 8)),
            Map.entry(16, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/pilot-10.png"), 48, 8)))), PresentationImages.backing(assets,"art/talon/endings/pilot-01.png",0).rgb());
    }
    private static EndingSpriteSet s3k(ModAssetRoot assets) throws IOException {
        return new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
            Map.entry(3, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s3k-03.png"), 20, 24)),
            Map.entry(4, new StageSpriteSet.Frame(PresentationImages.read(assets,"art/talon/endings/s3k-04.png"), 48, 48)))), PresentationImages.backing(assets,"art/talon/endings/s3k-03.png",0).rgb());
    }
}
