package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.Rom;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Immutable, byte-addressed Mode 5 VRAM image; no ordinary 8x8 pattern conversion. */
public record Sonic2InterlaceVram(byte[] bytes) {
    public static final int SIZE = 0x10000;

    public Sonic2InterlaceVram {
        if (bytes.length != SIZE) throw new IllegalArgumentException("VRAM must contain 65536 bytes");
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }

    /** VDP byte addresses wrap within its 64 KiB VRAM. */
    public int readByte(int address) { return bytes[address & 0xFFFF] & 0xFF; }

    public int readWord(int address) { return readByte(address) << 8 | readByte(address + 1); }

    /** A new image for an actual upload/name-table/SAT write; the old image remains usable. */
    public Sonic2InterlaceVram withWrite(int address, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (address < 0 || address > SIZE || data.length > SIZE - address)
            throw new IllegalArgumentException("Upload must lie inside VRAM");
        byte[] updated = bytes.clone();
        System.arraycopy(data, 0, updated, address, data.length);
        return new Sonic2InterlaceVram(updated);
    }

    /**
     * LoadZoneTiles (s2.asm:6471-6485) Kosinski-decodes LevelArtPointers' first
     * low-24-bit pointer. ArtTile_ArtKos_LevelArt is zero; 2P changes interpretation,
     * not the art bytes. This loads base zone art only, without PLC/animation claims.
     */
    public static Sonic2InterlaceVram fromZoneArt(Rom rom, Sonic2CompetitionLevelProfile profile)
            throws IOException {
        byte[] art;
        try (var channel = RomChannel.at(rom, profile.art().patternAddress())) {
            art = KosinskiReader.decompress(channel, false);
        }
        return new Sonic2InterlaceVram(new byte[SIZE]).withWrite(0, art);
    }

    @Override public boolean equals(Object other) {
        return other instanceof Sonic2InterlaceVram image && Arrays.equals(bytes, image.bytes);
    }

    @Override public int hashCode() { return Arrays.hashCode(bytes); }

    @Override public String toString() { return "Sonic2InterlaceVram[65536 bytes]"; }
}
