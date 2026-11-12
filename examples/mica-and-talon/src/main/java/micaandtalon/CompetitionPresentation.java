package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.util.*;

/**
 * CharacterDefinition.PresentationSupplier adapter for StageSpriteSet Competition banks. Merges
 * miniature bodies into the base presentation and explicitly hides TALON's native separate tail
 * bank.
 */
final class CompetitionPresentation {
    static Map<StageSpriteSet.Surface, StageSpriteSet> mica(ModAssetRoot assets)
            throws IOException {
        return Map.of(StageSpriteSet.Surface.S3K_COMPETITION, MicaCompetitionFrames.load(assets));
    }

    static Map<StageSpriteSet.Surface, StageSpriteSet> talon(ModAssetRoot assets)
            throws IOException {
        var blank =
                new StageSpriteSet.Frame(
                        new CharacterPresentation.Image(1, 1, new byte[] {0}), 0, 0);
        var tails = new LinkedHashMap<Integer, StageSpriteSet.Frame>();
        for (int i = 0; i < 16; i++) tails.put(i, blank);
        return Map.of(
                StageSpriteSet.Surface.S3K_COMPETITION,
                TalonCompetitionFrames.load(assets),
                StageSpriteSet.Surface.S3K_COMPETITION_TAIL,
                new StageSpriteSet(tails));
    }

    static Map<StageSpriteSet.Surface, StageSpriteSet> merge(
            Map<StageSpriteSet.Surface, StageSpriteSet> base,
            Map<StageSpriteSet.Surface, StageSpriteSet> extra) {
        var result =
                new EnumMap<StageSpriteSet.Surface, StageSpriteSet>(StageSpriteSet.Surface.class);
        result.putAll(base);
        result.putAll(extra);
        return Map.copyOf(result);
    }

    static CharacterDefinition withStages(
            CharacterDefinition d, Map<StageSpriteSet.Surface, StageSpriteSet> stages) {
        var supplier =
                new CharacterDefinition.PresentationSupplier() {
                    public CharacterPresentation load(String code) throws IOException {
                        return load(code, GameId.S3K);
                    }

                    public CharacterPresentation load(String code, GameId host) throws IOException {
                        var p = d.presentationSupplier().load(code, host);
                        return new CharacterPresentation(
                                p.displayName(),
                                p.hudName(),
                                p.art(),
                                p.supplementaryGlyphs(),
                                merge(p.stages(), stages),
                                p.endings());
                    }
                };
        return new CharacterDefinition(
                d.key(),
                d.displayName(),
                d.spriteFactory(),
                d.respawnStrategyFactory(),
                d.behavesLike(),
                d.secondaryAbility(),
                d.supportsSuperForm(),
                d.artSupplier(),
                d.paletteSupplier(),
                d.movementAbilities(),
                d.underwaterPaletteSupplier(),
                supplier,
                d.superFormSupplier(),
                d.defaultSidekick());
    }
}
