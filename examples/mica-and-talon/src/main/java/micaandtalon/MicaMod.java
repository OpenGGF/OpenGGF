package micaandtalon;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

import java.io.IOException;
import java.util.List;

/**
 * ModEntrypoint registrations and game patches; connects both character definitions to their
 * presentation and art suppliers.
 */
public final class MicaMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerMutator(GildedSettings.definition());
        for (String game : List.of("s1", "s2", "s3k"))
            context.registerGamePatch(new GildedSettings.Patch(game));
        try {
            byte[] bytes =
                    context.modAssets()
                            .readBounded(
                                    "art/mica.ggfp", context.modAssets().limits().maxAssetBytes());
            var materialized = PlayableSheetMaterializer.read(bytes);
            context.registerCharacter(
                    "mica",
                    MicaCharacter.definition(
                            context.ownerModId(),
                            materialized,
                            MicaPresentation.load(context.modAssets()),
                            MicaGilded.profile(context.modAssets())));
            TalonMod.register(context);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load sample playable art", failure);
        }
    }
}
