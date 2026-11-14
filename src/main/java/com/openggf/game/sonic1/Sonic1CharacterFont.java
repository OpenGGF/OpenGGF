package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation.Image;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.*;

/** Native Title Cards.nem alphabet, including the unused K and U. */
public final class Sonic1CharacterFont {
    private Sonic1CharacterFont() { }
    public static Map<Character, Image> title(Rom rom) throws IOException {
        var tiles = PatternDecompressor.nemesis(rom,
                Sonic1RomProfile.resolve(rom, Sonic1Constants.ART_NEM_TITLE_CARD_ADDR));
        String letters = "ABCDEFGHIKLMNOPRSTUYZ";
        int[] offsets = {0,4,8,12,16,20,24,28,32,34,38,42,46,50,54,58,62,66,70,74,78};
        var glyphs = new LinkedHashMap<Character, Image>();
        for (int i = 0; i < letters.length(); i++) {
            int columns = letters.charAt(i) == 'I' ? 1 : 2;
            glyphs.put(letters.charAt(i), CharacterNameComposer.extract(tiles, offsets[i], columns, 2,
                    0, 0, columns * 8, 16));
        }
        return Map.copyOf(glyphs);
    }
    /** The upper row of the native life-counter name cell; no large-font substitutions. */
    public static Map<Character, Image> hud(Rom rom) throws IOException {
        var glyphs = new LinkedHashMap<Character, Image>();
        var sonic = PatternDecompressor.nemesis(rom,
                Sonic1RomProfile.resolve(rom, Sonic1Constants.ART_NEM_LIFE_ICON_ADDR));
        com.openggf.game.presentation.HudNameFont.collect(glyphs, sonic, "SONIC", 0, 7, 14, 21, 25, 32);
        return Map.copyOf(glyphs);
    }

}
