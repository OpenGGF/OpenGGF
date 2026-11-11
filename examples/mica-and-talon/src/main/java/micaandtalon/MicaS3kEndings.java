package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.Map;

/** EndingSpriteSet for MICA's Sonic 3 & Knuckles ending actors. Supplies native pose selectors and local palettes while the host owns the ending sequence and vehicle pieces. */
final class MicaS3kEndings {
    static Map<EndingSpriteSet.Surface, EndingSpriteSet> load(ModAssetRoot assets) throws IOException {
        return Map.ofEntries(
            Map.entry(EndingSpriteSet.Surface.S3K_NORMAL_POSE, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(0, frame(assets, "normal_pose-00.png", 0, 0)),
                Map.entry(1, frame(assets, "normal_pose-01.png", 44, 64)))), PresentationImages.backing(assets, "art/mica/endings/s3k/normal_pose-00.png", 0).rgb())),
            Map.entry(EndingSpriteSet.Surface.S3K_POWERED_POSE, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(0, frame(assets, "super_pose-00.png", 0, 0)),
                Map.entry(1, frame(assets, "super_pose-01.png", 44, 64)),
                Map.entry(2, frame(assets, "super_pose-02.png", 64, 68)))), PresentationImages.backing(assets, "art/mica/endings/s3k/super_pose-00.png", 0).rgb())),
            Map.entry(EndingSpriteSet.Surface.S3K_PASSENGER, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(22, frame(assets, "tiny_passenger-16.png", 12, 4)),
                Map.entry(23, frame(assets, "tiny_passenger-17.png", 4, 4)))), PresentationImages.backing(assets, "art/mica/endings/s3k/tiny_passenger-16.png", 0).rgb())),
            Map.entry(EndingSpriteSet.Surface.S3K_EYECATCH, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(1, frame(assets, "eyecatch_sonic-01.png", 12, 20)),
                Map.entry(2, frame(assets, "eyecatch_sonic-02.png", 12, 20)),
                Map.entry(3, frame(assets, "eyecatch_sonic-03.png", 20, 20)),
                Map.entry(4, frame(assets, "eyecatch_sonic-04.png", 20, 20)))), PresentationImages.backing(assets, "art/mica/endings/s3k/eyecatch_sonic-01.png", 0).rgb())),
            Map.entry(EndingSpriteSet.Surface.S3K_PLAYER, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(200, frame(assets, "rescue_float-c8.png", 29, 12)),
                Map.entry(201, frame(assets, "rescue_float-c9.png", 20, 12)),
                Map.entry(202, frame(assets, "rescue_float-ca.png", 17, 12)),
                Map.entry(203, frame(assets, "rescue_float-cb.png", 21, 12)),
                Map.entry(204, frame(assets, "rescue_float-cc.png", 18, 12)),
                Map.entry(205, frame(assets, "rescue_float-cd.png", 22, 12)),
                Map.entry(206, frame(assets, "rescue_float-ce.png", 17, 12)),
                Map.entry(207, frame(assets, "rescue_float-cf.png", 17, 12)),
                Map.entry(186, frame(assets, "ride-ba.png", 16, 20)))), PresentationImages.backing(assets, "art/mica/endings/s3k/rescue_float-c8.png", 0).rgb())),
            Map.entry(EndingSpriteSet.Surface.S3K_POWERED_PLAYER, new EndingSpriteSet(new StageSpriteSet(Map.ofEntries(
                Map.entry(210, frame(assets, "transform-d2.png", 12, 16)),
                Map.entry(211, frame(assets, "transform-d3.png", 13, 24)),
                Map.entry(212, frame(assets, "transform-d4.png", 16, 24)),
                Map.entry(213, frame(assets, "transform-d5.png", 16, 24)),
                Map.entry(214, frame(assets, "transform-d6.png", 16, 24)),
                Map.entry(200, frame(assets, "gilded-rescue_float-c8.png", 29, 12)),
                Map.entry(201, frame(assets, "gilded-rescue_float-c9.png", 20, 12)),
                Map.entry(202, frame(assets, "gilded-rescue_float-ca.png", 17, 12)),
                Map.entry(203, frame(assets, "gilded-rescue_float-cb.png", 21, 12)),
                Map.entry(204, frame(assets, "gilded-rescue_float-cc.png", 18, 12)),
                Map.entry(205, frame(assets, "gilded-rescue_float-cd.png", 22, 12)),
                Map.entry(206, frame(assets, "gilded-rescue_float-ce.png", 17, 12)),
                Map.entry(207, frame(assets, "gilded-rescue_float-cf.png", 17, 12)))), PresentationImages.backing(assets, "art/mica/endings/s3k/transform-d2.png", 0).rgb())));
    }
    private static StageSpriteSet.Frame frame(ModAssetRoot assets, String file, int x, int y) throws IOException {
        return new StageSpriteSet.Frame(PresentationImages.read(assets, "art/mica/endings/s3k/" + file), x, y);
    }
}
