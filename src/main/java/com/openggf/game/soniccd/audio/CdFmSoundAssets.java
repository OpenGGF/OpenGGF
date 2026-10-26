package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Detached, authenticated USA MDINIT FM program/SFX bytes in their origin-zero Z80 domain.
 * This is data ownership, not a native driver execution, SMPS parser, audio installation or clock.
 * Source pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Mega Drive Init/Main.asm Start:46-59,
 * FMSFXDriver:133-143; Sound Drivers/FM/_Driver.asm DriverInfo:1874-1886.
 */
public final class CdFmSoundAssets {
    public static final int FILE_OFFSET = 0x1DA;
    public static final int Z80_ORIGIN = 0;
    private static final int BANK_BYTES = 0x1AD6;
    private static final int INDEX = 0x6EB;
    private static final int PRIORITIES = 0x78B;
    private static final int PRIORITY_BYTES = 79;
    private static final int FIRST_SFX = 0x90;
    private static final int LAST_SFX = 0xDF;
    private static final String MDINIT_SHA256 =
            "abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e";
    private static final String BANK_SHA256 =
            "23f3df846b228c8de5b55cb6503318f05df1a49c5bdfe282ceecf2502ce5bc99";

    /** Observed content metadata; only the private asset constructor publishes a usable bank. */
    public record Source(CdReleaseVariant variant, String wholeDiscSha256, CdNativeFile file,
                         String mdinitSha256, CdMmdHeader header, int unresolvedRelocationBytes) { }

    private final Source source;
    private final CdAssetBytes bank;

    private CdFmSoundAssets(Source source, CdAssetBytes bank) {
        this.source = source;
        this.bank = bank;
    }

    /**
     * Read through the existing observed-disc/program capability while it is open. No file handle is retained.
     * Other regional offsets/driver identities remain unadmitted. Missing relocation bytes are never padded:
     * the native byte-copy source is entirely inside the actual file-backed MDINIT payload.
     */
    public static CdFmSoundAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual CD program owner");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("FM assets require an observed known retail content record"));
        if (release.variant() != CdReleaseVariant.USA
                || programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("Only the authenticated USA MDINIT FM bank is admitted");
        }
        var image = programs.mmd(CdNativeFile.MDINIT);
        byte[] file = image.readFile(0, image.fileBytes());
        String fileHash = sha256(file);
        if (!MDINIT_SHA256.equals(fileHash) || image.fileBytes() != 0x1D5A) {
            throw new IOException("USA MDINIT file identity does not match its authenticated FM source");
        }
        var expectedHeader = new CdMmdHeader(0x40, 0, 0xFF2000, 0x7FF, 0xFF2000, 0, 0);
        if (!expectedHeader.equals(image.header()) || image.unresolvedCopyBytes() != 934) {
            throw new IOException("MDINIT relocation does not match the file-backed FM domain");
        }
        var bytes = CdAssetBytes.copyOf(file);
        // Actual LEA at file158 uses PC15A; MOVE.W #count,d7 at162 feeds DBF at168.
        // Native installer copies plain bytes toA00000; internal u16 pointers stay absolute Z80 addresses.
        int offset = 0x15A + (short) bytes.unsignedWord(0x15A);
        int count = bytes.unsignedWord(0x164) + 1;
        if (offset != FILE_OFFSET || count != BANK_BYTES) {
            throw new IOException("MDINIT FM installer operands escape the admitted source slice");
        }
        var bank = bytes.slice(offset, count);
        if (!BANK_SHA256.equals(sha256(bank.copy()))) {
            throw new IOException("MDINIT FM copy bytes do not match the authenticated bank");
        }
        var source = new Source(release.variant(), programs.identity().normalizedSha256(), CdNativeFile.MDINIT,
                fileHash, image.header(), image.unresolvedCopyBytes());
        return new CdFmSoundAssets(source, bank);
    }

    public Source source() { return source; }
    public CdAssetBytes bank() { return bank; }

    /** Only installed program/data bytes; variables at1C00 and all MMD/main-CPU coordinates reject. */
    public CdAssetBytes readZ80(int address, int length) { return bank.slice(address, length); }

    /** The native GetList/ReadHLPtr domain is little-endian absolute u16, unlike MMD's BE fields. */
    public int unsignedWordLE(int address) {
        var word = readZ80(address, 2);
        return word.unsignedByte(0) | (word.unsignedByte(1) << 8);
    }

    /** Raw source pointer only; this does not admit all80 headers to any parser (D5 is nonordinary). */
    public int sfxHeaderAddress(int soundId) {
        return unsignedWordLE(INDEX + 2 * index(soundId));
    }

    /** Exact79 declared priority bytes, FM/Data.asm:103-182; there are80 SFX pointers, not80 declarations. */
    public CdAssetBytes declaredPriorities() { return readZ80(PRIORITIES, PRIORITY_BYTES); }

    /**
     * Native unchecked ProcSoundQueue priority read (FM/_Driver.asm:1012-1024), bounded to known IDs.
     * DF reads byte4 at7DA, the first SFX header byte. It is not a fabricated80th declared priority.
     */
    public int nativePriorityByte(int soundId) { return bank.unsignedByte(PRIORITIES + index(soundId)); }

    private static int index(int soundId) {
        if (soundId < FIRST_SFX || soundId > LAST_SFX) {
            throw new IllegalArgumentException("No admitted native FM index row for sound ID " + soundId);
        }
        return soundId - FIRST_SFX;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("Required SHA-256 is unavailable", impossible); }
    }
}
