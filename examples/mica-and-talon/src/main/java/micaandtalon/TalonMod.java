package micaandtalon;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.ModContext;

import java.io.IOException;

/**
 * ModContext character registration for TALON. Loads his independent playable sheet and
 * presentation and registers the stable talon character ID alongside MICA.
 */
final class TalonMod {
    static void register(ModContext context) throws IOException {
        byte[] bytes =
                context.modAssets()
                        .readBounded(
                                "art/talon.ggfp", context.modAssets().limits().maxAssetBytes());
        var materialized = PlayableSheetMaterializer.read(bytes);
        var definition =
                TalonCharacter.definition(
                        context.ownerModId(),
                        materialized,
                        TalonPresentation.load(context.modAssets()),
                        TalonSolar.profile(context.modAssets()),
                        EffectsTable.read(context.modAssets(), TalonSolar.EFFECTS).trail("normal"));
        var competition = CompetitionPresentation.talon(context.modAssets());
        context.registerCharacter(
                "talon",
                ReelPresentation.decorate(
                        CompetitionPresentation.withStages(definition, competition),
                        context.modAssets()));
    }
}
