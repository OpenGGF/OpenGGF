package com.openggf.game.sonic3k;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.CustomZonePaletteBridge;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.level.Palette;
import com.openggf.level.Level;
import com.openggf.level.LevelOrigin;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Composes immutable custom-zone palette claims with host-owned character and
 * HUD palette data. Stock levels do not install this bridge.
 */
public final class S3kCustomZonePaletteBridge implements CustomZonePaletteBridge {
    public static final String CHARACTER_OWNER = "host:s3k-character";
    public static final String HUD_OWNER = "host:s3k-hud";

    private static final int CHARACTER_PRIORITY = 0;
    private static final int CREATOR_PRIORITY = 10;
    /** Intrinsic host presentation must win over all established S3K priorities (90-300). */
    static final int HUD_PRIORITY = 1000;

    private final String creatorOwner;
    private final Palette characterPalette;
    private final List<ModPaletteClaim> creatorClaims;
    private final int hudPaletteLine;
    private final boolean[] hudUsedColors;
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
                                      HudStaticArt hudStaticArt,
                                      Pattern[] hudLivesNumbers,
                                      Supplier<Palette> hudPaletteSupplier) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        this.creatorOwner = Objects.requireNonNull(creatorOwner, "creatorOwner");
        this.characterPalette = Objects.requireNonNull(characterPalette, "characterPalette").deepCopy();
        this.creatorClaims = List.copyOf(Objects.requireNonNull(creatorClaims, "creatorClaims"));
        requirePaletteLine(hudPaletteLine);
        this.hudPaletteLine = hudPaletteLine;
        this.hudUsedColors = deriveHudUsedColors(hudPaletteLine, hudStaticArt, hudLivesNumbers);
        this.hudPaletteSupplier = Objects.requireNonNull(hudPaletteSupplier, "hudPaletteSupplier");
        validateCreatorClaims(ownerModId, creatorClaims, hudPaletteLine, hudUsedColors);
    }

    /** Validates the character-line reservation before a creator zone is published. */
    public static void validateCreatorClaims(String ownerModId,
                                             List<ModPaletteClaim> creatorClaims) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(creatorClaims, "creatorClaims");
        for (ModPaletteClaim claim : creatorClaims) {
            Objects.requireNonNull(claim, "creator palette claim");
            if (claim.line() == 0) {
                throw new ModZoneRegistrationException(ownerModId,
                        "MOD_PALETTE_RESERVED",
                        "Creator palette claim targets host-reserved line " + claim.line()
                                + ", color " + claim.color(),
                        null, null);
            }
        }
    }

    private static void validateCreatorClaims(String ownerModId,
                                              List<ModPaletteClaim> creatorClaims,
                                              int hudPaletteLine,
                                              boolean[] hudUsedColors) {
        validateCreatorClaims(ownerModId, creatorClaims);
        for (ModPaletteClaim claim : creatorClaims) {
            if (claim.line() == hudPaletteLine && hudUsedColors[claim.color()]) {
                throw new ModZoneRegistrationException(ownerModId,
                        "MOD_PALETTE_RESERVED",
                        "Creator palette claim targets host HUD-reserved line " + claim.line()
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
        Palette override = hudPaletteSupplier.get();
        Palette hudPalette = override != null ? override : characterPalette;
        for (int color = 1; color < hudUsedColors.length; color++) {
            if (hudUsedColors[color]) {
                registry.submit(PaletteWrite.normal(HUD_OWNER, HUD_PRIORITY,
                        hudPaletteLine, color,
                        segaBytes(PaletteWriteSupport.segaWordFromColor(hudPalette.getColor(color)))));
            }
        }
    }

    private static boolean[] deriveHudUsedColors(int hudPaletteLine,
                                                  HudStaticArt staticArt,
                                                  Pattern[] livesNumbers) {
        boolean[] used = new boolean[Palette.PALETTE_SIZE];
        if (staticArt != null && staticArt.livesFrame() != null && staticArt.patterns() != null) {
            Pattern[] patterns = staticArt.patterns();
            for (SpriteMappingPiece piece : staticArt.livesFrame().pieces()) {
                if (piece == null || piece.paletteIndex() != hudPaletteLine) {
                    continue;
                }
                int tileCount = Math.multiplyExact(piece.widthTiles(), piece.heightTiles());
                for (int tile = 0; tile < tileCount; tile++) {
                    int patternIndex = Math.addExact(piece.tileIndex(), tile);
                    if (patternIndex < 0 || patternIndex >= patterns.length) {
                        throw new IllegalArgumentException(
                                "HUD lives mapping references missing pattern " + patternIndex);
                    }
                    collectNonzeroColors(patterns[patternIndex], used);
                }
            }
        }
        if (livesNumbers != null) {
            for (Pattern pattern : livesNumbers) {
                collectNonzeroColors(Objects.requireNonNull(pattern, "HUD lives number pattern"), used);
            }
        }
        return used;
    }

    private static void collectNonzeroColors(Pattern pattern, boolean[] used) {
        Objects.requireNonNull(pattern, "HUD pattern");
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                int color = Byte.toUnsignedInt(pattern.getPixel(x, y));
                if (color != 0) {
                    used[color] = true;
                }
            }
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
