package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.level.Pattern;
import java.io.IOException;
import java.util.Map;

/** Internal S2 projection of the module-owned, owner-bounded presentation supplier. */
public final class Sonic2CharacterPresentation {
    private Sonic2CharacterPresentation() { }

    public static CharacterPresentation resolve(GameModule module, SonicConfigurationService config) throws IOException {
        if (module == null) return null;
        var key = CharacterKey.parsePersisted(ActiveGameplayTeamResolver.resolveMainCharacterCode(config));
        var definition = module.getPlayableCharacterRegistry().find(key).orElse(null);
        return definition == null || definition.presentationSupplier() == null ? null
                : definition.presentationSupplier().load(key.persisted());
    }

    public static CharacterPresentation.Image name(Rom rom, CharacterPresentation presentation,
            CharacterPresentation.Style style) throws IOException {
        if (presentation == null) return null;
        String name = style == CharacterPresentation.Style.HUD && presentation.hudName() != null
                ? presentation.hudName() : presentation.displayName();
        if (name == null) return null;
        var composition = CharacterNameComposer.compose(name,
                style == CharacterPresentation.Style.HUD ? Sonic2CharacterFont.hud(rom) : Sonic2CharacterFont.title(rom),
                presentation.supplementaryGlyphs().getOrDefault(
                        new CharacterPresentation.Font(CharacterPresentation.Host.S2, style), Map.of()), 0);
        return composition.image();
    }

    public static CharacterPresentation.Art art(CharacterPresentation presentation) {
        return presentation == null ? null : presentation.art().get(CharacterPresentation.Host.S2);
    }

    /** Keep the native counter's lower row (cross and digit space) and tile layout. */
    public static Pattern[] oneUp(CharacterPresentation presentation) {
        var art = art(presentation);
        if (art == null || art.oneUpFace() == null) return null;
        // Obj26's face is a fixed 2 by 2 tile piece; never overwrite its frame/glass art.
        var image = art.oneUpFace();
        return image.width() == 16 && image.height() == 16 ? CharacterNameComposer.tiles(image) : null;
    }

    public static Pattern[] hud(Rom rom, Pattern[] nativeArt, CharacterPresentation presentation) throws IOException {
        var art = art(presentation);
        var name = name(rom, presentation, CharacterPresentation.Style.HUD);
        if ((art == null || art.hudLifeIcon() == null) && name == null) return nativeArt;
        Pattern[] result = new Pattern[nativeArt.length];
        for (int i = 0; i < result.length; i++) { result[i] = new Pattern(); result[i].copyFrom(nativeArt[i]); }
        if (art != null && art.hudLifeIcon() != null)
            System.arraycopy(CharacterNameComposer.tiles(art.hudLifeIcon()), 0, result, 0, 4);
        if (name != null && name.width() <= 32 && name.height() <= 8) {
            byte[] pixels = name.pixels();
            for (int x = 0; x < 32; x++) for (int y = 0; y < 8; y++)
                result[4 + x / 8 * 2].setPixel(x % 8, y,
                        x < name.width() && y < name.height() ? pixels[y * name.width() + x] : 0);
        }
        return result;
    }
}
