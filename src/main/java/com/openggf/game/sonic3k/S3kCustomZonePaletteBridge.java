package com.openggf.game.sonic3k;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.level.Palette;
import com.openggf.level.Level;
import com.openggf.level.LevelOrigin;
import com.openggf.mods.code.ModPaletteClaim;
import com.openggf.mods.code.ModRegistrationException;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Composes immutable custom-zone palette claims with host-owned character and
 * HUD palette data. Stock levels do not install this bridge.
 */
public final class S3kCustomZonePaletteBridge {
    public static final String CHARACTER_OWNER = "host:s3k-character";
    public static final String HUD_OWNER = "host:s3k-hud";

    private static final int CHARACTER_PRIORITY = 0;
    private static final int CREATOR_PRIORITY = 10;
    private static final int HUD_PRIORITY = 20;

    private final String creatorOwner;
    private final Palette characterPalette;
    private final List<ModPaletteClaim> creatorClaims;
    private final int hudPaletteLine;
    private final Supplier<Palette> hudPaletteSupplier;

    /** Whether a level (including an editor snapshot) is a custom S3K level. */
    public static boolean supports(Level level) {
        if (level == null) {
            return false;
        }
        Level original = LevelOrigin.original(level);
        return original instanceof Sonic3kLevel s3kLevel
                && !s3kLevel.hasStockRomZoneIdentity();
    }

    public S3kCustomZonePaletteBridge(String ownerModId,
                                      String creatorOwner,
                                      Palette characterPalette,
                                      List<ModPaletteClaim> creatorClaims,
                                      int hudPaletteLine,
                                      Supplier<Palette> hudPaletteSupplier) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        this.creatorOwner = Objects.requireNonNull(creatorOwner, "creatorOwner");
        this.characterPalette = Objects.requireNonNull(characterPalette, "characterPalette").deepCopy();
        this.creatorClaims = List.copyOf(Objects.requireNonNull(creatorClaims, "creatorClaims"));
        requirePaletteLine(hudPaletteLine);
        this.hudPaletteLine = hudPaletteLine;
        this.hudPaletteSupplier = Objects.requireNonNull(hudPaletteSupplier, "hudPaletteSupplier");
        validateCreatorClaims(ownerModId, creatorClaims, hudPaletteLine, hudPaletteSupplier.get());
    }

    /** Validates the host reservations before a creator zone is published. */
    public static void validateCreatorClaims(String ownerModId,
                                             List<ModPaletteClaim> creatorClaims,
                                             int hudPaletteLine,
                                             Palette hudPaletteOverride) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(creatorClaims, "creatorClaims");
        requirePaletteLine(hudPaletteLine);
        for (ModPaletteClaim claim : creatorClaims) {
            Objects.requireNonNull(claim, "creator palette claim");
            if (claim.line() == 0 || (hudPaletteOverride != null && claim.line() == hudPaletteLine)) {
                throw new ModRegistrationException(ownerModId,
                        "MOD_PALETTE_RESERVED",
                        "Creator palette claim targets host-reserved line " + claim.line()
                                + ", color " + claim.color(),
                        null, null);
            }
        }
    }

    /** Submits all current-frame claims after the registry frame has begun. */
    public void submitFrameClaims(PaletteOwnershipRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.submit(PaletteWrite.normal(CHARACTER_OWNER, CHARACTER_PRIORITY,
                0, 0, segaBytes(characterPalette)));
        for (ModPaletteClaim claim : creatorClaims) {
            registry.submit(PaletteWrite.normal(creatorOwner, CREATOR_PRIORITY,
                    claim.line(), claim.color(), segaBytes(claim.segaColor())));
        }
        Palette hudPalette = hudPaletteSupplier.get();
        if (hudPalette != null) {
            registry.submit(PaletteWrite.normal(HUD_OWNER, HUD_PRIORITY,
                    hudPaletteLine, 0, segaBytes(hudPalette)));
        }
    }

    private static byte[] segaBytes(Palette palette) {
        byte[] bytes = new byte[Palette.PALETTE_SIZE_IN_ROM];
        for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
            int word = PaletteWriteSupport.segaWordFromColor(palette.getColor(color));
            bytes[color * 2] = (byte) (word >>> 8);
            bytes[color * 2 + 1] = (byte) word;
        }
        return bytes;
    }

    private static byte[] segaBytes(int word) {
        return new byte[]{(byte) (word >>> 8), (byte) word};
    }

    private static void requirePaletteLine(int paletteLine) {
        if (paletteLine < 0 || paletteLine >= 4) {
            throw new IllegalArgumentException("HUD palette line must be 0-3, was: " + paletteLine);
        }
    }
}
