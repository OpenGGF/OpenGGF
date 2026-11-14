package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure, bounded Nemesis continuation at the complete-tile cuts used by CD PLCs.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Functions (General).asm, NemDec_ProcessCompressedData (425-457),
 * NemPCD_NewRow/InlineData/writers (461-529), NemDec_BuildCodeTable (533-579),
 * ProcessPLCs (664-695), DecompPLC_Main (743-766).
 *
 * <p>Preparation emits nothing. Each successful decode emits eight big-endian
 * row longs, and stops before the final pixel's pending DBF. The next call
 * starts with NemPCD_NewRow and that DBF. Symbol reads/refills happen before
 * pixel emission, including the last run. XOR history is the full emitted
 * 32-bit previous row, retained across tiles.</p>
 *
 * <p>This is an equivalence over the relevant low fields, not a raw-register
 * image: d0.w is the pending repeat, d1's low nibble the pixel, d2.l the XOR
 * history, d5.w the read window, and d6.w the shift. Incoming high halves are
 * neither asserted zero nor reconstructed. ProcessPLCs initializes repeat,
 * pixel and history to zero; its MOVE.B/ASL.W prefetch defines only d5.w.
 * d4/d3/a5 are recreated at the next tile entry and are not captured. There
 * is no midinstruction, scanline, interrupt or hardware timing continuation.</p>
 *
 * <p>The caller supplies the actual finite input and all 512 incoming code
 * table bytes. Unwritten holes remain supplied bytes. A plain source/address
 * tuple authenticates no media, release or address-space owner. Address longs
 * retain all 32 bits and advance modulo 2^32; this finite sequential view does
 * not emulate the 68000's external bus, RAM aliases or unrelated memory.</p>
 *
 * <p>The sole remaining-tile count belongs to State. A PLC owner may project
 * that count into its metadata, but must not decrement it independently. This
 * class owns no PLC queue, budget, destination, residency, upload or frame.</p>
 */
public final class CdNemesisPlcDecoder {
    private static final long LONG_MASK = 0xFFFF_FFFFL;

    private CdNemesisPlcDecoder() { }

    /**
     * Performs ProcessPLCs' header/table/initial-two-byte-read work only.
     * A zero tile count still builds the table and prefetches; its tile service
     * is guarded exactly as DecompPLCSlow/Fast's initial TST.W would skip it.
     * No source bytes are invented, rounded to a blob extent or silently padded.
     *
     * @throws NullPointerException for a null immutable input or seed
     * @throws IllegalArgumentException for a non-512-byte seed or non-long address
     * @throws DecodeException at a reached owned-memory/read boundary
     */
    public static State prepare(CdAssetBytes sourceBytes, long nativeAddress, CdAssetBytes codeTableSeed) {
        Objects.requireNonNull(sourceBytes, "sourceBytes");
        Objects.requireNonNull(codeTableSeed, "codeTableSeed");
        if (nativeAddress < 0 || nativeAddress > LONG_MASK) {
            throw new IllegalArgumentException("Native source address must be an unsigned 32-bit long");
        }
        if (codeTableSeed.length() != 512) {
            throw new IllegalArgumentException("Incoming Nemesis code table must contain exactly 512 bytes");
        }
        Work work = new Work(sourceBytes, nativeAddress, codeTableSeed);
        if ((nativeAddress & 1) != 0) {
            throw work.failure(Reason.SOURCE_ADDRESS, "Header MOVE.W requires an even source address");
        }
        // MOVE.W is one access: an incomplete header does not consume its
        // available first byte. All subsequent source operations are MOVE.B.
        if (sourceBytes.length() < 2) {
            throw work.failure(Reason.SOURCE_EXHAUSTED, "Header word crosses the finite source view");
        }
        work.header = sourceBytes.unsignedWord(0);
        work.cursor = 2;
        work.remaining = work.header & 0x7FFF;
        buildCodeTable(work);
        work.tableOffset = -1;
        int high = work.readByte("PREFETCH_HIGH");
        work.read = high << 8; // After ASL.W, incoming d5 high bits cannot matter.
        work.read |= work.readByte("PREFETCH_LOW");
        work.shift = 16;
        work.repeat = 0;
        work.pixel = 0;
        work.previous = 0;
        return work.freeze();
    }

    /**
     * Emits precisely one tile and returns a detached immutable continuation.
     * Header-derived remainingTiles is decremented once, only on success.
     * A fault reports reached row-prefix evidence without publishing a forged
     * partial continuation or mutating the supplied complete-tile boundary.
     */
    public static TileResult decodeTile(State state) {
        Objects.requireNonNull(state, "state");
        Work work = new Work(state);
        if (work.remaining == 0) {
            throw work.failure(Reason.NO_TILES_REMAINING, "Native PLC tile service would skip a zero count");
        }
        while (work.emitted < 8) {
            // NemPCD_NewRow resets row/nibble count but falls through to DBF.
            // The tile-final writer returned before this decrement last time.
            work.phase = "PENDING_DBF";
            work.repeat = (work.repeat - 1) & 0xFFFF;
            if (work.repeat == 0xFFFF) { readRun(work); }

            work.row = (work.row << 4) | work.pixel;
            work.pixels++;
            if (work.pixels != 8) { continue; }
            int value = work.row;
            if ((work.header & 0x8000) != 0) {
                work.previous ^= value;
                value = work.previous;
            }
            int offset = work.emitted * 4;
            work.output[offset] = (byte) (value >>> 24);
            work.output[offset + 1] = (byte) (value >>> 16);
            work.output[offset + 2] = (byte) (value >>> 8);
            work.output[offset + 3] = (byte) value;
            work.emitted++;
            if (work.emitted == 8) { break; } // Writer RTS: no DBF or next-symbol read.
            work.row = 0;
            work.pixels = 0;
        }
        work.remaining--;
        return new TileResult(work.freeze(), CdAssetBytes.copyOf(work.output));
    }

    private static void buildCodeTable(Work work) {
        int descriptor = work.readByte("TABLE_FIRST");
        while (descriptor != 0xFF) {
            int entry = descriptor;
            while (true) {
                descriptor = work.readByte("TABLE_DESCRIPTOR");
                if (descriptor >= 0x80) { break; }
                int length = descriptor & 0xF;
                entry = (entry & 0xF) | (descriptor & 0x70) | (length << 8);
                int shift = (8 - length) & 0xFFFF;
                int code = work.readByte("TABLE_CODE");
                if (shift == 0) {
                    work.writeTableWord((short) ((code << 1) & 0xFFFF), entry);
                    continue;
                }
                // Register-count word shifts use count modulo64. Preserve
                // native truncation and signed .w relative addressing even
                // for malformed descriptions; stop on the first unowned word.
                int offset = (wordLeft(code, shift) << 1) & 0xFFFF;
                int counter = (wordLeft(1, shift) - 1) & 0xFFFF;
                do {
                    work.writeTableWord((short) offset, entry);
                    offset = (offset + 2) & 0xFFFF;
                    counter = (counter - 1) & 0xFFFF;
                } while (counter != 0xFFFF);
            }
        }
    }

    private static void readRun(Work work) {
        work.phase = "CODE_LOOKUP";
        int index = wordRight(work.read, (work.shift - 8) & 0xFFFF);
        if ((index & 0xFF) >= 0xFC) {
            work.tableOffset = -1;
            work.shift = (work.shift - 6) & 0xFFFF;
            if (work.shift < 9) { work.refill("INLINE_MARKER_REFILL"); }
            work.shift = (work.shift - 7) & 0xFFFF;
            int value = wordRight(work.read, work.shift);
            work.repeat = value & 0x70;
            work.pixel = value & 0xF;
            if (work.shift < 9) { work.refill("INLINE_VALUE_REFILL"); }
            work.repeat >>>= 4;
            return;
        }
        work.tableOffset = (index & 0xFF) * 2;
        int length = Byte.toUnsignedInt(work.table[work.tableOffset]);
        if (length < 1 || length > 8) {
            // The native EXT.W/SUB.W path for an arbitrary retained RAM hole
            // can consume zero/negative/oversized lengths. Such malformed
            // states have no supported continuation/parity claim here.
            throw work.failure(Reason.UNSUPPORTED_CODE_LENGTH,
                    "Reached code length outside the supported native 1..8-bit code domain: " + length);
        }
        work.repeat = length; // Native d0.w holds the code length until refill.
        work.shift = (work.shift - length) & 0xFFFF;
        if (work.shift < 9) { work.refill("CODE_REFILL"); }
        int value = Byte.toUnsignedInt(work.table[work.tableOffset + 1]);
        work.pixel = value & 0xF;
        work.repeat = (value & 0xF0) >>> 4;
    }

    private static int wordLeft(int value, int count) {
        int bits = count & 63;
        return bits >= 16 ? 0 : (value << bits) & 0xFFFF;
    }

    private static int wordRight(int value, int count) {
        int bits = count & 63;
        return bits >= 16 ? 0 : (value & 0xFFFF) >>> bits;
    }

    /** Reasons distinguish caller misuse from a reached decode/memory boundary. */
    public enum Reason {
        SOURCE_EXHAUSTED, SOURCE_ADDRESS, TABLE_ADDRESS, UNSUPPORTED_CODE_LENGTH, NO_TILES_REMAINING
    }

    /** A complete prepared or tile-final boundary; no public construction or hydration port. */
    public static final class State {
        private final CdAssetBytes sourceBytes;
        private final long nativeAddress;
        private final CdAssetBytes codeTable;
        private final int inputOffset;
        private final int headerWord;
        private final int remainingTiles;
        private final int repeatWord;
        private final int pixelNibble;
        private final int previousRow;
        private final int readWord;
        private final int shiftWord;

        private State(CdAssetBytes sourceBytes, long nativeAddress, CdAssetBytes codeTable,
                      int inputOffset, int headerWord, int remainingTiles, int repeatWord,
                      int pixelNibble, int previousRow, int readWord, int shiftWord) {
            this.sourceBytes = sourceBytes;
            this.nativeAddress = nativeAddress;
            this.codeTable = codeTable;
            this.inputOffset = inputOffset;
            this.headerWord = headerWord;
            this.remainingTiles = remainingTiles;
            this.repeatWord = repeatWord;
            this.pixelNibble = pixelNibble;
            this.previousRow = previousRow;
            this.readWord = readWord;
            this.shiftWord = shiftWord;
        }

        public CdAssetBytes sourceBytes() { return sourceBytes; }
        public long nativeAddress() { return nativeAddress; }
        public long sourcePointer() { return (nativeAddress + inputOffset) & LONG_MASK; }
        public int inputOffset() { return inputOffset; }
        public int headerWord() { return headerWord; }
        public boolean xorMode() { return (headerWord & 0x8000) != 0; }
        public int remainingTiles() { return remainingTiles; }
        public CdAssetBytes codeTable() { return codeTable; }
        /** Native d0.w before the next entry's DBF, not an already-decremented remainder. */
        public int repeatWord() { return repeatWord; }
        public int pixelNibble() { return pixelNibble; }
        /** Raw full32 d2 bits in XOR mode; initialized zero and untouched in normal mode. */
        public int previousRow() { return previousRow; }
        public int readWord() { return readWord; }
        public int shiftWord() { return shiftWord; }

        @Override public boolean equals(Object other) {
            if (this == other) { return true; }
            if (!(other instanceof State state)) { return false; }
            return nativeAddress == state.nativeAddress && inputOffset == state.inputOffset
                    && headerWord == state.headerWord && remainingTiles == state.remainingTiles
                    && repeatWord == state.repeatWord && pixelNibble == state.pixelNibble
                    && previousRow == state.previousRow && readWord == state.readWord
                    && shiftWord == state.shiftWord && sameBytes(sourceBytes, state.sourceBytes)
                    && sameBytes(codeTable, state.codeTable);
        }

        @Override public int hashCode() {
            int hash = Objects.hash(nativeAddress, inputOffset, headerWord, remainingTiles,
                    repeatWord, pixelNibble, previousRow, readWord, shiftWord);
            return 31 * (31 * hash + bytesHash(sourceBytes)) + bytesHash(codeTable);
        }
    }

    /** Exactly32 immutable output bytes and the next complete-tile continuation. */
    public static final class TileResult {
        private final State state;
        private final CdAssetBytes bytes;

        private TileResult(State state, CdAssetBytes bytes) {
            this.state = state;
            this.bytes = bytes;
        }

        public State state() { return state; }
        public CdAssetBytes bytes() { return bytes; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof TileResult result
                    && state.equals(result.state) && sameBytes(bytes, result.bytes);
        }
        @Override public int hashCode() { return 31 * state.hashCode() + bytesHash(bytes); }
    }

    /**
     * Reached fault evidence, never a resumable midinstruction State. emittedRows
     * contains only completed row longs within the attempted tile. The current
     * row/pixels describe the un-emitted row. Read/shift/repeat/pixel fields are
     * staged decoder evidence; in preparation (before the prefetch is complete)
     * their initial zero sentinels do not describe incoming native registers.
     * Unsupported lengths report before native EXT.W/SUB.W and claim no native
     * continuation/parity for that malformed path. tableOffset is -1 when no
     * table access is relevant. boundaryState is null only during prepare.
     */
    public static final class Diagnostic {
        private final String phase;
        private final int inputOffset;
        private final long sourcePointer;
        private final int tableOffset;
        private final CdAssetBytes codeTable;
        private final int readWord;
        private final int shiftWord;
        private final int repeatWord;
        private final int pixelNibble;
        private final int previousRow;
        private final int row;
        private final int pixelsInRow;
        private final CdAssetBytes emittedRows;
        private final State boundaryState;

        private Diagnostic(Work work) {
            phase = work.phase;
            inputOffset = work.cursor;
            sourcePointer = (work.address + work.cursor) & LONG_MASK;
            tableOffset = work.tableOffset;
            codeTable = CdAssetBytes.copyOf(work.table);
            readWord = work.read;
            shiftWord = work.shift;
            repeatWord = work.repeat;
            pixelNibble = work.pixel;
            previousRow = work.previous;
            row = work.row;
            pixelsInRow = work.pixels;
            emittedRows = CdAssetBytes.copyOf(Arrays.copyOf(work.output, work.emitted * 4));
            boundaryState = work.boundary;
        }

        public String phase() { return phase; }
        public int inputOffset() { return inputOffset; }
        public long sourcePointer() { return sourcePointer; }
        public int tableOffset() { return tableOffset; }
        public CdAssetBytes codeTable() { return codeTable; }
        public int readWord() { return readWord; }
        public int shiftWord() { return shiftWord; }
        public int repeatWord() { return repeatWord; }
        public int pixelNibble() { return pixelNibble; }
        public int previousRow() { return previousRow; }
        public int row() { return row; }
        public int pixelsInRow() { return pixelsInRow; }
        public CdAssetBytes emittedRows() { return emittedRows; }
        public State boundaryState() { return boundaryState; }
    }

    public static final class DecodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        private final Diagnostic diagnostic;

        private DecodeException(Reason reason, Diagnostic diagnostic, String message) {
            super(message + " [" + diagnostic.phase() + ", input+" + diagnostic.inputOffset() + "]");
            this.reason = reason;
            this.diagnostic = diagnostic;
        }

        public Reason reason() { return reason; }
        public Diagnostic diagnostic() { return diagnostic; }
    }

    /** Transient local execution scratch. No array is retained by a published value or owner. */
    private static final class Work {
        private final CdAssetBytes source;
        private final long address;
        private final byte[] table;
        private final byte[] output = new byte[32];
        private final State boundary;
        private int cursor;
        private int header;
        private int remaining;
        private int repeat;
        private int pixel;
        private int previous;
        private int read;
        private int shift;
        private int row;
        private int pixels;
        private int emitted;
        private int tableOffset = -1;
        private String phase = "HEADER";

        private Work(CdAssetBytes source, long address, CdAssetBytes seed) {
            this.source = source;
            this.address = address;
            table = seed.copy();
            boundary = null;
        }

        private Work(State state) {
            source = state.sourceBytes;
            address = state.nativeAddress;
            table = state.codeTable.copy();
            boundary = state;
            cursor = state.inputOffset;
            header = state.headerWord;
            remaining = state.remainingTiles;
            repeat = state.repeatWord;
            pixel = state.pixelNibble;
            previous = state.previousRow;
            read = state.readWord;
            shift = state.shiftWord;
            phase = "TILE_ENTRY";
        }

        private int readByte(String reachedPhase) {
            phase = reachedPhase;
            if (cursor >= source.length()) {
                throw failure(Reason.SOURCE_EXHAUSTED, "Reached byte read outside the finite source view");
            }
            return source.unsignedByte(cursor++);
        }

        private void refill(String reachedPhase) {
            shift = (shift + 8) & 0xFFFF;
            read = (read << 8) & 0xFFFF;
            read |= readByte(reachedPhase);
        }

        private void writeTableWord(int signedOffset, int value) {
            phase = "TABLE_WRITE";
            tableOffset = signedOffset;
            if (signedOffset < 0 || signedOffset > 510 || (signedOffset & 1) != 0) {
                throw failure(Reason.TABLE_ADDRESS, "Reached signed-relative word outside the supplied code table");
            }
            table[signedOffset] = (byte) (value >>> 8);
            table[signedOffset + 1] = (byte) value;
        }

        private State freeze() {
            return new State(source, address, CdAssetBytes.copyOf(table), cursor, header, remaining,
                    repeat, pixel, previous, read, shift);
        }

        private DecodeException failure(Reason reason, String message) {
            return new DecodeException(reason, new Diagnostic(this), message);
        }
    }

    private static boolean sameBytes(CdAssetBytes first, CdAssetBytes second) {
        if (first == second) { return true; }
        if (first.length() != second.length()) { return false; }
        for (int i = 0; i < first.length(); i++) {
            if (first.signedByte(i) != second.signedByte(i)) { return false; }
        }
        return true;
    }

    private static int bytesHash(CdAssetBytes value) {
        int hash = 1;
        for (int i = 0; i < value.length(); i++) { hash = 31 * hash + value.signedByte(i); }
        return hash;
    }
}
