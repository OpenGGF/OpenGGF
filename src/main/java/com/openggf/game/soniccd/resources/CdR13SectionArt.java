package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13SectionSources;
import com.openggf.game.soniccd.level.CdR13DirectArtSources;
import com.openggf.game.soniccd.level.CdR13LoadPlan;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR13FutureAssets.Asset.LEVEL_BLOCKS;

/**
 * Pure original-USA C/D art value facade over the shared queued and immediate kernels.
 * Native Act3 Init/UpdateSectionGfx are RTS; neither selects a camera threshold nor submits work.
 * Factory binds finite original sources only and performs no decoder prepare/header prefetch.
 * Caller owns LevelStart's immediate3 then1, LoadLevelData's bit1/queued2 and all clears/order/adoption.
 * UNKNOWN patterns become known only through explicit clear or complete original tile writes.
 * No Frame, programme invocation, clock, GPU, bank or registered capture authority is granted.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Main.asm:156-165,599-614;
 * Section Graphics (Act 3).asm:7-10; Load Level Data (Act 3).asm:11-43.
 */
public final class CdR13SectionArt {
    public enum Reason {
        SECTION_TABLE, PLC_LIST, DESCRIPTOR_BUFFER, UNKNOWN_SOURCE,
        DECODE_PREPARE, DECODE_TILE, RESIDENCY, ACTIVE_QUEUED_DECODE,
        IMMEDIATE_HEADER_COUNT, LIFE_ICON_SOURCE, BLOCK_PRODUCT_MISMATCH
    }
    /** Destination offsets are pattern byte addresses, except RAM's local product-byte offset. */
    public enum Operation { QUEUED, IMMEDIATE_LIST, BLOCKS_RAM, LIFE_ICON }

    public static final class SectionArtException extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        private final State state;
        private final Operation operation;
        private final long sourcePointer;
        private final int destinationByteOffset;
        private final CdAssetBytes completedRamPrefix;

        private SectionArtException(Reason reason, State state, Operation operation, long sourcePointer,
                                    int destinationByteOffset, CdAssetBytes completedRamPrefix, Throwable cause) {
            super("R13C/D section art " + reason + " at " + operation + " source $"
                    + Long.toHexString(sourcePointer) + ", destination byte $" + Integer.toHexString(destinationByteOffset), cause);
            this.reason = reason; this.state = state; this.operation = operation;
            this.sourcePointer = sourcePointer; this.destinationByteOffset = destinationByteOffset;
            this.completedRamPrefix = completedRamPrefix;
        }
        public Reason reason() { return reason; }
        public State state() { return state; }
        public Operation operation() { return operation; }
        public long sourcePointer() { return sourcePointer; }
        public int destinationByteOffset() { return destinationByteOffset; }
        /** Completed RAM tiles only. Any attempted tile's emitted rows remain in the decoder cause. */
        public Optional<CdAssetBytes> completedRamPrefix() { return Optional.ofNullable(completedRamPrefix); }
    }

    public static final class State {
        private final CdR13SectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR13ArtResidency.State residency;
        private final boolean stopped;

        private State(CdR13SectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR13ArtResidency.State residency,
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
        public CdR13ArtResidency.State residency() { return residency; }
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

    public static final class BlocksResult {
        private final State state;
        private final CdAssetBytes blocks;
        private BlocksResult(State state, CdAssetBytes blocks) { this.state = state; this.blocks = blocks; }
        public State state() { return state; }
        public CdAssetBytes blocks() { return blocks; }
    }

    private static final SavedDecoder CLEARED = new SavedDecoder(Writer.CLEARED, 0, 0, 0, 0, 0);
    private final CdR13FutureAssets assets;
    private final CdR13SectionSources sourceRegistry;
    private final Map<CdR13SectionSources.SourceId, CdSectionArtKernel.Source> inputs;
    private final CdSectionArtKernel.Source blocksInput;
    private final CdAssetBytes lifeIcon;
    private final CdSectionArtKernel<State, CdR13ArtResidency.State> kernel;
    private final CdImmediateArtKernel<State, CdR13ArtResidency.State> immediate;

    private CdR13SectionArt(CdR13FutureAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual original R13C/D assets");
        var plan = CdR13LoadPlan.fromAssets(assets);
        sourceRegistry = plan.sources();
        var direct = CdR13DirectArtSources.fromAssets(assets);
        var identity = assets.identity();
        if (!plan.ownsAssets(assets) || !sourceRegistry.ownsAssets(assets) || !direct.ownsAssets(assets)
                || !identity.equals(sourceRegistry.identity()) || !identity.equals(direct.identity())
                || sourceRegistry.sources().size() != 19 || plan.plcs().size() != 17
                || !plan.initSectionOperations().isEmpty() || !plan.updateSectionOperations().isEmpty())
            throw new IllegalArgumentException("Actual original USA R13C/D source/plan required");
        var binding = new CdSectionArtKernel.Binding(identity, identity.file(), identity.domain());
        var sources = new ArrayList<CdSectionArtKernel.Source>();
        var inputMap = new EnumMap<CdR13SectionSources.SourceId,CdSectionArtKernel.Source>(CdR13SectionSources.SourceId.class);
        for (var reader : sourceRegistry.sources()) {
            var source = source(sourceRegistry, binding, reader);
            if (inputMap.putIfAbsent(reader.id(), source) != null)
                throw new IllegalArgumentException("Duplicate R13 native source metadata");
            sources.add(source);
        }
        inputs = Map.copyOf(inputMap);
        var blocks = direct.blocksBlobRange();
        blocksInput = new CdSectionArtKernel.Source(binding, LEVEL_BLOCKS, blocks.file(), blocks.domain(),
                blocks.fileOffset(), blocks.length(), Integer.toUnsignedLong(blocks.nativeAddress()), direct.blocksReader());
        lifeIcon = direct.lifeIcon();
        var lists = new HashMap<Integer,List<CdR11aPlcBuffer.Entry>>();
        for (var list : plan.plcs()) {
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                if (!inputs.containsKey(sourceRegistry.source(entry.sourceNativeAddress()).id()))
                    throw new IllegalArgumentException("R13 list and source registry differ");
                rows.add(new CdR11aPlcBuffer.Entry(entry.sourceNativeAddress(), entry.vramAddress()));
            }
            if (lists.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null)
                throw new IllegalArgumentException("Duplicate native R13 PLC slot");
        }
        kernel = CdSectionArtKernel.nativeSectionRts(binding, lists, sources, new KernelStates(), CLEARED);
        var immediateSources = new HashMap<Long,CdSectionArtKernel.Source>();
        for (var source : sources) immediateSources.put(source.nativeAddress(), source);
        immediate = new CdImmediateArtKernel<>(lists, immediateSources, blocksInput, () -> assets.blocksDecode().bytes(),
                lifeIcon, Integer.toUnsignedLong(direct.lifeIconRange().nativeAddress()), new ImmediateStates());
    }

    /** Binds authenticated input views only; asset identity is not execution or known residency. */
    public static CdR13SectionArt fromAssets(CdR13FutureAssets assets) { return new CdR13SectionArt(assets); }
    public boolean ownsAssets(CdR13FutureAssets assets) { return this.assets == assets; }
    public CdR13FutureAssets.Identity identity() { return assets.identity(); }

    /**
     * Explicit complete value ingress, not a clear or lifecycle producer. All native fields and the
     * actual queue/table/current references must be supplied. A present active typed decoder must
     * match this source's finite native view, current table, remaining count and saved queue cursor.
     * Same-value ingress grants no World invocation, history or restoration authority.
     */
    public State fromValues(CdAssetBytes table512, CdR13ArtResidency.State resident, int rawSectionWord,
                            CdR11aPlcBuffer.State queue, Optional<CdNemesisPlcDecoder.State> decoder,
                            SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                            int hintWord, int padWord) {
        Objects.requireNonNull(table512, "table512"); Objects.requireNonNull(resident, "resident");
        Objects.requireNonNull(queue, "queue"); Objects.requireNonNull(decoder, "typed decoder presence");
        Objects.requireNonNull(savedDecoder, "saved decoder fields");
        if (!resident.ownsAssets(assets)) throw new IllegalArgumentException("Foreign original R13 current-pattern owner");
        word(rawSectionWord); word(remainingTilesWord); word(processBudgetWord); word(hintWord); word(padWord);
        if (table512.length() != 512) throw new IllegalArgumentException("Explicit512-byte code table required");
        var live = decoder.orElse(null);
        if (live == null && remainingTilesWord != 0)
            throw new IllegalArgumentException("Active remaining count requires an actual typed decoder");
        if (live != null) {
            CdSectionArtKernel.Source source = null;
            for (var candidate : inputs.values()) if (candidate.nativeAddress() == live.nativeAddress()) { source = candidate; break; }
            if (source == null || !Arrays.equals(source.readerBytes().copy(), live.sourceBytes().copy())
                    || live.remainingTiles() != remainingTilesWord)
                throw new IllegalArgumentException("Incoming decoder does not belong to the bound R13C/D source view/count");
            if (remainingTilesWord != 0 && (queue.headSource() != live.sourcePointer()
                    || !Arrays.equals(table512.copy(), live.codeTable().copy()) || !savedDecoder.equals(saved(live))))
                throw new IllegalArgumentException("Incoming active decoder/table/saved/cursor values differ");
        }
        return new State(this, rawSectionWord, queue, table512, live, savedDecoder, remainingTilesWord,
                processBudgetWord, hintWord, padWord, resident, false);
    }

    public State clearPlcs(State state) { return kernel.clearPlcs(state); }
    public State initList(State state, int id) { return kernel.initList(state, id); }
    public State appendList(State state, int id) { return kernel.appendList(state, id); }
    public State initSection(State state) { return kernel.nativeSectionRts(state); }
    public State updateSection(State state) { return kernel.nativeSectionRts(state); }
    public State prepare(State state) { return kernel.prepare(state); }
    public State serviceFast(State state) { return kernel.serviceFast(state); }
    public State serviceSlow(State state, int scrollLockWORD) { return kernel.serviceSlow(state, scrollLockWORD); }

    /** Pure explicit InitVDP-pattern transform, not an actual clear or a Sonic/plane writer. */
    public State clearCurrentPatterns(State state) {
        checked(state);
        return withValues(state, state.codeTable, CdR13ArtResidency.clear(state.residency));
    }

    /** Complete native caller-owned tile value; exact source/operation admission belongs to actual World. */
    public State writeCurrentTile(State state,int destinationWord,CdAssetBytes tile32) {
        checked(state);return withValues(state,state.codeTable,commitTile(state,destinationWord,tile32,Operation.QUEUED,0));
    }

    /**
     * Native LoadPLCImm, not InitPLC/append/service/POP. Active queued decoding is
     * unowned here because its saved continuation would need the overwritten table.
     * A merely pending inactive descriptor is untouched and later prepares with the new table.
     */
    public State loadImmediateList(State state, int id) { return immediate.loadImmediateList(state, id); }

    /** Native NemDecToRAM; returns the existing product after the shared complete decode loop. */
    public BlocksResult decodeBlocksToRam(State state) {
        var result = immediate.decodeBlocksToRam(state);
        return new BlocksResult(result.state(), result.bytes());
    }

    /** Actual raw period selection, with all queued fields retained. */
    public State loadLifeIcon(State state, int rawTimeZoneByte) { return immediate.loadLifeIcon(state, rawTimeZoneByte); }

    private static CdSectionArtKernel.Source source(CdR13SectionSources registry, CdSectionArtKernel.Binding binding,
                                                    CdR13SectionSources.Source profile) {
        var bytes = registry.view(profile.id(), CdR13SectionSources.ReaderKind.NATIVE);
        var blob = profile.blobRange();
        if (bytes.length() != profile.nativeReaderRange().length()
                || profile.nativeReaderRange().fileOffset() != blob.fileOffset())
            throw new IllegalArgumentException("Named R13C/D native reader differs from bound metadata");
        return new CdSectionArtKernel.Source(binding, profile.id(), blob.file(), blob.domain(), blob.fileOffset(),
                blob.length(), profile.nativeAddress(), bytes);
    }

    private static SavedDecoder saved(CdNemesisPlcDecoder.State state) {
        return new SavedDecoder(state.xorMode() ? Writer.XOR : Writer.NORMAL, state.repeatWord(), state.pixelNibble(),
                state.previousRow(), state.readWord(), state.shiftWord());
    }

    private State withValues(State state, CdAssetBytes table, CdR13ArtResidency.State resident) {
        return new State(this, state.sectionWord, state.queue, table, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, resident, state.stopped);
    }

    private void admitDestination(State state, int destination, Operation operation, long source) {
        for (var region : CdR13ArtResidency.Region.values()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + state.residency.region(region).byteLength() - 32) return;
        }
        try { CdR13ArtResidency.readTile(state.residency, destination >>> 5); }
        catch (CdR13ArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
        throw new IllegalStateException("Residency extent and reader disagree");
    }

    private CdR13ArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                                                     Operation operation, long source) {
        try { return CdR13ArtResidency.withTile(state.residency, destination, bytes); }
        catch (CdR13ArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
    }

    private State checked(State state) {
        Objects.requireNonNull(state, "state");
        if (state.owner != this) throw new IllegalArgumentException("Foreign section/source/state owner");
        if (state.stopped) throw new IllegalStateException("Reached boundary is terminal; no partial continuation");
        return state;
    }

    private SectionArtException failure(Reason reason, State state, Operation operation, long source,
                                       int destination, CdAssetBytes ramPrefix, Throwable cause) {
        State terminal = new State(this, state.sectionWord, state.queue, state.codeTable, state.decoder,
                state.savedDecoder, state.remainingTilesWord, state.processBudgetWord, state.hintWord,
                state.padWord, state.residency, true);
        return new SectionArtException(reason, terminal, operation, source, destination, ramPrefix, cause);
    }

    private final class ImmediateStates implements CdImmediateArtKernel.States<State, CdR13ArtResidency.State> {
        @Override public void checked(State state) { CdR13SectionArt.this.checked(state); }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdR13ArtResidency.State residency(State state) { return state.residency; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public State withValues(State state, CdAssetBytes table, CdR13ArtResidency.State resident) {
            return CdR13SectionArt.this.withValues(state, table, resident);
        }
        @Override public SectionArtException failure(CdImmediateArtKernel.Reason reason, State state,
                CdImmediateArtKernel.Operation operation, long source, int destination, CdAssetBytes prefix, Throwable cause) {
            return CdR13SectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.valueOf(operation.name()), source, destination, prefix, cause);
        }
        @Override public void admitDestination(State state, int destination, CdImmediateArtKernel.Operation operation, long source) {
            CdR13SectionArt.this.admitDestination(state, destination, Operation.valueOf(operation.name()), source);
        }
        @Override public CdR13ArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                CdImmediateArtKernel.Operation operation, long source) {
            return CdR13SectionArt.this.commitTile(state, destination, bytes, Operation.valueOf(operation.name()), source);
        }
    }

    private final class KernelStates implements CdSectionArtKernel.States<State, CdR13ArtResidency.State> {
        @Override public void checked(State state) { CdR13SectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR13ArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }
        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remaining,
                                    int budget, int hint, int pad, CdR13ArtResidency.State resident, boolean stopped) {
            return new State(CdR13SectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remaining, budget, hint, pad, resident, stopped);
        }
        @Override public SectionArtException failure(CdR11aSectionArt.Reason reason, State state,
                                                     long source, int destination, Throwable cause) {
            return CdR13SectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.QUEUED,
                    source, destination, null, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR13SectionArt.this.admitDestination(state, destination, Operation.QUEUED, state.decoder.sourcePointer());
        }
        @Override public CdR13ArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
            return commitTile(state, destination, bytes, Operation.QUEUED, state.decoder.sourcePointer());
        }
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
}
