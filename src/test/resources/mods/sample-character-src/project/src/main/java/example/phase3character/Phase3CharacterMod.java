package example.phase3character;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

public final class Phase3CharacterMod implements GgfMod {
    @Override public void register(ModContext context) {
        try {
            byte[] bytes = context.modAssets().readBounded(
                    "art/runner.ggfp", context.modAssets().limits().maxAssetBytes());
            var materialized = PlayableSheetMaterializer.read(bytes);
            context.registerCharacter("runner", SampleCharacter.definition(
                    context.ownerModId(), materialized));
            context.registerGamePatch(new SampleCharacterPhysicsPatch(context.ownerModId()));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to load sample playable art", failure);
        }
    }
}
