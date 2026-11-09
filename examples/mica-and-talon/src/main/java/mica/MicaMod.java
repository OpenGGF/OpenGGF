package mica;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

public final class MicaMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerMutator(GildedSettings.definition());
        for (String game : java.util.List.of("s1", "s2", "s3k")) context.registerGamePatch(new GildedSettings.Patch(game));
        try {
            byte[] bytes = context.modAssets().readBounded(
                    "art/mica.ggfp", context.modAssets().limits().maxAssetBytes());
            var materialized = PlayableSheetMaterializer.read(bytes);
            context.registerCharacter("mica", MicaCharacter.definition(
                    context.ownerModId(), materialized, MicaPresentation.load(context.modAssets())));
            TalonMod.register(context);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to load sample playable art", failure);
        }
    }
}
