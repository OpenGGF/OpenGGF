package com.openggf.game.soniccd;

import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.audio.CdFmSoundAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12aPlayerAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bPlayerAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailRelease;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached stock-disc capability for the first static R11A consumer. Instances come from
 * {@link CdStockLaunch}, which authenticates actual user media before publishing these assets.
 * USA also retains its authenticated FM bank, finite WARP/R11B program images, Act2 data pairs
 * both Act3 future programmes, and original Collision Chaos Act1 data.
 * JP/EU FM/program consumers remain explicitly unsupported.
 * No retained file handles, cartridge address space, creator root or runtime reference files.
 * This data identity is not a firmware, gameplay or execution-environment certificate.
 */
public final class CdGameDataSource implements GameDataSource {
    public static final String DECODED_BLOCKS = "stage/R11A/blocks.bin";
    public static final String DECODED_TILES = "stage/R11A/tiles.bin";
    public static final String INITIAL_PALETTE = "stage/R11A/palette.bin";

    /** Detached original-USA Act2 data only; this record is not a load or programme capability. */
    public record Act2Assets(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player) {
        public Act2Assets {
            CdStagePlayerSource.fromAct2(Objects.requireNonNull(terrain, "Act2 terrain assets"),
                    Objects.requireNonNull(player, "Act2 player assets"));
        }
    }

    /** Original-USA R12B data only; no execution, load or source-epoch authority. */
    public record Act2PastAssets(CdR12bTerrainAssets terrain, CdR12bPlayerAssets player) {
        public Act2PastAssets {
            CdStagePlayerSource.fromAct2Past(Objects.requireNonNull(terrain, "Act2 Past terrain assets"),
                    Objects.requireNonNull(player, "Act2 Past player assets"));
        }
    }

    /** Detached original-USA Act3 pair. Presence/selection is data, never programme/World admission. */
    public record Act3FutureAssets(CdR13FutureAssets goodFuture, CdR13FutureAssets badFuture) {
        public Act3FutureAssets {
            Objects.requireNonNull(goodFuture, "Act3 good-future assets");
            Objects.requireNonNull(badFuture, "Act3 bad-future assets");
            CdStagePlayerSource.fromAct3Future(goodFuture);
            CdStagePlayerSource.fromAct3Future(badFuture);
            if (!goodFuture.identity().stage().equals(CdStagePayload.R13C.stage())
                    || !badFuture.identity().stage().equals(CdStagePayload.R13D.stage())
                    || !goodFuture.identity().observedWholeDiscSha256().equals(badFuture.identity().observedWholeDiscSha256()))
                throw new IllegalArgumentException("Actual same-disc R13C/R13D roles required");
        }
        /** RunBossLevel (CD Initial Program/IPX.asm:606-614 at N1ecc5f3a) uses TST.B; preserve all255 nonzero selections. No field is written. */
        public CdR13FutureAssets select(int rawGoodFutureByte) {
            if ((rawGoodFutureByte & ~255) != 0) throw new IllegalArgumentException("Native goodFuture BYTE required");
            return rawGoodFutureByte == 0 ? badFuture : goodFuture;
        }
    }

    private final CdRetailRelease release;
    private final CdR11aAssets assets;
    private final Optional<CdFmSoundAssets> fmSoundAssets;
    private final Optional<CdTimeWarpPrograms> timeWarpPrograms;
    private final Optional<Act2Assets> act2Assets;
    private final Optional<Act2PastAssets> act2PastAssets;
    private final Optional<Act3FutureAssets> act3FutureAssets;
    private final Optional<CdR31aAssets> r31aAssets;
    private final String identity;

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets) {
        this(release, assets, fmSoundAssets, Optional.empty());
    }

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets,
                     Optional<CdTimeWarpPrograms> timeWarpPrograms) {
        this(release, assets, fmSoundAssets, timeWarpPrograms, Optional.empty());
    }

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets,
                     Optional<CdTimeWarpPrograms> timeWarpPrograms, Optional<Act2Assets> act2Assets) {
        this(release, assets, fmSoundAssets, timeWarpPrograms, act2Assets, Optional.empty());
    }

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets,
                     Optional<CdTimeWarpPrograms> timeWarpPrograms, Optional<Act2Assets> act2Assets,
                     Optional<Act2PastAssets> act2PastAssets) {
        this(release, assets, fmSoundAssets, timeWarpPrograms, act2Assets, act2PastAssets, Optional.empty());
    }

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets,
                     Optional<CdTimeWarpPrograms> timeWarpPrograms, Optional<Act2Assets> act2Assets,
                     Optional<Act2PastAssets> act2PastAssets, Optional<Act3FutureAssets> act3FutureAssets) {
        this(release, assets, fmSoundAssets, timeWarpPrograms, act2Assets, act2PastAssets, act3FutureAssets, Optional.empty());
    }

    CdGameDataSource(CdRetailRelease release, CdR11aAssets assets, Optional<CdFmSoundAssets> fmSoundAssets,
                     Optional<CdTimeWarpPrograms> timeWarpPrograms, Optional<Act2Assets> act2Assets,
                     Optional<Act2PastAssets> act2PastAssets, Optional<Act3FutureAssets> act3FutureAssets,
                     Optional<CdR31aAssets> r31aAssets) {
        this.release = Objects.requireNonNull(release, "release");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.fmSoundAssets = Objects.requireNonNull(fmSoundAssets, "fmSoundAssets");
        this.timeWarpPrograms = Objects.requireNonNull(timeWarpPrograms, "timeWarpPrograms");
        this.act2Assets = Objects.requireNonNull(act2Assets, "act2Assets");
        this.act2PastAssets = Objects.requireNonNull(act2PastAssets, "act2PastAssets");
        this.act3FutureAssets = Objects.requireNonNull(act3FutureAssets, "act3FutureAssets");
        this.r31aAssets = Objects.requireNonNull(r31aAssets, "r31aAssets");
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
        if (timeWarpPrograms.isPresent()) {
            var programSource = timeWarpPrograms.orElseThrow().source();
            if (release.variant() != CdReleaseVariant.USA || programSource.variant() != binding.variant()
                    || !programSource.wholeDiscSha256().equals(binding.observedWholeDiscSha256())) {
                throw new IllegalArgumentException("Detached programs, stage and observed retail content do not agree");
            }
        }
        if (act2Assets.isPresent()) {
            var pair = act2Assets.orElseThrow();
            // Reuse existing finite relation checks; neither operation reads media or mints runtime authority.
            var next = CdStagePlayerSource.fromAct2(pair.terrain(), pair.player());
            var current = CdStagePlayerSource.fromPresent(assets);
            var nextBinding = pair.terrain().identity();
            if (release.variant() != CdReleaseVariant.USA || next.variant() != binding.variant()
                    || current.variant() != next.variant()
                    || !binding.mmdSha256().equals(assets.profile().expectedMmdSha256())
                    || !nextBinding.observedWholeDiscSha256().equals(binding.observedWholeDiscSha256())
                    || !nextBinding.observedWholeDiscSha256().equals(release.normalizedSha256())) {
                throw new IllegalArgumentException("Detached Act2 pair, stage and observed retail content do not agree");
            }
        }
        if (act2PastAssets.isPresent()) {
            var pair = act2PastAssets.orElseThrow();
            var next = CdStagePlayerSource.fromAct2Past(pair.terrain(), pair.player());
            if (release.variant() != CdReleaseVariant.USA || next.variant() != binding.variant()
                    || !binding.mmdSha256().equals(assets.profile().expectedMmdSha256())
                    || !pair.terrain().identity().observedWholeDiscSha256().equals(binding.observedWholeDiscSha256())
                    || !pair.terrain().identity().observedWholeDiscSha256().equals(release.normalizedSha256()))
                throw new IllegalArgumentException("Detached R12B pair, stage and observed retail content do not agree");
        }
        if (act3FutureAssets.isPresent()) {
            var pair = act3FutureAssets.orElseThrow();
            if (release.variant() != CdReleaseVariant.USA
                    || !pair.goodFuture().identity().observedWholeDiscSha256().equals(binding.observedWholeDiscSha256())
                    || !pair.badFuture().identity().observedWholeDiscSha256().equals(release.normalizedSha256()))
                throw new IllegalArgumentException("Detached R13 pair, stage and observed retail content do not agree");
        }
        if (r31aAssets.isPresent()) {
            var next = r31aAssets.orElseThrow();
            if (release.variant() != CdReleaseVariant.USA
                    || next.identity().variant() != binding.variant()
                    || !next.identity().stage().equals(CdStagePayload.R31A.stage())
                    || next.identity().file() != CdNativeFile.R31A
                    || !next.identity().observedWholeDiscSha256().equals(binding.observedWholeDiscSha256())
                    || !next.identity().observedWholeDiscSha256().equals(release.normalizedSha256()))
                throw new IllegalArgumentException("Detached R31A, stage and observed retail content do not agree");
            // Reuse the authentic private image/cache relation; matching public metadata is insufficient.
            CdObjectPlacements.fromR31aAssets(next);
        }
        // Whole-disc content also covers later data. Identity never certifies capability presence.
        identity = "scd:detached-stage-v1:" + release.key() + ":" + binding.stage().nativeId()
                + ":" + binding.observedWholeDiscSha256() + ":" + binding.mmdSha256();
    }

    public CdRetailRelease release() { return release; }
    public CdR11aAssets assets() { return assets; }

    /** Detached USA FM data only. JP/EU are absent; presence does not install an audio driver. */
    public Optional<CdFmSoundAssets> fmSoundAssets() { return fmSoundAssets; }

    /** Explicit finite USA program data only; presence does not load or execute either program. */
    public Optional<CdTimeWarpPrograms> timeWarpPrograms() { return timeWarpPrograms; }

    /** Detached USA Act2 source pair only; presence selects no stage and performs no load or execution. */
    public Optional<Act2Assets> act2Assets() { return act2Assets; }

    /** Detached actual USA R12B pair; no target entry or runtime readiness is implied. */
    public Optional<Act2PastAssets> act2PastAssets() { return act2PastAssets; }

    /** Detached actual USA C/D pair; no Frame, epoch, selected programme or runtime readiness is implied. */
    public Optional<Act3FutureAssets> act3FutureAssets() { return act3FutureAssets; }

    /** Detached original-USA R31A only; no programme, Frame, FULL or runtime admission. */
    public Optional<CdR31aAssets> r31aAssets() { return r31aAssets; }

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
