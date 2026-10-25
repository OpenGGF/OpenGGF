package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import java.io.IOException;
import java.util.Objects;

/** Immutable ROM bank for SlotBonusLayout and sub_4C7A2/Slots_CycleOptions.
 * Locked-on addresses were byte-verified during the 2026-10-14 parity task.
 * No disassembly or literal asset fallback participates in runtime loading.
 */
public final class S3kSlotResources {
    private final RomByteReader reader;
    private final byte[][] reelSequences;
    private final int[] backgroundDeformHeights;
    private final java.util.List<BackgroundBand> backgroundBands;

    /** word_599DE: eight $14-byte rows consumed by loc_598B4. */
    public record BackgroundBand(int stepWord, int deltaLong, int resetCountdown, int phaseLimit,
            int destVramAddress, int oddPhaseDestDelta, boolean doubleRow, int sourceY, int longWordCount) { }


    private S3kSlotResources(RomByteReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
        // Validate every required bank before bootstrap changes any player or layout.
        reader.window(0x1E4080, 4 + 1024);
        reader.window(0x4C8A4, 16 + 24 + 24);
        animationFrames(0x4B5EC, 5);
        animationFrames(0x4B622, 3);
        animationFrames(0x4B656, 4);
        animationFrames(0x4B688, 25);
        backgroundDeformHeights = new int[16];
        for (int i = 0; i < backgroundDeformHeights.length; i++) {
            int height = reader.readU16BE(0x599BE + i * 2);
            if (height == 0 || height > 0x7FFF || (i == 15 && height != 0x7FFF)) {
                throw new IllegalArgumentException("Invalid Slot background deformation height at " + i);
            }
            backgroundDeformHeights[i] = height;
        }
        var bands = new java.util.ArrayList<BackgroundBand>(8);
        for (int i = 0; i < 8; i++) {
            int row = 0x599DE + i * 0x14;
            int step = reader.readU16BE(row);
            int countdown = reader.readS16BE(row + 6);
            int phases = reader.readU16BE(row + 8);
            if (step == 0 || countdown < 0 || phases == 0) {
                throw new IllegalArgumentException("Invalid Slot background band at " + i);
            }
            bands.add(new BackgroundBand(step, reader.readU32BE(row + 2), countdown, phases,
                    reader.readU16BE(row + 10), reader.readS16BE(row + 12),
                    reader.readU16BE(row + 14) != 0, reader.readU16BE(row + 16), reader.readU16BE(row + 18)));
        }
        backgroundBands = java.util.List.copyOf(bands);
        reelSequences = new byte[][] {reader.slice(0x4C8CC, 8), reader.slice(0x4C8D4, 8), reader.slice(0x4C8DC, 8)};
    }

    public static S3kSlotResources fromRom(RomByteReader reader) {
        return new S3kSlotResources(reader);
    }

    static S3kSlotResources fromActiveRom() {
        try {
            return fromRom(RomByteReader.fromRom(GameServices.rom().getRom()));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Slot bonus resources require the active ROM", e);
        }
    }

    public int[] backgroundDeformHeights() { return backgroundDeformHeights.clone(); }
    public java.util.List<BackgroundBand> backgroundBands() { return backgroundBands; }

    public short playerStartX() { return (short) reader.readU16BE(0x1E4080); }
    public short playerStartY() { return (short) reader.readU16BE(0x1E4082); }
    public byte[] layout() { return reader.slice(0x1E4084, 1024); }
    public short[] rewardValues() {
        short[] values = new short[8];
        for (int i = 0; i < values.length; i++) values[i] = (short) reader.readU16BE(0x4C8A4 + 2 * i);
        return values;
    }
    public byte[] targetRows() { return reader.slice(0x4C8B4, 24); }
    public byte[] reelSequenceA() { return reelSequences[0].clone(); }
    public byte[] reelSequenceB() { return reelSequences[1].clone(); }
    public byte[] reelSequenceC() { return reelSequences[2].clone(); }
    // Package-owned read-only views keep the warmed panel publication allocation-free.
    byte[] reel(int index) { return reelSequences[index]; }
    public byte[] ringFrames() { return animationFrames(0x4B5EC, 5); }
    public byte[] bumperFrames() { return animationFrames(0x4B622, 3); }
    public byte[] spikeFrames() { return animationFrames(0x4B656, 4); }
    public byte[] wallFrames() { return animationFrames(0x4B688, 25); }

    /** Ani_Ring / Ani_RingSparkle, selected by Obj_SlotRing without resetting anim_frame. */
    byte[] rewardRingAnimation(boolean sparkle) {
        int table = sparkle ? 0x1A992 : 0x1AA66;
        return reader.slice(table + reader.readU16BE(table), 6);
    }

    public byte[] expandedLayout() {
        byte[] compact = layout();
        byte[] expanded = new byte[S3kSlotRomData.SLOT_EXPANDED_STRIDE * S3kSlotRomData.SLOT_EXPANDED_STRIDE];
        for (int row = 0; row < S3kSlotRomData.SLOT_LAYOUT_SIZE; row++) {
            int offset = (row + S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET) * S3kSlotRomData.SLOT_EXPANDED_STRIDE
                    + S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET;
            System.arraycopy(compact, row * S3kSlotRomData.SLOT_LAYOUT_SIZE, expanded, offset, S3kSlotRomData.SLOT_LAYOUT_SIZE);
        }
        return expanded;
    }

    private byte[] animationFrames(int address, int length) {
        byte[] bank = reader.slice(address, length);
        for (int i = 0; i < bank.length; i++) {
            if (bank[i] == 0) return java.util.Arrays.copyOf(bank, i);
        }
        throw new IllegalArgumentException("Missing Slot cell-animation terminator at " + Integer.toHexString(address));
    }
}
