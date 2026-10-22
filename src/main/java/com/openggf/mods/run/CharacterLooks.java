package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.net.identity.PlayerAppearance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds player looks from a base colour. Each stock character has a body ramp: the entries of
 * its palette line that colour the body, ordered lightest first. A base colour becomes that
 * ramp (one step lighter, the base itself, then darker steps), so presets recolour the body
 * and leave eyes, skin, shoes and outlines alone.
 */
@ModApi
public final class CharacterLooks {
    private CharacterLooks() { }

    /**
     * Body-ramp palette indices for {@code character} in {@code gameId} ({@code s1}, {@code s2},
     * {@code s3k}), lightest first; empty when the ramp is not known, in which case a look only
     * changes the name colour.
     */
    public static int[] bodyRamp(String gameId, String character) {
        String key = gameId.toLowerCase(Locale.ROOT) + ":" + character.toLowerCase(Locale.ROOT);
        return switch (key) {
            // Super Sonic's palette cycle rewrites exactly these entries (Sonic2SuperStateController).
            case "s2:sonic" -> new int[] {2, 3, 4, 5};
            // Sonic3kSuperStateController's Super cycles: Sonic/Knuckles {2,3,4}, Tails {8,9,11}.
            case "s3k:sonic", "s3k:knuckles" -> new int[] {2, 3, 4};
            case "s3k:tails" -> new int[] {8, 9, 11};
            default -> new int[0];
        };
    }

    /** A look whose body ramp follows {@code baseColour} (a Mega Drive colour word). */
    public static PlayerAppearance withBaseColour(String gameId, String character, int baseColour) {
        int[] ramp = bodyRamp(gameId, character);
        List<PlayerAppearance.Entry> entries = new ArrayList<>(ramp.length);
        for (int step = 0; step < ramp.length; step++) {
            entries.add(new PlayerAppearance.Entry(ramp[step], shade(baseColour, 1 - step)));
        }
        entries.sort(java.util.Comparator.comparingInt(PlayerAppearance.Entry::index));
        return new PlayerAppearance(baseColour, entries);
    }

    /** {@code colour} moved {@code steps} levels lighter (positive) or darker on every channel. */
    static int shade(int colour, int steps) {
        int result = 0;
        for (int shift : new int[] {1, 5, 9}) {
            int level = (colour >> shift) & 7;
            int moved = steps >= 0 ? Math.min(7, level + steps) : Math.max(0, level + steps);
            // Darkening keeps empty channels empty so a pure hue stays pure.
            if (steps < 0 && level == 0) moved = 0;
            result |= moved << shift;
        }
        return result;
    }
}
