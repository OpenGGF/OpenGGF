package com.openggf.game.soniccd.media;

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
 * Exact original-USA SPX___.BIN source custody. This raw system binary has a filename-table
 * prelude, not an MMD header. Source labels: N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * CD System Program/SP.asm:94-99; SPX.asm:20-22,298-304; _Include/System.inc:371,380-382,438-443.
 * The literal entry/clear operands were independently checked in original complete USA media.
 * No boot, clear, selected-driver transition, CPU/bus/WordRAM protocol, installation, clock,
 * World admission or original operation completion occurs here. Metadata never grants those owners.
 */
public final class CdSystemProgramSource {
    private static final String FILE_PATH = "/SPX___.BIN;1";
    private static final int FILE_BYTES = 17408;
    private static final long ISO_LBA = 10237;
    private static final String FILE_SHA = "f0e9805a060fdd201e9f9e0043bd1d89d61ea69bd3de429bd4c2a83dd498f0d5";
    private static final int LOAD_ADDRESS = 0xB800;
    private static final int ENTRY_ADDRESS = 0xC000;
    private static final InitializationClear INITIAL_CLEAR = new InitializationClear(0x800, 0x7000, 0x400, 0, 4);

    /** Finite observed/source metadata only; constructing an equal record cannot mint the byte owner. */
    public record Source(CdReleaseVariant variant, String wholeDiscSha256, String filePath,
                         long isoLba, int fileBytes, String fileSha256, int loadAddress, int entryAddress) {
        public Source {
            Objects.requireNonNull(variant); Objects.requireNonNull(wholeDiscSha256);
            Objects.requireNonNull(filePath); Objects.requireNonNull(fileSha256);
            if (variant != CdReleaseVariant.USA
                    || !CdRetailReleases.USA.normalizedSha256().equals(wholeDiscSha256)
                    || !FILE_PATH.equals(filePath) || isoLba != ISO_LBA || fileBytes != FILE_BYTES
                    || !FILE_SHA.equals(fileSha256) || loadAddress != LOAD_ADDRESS || entryAddress != ENTRY_ADDRESS) {
                throw new IllegalArgumentException("Only exact original-USA system-program metadata");
            }
        }
    }

    /**
     * Inert literal/source relation: file800 LEA7000; DBF255 with four-byte stores describes
     * [7000,7400), whose first LONG is curPCMDriver. This performs no store or transition.
     * Offsets/addresses are descriptive, never an arbitrary RAM/address reader or execution API.
     */
    public record InitializationClear(int instructionFileOffset, int targetAddress, int byteCount,
                                      int selectorOffset, int selectorBytes) {
        public InitializationClear {
            if (instructionFileOffset != 0x800 || targetAddress != 0x7000 || byteCount != 0x400
                    || selectorOffset != 0 || selectorBytes != 4) {
                throw new IllegalArgumentException("Only the exact original system initialization clear descriptor");
            }
        }
    }

    private final Source source;
    private final CdAssetBytes bytes;

    private CdSystemProgramSource(Source source, CdAssetBytes bytes) {
        this.source = source;
        this.bytes = bytes;
    }

    /** Borrows the same open authenticated complete-disc pipeline and detaches only this finite file. */
    public static CdSystemProgramSource fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual complete-disc owner");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("System source requires a known original complete-disc profile"));
        if (release.variant() != CdReleaseVariant.USA
                || programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("Only original USA system-program source is qualified");
        }
        var original = programs.systemProgramFile();
        var entry = original.entry();
        if (!FILE_PATH.equals(entry.path()) || entry.directory() || entry.version() != 1
                || entry.extentBlock() != ISO_LBA || entry.size() != FILE_BYTES) {
            throw new IOException("Original USA system-program source relation differs");
        }
        var data = original.bytes();
        if (data.length() != FILE_BYTES || !FILE_SHA.equals(sha(data.copy()))) {
            throw new IOException("Original USA system-program complete file differs");
        }
        // Raw filename prelude, not flags/origin/size/interrupt fields of an MMD header.
        if (!Arrays.equals("R11A__.MMD;1\0".getBytes(StandardCharsets.US_ASCII), data.slice(0, 13).copy())
                || !Arrays.equals(HexFormat.of().parseHex("41f870003e3c00ff20fc0000000051cffff8"),
                        data.slice(0x800, 18).copy())) {
            throw new IOException("Original system filename prelude or initialization operand relation differs");
        }
        return new CdSystemProgramSource(new Source(release.variant(), programs.identity().normalizedSha256(),
                entry.path(), entry.extentBlock(), (int) entry.size(), FILE_SHA, LOAD_ADDRESS, ENTRY_ADDRESS), data);
    }

    public Source source() { return source; }
    public CdAssetBytes bytes() { return bytes; }
    public InitializationClear initializationClear() { return INITIAL_CLEAR; }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
