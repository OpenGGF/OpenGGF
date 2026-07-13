package example.phase3standalone;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.Pattern;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.StandaloneLevelLoader;
import com.openggf.sprites.playable.SecondaryAbility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public final class Phase3StandaloneMod implements GgfMod {
    @Override public void register(ModContext context) {
        try {
            String owner = context.ownerModId();
            var assets = context.modAssets();
            var materialized = PlayableSheetMaterializer.read(assets.readBounded(
                    "art/runner.ggfp", assets.limits().maxAssetBytes()));
            var level = StandaloneLevelLoader.load(assets,
                    new BakedLevelRef("levels/sample/level.json"), owner,
                    new RingSpriteSheet(new Pattern[0], List.of(), 0, 8, 0, 0));
            CharacterKey key = CharacterKey.mod(owner, "runner");
            context.registerCharacter("runner", new CharacterDefinition(key, "Sample Runner",
                    SampleCharacter::new, null, PlayerCharacter.SONIC_AND_TAILS,
                    SecondaryAbility.NONE, false, ignored -> materialized.art(),
                    ignored -> materialized.palette()));
            CharacterKey friendKey = CharacterKey.mod(owner, "friend");
            context.registerCharacter("friend", new CharacterDefinition(friendKey, "Sample Friend",
                    SampleCharacter::new, null, PlayerCharacter.SONIC_ALONE,
                    SecondaryAbility.NONE, false, ignored -> materialized.art(),
                    ignored -> materialized.palette()));
            context.registerGameModule(new SampleStandaloneModule(owner, level));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
