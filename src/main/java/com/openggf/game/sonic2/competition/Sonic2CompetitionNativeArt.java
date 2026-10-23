package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVram;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable ROM-backed native terrain RAM and initial zone-art VRAM.
 *
 * <p>The RAM image owns the decoded Chunk_Table ($0000-$7FFF), Level_Layout
 * ($8000-$8FFF), and Block_Table ($9000-$A7FF), including LoadAnimatedBlocks'
 * shipped writes into the following deformation scratch. Other RAM bytes begin
 * at zero; this is not a snapshot of the complete running game's work RAM.
 * The VRAM image contains only LoadZoneTiles' initial art. PLCs, animated art,
 * playable DPLCs, planes and SAT publication remain the native world's owners.</p>
 */
public final class Sonic2CompetitionNativeArt {
    private static final int WORK_RAM_SIZE = 0x10000;
    private static final int LAYOUT_START = 0x8000;
    private static final int BLOCK_TABLE_START = 0x9000;
    private static final int BLOCK_TABLE_SIZE = 0x1800;

    private final Sonic2CompetitionLevelProfile profile;
    private final Sonic2CompetitionLevelProfile.BaseTerrain baseTerrain;
    private final Sonic2InterlaceVram vram;
    private final byte[] workRam;
    private final int animationCopiedWords;

    private Sonic2CompetitionNativeArt(Sonic2CompetitionLevelProfile profile,
            Sonic2CompetitionLevelProfile.BaseTerrain baseTerrain,
            Sonic2InterlaceVram vram, byte[] workRam, int animationCopiedWords) {
        this.profile = profile;
        this.baseTerrain = baseTerrain;
        this.vram = vram;
        this.workRam = workRam;
        this.animationCopiedWords = animationCopiedWords;
    }

    public static Sonic2CompetitionNativeArt load(Rom rom, Sonic2CompetitionLevelProfile profile)
            throws IOException {
        Objects.requireNonNull(rom, "rom");
        Objects.requireNonNull(profile, "profile");
        var terrain = profile.decodeBaseTerrain(rom);
        byte[] ram = new byte[WORK_RAM_SIZE];
        byte[] chunks = terrain.chunkBytes();
        byte[] layout = terrain.layoutBytes();
        Objects.checkFromIndexSize(0, chunks.length, LAYOUT_START);
        Objects.checkFromIndexSize(0, layout.length, BLOCK_TABLE_START - LAYOUT_START);
        System.arraycopy(chunks, 0, ram, 0, chunks.length);
        System.arraycopy(layout, 0, ram, LAYOUT_START, layout.length);
        Objects.checkFromIndexSize(0, terrain.nativeBlockWords().size(), BLOCK_TABLE_SIZE / 2);
        // loadZoneBlockMaps (s2.asm:20084-20099) already halves every native
        // pattern index in decodeBaseTerrain; do not halve those words twice.
        int blockAddress = BLOCK_TABLE_START;
        for (int word : terrain.nativeBlockWords()) {
            writeWord(ram, blockAddress, word);
            blockAddress += 2;
        }
        int copied = loadAnimatedBlocks(RomByteReader.fromRom(rom),
                profile.animationPatch().address(), ram);
        // LoadZoneTiles (s2.asm:6471-6485): the bytes remain in native upload
        // order. Only their VDP interpretation changes to interlace 8x16 cells.
        var vram = new Sonic2InterlaceVram(new byte[Sonic2InterlaceVram.SIZE])
                .withWrite(0, terrain.patternBytes());
        return new Sonic2CompetitionNativeArt(profile, terrain, vram, ram, copied);
    }

    private static int loadAnimatedBlocks(RomByteReader rom, int mapAddress, byte[] ram) {
        int offset = rom.readU16BE(mapAddress);
        if (offset == 0) return 0;
        // ADDA.W sign-extends the header's destination displacement.
        int destination = BLOCK_TABLE_START + (short) offset;
        int d1 = rom.readU16BE(mapAddress + 2);
        int source = mapAddress + 4;
        int copied = 0;
        do {
            int word = rom.readU16BE(source);
            source += 2;
            // FixBugs=0 LoadLevelBlocks_2P (s2.asm:86878-86898) overwrites
            // d1, its DBF counter, with EACH source word's halved tile index.
            // The fixed branch uses d2 and obeys the declared header length.
            d1 = (word & 0x7FF) >>> 1;
            writeWord(ram, destination, (word & 0xF800) | d1);
            destination += 2;
            copied++;
            d1 = (d1 - 1) & 0xFFFF;
        } while (d1 != 0xFFFF);
        return copied;
    }

    private static void writeWord(byte[] ram, int address, int value) {
        Objects.checkFromIndexSize(address, 2, ram.length);
        ram[address] = (byte) (value >>> 8);
        ram[address + 1] = (byte) value;
    }

    public Sonic2CompetitionLevelProfile profile() { return profile; }

    /** Raw source terrain before the animated block patch, including unchanged chunk bytes. */
    public Sonic2CompetitionLevelProfile.BaseTerrain baseTerrain() { return baseTerrain; }

    /** Initial zone art only; this image contains no PLC, playable-DPLC, plane or SAT uploads. */
    public Sonic2InterlaceVram vram() { return vram; }

    /** All $C00 native block words after LoadAnimatedBlocks; scratch spill remains in work RAM. */
    public byte[] blockTableBytes() {
        return Arrays.copyOfRange(workRam, BLOCK_TABLE_START, BLOCK_TABLE_START + BLOCK_TABLE_SIZE);
    }

    public byte[] workRamBytes() { return workRam.clone(); }

    public int ramByte(int address) {
        return workRam[Objects.checkIndex(address, workRam.length)] & 0xFF;
    }

    /** Bounded RAM access, without the VDP's independent 64 KiB address wrapping. */
    public int readWord(int address) {
        Objects.checkFromIndexSize(address, 2, workRam.length);
        return (workRam[address] & 0xFF) << 8 | workRam[address + 1] & 0xFF;
    }

    public int animationCopiedWords() { return animationCopiedWords; }
}
