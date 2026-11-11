package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** SuperFormProfile for TALON, including Solar palette cycles and native flight physics. */
final class TalonSolar {
    static final String EFFECTS = "art/talon/powered/effects/effects.txt";

    private TalonSolar() {}

    static SuperFormProfile profile(ModAssetRoot assets) throws IOException {
        var colors = FrameTable.colors(assets, "art/talon/powered/palettes.txt");
        var fade = colors.get("fade");
        var solar = EffectsTable.read(assets, EFFECTS).effects("super");
        var s2 =
                new SuperFormProfile.Form(
                        fade,
                        colors.get("s2"),
                        List.of(),
                        8,
                        4,
                        PhysicsProfile.SONIC_3K_SUPER_TAILS,
                        CanonicalAnimation.WAIT,
                        Map.of(),
                        false,
                        false,
                        null,
                        solar);
        var s3k =
                new SuperFormProfile.Form(
                        fade,
                        colors.get("s3k"),
                        List.of(),
                        7,
                        2,
                        PhysicsProfile.SONIC_3K_SUPER_TAILS,
                        CanonicalAnimation.WAIT,
                        Map.of(),
                        false,
                        false,
                        null,
                        solar);
        // Solar has no Hyper tier, native stars, after-images or Super Flickies; its own motes,
        // flare and heat trail come from the effects table.
        return new SuperFormProfile(
                Map.of(
                        GameId.S1,
                        Map.of(SuperFormProfile.Tier.SUPER, s2),
                        GameId.S2,
                        Map.of(SuperFormProfile.Tier.SUPER, s2),
                        GameId.S3K,
                        Map.of(SuperFormProfile.Tier.SUPER, s3k)));
    }
}
