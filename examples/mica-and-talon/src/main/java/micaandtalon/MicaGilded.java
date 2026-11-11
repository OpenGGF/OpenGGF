package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.PlayableSheetMaterializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SuperFormProfile for MICA, including powered sheets, native gates and palette cycles. */
final class MicaGilded {
    static final String EFFECTS = "art/mica/powered/effects/effects.txt";

    private MicaGilded() {}

    static SuperFormProfile profile(ModAssetRoot assets) throws IOException {
        var s2Bank = bank(assets, "s2");
        var s3kBank = bank(assets, "s3k");
        var colors = FrameTable.colors(assets, "art/mica/powered/palettes.txt");
        var fade = colors.get("fade");
        var effects = EffectsTable.read(assets, EFFECTS);
        var gilded = effects.effects("super");
        var s2 =
                new SuperFormProfile.Form(
                        gold(fade),
                        gold(colors.get("s2")),
                        List.of(),
                        8,
                        4,
                        PhysicsProfile.SONIC_2_SUPER_SONIC,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        Map.of(),
                        false,
                        false,
                        s2Bank,
                        gilded);
        var s3k =
                new SuperFormProfile.Form(
                        gold(fade),
                        gold(colors.get("s3k")),
                        List.of(),
                        7,
                        2,
                        PhysicsProfile.SONIC_3K_SUPER_SONIC,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        Map.of(),
                        false,
                        false,
                        s3kBank,
                        gilded);
        // Hyper Gilded keeps her gold palette and adds the native echo trail, her own denser
        // glints and Instagib strikes; no Sonic stars.
        var hyper =
                new SuperFormProfile.Form(
                        s3k.fade(),
                        s3k.dryCycle(),
                        List.of(),
                        7,
                        2,
                        PhysicsProfile.SONIC_3K_SUPER_SONIC,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        Map.of(),
                        true,
                        false,
                        s3kBank,
                        effects.effects("hyper"));
        return new SuperFormProfile(
                Map.of(
                        GameId.S1,
                        Map.of(
                                SuperFormProfile.Tier.SUPER,
                                new SuperFormProfile.Form(
                                        gold(fade),
                                        gold(colors.get("s2")),
                                        List.of(),
                                        8,
                                        4,
                                        PhysicsProfile.SONIC_2_SUPER_SONIC,
                                        CanonicalAnimation.SUPER_TRANSFORM,
                                        Map.of(),
                                        false,
                                        false,
                                        s2Bank,
                                        gilded)),
                        GameId.S2,
                        Map.of(SuperFormProfile.Tier.SUPER, s2),
                        GameId.S3K,
                        Map.of(
                                SuperFormProfile.Tier.SUPER,
                                s3k,
                                SuperFormProfile.Tier.HYPER,
                                hyper)));
    }

    private static PoweredPlayableSheet bank(ModAssetRoot assets, String game) throws IOException {
        var art =
                PlayableSheetMaterializer.read(
                                assets.readBounded(
                                        "art/mica-powered-" + game + ".ggfp",
                                        assets.limits().maxAssetBytes()))
                        .art();
        var aliases = EnumSet.allOf(CanonicalAnimation.class);
        aliases.removeAll(
                Set.of(
                        CanonicalAnimation.WALK,
                        CanonicalAnimation.RUN,
                        CanonicalAnimation.PUSH,
                        CanonicalAnimation.WAIT,
                        CanonicalAnimation.BALANCE,
                        CanonicalAnimation.DUCK,
                        CanonicalAnimation.SUPER_TRANSFORM,
                        CanonicalAnimation.ROLL, CanonicalAnimation.ROLL2));
        if (game.equals("s3k"))
            aliases.removeAll(
                    Set.of(
                            CanonicalAnimation.BLINK,
                            CanonicalAnimation.BALANCE2,
                            CanonicalAnimation.BUBBLE,
                            CanonicalAnimation.FLY));
        var phaseAliases = new HashMap<String, String>();
        phaseAliases.put("family-wait/6", "family-wait/6");
        if (game.equals("s3k")) {
            phaseAliases.remove("family-wait/6");
            phaseAliases.put("family-bubble/0", "family-bubble/0");
        }
        for (int phase = 0; phase < 2; phase++)
            phaseAliases.put("family-hurt-fall/" + phase, "family-hurt-fall/" + phase);
        for (int phase = 0; phase < 3; phase++)
            phaseAliases.put("family-tails-carried/" + phase, "family-tails-carried/" + phase);
        return new PoweredPlayableSheet(art, aliases, phaseAliases);
    }

    /** Pale gold belongs to the powered view; normal MICA cream slots stay unchanged. */
    private static List<List<Integer>> gold(List<List<Integer>> rows) {
        return rows.stream()
                .map(
                        row -> {
                            var copy = new ArrayList<>(row);
                            copy.set(7, 0xAEE);
                            copy.set(8, 0x8CC);
                            return List.copyOf(copy);
                        })
                .toList();
    }
}
