package com.openggf.game.soniccd;

import com.openggf.game.soniccd.audio.CdFmSoundAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailRelease;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Prepare authenticated detached data for development static-stage admission through the normal
 * engine. This does not boot firmware, execute native programs, select a mode or publish a world.
 * The CUE owner closes before the capability is returned, including every failure path.
 */
public final class CdStockLaunch {
    private CdStockLaunch() { }

    /** Software/content requests are assertions, distinct from a future execution environment. */
    public record Request(CdStageKey stage, Optional<CdReleaseVariant> requestedVariant,
                          Optional<String> requestedContentKey) {
        public Request {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(requestedVariant, "requestedVariant");
            Objects.requireNonNull(requestedContentKey, "requestedContentKey");
            if (requestedContentKey.isPresent() && requestedContentKey.get().isBlank()) {
                throw new IllegalArgumentException("Requested content key is blank");
            }
        }

        public static Request r11a() {
            return new Request(CdStageKey.parse("R11A"), Optional.empty(), Optional.empty());
        }
    }

    public static CdGameDataSource prepare(Path cue, Request request) throws IOException {
        Objects.requireNonNull(cue, "cue");
        Objects.requireNonNull(request, "request");
        // Consult the actual loader's supported stage profile before opening or hashing media.
        // USA here is only a profile preflight; absent requested variants are observed below.
        CdR11aProfile.forStage(request.requestedVariant().orElse(CdReleaseVariant.USA), request.stage());
        try (var media = CdProgramAssets.open(cue, request.requestedVariant(), Optional.empty())) {
            CdRetailRelease release = knownContent(media, request.requestedContentKey());
            var assets = CdR11aAssetLoader.load(media, release.variant(), request.stage());
            // Only USA's authenticated MDINIT bank is admitted. Regional static preparation stays supported.
            Optional<CdFmSoundAssets> fmSoundAssets = release.variant() == CdReleaseVariant.USA
                    ? Optional.of(CdFmSoundAssets.fromPrograms(media)) : Optional.empty();
            return new CdGameDataSource(release, assets, fmSoundAssets);
        }
    }

    private static CdRetailRelease knownContent(CdProgramAssets media, Optional<String> requested) throws IOException {
        if (requested.isPresent()) {
            try {
                return CdRetailReleases.requireRequested(media.identity(), requested.get());
            } catch (IllegalArgumentException mismatch) {
                throw new IOException("Requested Sonic CD content does not match supplied media", mismatch);
            }
        }
        return CdRetailReleases.find(media.identity())
                .orElseThrow(() -> new IOException("Static Sonic CD entry requires a known complete retail content record"));
    }
}
