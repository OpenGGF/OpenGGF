package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact original-USA R4PCM file custody only. SPX LoadLevel selects R4PCM for R41A
 * (N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff SPX.asm:745-773,976).
 * Tidal Tempest.asm:15-19 selects SNCBNK28.S28/BOSS0/shared _Driver.asm.
 * Measured from original complete USA media in memory, never a native-source asset fallback.
 * No MMD coordinates, device, code execution, UpdateDriver/lazy InitDriver, clock, output,
 * source-selection invocation, World admission or completed programme acknowledgement is created.
 */
public final class CdR4PcmSource {
    private static final String FILE_SHA = "7746a04d60551aeeae724a2c6d77b48d7ff52bb050707495010b514e10b41296";

    /** Finite descriptive value; constructing it cannot mint this private-constructed byte owner. */
    public record Source(CdReleaseVariant variant, String wholeDiscSha256, String fileSha256) {
        public Source {
            Objects.requireNonNull(variant);
            Objects.requireNonNull(wholeDiscSha256);
            Objects.requireNonNull(fileSha256);
            if (variant != CdReleaseVariant.USA
                    || !CdRetailReleases.USA.normalizedSha256().equals(wholeDiscSha256)
                    || !FILE_SHA.equals(fileSha256)) {
                throw new IllegalArgumentException("Only exact original-USA R4PCM metadata");
            }
        }
    }

    private final Source source;
    private final CdAssetBytes bytes;

    private CdR4PcmSource(Source source, CdAssetBytes bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    /** Borrows one open complete-disc pipeline, retaining only the exact finite original R4 file. */
    public static CdR4PcmSource fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual complete-disc owner");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("R4PCM requires a known original complete-disc profile"));
        if (release.variant() != CdReleaseVariant.USA
                || programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("Only original USA R4PCM is source-qualified");
        }
        var data = CdPcmFileSource.read(programs, CdPcmFileSource.R4);
        return new CdR4PcmSource(new Source(release.variant(), programs.identity().normalizedSha256(), FILE_SHA), data);
    }

    public Source source() { return source; }
    public CdNativeFile file() { return CdNativeFile.R4PCM; }
    public CdAssetBytes bytes() { return bytes; }

}
