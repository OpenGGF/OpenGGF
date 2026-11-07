package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Exact original-USA R3PCM file custody only. SPX LoadLevel selects R3PCM for R31A
 * (N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff SPX.asm:745-773,976).
 * Collision Chaos.asm:15-19 selects SNCBNK27.S28/BOSS0/shared _Driver.asm.
 * Measured from original complete USA media in memory, never a native-source asset fallback.
 * No MMD coordinates, device, code execution, UpdateDriver/lazy InitDriver, clock, output,
 * source-selection invocation, World admission or completed programme acknowledgement is created.
 */
public final class CdR3PcmSource {
    private static final int FILE_BYTES = 242597;
    private static final String FILE_SHA = "82b9ac37eddbf379155ab76407efc1596e61c7707c592eb47e41cfc468012340";

    /** Finite descriptive value; constructing it cannot mint this private-constructed byte owner. */
    public record Source(CdReleaseVariant variant, String wholeDiscSha256, String fileSha256) {
        public Source {
            Objects.requireNonNull(variant);
            Objects.requireNonNull(wholeDiscSha256);
            Objects.requireNonNull(fileSha256);
            if (variant != CdReleaseVariant.USA
                    || !CdRetailReleases.USA.normalizedSha256().equals(wholeDiscSha256)
                    || !FILE_SHA.equals(fileSha256)) {
                throw new IllegalArgumentException("Only exact original-USA R3PCM metadata");
            }
        }
    }

    private final Source source;
    private final CdAssetBytes bytes;

    private CdR3PcmSource(Source source, CdAssetBytes bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    /** Borrows one open complete-disc pipeline, retaining only the exact finite original R3 file. */
    public static CdR3PcmSource fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual complete-disc owner");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("R3PCM requires a known original complete-disc profile"));
        if (release.variant() != CdReleaseVariant.USA
                || programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("Only original USA R3PCM is source-qualified");
        }
        var entry = programs.entry(CdNativeFile.R3PCM);
        if (CdNativeFile.R3PCM.kind() != CdNativeFile.Kind.PCM_DRIVER
                || !"/SNCBNK3B.BIN;1".equals(entry.path()) || entry.size() != FILE_BYTES) {
            throw new IOException("Original USA R3PCM file path/extent differs");
        }
        byte[] raw = programs.read(CdNativeFile.R3PCM, 0, FILE_BYTES);
        if (raw.length != FILE_BYTES || !FILE_SHA.equals(sha(raw))) {
            throw new IOException("Original USA R3PCM content differs");
        }
        var data = CdAssetBytes.copyOf(raw);
        if (!Arrays.equals("SNCBNK27.S28    ".getBytes(StandardCharsets.US_ASCII), data.slice(0, 16).copy())
                || data.unsignedLong(0x10) != 0x4EFA0906L || data.unsignedLong(0x14) != 0x4EFA0F90L) {
            throw new IOException("Original R3 non-Boss header/entry source differs");
        }
        for (int i = 0; i < 0x900; i++) {
            if (data.unsignedByte(0x18 + i) != 0) throw new IOException("Original R3 variable template differs");
        }
        return new CdR3PcmSource(new Source(release.variant(), programs.identity().normalizedSha256(), FILE_SHA), data);
    }

    public Source source() { return source; }
    public CdNativeFile file() { return CdNativeFile.R3PCM; }
    public CdAssetBytes bytes() { return bytes; }

    private static String sha(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
