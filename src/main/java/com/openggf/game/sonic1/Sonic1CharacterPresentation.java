package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import java.io.IOException;
import java.util.Map;

/** S1-owned adapters from optional character art to native surface tile layouts. */
public final class Sonic1CharacterPresentation {
    private Sonic1CharacterPresentation() { }

    public static CharacterPresentation active() throws IOException {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) return null;
        String code = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
        var definition = world.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(code)).orElse(null);
        return definition == null || definition.presentationSupplier() == null
                ? null : definition.presentationSupplier().load(code);
    }

    static CharacterPresentation.Art art(CharacterPresentation presentation) {
        return presentation == null ? null : presentation.art().get(CharacterPresentation.Host.S1);
    }

    public static CharacterNameComposer.Composition name(Rom rom, CharacterPresentation presentation,
            CharacterPresentation.Style style) throws IOException {
        if (presentation == null) return null;
        String name = style == CharacterPresentation.Style.HUD && presentation.hudName() != null
                ? presentation.hudName() : presentation.displayName();
        if (name == null) return null;
        return CharacterNameComposer.compose(name,
                style == CharacterPresentation.Style.HUD ? Sonic1CharacterFont.hud(rom) : Sonic1CharacterFont.title(rom),
                presentation.supplementaryGlyphs().getOrDefault(
                        new CharacterPresentation.Font(CharacterPresentation.Host.S1, style), Map.of()), 0);
    }

    static Pattern[] hud(Rom rom, Pattern[] nativeTiles, CharacterPresentation presentation) throws IOException {
        if (presentation == null) return nativeTiles;
        Pattern[] tiles = nativeTiles.clone();
        var art = art(presentation);
        if (art != null && art.hudLifeIcon() != null) {
            System.arraycopy(CharacterNameComposer.tiles(art.hudLifeIcon()), 0, tiles, 0, 4);
        }
        var name = name(rom, presentation, CharacterPresentation.Style.HUD);
        if (name != null && name.complete() && name.image().width() <= 32 && name.image().height() <= 8) {
            // Only the upper row is the name. Keep the native lower-row X and life-number cell.
            var image = name.image();
            byte[] pixels = image.pixels();
            for (int x = 0; x < 32; x++) {
                int tile = 4 + x / 8 * 2;
                if (x % 8 == 0) { tiles[tile] = new Pattern(); }
                for (int y = 0; y < image.height(); y++) {
                    if (x < image.width()) tiles[tile].setPixel(x % 8, y, pixels[y * image.width() + x]);
                }
            }
        }
        return tiles;
    }
}
