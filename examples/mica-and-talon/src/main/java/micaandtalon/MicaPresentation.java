package micaandtalon;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.EndingSpriteSet;
import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CharacterPresentation for MICA's HUD identity, lives, signposts and native object/special-stage
 * banks. Composes title, screen, reel and ending contributions without changing host selectors.
 */
final class MicaPresentation {
    private static final String ROOT = "art/mica/hud-and-screens/";

    static CharacterPresentation load(ModAssetRoot assets) throws IOException {
        var art = new LinkedHashMap<Host, Art>();
        for (var host : Host.values()) art.put(host, art(assets, host));
        var letters = Map.of('M', image(assets, "s1-name-M"), 'A', image(assets, "s1-name-A"));
        var glyphs = Map.of(new Font(Host.S1, Style.HUD), letters);
        var stages = OrientedStageArt.load(assets, "mica", stages(assets));
        stages = CompetitionPresentation.merge(stages, CompetitionPresentation.mica(assets));
        return new CharacterPresentation("MICA", "MICA", art, glyphs, stages, endings(assets))
                .withReelFaces(ReelPresentation.load(assets, "mica"))
                .withCompetitionPortraits(CompetitionMenuPresentation.load(assets, "mica"))
                .withTitleMascots(TitlePresentation.load(assets, "mica"));
    }

    private static Art art(ModAssetRoot assets, Host host) throws IOException {
        String prefix = host.name().toLowerCase(Locale.ROOT);
        var face = pose(assets, prefix + "-sign");
        var alternate = host == Host.S2 ? pose(assets, prefix + "-sign-alternate") : null;
        var sign =
                new Sign(
                        face,
                        alternate,
                        pose(assets, prefix + "-spin1"),
                        null,
                        pose(assets, prefix + "-spin3"));
        var screens = MicaScreenFrames.load(assets, host);
        return new Art(
                image(assets, prefix + "-hud"),
                image(assets, prefix + "-monitor"),
                face.face(),
                face.face(),
                sign,
                screens);
    }

    /** S1/S2 and S3K ending sets share one map keyed by surface. */
    private static Map<EndingSpriteSet.Surface, EndingSpriteSet> endings(ModAssetRoot assets)
            throws IOException {
        var all =
                new EnumMap<EndingSpriteSet.Surface, EndingSpriteSet>(
                        EndingSpriteSet.Surface.class);
        all.putAll(MicaEndings.load(assets));
        all.putAll(MicaS3kEndings.load(assets));
        return Map.copyOf(all);
    }

    private static Map<StageSpriteSet.Surface, StageSpriteSet> stages(ModAssetRoot assets)
            throws IOException {
        return FrameTable.read(assets, "art/mica/stage-poses/frames.txt").stages();
    }

    private static Image image(ModAssetRoot assets, String name) throws IOException {
        return PresentationImages.read(assets, ROOT + name + ".png");
    }

    private static SignFrame pose(ModAssetRoot assets, String name) throws IOException {
        return new SignFrame(
                image(assets, name),
                PresentationImages.backing(assets, ROOT + name + "-backing.png", 0));
    }
}
