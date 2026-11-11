package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.PlayableSheetMaterializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

/** SuperFormProfile for TALON, including Solar palette cycles and native flight physics. */
final class TalonSolar {
    private TalonSolar() {}

    static SuperFormProfile profile(ModAssetRoot assets) throws IOException {
        var colors = FrameTable.colors(assets, "art/talon/powered/palettes.txt");
        var fade = colors.get("fade");
        var s2Bank = bank(assets, "s2");
        var s3kBank = bank(assets, "s3k");
        var s2 =
                new SuperFormProfile.Form(
                        fade,
                        colors.get("s2"),
                        List.of(),
                        8,
                        4,
                        PhysicsProfile.SONIC_3K_SUPER_TAILS,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        Map.of(),
                        false,
                        false,
                        s2Bank);
        var s3k =
                new SuperFormProfile.Form(
                        fade,
                        colors.get("s3k"),
                        List.of(),
                        7,
                        2,
                        PhysicsProfile.SONIC_3K_SUPER_TAILS,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        Map.of(),
                        false,
                        false,
                        s3kBank);
        // Solar has no Hyper tier, stars, after-images or Super Flickies.
        return new SuperFormProfile(
                Map.of(
                        GameId.S1,
                        Map.of(SuperFormProfile.Tier.SUPER,
                                new SuperFormProfile.Form(fade, colors.get("s2"), List.of(), 8, 4,
                                        PhysicsProfile.SONIC_3K_SUPER_TAILS, CanonicalAnimation.SUPER_TRANSFORM,
                                        Map.of(), false, false, s2Bank)),
                        GameId.S2,
                        Map.of(SuperFormProfile.Tier.SUPER, s2),
                        GameId.S3K,
                        Map.of(SuperFormProfile.Tier.SUPER, s3k)));
    }
    private static PoweredPlayableSheet bank(ModAssetRoot assets, String game) throws IOException {
        var art = PlayableSheetMaterializer.read(assets.readBounded(
                "art/talon-powered-" + game + ".ggfp", assets.limits().maxAssetBytes())).art();
        var aliases = EnumSet.allOf(CanonicalAnimation.class);
        aliases.removeAll(Set.of(CanonicalAnimation.WAIT, CanonicalAnimation.RUN,
                CanonicalAnimation.TAILS_FLY, CanonicalAnimation.TAILS_FLY_ASCEND,
                CanonicalAnimation.SUPER_TRANSFORM));
        // Native carry geometry/contact and all other poses are explicit normal-bank aliases.
        return new PoweredPlayableSheet(art, aliases);
    }

}
