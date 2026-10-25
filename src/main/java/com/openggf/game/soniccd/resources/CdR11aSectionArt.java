package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure immutable R11A section requests, native PLC preparation and selected
 * complete-tile service. No frame, device, actor, graphics or timing owner.
 * Sources: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Section Art Load.asm:7-46; Palmtree Panic/Section Data
 * (Act 1 Present).asm:7-26; Functions (General).asm:588-785.
 *
 * <p>The supplied plan selects whole lists. Source programs supply finite
 * native inputs, never an object-selected pixel bank or resident certificate.
 * The actual queue destination owns writes; canonical source metadata never
 * selects a list or overrides a relocated destination. The aggregate State
 * owns the single current residency value and is itself the capture value.
 * Reusing it performs no decoding, upload or implicit preparation.</p>
 *
 * <p>State captures relevant low decoder fields, not all raw CPU register
 * halves. In particular completion POP does not save final decoder RAM.
 * A malformed declared-source fault is terminal: its decoder diagnostic may
 * describe intra-tile rows not committed to this complete-tile residency
 * owner. Such evidence is not a resumable midrow/native-prefix snapshot.</p>
 */
public final class CdR11aSectionArt {
    /** Plain declared input, with no authentication or native ingress claim. */
    public record SourceProgram(CdR11aLoadPlan.PlcEntry entry, CdAssetBytes readerBytes) {
        public SourceProgram {
            checkedEntry(entry);
            Objects.requireNonNull(readerBytes, "readerBytes");
        }
    }

    public enum Writer { CLEARED, NORMAL, XOR }

    /**
     * Semantic content of the six saved decoder longs. Only repeat/read/shift
     * low WORDs, pixel low nibble, full XOR history and writer choice are owned;
     * incoming unowned high halves are not asserted to be zero.
     */
    public record SavedDecoder(Writer writer, int repeatWord, int pixelNibble,
                               int previousRow, int readWord, int shiftWord) {
        public SavedDecoder {
            Objects.requireNonNull(writer, "writer");
            word(repeatWord); word(readWord); word(shiftWord);
            if ((pixelNibble & ~15) != 0) throw new IllegalArgumentException("Pixel must be a nibble");
        }
    }

    public enum Reason {
        SECTION_TABLE, PLC_LIST, DESCRIPTOR_BUFFER, UNKNOWN_SOURCE,
        DECODE_PREPARE, DECODE_TILE, RESIDENCY
    }

    /** Reached boundary with all prior complete owned effects, terminal thereafter. */
    public static final class SectionArtException extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        private final State state;
        private final long sourcePointer;
        private final int destinationWord;

        private SectionArtException(Reason reason, State state, long sourcePointer,
                                    int destinationWord, Throwable cause) {
            super("R11A section art " + reason + " at source $" + Long.toHexString(sourcePointer)
                    + ", VRAM $" + Integer.toHexString(destinationWord), cause);
            this.reason = reason;
            this.state = state;
            this.sourcePointer = sourcePointer;
            this.destinationWord = destinationWord;
        }

        public Reason reason() { return reason; }
        public State state() { return state; }
        public long sourcePointer() { return sourcePointer; }
        public int destinationWord() { return destinationWord; }
    }

    /**
     * One immutable aggregate. A decoder may remain diagnostically present with
     * count0 after completion; the native count WORD alone admits tile service.
     * savedDecoder is the separately retained semantic RAM image, not a second
     * resumable decoder. Hint/pad enter as explicit DEV0 with no external writer.
     */
    public static final class State {
        private final CdR11aSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR11aCameraArtResidency.State residency;
        private final boolean stopped;

        private State(CdR11aSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR11aCameraArtResidency.State residency,
                      boolean stopped) {
            this.owner = owner;
            this.sectionWord = sectionWord;
            this.queue = queue;
            this.codeTable = codeTable;
            this.decoder = decoder;
            this.savedDecoder = savedDecoder;
            this.remainingTilesWord = remainingTilesWord;
            this.processBudgetWord = processBudgetWord;
            this.hintWord = hintWord;
            this.padWord = padWord;
            this.residency = residency;
            this.stopped = stopped;
        }

        public int sectionWord() { return sectionWord; }
        public CdR11aPlcBuffer.State queue() { return queue; }
        public CdAssetBytes codeTable() { return codeTable; }
        public Optional<CdNemesisPlcDecoder.State> decoder() { return Optional.ofNullable(decoder); }
        public SavedDecoder savedDecoder() { return savedDecoder; }
        public int remainingTilesWord() { return remainingTilesWord; }
        public int processBudgetWord() { return processBudgetWord; }
        public int hintWord() { return hintWord; }
        public int padWord() { return padWord; }
        public CdR11aCameraArtResidency.State residency() { return residency; }
        public boolean stopped() { return stopped; }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof State state && owner == state.owner
                    && sectionWord == state.sectionWord && queue.equals(state.queue)
                    && Arrays.equals(codeTable.copy(), state.codeTable.copy())
                    && Objects.equals(decoder, state.decoder) && savedDecoder.equals(state.savedDecoder)
                    && remainingTilesWord == state.remainingTilesWord
                    && processBudgetWord == state.processBudgetWord && hintWord == state.hintWord
                    && padWord == state.padWord && residency.equals(state.residency) && stopped == state.stopped;
        }

        @Override public int hashCode() {
            return 31 * Objects.hash(System.identityHashCode(owner), sectionWord, queue, decoder,
                    savedDecoder, remainingTilesWord, processBudgetWord, hintWord, padWord,
                    residency, stopped) + Arrays.hashCode(codeTable.copy());
        }
    }

    private static final SavedDecoder CLEARED = new SavedDecoder(Writer.CLEARED, 0, 0, 0, 0, 0);
    private final Map<Integer, List<CdR11aPlcBuffer.Entry>> lists;
    private final List<CdR11aLoadPlan.SectionArt> sections;
    private final Map<Long, SourceProgram> programs;

    /**
     * Copies the declared plan/table/source containers. Plain construction
     * authenticates no media. Producer/range collisions are rejected rather
     * than allowing canonical destination or list membership to select bytes.
     * Missing producers/lists are reached typed boundaries, not eager filters.
     */
    public CdR11aSectionArt(List<CdR11aLoadPlan.PlcList> lists,
                           List<CdR11aLoadPlan.SectionArt> sections, List<SourceProgram> sources) {
        var copiedLists = List.copyOf(Objects.requireNonNull(lists, "lists"));
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        var copiedSources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (this.sections.isEmpty() || this.sections.size() > 128) {
            throw new IllegalArgumentException("A bounded section byte-offset table is required");
        }
        int previous = -1;
        for (var section : this.sections) {
            word(section.rightThresholdWord()); word(section.initialPlcId()); word(section.updatePlcId());
            if (section.rightThresholdWord() <= previous) {
                throw new IllegalArgumentException("Section thresholds must increase unsigned");
            }
            previous = section.rightThresholdWord();
        }
        if (previous != 0xFFFF) throw new IllegalArgumentException("Terminal section threshold must be FFFF");
        var declared = new HashMap<Long, CdR11aLoadPlan.PlcEntry>();
        var listMap = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var list : copiedLists) {
            word(list.nativeId());
            Objects.requireNonNull(list.bodyRange(), "bodyRange");
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                checkedEntry(entry);
                declare(declared, entry);
                rows.add(new CdR11aPlcBuffer.Entry(entry.sourceRange().nativeAddress(), entry.vramAddress()));
            }
            if (listMap.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null) {
                throw new IllegalArgumentException("Duplicate PLC list ID");
            }
        }
        this.lists = Map.copyOf(listMap);
        var sourceMap = new HashMap<Long, SourceProgram>();
        for (var program : copiedSources) {
            declare(declared, program.entry());
            long address = program.entry().sourceRange().nativeAddress();
            if (sourceMap.putIfAbsent(address, program) != null) {
                throw new IllegalArgumentException("Duplicate native source program");
            }
        }
        this.programs = Map.copyOf(sourceMap);
    }

    /**
     * Explicit authenticated-assets opt-in. Reuses the actual USA plan and all
     * nine finite source views, including Spikes at PLC_Std row0; never decodes
     * or preloads their output here. Canonical metadata describes the source;
     * the actual queued entry retains its destination and source-range authority.
     * Asset/region authentication remains owned by the existing asset pipeline.
     */
    public static CdR11aSectionArt fromAssets(CdR11aAssets assets) throws IOException {
        Objects.requireNonNull(assets, "assets");
        var plan = assets.loadPlan();
        var bank = assets.openingObjects().sectionArtSources();
        if (bank.sources().size() != 9) throw new IllegalArgumentException("Nine reviewed source views required");
        var sources = new ArrayList<SourceProgram>();
        for (var source : bank.sources()) {
            var entry = source.canonicalPlc();
            var reader = source.readerInputRange();
            var blob = entry.sourceRange();
            if (source.canonicalPlcId() < 0 || source.canonicalPlcId() >= plan.plcs().size()
                    || source.canonicalRowIndex() < 0
                    || source.canonicalRowIndex() >= plan.plcs().get(source.canonicalPlcId()).entries().size()
                    || !entry.equals(plan.plcs().get(source.canonicalPlcId()).entries().get(source.canonicalRowIndex()))
                    || source.source() != entry.source() || !source.decode().sourceBlobRange().equals(blob)
                    || reader.file() != blob.file() || reader.domain() != blob.domain()
                    || reader.fileOffset() != blob.fileOffset() || reader.length() != source.readerBytes().length()
                    || source.readerBytes().length() < 2
                    || (source.readerBytes().unsignedWord(0) & 0x7FFF) != source.tileCount()) {
                throw new IllegalArgumentException("Source bank and actual load plan differ");
            }
            sources.add(new SourceProgram(entry, source.readerBytes()));
        }
        return new CdR11aSectionArt(plan.plcs(), plan.sections(), sources);
    }

    /** Explicit DEV ingress, never a native cold/title/restart or zero-VRAM assertion. */
    public State devState(CdR11aCameraArtResidency.State resident, CdAssetBytes table512, int rawSectionWord) {
        Objects.requireNonNull(resident, "resident"); Objects.requireNonNull(table512, "table512");
        word(rawSectionWord);
        if (table512.length() != 512) throw new IllegalArgumentException("Explicit 512-byte code table required");
        return new State(this, rawSectionWord, CdR11aPlcBuffer.cleared(), table512, null,
                CLEARED, 0, 0, 0, 0, resident, false);
    }

    /** UpdateSectionArt stores the addressed HIGH byte before InitPLC. */
    public State updateSection(State state, int cameraXword) {
        checked(state); word(cameraXword);
        int index = sectionIndex(state, cameraXword);
        int selector = index * 2;
        if ((state.sectionWord >>> 8) == selector) return state;
        State selected = withSection(state, (selector << 8) | (state.sectionWord & 0xFF));
        return initList(selected, sections.get(index).updatePlcId());
    }

    /** LoadSectionArt stores HIGH then appends the complete initial list without cancel. */
    public State loadSection(State state, int cameraXword) {
        checked(state); word(cameraXword);
        int index = sectionIndex(state, cameraXword);
        State selected = withSection(state, ((index * 2) << 8) | (state.sectionWord & 0xFF));
        return appendList(selected, sections.get(index).initialPlcId());
    }

    /** Resolve before ClearPLCs; clear the owned128 CPU bytes, not NEMBUFFER or residency. */
    public State initList(State state, int id) {
        checked(state); word(id);
        var rows = resolveList(state, id);
        State cleared = new State(this, state.sectionWord, CdR11aPlcBuffer.cleared(), state.codeTable,
                null, CLEARED, 0, 0, 0, 0, state.residency, false);
        try {
            return withQueue(cleared, CdR11aPlcBuffer.replace(cleared.queue, rows));
        } catch (CdR11aPlcBuffer.BufferBoundary boundary) {
            throw failure(Reason.DESCRIPTOR_BUFFER, withQueue(cleared, boundary.state()),
                    boundary.state().headSource(), boundary.state().headDestination(), boundary);
        }
    }

    /** Native source-long free scan, including for an empty list; active work survives. */
    public State appendList(State state, int id) {
        checked(state); word(id);
        var rows = resolveList(state, id);
        try {
            return withQueue(state, CdR11aPlcBuffer.append(state.queue, rows));
        } catch (CdR11aPlcBuffer.BufferBoundary boundary) {
            throw failure(Reason.DESCRIPTOR_BUFFER, withQueue(state, boundary.state()),
                    boundary.state().headSource(), boundary.state().headDestination(), boundary);
        }
    }

    /** ProcessPLCs prepares only. Header0 still builds table and prefetches; no silent POP. */
    public State prepare(State state) {
        checked(state);
        if (state.queue.headSource() == 0 || state.remainingTilesWord != 0) return state;
        long source = state.queue.headSource();
        var program = programs.get(source);
        if (program == null) {
            throw failure(Reason.UNKNOWN_SOURCE, state, source, state.queue.headDestination(), null);
        }
        // Native MOVE.W/ANDI.W/store precedes table building. An unaligned or
        // incomplete atomic header read must not publish even a partial count.
        int headerCount = (source & 1) == 0 && program.readerBytes().length() >= 2
                ? program.readerBytes().unsignedWord(0) & 0x7FFF : 0;
        try {
            var decoder = CdNemesisPlcDecoder.prepare(program.readerBytes(), source, state.codeTable);
            return new State(this, state.sectionWord,
                    CdR11aPlcBuffer.withHeadSource(state.queue, decoder.sourcePointer()),
                    decoder.codeTable(), decoder, saved(decoder), decoder.remainingTiles(),
                    state.processBudgetWord, state.hintWord, state.padWord, state.residency, false);
        } catch (CdNemesisPlcDecoder.DecodeException boundary) {
            State prefix = new State(this, state.sectionWord, state.queue, boundary.diagnostic().codeTable(),
                    null, state.savedDecoder, headerCount, state.processBudgetWord,
                    state.hintWord, state.padWord, state.residency, false);
            throw failure(Reason.DECODE_PREPARE, prefix, boundary.diagnostic().sourcePointer(),
                    state.queue.headDestination(), boundary);
        }
    }

    /** scrollLock.b addresses the HIGH byte of this raw big-endian WORD. */
    public State serviceSlow(State state, int scrollLockWORD) {
        checked(state); word(scrollLockWORD);
        return service(state, (scrollLockWORD & 0xFF00) != 0 ? 18 : 3);
    }

    public State serviceFast(State state) {
        checked(state);
        return service(state, 18);
    }

    private State service(State before, int budget) {
        if (before.remainingTilesWord == 0) return before;
        if (before.decoder == null || before.decoder.remainingTiles() != before.remainingTilesWord) {
            throw new IllegalStateException("No matching complete-tile decoder continuation");
        }
        int destination = before.queue.headDestination();
        // Native preadvance is by the WHOLE chosen budget, before any decode.
        var advanced = CdR11aPlcBuffer.withHeadDestination(before.queue, (destination + budget * 32) & 0xFFFF);
        State current = new State(this, before.sectionWord, advanced, before.codeTable,
                before.decoder, before.savedDecoder, before.remainingTilesWord, budget,
                before.hintWord, before.padWord, before.residency, false);
        while (true) {
            admitDestination(current, destination);
            CdNemesisPlcDecoder.TileResult tile;
            try {
                tile = CdNemesisPlcDecoder.decodeTile(current.decoder);
            } catch (CdNemesisPlcDecoder.DecodeException boundary) {
                // Only completed earlier tiles are resident. emittedRows is
                // diagnostic evidence, not an admitted partial-tile commit.
                throw failure(Reason.DECODE_TILE, current, boundary.diagnostic().sourcePointer(), destination, boundary);
            }
            CdR11aCameraArtResidency.State resident;
            try {
                resident = CdR11aCameraArtResidency.writeTile(current.residency, destination, tile.bytes());
            } catch (CdR11aCameraArtResidency.OwnershipBoundary boundary) {
                throw failure(Reason.RESIDENCY, current, current.decoder.sourcePointer(), destination, boundary);
            }
            var decoder = tile.state();
            int remaining = decoder.remainingTiles(); // Sole counter: NEVER independently decrement.
            if (remaining == 0) {
                // FixBugs=0: native88-byte POP, no erase, NO final decoder RAM
                // store and NO final budget decrement. Next head waits for CPU.
                return new State(this, current.sectionWord, CdR11aPlcBuffer.pop(current.queue),
                        current.codeTable, decoder, current.savedDecoder, remaining, current.processBudgetWord,
                        current.hintWord, current.padWord, resident, false);
            }
            int left = current.processBudgetWord - 1;
            if (left == 0) {
                return new State(this, current.sectionWord,
                        CdR11aPlcBuffer.withHeadSource(current.queue, decoder.sourcePointer()),
                        current.codeTable, decoder, saved(decoder), remaining, 0,
                        current.hintWord, current.padWord, resident, false);
            }
            current = new State(this, current.sectionWord, current.queue, current.codeTable,
                    decoder, current.savedDecoder, remaining, left, current.hintWord,
                    current.padWord, resident, false);
            destination = (destination + 32) & 0xFFFF;
        }
    }

    /** Admission uses physical extent, not known-ingress pixels or producer identity. */
    private void admitDestination(State state, int destination) {
        for (var region : CdR11aCameraArtResidency.Region.values()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        // Obtain the existing typed outside-owner cause without attempting a
        // decode or write. This path cannot return any tile from an owned region.
        try {
            CdR11aCameraArtResidency.readTile(state.residency, destination >>> 5);
        } catch (CdR11aCameraArtResidency.OwnershipBoundary boundary) {
            throw failure(Reason.RESIDENCY, state, state.decoder.sourcePointer(), destination, boundary);
        }
        throw new IllegalStateException("Residency extent and native reader disagree");
    }

    private int sectionIndex(State state, int camera) {
        for (int i = 0; i < sections.size(); i++) {
            if (camera < sections.get(i).rightThresholdWord()) return i;
        }
        // FFFF equality reaches an unowned next table word before section write.
        throw failure(Reason.SECTION_TABLE, state, 0, 0, null);
    }

    private List<CdR11aPlcBuffer.Entry> resolveList(State state, int id) {
        var rows = lists.get(id);
        if (rows == null) throw failure(Reason.PLC_LIST, state, state.queue.headSource(), state.queue.headDestination(), null);
        return rows;
    }

    private State checked(State state) {
        Objects.requireNonNull(state, "state");
        if (state.owner != this) throw new IllegalArgumentException("Foreign section/source/state owner");
        if (state.stopped) throw new IllegalStateException("Reached boundary is terminal; no midrow continuation");
        return state;
    }

    private State withSection(State state, int section) {
        return new State(this, section, state.queue, state.codeTable, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord,
                state.residency, state.stopped);
    }

    private State withQueue(State state, CdR11aPlcBuffer.State queue) {
        return new State(this, state.sectionWord, queue, state.codeTable, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord,
                state.residency, state.stopped);
    }

    private SectionArtException failure(Reason reason, State state, long source, int destination, Throwable cause) {
        State terminal = new State(this, state.sectionWord, state.queue, state.codeTable, state.decoder,
                state.savedDecoder, state.remainingTilesWord, state.processBudgetWord,
                state.hintWord, state.padWord, state.residency, true);
        return new SectionArtException(reason, terminal, source, destination, cause);
    }

    private static SavedDecoder saved(CdNemesisPlcDecoder.State decoder) {
        return new SavedDecoder(decoder.xorMode() ? Writer.XOR : Writer.NORMAL, decoder.repeatWord(),
                decoder.pixelNibble(), decoder.previousRow(), decoder.readWord(), decoder.shiftWord());
    }

    private static void declare(Map<Long, CdR11aLoadPlan.PlcEntry> declared, CdR11aLoadPlan.PlcEntry entry) {
        long source = entry.sourceRange().nativeAddress();
        var existing = declared.putIfAbsent(source, entry);
        if (existing != null && (existing.source() != entry.source()
                || !existing.sourceRange().equals(entry.sourceRange()))) {
            throw new IllegalArgumentException("Ambiguous native producer/source extent");
        }
    }

    private static void checkedEntry(CdR11aLoadPlan.PlcEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.source(), "source"); Objects.requireNonNull(entry.sourceRange(), "sourceRange");
        word(entry.vramAddress());
        if ((entry.vramAddress() & 31) != 0) throw new IllegalArgumentException("PLC destination must align to32");
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
