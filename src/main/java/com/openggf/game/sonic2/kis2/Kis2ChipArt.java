package com.openggf.game.sonic2.kis2;

import com.openggf.data.PaletteLoader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The chip-resident KiS2 assets, read through the lock-on address space
 * ({@code docs/kis2/BRANCH_DIFFS.md} §Chip addresses). Every asset was
 * authored for Sonic 2's palette layout, so unlike the S&amp;K-side Knuckles art
 * nothing here passes through {@code ArtConvTable}.
 *
 * <p>Requires an address space whose chip window is present
 * ({@link LockOnAddressSpace#hasChip()}); tier one never constructs one.
 */
public final class Kis2ChipArt {

    /** Nemesis art the chip holds, keyed by the KiS2 label that owns it. */
    public enum Asset {
        /** {@code ArtNem_Sonic_life_counter} = "Knuckles lives counter.nem". */
        LIFE_COUNTER(Kis2Constants.ART_NEM_KNUCKLES_LIFE_COUNTER, 12),
        /** {@code ArtNem_MiniSonic} = "Knuckles continue.nem". */
        CONTINUE_ICON(Kis2Constants.ART_NEM_KNUCKLES_CONTINUE, 12),
        /** {@code ArtNem_Shield_and_invincible_stars}. */
        SHIELD_AND_STARS(Kis2Constants.ART_NEM_SHIELD_AND_STARS, 66),
        /** {@code ArtNem_SignpostKnucklesPatch}. */
        SIGNPOST_PATCH(Kis2Constants.ART_NEM_SIGNPOST_KNUCKLES_PATCH, 24),
        /** {@code ArtNem_PowerupsKnucklesPatch}. */
        POWERUPS_PATCH(Kis2Constants.ART_NEM_POWERUPS_KNUCKLES_PATCH, 8);

        private final int address;
        private final int tileCount;

        Asset(int address, int tileCount) {
            this.address = address;
            this.tileCount = tileCount;
        }

        public int address() {
            return address;
        }

        /** Tile count declared by the asset's Nemesis header. */
        public int tileCount() {
            return tileCount;
        }
    }

    private static final int NEMESIS_READ_SIZE = 4096;

    private final LockOnAddressSpace addressSpace;
    private final Map<Asset, Pattern[]> cache = new EnumMap<>(Asset.class);

    public Kis2ChipArt(LockOnAddressSpace addressSpace) {
        this.addressSpace = Objects.requireNonNull(addressSpace, "addressSpace");
        if (!addressSpace.hasChip()) {
            throw new IllegalArgumentException("Kis2ChipArt needs the chip window");
        }
    }

    /** Decompressed tiles of {@code asset}; the array is a fresh copy. */
    public Pattern[] load(Asset asset) {
        Pattern[] tiles = cache.get(asset);
        if (tiles == null) {
            LockOnAddressSpace.Read read = addressSpace.require(asset.address());
            int available = Math.min(NEMESIS_READ_SIZE, read.reader().size() - read.localAddress());
            byte[] compressed = read.reader().slice(read.localAddress(), available);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                tiles = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
            } catch (IOException e) {
                throw new UncheckedIOException("KiS2 chip asset " + asset + " unreadable", e);
            }
            cache.put(asset, tiles);
        }
        return tiles.clone();
    }

    /** {@code ArtNem_Shield_and_invincible_stars} tiles 0-31: the shield ({@code ArtTile_ArtNem_Shield}). */
    public Pattern[] shield() {
        return Arrays.copyOfRange(load(Asset.SHIELD_AND_STARS), 0, Kis2Constants.SHIELD_TILE_COUNT);
    }

    /** Tiles 32-65 of the merged art: the stars ({@code ArtTile_ArtNem_Invincible_stars}). */
    public Pattern[] invincibilityStars() {
        Pattern[] merged = load(Asset.SHIELD_AND_STARS);
        return Arrays.copyOfRange(merged, Kis2Constants.SHIELD_TILE_COUNT, merged.length);
    }

    /** {@code Pal_BGND} line 0: the KiS2 "Sonic and Miles" line with Knuckles' colours. */
    public Palette backgroundPaletteLine0() {
        LockOnAddressSpace.Read read = addressSpace.require(Kis2Constants.PAL_BGND);
        Palette palette = new Palette();
        palette.fromSegaFormat(read.reader().slice(read.localAddress(), Palette.PALETTE_SIZE_IN_ROM));
        return palette;
    }

    /** {@code Pal_CPZ_U}: the four underwater lines {@code PalLoad_Water} copies for Chemical Plant. */
    public Palette[] chemicalPlantUnderwaterPalette() {
        return underwaterPalette(Kis2Constants.PAL_CPZ_U);
    }

    /** {@code Pal_ARZ_U}: the four underwater lines for Aquatic Ruin. */
    public Palette[] aquaticRuinUnderwaterPalette() {
        return underwaterPalette(Kis2Constants.PAL_ARZ_U);
    }

    private Palette[] underwaterPalette(int address) {
        LockOnAddressSpace.Read read = addressSpace.require(address);
        return PaletteLoader.fromBytes(read.reader().slice(read.localAddress(),
                Kis2Constants.UNDERWATER_PALETTE_SIZE));
    }
}
