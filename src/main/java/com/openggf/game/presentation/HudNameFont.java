package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation.Image;
import com.openggf.level.Pattern;
import java.util.Map;

/** Pixel crops from the name row of the native 4 by 2 tile life-counter cell. */
public final class HudNameFont {
    private HudNameFont() { }

    public static void collect(Map<Character, Image> target, Pattern[] lifeArt,
            String letters, int... boundaries) {
        if (boundaries.length != letters.length() + 1)
            throw new IllegalArgumentException("Each letter needs start and end boundaries");
        for (int i = 0; i < letters.length(); i++) {
            int x = boundaries[i];
            target.putIfAbsent(letters.charAt(i), CharacterNameComposer.extract(
                    lifeArt, 4, 4, 2, x, 0, boundaries[i + 1] - x, 8));
        }
    }
}
