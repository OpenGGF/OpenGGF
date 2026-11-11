package micaandtalon;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

import java.io.IOException;

/**
 * Tutorial part 2: registers one playable character from the sheet that
 * generate_resources.py baked into art/mica.ggfp.
 */
public final class MicaMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        try {
            byte[] bytes =
                    context.modAssets()
                            .readBounded(
                                    "art/mica.ggfp", context.modAssets().limits().maxAssetBytes());
            var sheet = PlayableSheetMaterializer.read(bytes);
            context.registerCharacter("mica", MicaCharacter.definition(context.ownerModId(), sheet));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load MICA's playable sheet", failure);
        }
    }
}
