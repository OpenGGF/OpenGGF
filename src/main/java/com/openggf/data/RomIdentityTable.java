package com.openggf.data;


import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Known fingerprints of the logical ROMs the engine is verified against.
 *
 * <p>Verification only breaks ties between several images that all contain
 * the same logical ROM and labels log output; an image whose header matches
 * but whose hash is unknown is still accepted. The lock-on halves are
 * verified per window, so a user-supplied lock-on dump whose halves match the
 * standalone cartridges verifies without a separate whole-file entry.
 */
public final class RomIdentityTable {

    /** One verified dump: a label for logs plus its CRC32 and SHA-1. */
    public record KnownImage(String label, long size, String crc32Hex, String sha1Hex) {
    }

    private static final Map<RomIdentity, List<KnownImage>> KNOWN = Map.of(
            RomIdentity.S1, List.of(RetailRomRevision.S1_REV01.knownImage(),
                    RetailRomRevision.S1_REV00.knownImage()),
            RomIdentity.S2, List.of(RetailRomRevision.S2_REV01.knownImage(),
                    RetailRomRevision.S2_REV00.knownImage()),
            RomIdentity.S3, List.of(new KnownImage("Sonic 3", 0x200000,
                    "9BC192CE", "75E9C4705259D84112B3E697A6C00A0813D47D71")),
            RomIdentity.SK, List.of(new KnownImage("Sonic & Knuckles", 0x200000,
                    "0658F691", "88D6499D874DCB5721FF58D76FE1B9AF811192E3")),
            RomIdentity.S3K, List.of(new KnownImage("Sonic 3 & Knuckles lock-on", 0x400000,
                    "63522553", "CFBF98C36C776677290A872547AC47C53D2761D6")),
            RomIdentity.KIS2, List.of(new KnownImage("Sonic & Knuckles + Sonic 2 lock-on", 0x340000,
                    "2AC1E7C6", "6CD0537A3AEE0E012BB86D5837DDFF9342595004")));

    private RomIdentityTable() {
    }

    public static List<KnownImage> knownImages(RomIdentity rom) {
        return KNOWN.getOrDefault(rom, List.of());
    }

    /** Returns the verified dump matching the fingerprint, if any. */
    public static Optional<KnownImage> verify(RomIdentity rom, RomFingerprint fingerprint) {
        return knownImages(rom).stream()
                .filter(known -> known.size() == fingerprint.size()
                        && fingerprint.matches(known.crc32Hex(), known.sha1Hex()))
                .findFirst();
    }
}
