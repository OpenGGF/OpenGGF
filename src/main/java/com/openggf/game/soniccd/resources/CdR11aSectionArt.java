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
    private final CdSectionArtKernel<State, CdR11aCameraArtResidency.State> kernel;

    /**
     * Copies the declared plan/table/source containers. Plain construction
     * authenticates no media. Producer/range collisions are rejected rather
     * than allowing canonical destination or list membership to select bytes.
     * Missing producers/lists are reached typed boundaries, not eager filters.
     */
    public CdR11aSectionArt(List<CdR11aLoadPlan.PlcList> lists,
                           List<CdR11aLoadPlan.SectionArt> sections, List<SourceProgram> sources) {
        this(lists, sections, sources, null);
    }

    private CdR11aSectionArt(List<CdR11aLoadPlan.PlcList> lists,
                            List<CdR11aLoadPlan.SectionArt> sections, List<SourceProgram> sources,
                            CdR11aAssets.Identity identity) {
        var copiedLists = List.copyOf(Objects.requireNonNull(lists, "lists"));
        var copiedSections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        var copiedSources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (copiedSections.isEmpty() || copiedSections.size() > 128) {
            throw new IllegalArgumentException("A bounded section byte-offset table is required");
        }
        int previous = -1;
        for (var section : copiedSections) {
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
        var immutableLists = Map.copyOf(listMap);
        var sourceMap = new HashMap<Long, SourceProgram>();
        for (var program : copiedSources) {
            declare(declared, program.entry());
            long address = program.entry().sourceRange().nativeAddress();
            if (sourceMap.putIfAbsent(address, program) != null) {
                throw new IllegalArgumentException("Duplicate native source program");
            }
        }
        var immutablePrograms = Map.copyOf(sourceMap);
        var binding = new CdSectionArtKernel.Binding(identity == null ? this : identity,
                com.openggf.game.soniccd.CdNativeFile.R11A,
                com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM);
        var normalizedSections = new ArrayList<CdSectionArtKernel.Section>();
        for (var section : copiedSections) {
            normalizedSections.add(new CdSectionArtKernel.Section(section.rightThresholdWord(),
                    section.initialPlcId(), section.updatePlcId()));
        }
        var normalizedPrograms = new ArrayList<CdSectionArtKernel.Source>();
        for (var program : immutablePrograms.values()) {
            var range = program.entry().sourceRange();
            normalizedPrograms.add(new CdSectionArtKernel.Source(binding, program.entry().source(),
                    range.file(), range.domain(), range.fileOffset(), range.length(),
                    Integer.toUnsignedLong(range.nativeAddress()), program.readerBytes()));
        }
        this.kernel = new CdSectionArtKernel<>(binding, immutableLists, normalizedSections,
                normalizedPrograms, new KernelStates(), CLEARED);
    }

    /**
     * Explicit authenticated-assets opt-in. Reuses the actual USA plan and all
     * seventeen finite source views, including cached Animals at canonical PLC2 row1; never decodes
     * or preloads their output here. Canonical metadata describes the source;
     * the actual queued entry retains its destination and source-range authority.
     * Asset/region authentication remains owned by the existing asset pipeline.
     */
    public static CdR11aSectionArt fromAssets(CdR11aAssets assets) throws IOException {
        Objects.requireNonNull(assets, "assets");
        var plan = assets.loadPlan();
        var bank = assets.openingObjects().sectionArtSources();
        if (bank.sources().size() != 17) throw new IllegalArgumentException("Seventeen reviewed source views required");
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
        return new CdR11aSectionArt(plan.plcs(), plan.sections(), sources, assets.identity());
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
        return kernel.updateSection(state, cameraXword);
    }

    /** LoadSectionArt stores HIGH then appends the complete initial list without cancel. */
    public State loadSection(State state, int cameraXword) {
        return kernel.loadSection(state, cameraXword);
    }

    /**
     * ClearPLCs:651-658 and _Definitions:225-235:96 descriptor bytes PLUS32 bytes of saved
     * decoder/count/budget/hint/pad RAM. NEMBUFFER512, section and resident art are outside it.
     * A prior decoder object cannot remain a resumable continuation after its native count clears.
     */
    public State clearPlcs(State state) {
        return kernel.clearPlcs(state);
    }

    /** Resolve before ClearPLCs; clear the owned128 CPU bytes, not NEMBUFFER or residency. */
    public State initList(State state, int id) {
        return kernel.initList(state, id);
    }

    /** Native source-long free scan, including for an empty list; active work survives. */
    public State appendList(State state, int id) {
        return kernel.appendList(state, id);
    }

    /** ProcessPLCs prepares only. Header0 still builds table and prefetches; no silent POP. */
    public State prepare(State state) {
        return kernel.prepare(state);
    }

    /** scrollLock.b addresses the HIGH byte of this raw big-endian WORD. */
    public State serviceSlow(State state, int scrollLockWORD) {
        return kernel.serviceSlow(state, scrollLockWORD);
    }

    public State serviceFast(State state) {
        return kernel.serviceFast(state);
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





    private State checked(State state) {
        Objects.requireNonNull(state, "state");
        if (state.owner != this) throw new IllegalArgumentException("Foreign section/source/state owner");
        if (state.stopped) throw new IllegalStateException("Reached boundary is terminal; no midrow continuation");
        return state;
    }





    private SectionArtException failure(Reason reason, State state, long source, int destination, Throwable cause) {
        State terminal = new State(this, state.sectionWord, state.queue, state.codeTable, state.decoder,
                state.savedDecoder, state.remainingTilesWord, state.processBudgetWord,
                state.hintWord, state.padWord, state.residency, true);
        return new SectionArtException(reason, terminal, source, destination, cause);
    }



    /**
     * Internal immutable-value glue only. The facade retains its exact State, identity,
     * exception and residency contracts; this supplies no lifecycle or execution authority.
     */
    private final class KernelStates implements CdSectionArtKernel.States<State, CdR11aCameraArtResidency.State> {
        @Override public void checked(State state) { CdR11aSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR11aCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }

        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder,
                                    int remainingTilesWord, int processBudgetWord, int hintWord, int padWord,
                                    CdR11aCameraArtResidency.State resident, boolean stopped) {
            return new State(CdR11aSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remainingTilesWord, processBudgetWord, hintWord, padWord, resident, stopped);
        }
        @Override public SectionArtException failure(Reason reason, State state, long source,
                                                    int destination, Throwable cause) {
            return CdR11aSectionArt.this.failure(reason, state, source, destination, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR11aSectionArt.this.admitDestination(state, destination);
        }
        @Override public CdR11aCameraArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
            try {
                return CdR11aCameraArtResidency.writeTile(state.residency, destination, bytes);
            } catch (CdR11aCameraArtResidency.OwnershipBoundary boundary) {
                throw CdR11aSectionArt.this.failure(Reason.RESIDENCY, state,
                        state.decoder.sourcePointer(), destination, boundary);
            }
        }
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
