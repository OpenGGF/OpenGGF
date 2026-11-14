package micaandtalon;

import com.openggf.game.CharacterPresentation.Host;
import com.openggf.game.CharacterPresentation.TitleMascot;
import com.openggf.game.CharacterPresentation.TitlePart;
import com.openggf.game.CharacterPresentation.TitlePlaneMask;
import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CharacterPresentation.TitleMascot for both characters; binds native title selectors, origins,
 * plane masks and palettes without changing title timing.
 */
final class TitlePresentation {
    static Map<Host, TitleMascot> load(ModAssetRoot assets, String character) throws IOException {
        String root = "art/" + character + "/titles/";
        int[] rgb = PresentationImages.backing(assets, root + "s1-0.png", 0).rgb();
        var result = new EnumMap<Host, TitleMascot>(Host.class);
        for (var host : Host.values()) {
            var table =
                    FrameTable.read(
                            assets, root + host.name().toLowerCase(Locale.ROOT) + "-frames.txt");
            var parts = new EnumMap<TitlePart, StageSpriteSet>(TitlePart.class);
            table.banks().forEach((name, bank) -> parts.put(TitlePart.valueOf(name), bank));
            // Keep each creator in its native role. The API's optional S2 slot exchange stays
            // disabled.
            int[] hostRgb =
                    host == Host.S3K && character.equals("mica")
                            ? PresentationImages.backing(assets, root + "s3k-plane-13-left.png", 0)
                                    .rgb()
                            : rgb;
            result.put(
                    host,
                    new TitleMascot(
                            parts,
                            host == Host.S3K ? masks(assets, root) : Map.of(),
                            hostRgb,
                            false));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, TitlePlaneMask> masks(ModAssetRoot assets, String root)
            throws IOException {
        var masks = new LinkedHashMap<Integer, TitlePlaneMask>();
        for (int frame = 1; frame <= 13; frame++) {
            // Only the final native plane has an explicit replacement-index clear mask.
            byte[] clear =
                    frame == 13
                            ? assets.readBounded(root + "s3k-plane-13.clear", 320 * 224)
                            : new byte[320 * 224];
            masks.put(
                    frame,
                    new TitlePlaneMask(
                            assets.readBounded(root + "s3k-plane-" + frame + ".mask", 320 * 224),
                            clear));
        }
        return Map.copyOf(masks);
    }
}
