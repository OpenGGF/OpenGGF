package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdProgramAssets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * A video stream file on the user's disc ({@code OPN.STM}, {@code BADEND.STM}, {@code GOODEND.STM},
 * {@code PTEST.STM}) read the way the file engine's FMV mode reads it: 2048-byte sectors in order, grouped in
 * packets of 75 (one second of a single-speed drive). Sectors 0-15 of a packet are PCM data and 16-74 graphics
 * data ({@code ReadFMVSectors}: {@code feFMVSectFrame} 15 switches to the graphics buffer, 74 ends the packet
 * and 75 wraps; CD System Program/SP.asm:823-865). The mute mode ({@code FFUNC_LOADFMVM}) reads packets of
 * five graphics sectors ({@code feFMVSectFrame} 5 ends the packet, SP.asm:1104-1130).
 */
public final class CdFmvStream {
    public static final int SECTOR_BYTES = 0x800;

    /** How the engine splits the stream into packets. */
    public enum Layout {
        /** {@code FMODE_LOADFMV}: 16 PCM sectors, then 59 graphics sectors. */
        SOUND(75, 16),
        /** {@code FMODE_LOADFMVM}: five graphics sectors. */
        MUTE(5, 0);

        public final int packetSectors;
        public final int pcmSectors;

        Layout(int packetSectors, int pcmSectors) {
            this.packetSectors = packetSectors;
            this.pcmSectors = pcmSectors;
        }
    }

    private final CdProgramAssets assets;
    private final CdNativeFile file;
    private final int sectors;

    public CdFmvStream(CdProgramAssets assets, CdNativeFile file) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.file = Objects.requireNonNull(file, "file");
        try {
            long size = assets.entry(file).size();
            // FileEngine_LoadFMV .GetSectors (SP.asm:691-697): one sector, plus one per further 2048 bytes.
            long count = 1;
            for (long rest = size - SECTOR_BYTES; rest > 0; rest -= SECTOR_BYTES) count++;
            this.sectors = Math.toIntExact(count);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + file.isoName(), failure);
        }
    }

    /** {@code feSectorCnt}: the sectors the engine reads. */
    public int sectors() { return sectors; }


    public CdNativeFile file() { return file; }

    /** One sector's 2048 bytes (the tail of the last sector past the file's end reads as zero). */
    public byte[] sector(int index) {
        if (index < 0 || index >= sectors) throw new IllegalArgumentException("Sector outside the stream: " + index);
        try {
            long size = assets.entry(file).size();
            long offset = (long) index * SECTOR_BYTES;
            int length = (int) Math.min(SECTOR_BYTES, size - offset);
            byte[] out = new byte[SECTOR_BYTES];
            byte[] read = assets.read(file, offset, length);
            System.arraycopy(read, 0, out, 0, read.length);
            return out;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + file.isoName(), failure);
        }
    }
}
