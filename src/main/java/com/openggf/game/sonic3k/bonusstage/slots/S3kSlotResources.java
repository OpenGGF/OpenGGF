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

    private S3kSlotResources(RomByteReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
        // Validate every required bank before bootstrap changes any player or layout.
        reader.window(0x1E4080, 4 + 1024);
        reader.window(0x4C8A4, 16 + 24 + 24);
        animationFrames(0x4B5EC, 5);
        animationFrames(0x4B622, 3);
        animationFrames(0x4B656, 4);
        animationFrames(0x4B688, 25);
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
