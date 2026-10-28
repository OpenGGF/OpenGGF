package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;

/**
 * One colour step of the level program's palette fades, after its actual VSync.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/Palette Fade.asm:104-343.
 * The two callers first write palFadeInfo=$003F, then perform22 VSync/colour/ProcessPLCs iterations.
 * These functions implement only their full64-word colour passes, normal palette THEN water palette.
 * They have no clock, wait, fade progress, renderer, PLC or program-lifetime authority. WARP's separate
 * 24-pulse fade is a different algorithm/caller and must not borrow this iteration count.
 *
 * <p>To-white/black require both current128-byte buffers. From-white processes one explicit
 * current/target pair; only the native zone1 caller selects a second water pair. No missing
 * target, waterFade/SAT alias or native RAM is supplied. Full WORD arithmetic retains dirty bits;
 * from-white uses unsigned comparisons/borrow, rather than per-channel RGB clamping.
 */
public final class CdLevelPaletteFade {
    private static final int PALETTE_BYTES = 128;
    private CdLevelPaletteFade() { }

    /** Complete value data only; immutable bytes and bytewise equality support retained/replayed cuts. */
    public record Palettes(CdAssetBytes palette, CdAssetBytes waterPalette) {
        public Palettes {
            Objects.requireNonNull(palette, "explicit normal palette");
            Objects.requireNonNull(waterPalette, "explicit water palette");
            if (palette.length() != PALETTE_BYTES || waterPalette.length() != PALETTE_BYTES)
                throw new IllegalArgumentException("Native $003F fade requires two complete128-byte palettes");
        }
        @Override public boolean equals(Object other) {
            return other instanceof Palettes value && Arrays.equals(palette.copy(), value.palette.copy())
                    && Arrays.equals(waterPalette.copy(), value.waterPalette.copy());
        }
        @Override public int hashCode() {
            return 31 * Arrays.hashCode(palette.copy()) + Arrays.hashCode(waterPalette.copy());
        }
    }

    public static Palettes stepToWhite(Palettes source) { return step(Objects.requireNonNull(source), true); }
    public static Palettes stepToBlack(Palettes source) { return step(Objects.requireNonNull(source), false); }

    /**
     * FadeColorsFromWhite's one64-word pass (Palette Fade.asm:207-268).
     * The caller owns the initial fill, zone1 water selection and22 waits/PLC iterations.
     * Both arguments are complete values; neither target nor current is mutated.
     */
    public static CdAssetBytes stepFromWhite(CdAssetBytes current, CdAssetBytes target) {
        Objects.requireNonNull(current, "explicit current palette");
        Objects.requireNonNull(target, "explicit target palette");
        if (current.length() != PALETTE_BYTES || target.length() != PALETTE_BYTES)
            throw new IllegalArgumentException("Native $003F fade requires complete128-byte current and target palettes");
        byte[] bytes = current.copy();
        for (int at = 0; at < PALETTE_BYTES; at += 2) {
            int colour = fromWhite(current.unsignedWord(at), target.unsignedWord(at));
            bytes[at] = (byte) (colour >>> 8); bytes[at + 1] = (byte) colour;
        }
        return CdAssetBytes.copyOf(bytes);
    }

    private static int fromWhite(int current, int target) {
        if (current == target) return current;
        // SUBI.W then BCS, followed by CMP.W/BCS: both comparisons use the entire unsigned WORD.
        if (current >= 0x200 && current - 0x200 >= target) return current - 0x200;
        if (current >= 0x20 && current - 0x20 >= target) return current - 0x20;
        return (current - 2) & 0xFFFF;
    }

    private static Palettes step(Palettes source, boolean white) {
        return new Palettes(colours(source.palette(), white), colours(source.waterPalette(), white));
    }
    private static CdAssetBytes colours(CdAssetBytes source, boolean white) {
        byte[] bytes = source.copy();
        for (int at = 0; at < PALETTE_BYTES; at += 2) {
            int before = source.unsignedWord(at);
            int after = white ? toWhite(before) : toBlack(before);
            bytes[at] = (byte) (after >>> 8); bytes[at + 1] = (byte) after;
        }
        return CdAssetBytes.copyOf(bytes);
    }
    private static int toWhite(int colour) {
        if (colour == 0x0EEE) return colour;
        if ((colour & 0x000E) != 0x000E) return (colour + 2) & 0xFFFF;
        if ((colour & 0x00E0) != 0x00E0) return (colour + 0x20) & 0xFFFF;
        if ((colour & 0x0E00) != 0x0E00) return (colour + 0x200) & 0xFFFF;
        return colour;
    }
    private static int toBlack(int colour) {
        if (colour == 0) return colour;
        if ((colour & 0x000E) != 0) return (colour - 2) & 0xFFFF;
        if ((colour & 0x00E0) != 0) return (colour - 0x20) & 0xFFFF;
        if ((colour & 0x0E00) != 0) return (colour - 0x200) & 0xFFFF;
        return colour;
    }
}
