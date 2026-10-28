package com.openggf.game.sonic3k.audio;

import com.openggf.data.Rom;
import com.openggf.data.RomHeaderName;
import com.openggf.data.RomIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Cartridge-owned SMPS resources, independent of the current level or game mode. */
public enum Sonic3kAudioResourceProfile {
    LOCKED_ON,
    SONIC_3,
    SONIC_KNUCKLES;

    /** Converts a native music-table request, never an SFX or system command. */
    public int musicId(int nativeMusicId) {
        int limit = this == SONIC_3 ? 0x32 : 0x33;
        if (nativeMusicId < 1 || nativeMusicId > limit) {
            throw new IllegalArgumentException("Not a music-table ID: " + nativeMusicId);
        }
        return this == SONIC_3 ? Sonic3kMusic.S3_MUSIC_ID_BASE | nativeMusicId : nativeMusicId;
    }

    /** For an authoritative logical-ROM identity already resolved by the ROM catalogue. */
    public static Sonic3kAudioResourceProfile fromLogicalRom(RomIdentity identity) {
        return switch (Objects.requireNonNull(identity, "identity")) {
            case S3 -> SONIC_3;
            case SK -> SONIC_KNUCKLES;
            case S3K -> LOCKED_ON;
            default -> throw new IllegalArgumentException("Not an S3/S&K audio cartridge: " + identity);
        };
    }

    /**
     * Resolves the supplied logical view, including views into a lock-on image. No filename,
     * current-world state, external resource, or process-global selection participates.
     */
    public static Sonic3kAudioResourceProfile fromRom(Rom rom) {
        Objects.requireNonNull(rom, "rom");
        try {
            if (rom.isOpen()) {
                long size = rom.getSize();
                RomHeaderName first = header(rom, 0);
                if (size == 0x200000) {
                    if (first == RomHeaderName.S3) return SONIC_3;
                    if (first == RomHeaderName.SK) return SONIC_KNUCKLES;
                } else if (size == 0x400000 && first == RomHeaderName.SK
                        && header(rom, 0x200000) == RomHeaderName.S3) {
                    return LOCKED_ON;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot identify audio cartridge " + rom.describe(), e);
        }
        throw new IllegalArgumentException("Unsupported S3/S&K audio cartridge " + rom.describe());
    }

    private static RomHeaderName header(Rom rom, int base) throws IOException {
        return RomHeaderName.of(
                new String(rom.readBytes(base + RomHeaderName.DOMESTIC_NAME_OFFSET,
                        RomHeaderName.NAME_LENGTH), StandardCharsets.US_ASCII),
                new String(rom.readBytes(base + RomHeaderName.INTERNATIONAL_NAME_OFFSET,
                        RomHeaderName.NAME_LENGTH), StandardCharsets.US_ASCII));
    }
}
