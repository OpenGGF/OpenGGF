package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import java.io.IOException;
import java.util.Map;

/** S3K surface adapter; native banks remain untouched when no slot is supplied. */
public final class Sonic3kCharacterPresentation {
    private Sonic3kCharacterPresentation() { }

    public static CharacterPresentation active() throws IOException {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) return null;
        String code = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
        var definition = world.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(code)).orElse(null);
        if (definition == null || definition.presentationSupplier() == null) return null;
        return definition.presentationSupplier().load(code);
    }

    public static Pattern[] hud(Rom rom, Pattern[] nativeTiles, CharacterPresentation presentation) throws IOException {
        if (presentation == null) return nativeTiles;
        var art = presentation.art().get(Host.S3K);
        String name = presentation.hudName() != null ? presentation.hudName() : presentation.displayName();
        Image composed = name == null ? null : compose(rom, presentation, name, Style.HUD);
        if (composed != null && (composed.width() > 32 || composed.height() > 16))
            throw new IllegalArgumentException("S3K HUD name exceeds the 32 by 16 pixel cell; supply a short HUD name");
        if (composed == null && (art == null || art.hudLifeIcon() == null)) return nativeTiles;
        Pattern[] result = nativeTiles.clone();
        if (art != null && art.hudLifeIcon() != null)
            System.arraycopy(CharacterNameComposer.tiles(art.hudLifeIcon()), 0, result, 0, 4);
        if (composed != null) {
            byte[] cell = new byte[32 * 16];
            byte[] pixels = composed.pixels();
            for (int y = 0; y < composed.height(); y++)
                System.arraycopy(pixels, y * composed.width(), cell, y * 32, composed.width());
            System.arraycopy(CharacterNameComposer.tiles(new Image(32, 16, cell)), 0, result, 4, 8);
        }
        return result;
    }

    private static Image compose(Rom rom, CharacterPresentation presentation, String name, Style style) throws IOException {
        Map<Character, Image> font;
        if (style == Style.HUD) font = Sonic3kCharacterFont.hud(rom);
        else {
            var letters = new java.util.LinkedHashMap<>(Sonic3kCharacterFont.results(rom));
            Sonic3kCharacterFont.title(rom).forEach(letters::putIfAbsent);
            font = letters;
        }
        var composition = CharacterNameComposer.compose(name, font,
                presentation.supplementaryGlyphs().getOrDefault(new Font(Host.S3K, style), Map.of()), 0);
        if (!composition.complete()) throw new IllegalArgumentException("Missing S3K " + style + " glyphs: " + composition.missingGlyphs());
        return composition.image();
    }
}
