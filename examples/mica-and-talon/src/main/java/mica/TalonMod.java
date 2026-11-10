package mica;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.ModContext;

/** Original TALON body art and palette, shared by every supported host. */
final class TalonMod {
    static void register(ModContext context) throws java.io.IOException {
        byte[] bytes = context.modAssets().readBounded(
                "art/talon.ggfp", context.modAssets().limits().maxAssetBytes());
        var materialized = PlayableSheetMaterializer.read(bytes);
        context.registerCharacter("talon", ReelPresentation.decorate(TalonCharacter.definition(context.ownerModId(), materialized), context.modAssets()));
    }
}
