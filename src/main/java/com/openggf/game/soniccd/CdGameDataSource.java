package com.openggf.game.soniccd;

import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.audio.CdFmSoundAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.release.CdRetailRelease;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached stock-disc capability for the first static R11A consumer. Instances come from
 * {@link CdStockLaunch}, which authenticates actual user media before publishing these assets.
 * USA also retains its authenticated FM data bank; JP/EU FM data remains explicitly unsupported.
 * No retained file handles, cartridge address space, creator root or runtime reference files.
 * This data identity is not a firmware, gameplay or execution-environment certificate.
 */
public final class CdGameDataSource implements GameDataSource {
    public static final String DECODED_BLOCKS = "stage/R11A/blocks.bin";
    public static final String DECODED_TILES = "stage/R11A/tiles.bin";
    public static final String INITIAL_PALETTE = "stage/R11A/palette.bin";

    private final CdRetailRelease release;
    private final CdR11aAssets assets;
    private final Optional<CdFmSoundAssets> fmSoundAssets;
    private final String identity;

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets) {
        this.release = Objects.requireNonNull(release, "release");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.fmSoundAssets = Objects.requireNonNull(fmSoundAssets, "fmSoundAssets");
        var binding = assets.identity();
        if (binding.variant() != release.variant()
                || !binding.observedWholeDiscSha256().equals(release.normalizedSha256())) {
            throw new IllegalArgumentException("Detached stage and observed retail content do not agree");
        }
        // This is a data-retention policy, not audio installation or playback admission.
        if ((release.variant() == CdReleaseVariant.USA) != fmSoundAssets.isPresent()) {
            throw new IllegalArgumentException("USA requires authenticated FM data; JP/EU FM data is unsupported");
        }
        if (fmSoundAssets.isPresent()) {
            var fmSource = fmSoundAssets.get().source();
            if (fmSource.variant() != binding.variant()
                    || !fmSource.wholeDiscSha256().equals(binding.observedWholeDiscSha256())) {
                throw new IllegalArgumentException("Detached FM, stage and observed retail content do not agree");
            }
        }
        // The observed whole-disc digest already covers MDINIT and its authenticated FM slice.
        identity = "scd:detached-stage-v1:" + release.key() + ":" + binding.stage().nativeId()
                + ":" + binding.observedWholeDiscSha256() + ":" + binding.mmdSha256();
    }

    public CdRetailRelease release() { return release; }
    public CdR11aAssets assets() { return assets; }

    /** Detached USA FM data only. JP/EU are absent; presence does not install an audio driver. */
    public Optional<CdFmSoundAssets> fmSoundAssets() { return fmSoundAssets; }

    @Override public Optional<Rom> rom() { return Optional.empty(); }
    @Override public String identity() { return identity; }

    /**
     * Three exact, reviewed logical views. Unknown or non-normalized names fail rather than
     * resolving host paths or exposing an unimplemented native consumer such as shield DMA.
     * Each stream owns a copy and an independent cursor; all reads survive the original disc close.
     */
    @Override public InputStream openAsset(String normalizedPath) throws FileNotFoundException {
        Objects.requireNonNull(normalizedPath, "normalizedPath");
        CdAssetBytes bytes = switch (normalizedPath) {
            case DECODED_BLOCKS -> assets.blocksDecode().bytes();
            case DECODED_TILES -> assets.levelTilesDecode().bytes();
            case INITIAL_PALETTE -> assets.loadPlan().initialPalette();
            default -> throw new FileNotFoundException("Unsupported detached CD asset: " + normalizedPath);
        };
        return new ByteArrayInputStream(bytes.copy());
    }
}
