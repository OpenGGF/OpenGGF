package com.openggf.game.sonic2.specialstage;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

/** Name row from the half-pipe's own SONIC/MILES and overseas TAILS art. */
public final class Sonic2SpecialStageHudPresentation {
    private Sonic2SpecialStageHudPresentation() { }

    public static Map<Character, CharacterPresentation.Image> font(Pattern[] hud, Pattern[] tails) {
        var glyphs = new LinkedHashMap<Character, CharacterPresentation.Image>();
        collect(glyphs, hud, 0, "SONIC");
        collect(glyphs, hud, 5, "MILES");
        collect(glyphs, tails, 0, "TAILS");
        return Map.copyOf(glyphs);
    }

    private static void collect(Map<Character, CharacterPresentation.Image> glyphs, Pattern[] art,
                                int first, String letters) {
        for (int i = 0; i < letters.length(); i++)
            glyphs.putIfAbsent(letters.charAt(i), CharacterNameComposer.extract(art, first + i,
                    1, 1, 0, 0, 8, 8));
    }

    public static Pattern[] apply(Pattern[] hud, Pattern[] tails, CharacterPresentation presentation) {
        if (presentation == null) return hud;
        String name = presentation.hudName() == null ? presentation.displayName() : presentation.hudName();
        if (name == null) return hud;
        var composition = CharacterNameComposer.compose(name, font(hud, tails),
                presentation.supplementaryGlyphs().getOrDefault(new CharacterPresentation.Font(
                        CharacterPresentation.Host.S2, CharacterPresentation.Style.HUD), Map.of()), 0);
        if (!composition.complete() || composition.image().width() > 40 || composition.image().height() > 8)
            return hud;
        Pattern[] result = hud.clone();
        // Map_obj5E_0008 (docs/s2disasm/mappings/sprite/obj5E.asm:7-12): five name tiles.
        for (int i = 0; i < 5; i++) result[i] = new Pattern();
        Pattern[] nameTiles = CharacterNameComposer.tiles(composition.image());
        System.arraycopy(nameTiles, 0, result, 0, nameTiles.length);
        return result;
    }
}
