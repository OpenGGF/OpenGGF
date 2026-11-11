package micaandtalon;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CharacterPresentation for TALON's HUD identity, lives, signposts and native object/special-stage
 * banks. Composes title, screen, reel and ending contributions without changing host selectors.
 */
final class TalonPresentation {
    private static final String ROOT = "art/talon/hud-and-screens/";

    static CharacterPresentation load(ModAssetRoot assets) throws IOException {
        var art = new LinkedHashMap<Host, Art>();
        for (var host : Host.values()) art.put(host, art(assets, host));
        var letters =
                Map.of(
                        'T',
                        image(assets, "s1-name-T"),
                        'A',
                        image(assets, "s1-name-A"),
                        'L',
                        image(assets, "s1-name-L"));
        var glyphs = Map.of(new Font(Host.S1, Style.HUD), letters);
        var stages = OrientedStageArt.load(assets, "talon", stages(assets));
        return new CharacterPresentation(
                "TALON", "TALO", art, glyphs, stages, TalonEndings.load(assets));
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
        var screens = TalonScreenFrames.load(assets, host);
        return new Art(
                image(assets, prefix + "-hud"),
                image(assets, prefix + "-monitor"),
                face.face(),
                face.face(),
                sign,
                screens);
    }

    // FixBugs=0 slot goal clears character_id: Sonic roll also selects Tails duck/balance
    // mappings $99/$9A while the Tails resident bank is held. Supply those selectors too.
    private static Map<StageSpriteSet.Surface, StageSpriteSet> stages(ModAssetRoot assets)
            throws IOException {
        return FrameTable.read(assets, "art/talon/stage-poses/frames.txt").stages();
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
