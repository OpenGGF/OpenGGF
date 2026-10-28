package com.openggf.data;

import java.io.IOException;
import java.util.Objects;

/**
 * Verified original retail program revisions. This identity is independent of
 * console territory, cartridge composition and any requested donor character.
 * Unknown images remain usable through each game's existing compatibility path.
 */
public enum RetailRomRevision {
    UNKNOWN(null, null),
    S1_REV00(RomIdentity.S1, new RomIdentityTable.KnownImage("Sonic 1 World REV00", 0x80000,
            "F9394E97", "6DDB7DE1E17E7F6CDB88927BD906352030DAA194")),
    S1_REV01(RomIdentity.S1, new RomIdentityTable.KnownImage("Sonic 1 World REV01", 0x80000,
            "AFE05EEE", "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B")),
    S2_REV00(RomIdentity.S2, new RomIdentityTable.KnownImage("Sonic 2 World REV00", 0x100000,
            "24AB4C3A", "14DD06FC3AA19A59A818EA1F6DE150C9061B14D4")),
    S2_REV01(RomIdentity.S2, new RomIdentityTable.KnownImage("Sonic 2 World REV01", 0x100000,
            "7B905383", "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9"));

    private final RomIdentity identity;
    private final RomIdentityTable.KnownImage knownImage;

    RetailRomRevision(RomIdentity identity, RomIdentityTable.KnownImage knownImage) {
        this.identity = identity;
        this.knownImage = knownImage;
    }

    public boolean isRev00() {
        return this == S1_REV00 || this == S2_REV00;
    }

    public boolean belongsTo(RomIdentity identity) {
        return this.identity == identity;
    }

    public RomIdentityTable.KnownImage knownImage() {
        if (knownImage == null) throw new IllegalStateException("Unknown images have no verified fingerprint");
        return knownImage;
    }

    public static RetailRomRevision detect(Rom rom) throws IOException {
        return detect(RomByteReader.fromRom(Objects.requireNonNull(rom, "rom")));
    }

    /** Recognizes exactly this logical view, including a cartridge inside a lock-on image. */
    public static RetailRomRevision detect(RomByteReader reader) {
        Objects.requireNonNull(reader, "reader");
        synchronized (reader) {
            if (reader.retailRevision == null) {
                reader.retailRevision = recognize(reader);
            }
            return reader.retailRevision;
        }
    }

    private static RetailRomRevision recognize(RomByteReader reader) {
        if (reader.size() != 0x80000 && reader.size() != 0x100000) return UNKNOWN;
        RomFingerprint fingerprint = RomFingerprint.of(reader.slice(0, reader.size()));
        for (RetailRomRevision revision : values()) {
            var known = revision.knownImage;
            if (known != null && known.size() == fingerprint.size()
                    && fingerprint.matches(known.crc32Hex(), known.sha1Hex())) return revision;
        }
        return UNKNOWN;
    }
}
