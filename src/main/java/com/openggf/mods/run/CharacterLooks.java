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
            // Evidence is the stock line each game loads and, where the game has one, the Super
            // palette cycle, which rewrites exactly the body entries. Brightness is the sum of
            // the 3-bit channels of the stock words.
            // Pal_Sonic (_inc/Palette Index.asm:51): idx2-5 = $822 $A44 $C66 $E88, the only
            // blue ramp; S1 ships no Super cycle. idx2 doubles as the body outline.
            case "s1:sonic" -> new int[] {5, 4, 3, 2};
            // Pal_BGND line 0 (s2.asm:3910) idx2-5 = $A22 $C42 $E44 $E66. PalCycle_SuperSonic
            // writes Normal_palette+4 for four words (s2.asm:3139-3143) from
            // CyclingPal_SSTransformation (s2.asm:3240-3241), whose first frame is that ramp.
            case "s2:sonic" -> new int[] {5, 4, 3, 2};
            // Same line: Tails' fur is idx14 $0AE, idx15 $08E, idx11 $46A (the S3K Super Tails
            // base frame below). idx11 is also Sonic's skin shadow, which matters only for
            // sprites that share the line, never for a privately recoloured one.
            case "s2:tails" -> new int[] {14, 15, 11};
            // Knuckles in Sonic 2 uses Pal_KnuxEndPose's S2 layout (sonic3k.asm:127442-127443):
            // idx5 $64E, idx3 $20C, idx2 $206 (idx4 $080 is his shoes).
            case "s2:knuckles" -> new int[] {5, 3, 2};
            // Pal_SonicTails (sonic3k.asm:200671-200672) idx2-4 = $E66 $C42 $822;
            // SuperHyper_PalCycle_SonicApply writes Normal_palette+$04 for three words
            // (sonic3k.asm:4674-4676) from PalCycle_SuperSonic (sonic3k.asm:4865-4866).
            case "s3k:sonic" -> new int[] {2, 3, 4};
            // Pal_Knuckles (sonic3k.asm:200677-200678) idx2-4 = $64E $20C $206;
            // SuperHyper_PalCycle_Apply writes Normal_palette+$4 (sonic3k.asm:4854-4856) and
            // PalCycle_SuperHyperKnucklesRevert (sonic3k.asm:4929-4930) restores that ramp.
            case "s3k:knuckles" -> new int[] {2, 3, 4};
            // SuperHyper_PalCycle_ApplyTails writes Normal_palette+$10, +$12 and +$16, skipping
            // the skin at idx10 (sonic3k.asm:4824-4828); PalCycle_SuperTails
            // (sonic3k.asm:4911-4912) starts at the stock $0AE $08E $46A.
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
