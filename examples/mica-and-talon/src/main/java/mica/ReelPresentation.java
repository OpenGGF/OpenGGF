package mica;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.Map;

/** Native-cell, independently paletted portraits; slot rewards stay native. */
final class ReelPresentation {
    static Map<CharacterPresentation.Host, CharacterPresentation.ReelFace> load(ModAssetRoot assets, String character)
            throws IOException {
        var normal = PresentationImages.backing(assets, "art/" + character + "/reels/normal.png", 0);
        var powered = PresentationImages.backing(assets, "art/" + character + "/reels/powered.png", 0);
        var face = new CharacterPresentation.ReelFace(normal.image(), powered == null ? null : powered.image(),
                normal.rgb(), powered == null ? null : powered.rgb());
        return Map.of(CharacterPresentation.Host.S2, face, CharacterPresentation.Host.S3K, face);
    }

    static CharacterDefinition decorate(CharacterDefinition d, ModAssetRoot assets) throws IOException {
        var reels = load(assets, "talon");
        var titles = TitlePresentation.load(assets, "talon");
        return new CharacterDefinition(d.key(), d.displayName(), d.spriteFactory(), d.respawnStrategyFactory(),
                d.behavesLike(), d.secondaryAbility(), d.supportsSuperForm(), d.artSupplier(), d.paletteSupplier(),
                d.movementAbilities(), d.underwaterPaletteSupplier(), new CharacterDefinition.PresentationSupplier() {
                    public CharacterPresentation load(String code) throws IOException { return load(code, GameId.S3K); }
                    public CharacterPresentation load(String code, GameId host) throws IOException {
                        return d.presentationSupplier().load(code, host).withReelFaces(reels).withTitleMascots(titles);
                    }
                }, d.superFormSupplier(), d.defaultSidekick());
    }
}
