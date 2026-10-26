package com.openggf.game.sonic3k.timing;

import com.openggf.data.RomByteReader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Instruction work of the shipped FixBugs=0 AIZ1_Deform callee, $23B03E..$23B0D4.
 * The R16 deformation ledger hand-sums its input-independent path to 1094 cycles
 * and matches every executed callee PC in 28 retained native ending buckets.
 * Native observations are comparison evidence only, never production inputs.
 * This is a closed callee term, never a frame CPU total or a Kos service budget.
 */
public final class S3kAizDeformInstructionWork {
    public static final int CODE_START = 0x23B03E;
    public static final int CODE_LENGTH = 0x23B0D4 - CODE_START;
    public static final int INSTRUCTION_CYCLES = 1094;
    public static final int BYTES = Long.BYTES + Integer.BYTES + 1;
    private static final byte[] CODE_SHA256 = HexFormat.of().parseHex(
            "e437770a9eae297587d89a6ba317e97ad6df65d651f11abb4a6dff77c0af157f");

    public enum Qualification { NOT_EXECUTED, ROM_UNSUPPORTED, QUALIFIED }
    /** These are deliberately unavailable from this single callee, not zero-cost. */
    public enum UnpricedTerm {
        ENDING_BACKGROUND_CALLER, APPLY_DEFORMATION, WATER_DEFORMATION, OTHER_FRAME_CALLERS,
        INTERRUPT_ACCEPTANCE, REFRESH, Z80_BUS, VDP_FIFO, DMA_FREEZE
    }
    private static final Set<UnpricedTerm> UNPRICED = Set.of(UnpricedTerm.values());

    // Derived from the immutable session ROM, not logical work or rewind state.
    private RomByteReader boundReader;
    private boolean supported;
    private long executionCount;
    private int lastExecutionFrame = Integer.MIN_VALUE;
    private Qualification qualification = Qualification.NOT_EXECUTED;

    /** Called only after the logical event owner executes the semantic callee. No per-call allocation. */
    public void recordExecution(RomByteReader reader, int levelFrame) {
        if (reader != boundReader) {
            supported = romMatches(reader);
            boundReader = reader;
        }
        executionCount++;
        lastExecutionFrame = levelFrame;
        qualification = supported ? Qualification.QUALIFIED : Qualification.ROM_UNSUPPORTED;
    }

    /** Authenticate all 150 bytes, including immediates, displacements, loops and RTS. */
    public static boolean romMatches(RomByteReader reader) {
        if (reader == null || reader.size() < CODE_START + CODE_LENGTH) return false;
        try {
            return MessageDigest.isEqual(CODE_SHA256,
                    MessageDigest.getInstance("SHA-256").digest(reader.slice(CODE_START, CODE_LENGTH)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required to verify AIZ1_Deform", unavailable);
        }
    }

    public long executionCount() { return executionCount; }
    public int lastExecutionFrame() { return lastExecutionFrame; }
    public Qualification qualification() { return qualification; }
    public int lastInstructionCycles() { return qualification == Qualification.QUALIFIED ? INSTRUCTION_CYCLES : -1; }
    public Set<UnpricedTerm> unpricedTerms() { return UNPRICED; }

    public void captureTo(ByteBuffer b) {
        b.putLong(executionCount).putInt(lastExecutionFrame).put((byte) qualification.ordinal());
    }
    public void restoreFrom(ByteBuffer b) {
        executionCount = b.getLong(); lastExecutionFrame = b.getInt();
        qualification = Qualification.values()[b.get() & 0xFF];
    }
}
