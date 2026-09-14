package com.openggf.game.sonic2;

import com.openggf.level.Pattern;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Pattern overlays a game patch layers over Sonic 2's object art.
 *
 * <p>The Sonic 2 PLC lists load extra requests over already-loaded art at a
 * tile offset ({@code plreq ArtTile_ArtNem_Powerups+44, ...}); a patch
 * expresses each such request as a {@link SheetPatch} against the sprite
 * sheet that owns the destination tiles. {@link Sonic2ObjectArtProvider}
 * applies them after the zone's own PLC loads, so the patch never names a
 * game, zone or donor. Every supplier may return {@code null} or an empty
 * array to leave the stock art in place.
 *
 * @param mainCharacterLifeIcon HUD life counter and 1-up monitor face
 *                              ({@code ArtNem_Sonic_life_counter} replacement)
 * @param sheetPatches          tile-range overwrites applied to registered sheets
 */
public record Sonic2ArtOverlays(Supplier<Pattern[]> mainCharacterLifeIcon, List<SheetPatch> sheetPatches) {

    /** Overwrite {@code sheetKey}'s tiles from {@code firstTile} with the supplied patterns. */
    public record SheetPatch(String sheetKey, int firstTile, Supplier<Pattern[]> patterns) {
        public SheetPatch {
            Objects.requireNonNull(sheetKey, "sheetKey");
            Objects.requireNonNull(patterns, "patterns");
            if (firstTile < 0) {
                throw new IllegalArgumentException("firstTile must be >= 0: " + firstTile);
            }
        }
    }

    public Sonic2ArtOverlays {
        sheetPatches = sheetPatches == null ? List.of() : List.copyOf(sheetPatches);
    }

    /** Only the life icon (tier-one lock-on art, cross-game stand-ins). */
    public static Sonic2ArtOverlays lifeIconOnly(Supplier<Pattern[]> mainCharacterLifeIcon) {
        return new Sonic2ArtOverlays(mainCharacterLifeIcon, List.of());
    }
}
