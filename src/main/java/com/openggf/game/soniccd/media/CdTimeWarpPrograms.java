package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Detached original USA WARP and R11B files from one authenticated disc owner.
 * Source pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Time Warp Cutscene/Main.asm:117-119,
 * Level/Palmtree Panic/Act 1 Past.asm and IPX.asm:1330-1340. Runtime reads only supplied media;
 * native source and research reports are never fallback assets.
 *
 * <p>These are finite immutable program data, not an executing program, loader, clock, palette or
 * gameplay certificate. WARP's declared relocation extends5302 bytes beyond its original file;
 * the existing image preserves that unresolved tail rather than manufacturing zeros or RAM.
 * R11B's shared header does not authorize R11A bytes as a Past level. Program-lifetime admission,
 * decoding, world transition and rewind remain with their actual future owners.
 */
public final class CdTimeWarpPrograms {
    /** Observed metadata only; constructing a Source cannot mint a usable program bundle. */
    public record Source(CdReleaseVariant variant, String wholeDiscSha256, String warpSha256, String r11bSha256) {
        public Source {
            Objects.requireNonNull(variant); Objects.requireNonNull(wholeDiscSha256);
            Objects.requireNonNull(warpSha256); Objects.requireNonNull(r11bSha256);
        }
    }

    private static final String WARP_SHA256 = "a417c910f61e0d95b33f292b3e84206e1f71018c4478c83da86737b10d0ffe06";
    private static final String R11B_SHA256 = "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final CdMmdHeader WARP_HEADER = new CdMmdHeader(0, 0, 0xFF2000, 0x0BFF, 0xFF2000, 0, 0);
    private static final CdMmdHeader R11B_HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);

    private final Source source;
    private final CdMmdImage warp;
    private final CdMmdImage r11b;

    private CdTimeWarpPrograms(Source source, CdMmdImage warp, CdMmdImage r11b) {
        this.source = source; this.warp = warp; this.r11b = r11b;
    }

    /**
     * Borrow the existing complete-disc owner while open, then retain only its two detached images.
     * JP/EU, unknown complete-disc observations, wrong files and unresolved-byte substitution reject;
     * rejection never closes the caller's disc or installs a program/audio/presentation owner.
     */
    public static CdTimeWarpPrograms fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual CD program owner");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("Warp programs require an observed known retail content record"));
        if (release.variant() != CdReleaseVariant.USA
                || programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("Only authenticated USA WARP and R11B program files are admitted");
        }
        var warp = programs.mmd(CdNativeFile.WARP);
        var warpHash = verifyProgram(warp, 7242, WARP_HEADER, 6986, 5302, WARP_SHA256, "WARP");
        var r11b = programs.mmd(CdNativeFile.R11B);
        var pastHash = verifyProgram(r11b, 262144, R11B_HEADER, 0, 0, R11B_SHA256, "R11B");
        return new CdTimeWarpPrograms(new Source(release.variant(), programs.identity().normalizedSha256(),
                warpHash, pastHash), warp, r11b);
    }

    public Source source() { return source; }
    public CdMmdImage warp() { return warp; }
    public CdMmdImage r11b() { return r11b; }

    private static String verifyProgram(CdMmdImage image, int fileBytes, CdMmdHeader header,
                                        int backedCopyBytes, int unresolvedCopyBytes, String expected, String name)
            throws IOException {
        Objects.requireNonNull(image, "detached " + name + " image");
        if (image.fileBytes() != fileBytes || !header.equals(image.header())
                || image.fileBackedCopyBytes() != backedCopyBytes || image.unresolvedCopyBytes() != unresolvedCopyBytes) {
            throw new IOException("USA " + name + " header or file extent does not match its authenticated source");
        }
        var observed = sha256(image.readFile(0, image.fileBytes()));
        if (!expected.equals(observed)) {
            throw new IOException("USA " + name + " content does not match its authenticated source");
        }
        return observed;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("Required SHA-256 is unavailable", impossible); }
    }
}
