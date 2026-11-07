package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.level.CdR31aLoadMetadata;
import com.openggf.game.soniccd.level.CdR31aLoadPlan;
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

import static com.openggf.game.soniccd.level.CdR31aAssets.Asset.LEVEL_BLOCKS;

/**
 * Original-USA R31A value adapter over unchanged shared queued/immediate/residency algorithms.
 * Four native camera sections are real; spawn0/titlebit-clear is an explicit no-PLC branch in LoadPlan.
 * Factory binds authenticated inputs and the existing immutable block format product, never a queued
 * decoder, clock, current clear, native lifecycle, World/FULL admission, GPU or capture owner.
 * Caller owns native immediate3 then1, conditional LoadSectionArt/queued2, Title deferred2 and service.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Main.asm:156-165,245-275,599-614;
 * Section Art Load.asm:7-46; Palmtree Panic/Load Level Data.asm:11-51.
 */
public final class CdR31aSectionArt {
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
            super("R31A section art " + reason + " at " + operation + " source $"
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
        private final CdR31aSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR31aArtResidency.State residency;
        private final boolean stopped;

        private State(CdR31aSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR31aArtResidency.State residency,
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
        public CdR31aArtResidency.State residency() { return residency; }
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
    private final CdR31aAssets assets;
    private final CdR31aLoadMetadata sourceRegistry;
    private final Map<CdR31aLoadMetadata.SourceId, CdSectionArtKernel.Source> inputs;
    private final CdSectionArtKernel.Source blocksInput;
    private final CdAssetBytes lifeIcon;
    private final CdSectionArtKernel<State, CdR31aArtResidency.State> kernel;
    private final CdImmediateArtKernel<State, CdR31aArtResidency.State> immediate;

    private CdR31aSectionArt(CdR31aAssets assets) throws java.io.IOException {
        this.assets=Objects.requireNonNull(assets,"actual original R31A assets");
        var plan=CdR31aLoadPlan.fromAssets(assets);sourceRegistry=plan.sources();
        var binding=new CdSectionArtKernel.Binding(assets.identity(),assets.identity().file(),assets.identity().domain());
        var byId=new EnumMap<CdR31aLoadMetadata.SourceId,CdSectionArtKernel.Source>(CdR31aLoadMetadata.SourceId.class);
        var sources=new ArrayList<CdSectionArtKernel.Source>();
        for(var profile:sourceRegistry.sources()){
            var input=new CdSectionArtKernel.Source(binding,profile.id(),assets.identity().file(),assets.identity().domain(),
                    profile.fileOffset(),profile.includeBytes(),profile.nativeAddress(),plan.nativeView(profile.id()));
            byId.put(profile.id(),input);sources.add(input);
        }
        inputs=Map.copyOf(byId);var blocks=assets.range(LEVEL_BLOCKS);
        blocksInput=new CdSectionArtKernel.Source(binding,LEVEL_BLOCKS,assets.identity().file(),assets.identity().domain(),
                blocks.fileOffset(),blocks.length(),plan.blocksNativeAddress(),plan.blocksReader());
        lifeIcon=plan.lifeIcon();
        var lists=new HashMap<Integer,List<CdR11aPlcBuffer.Entry>>();
        for(var list:plan.plcs())lists.put(list.nativeId(),list.entries().stream().map(e->
                new CdR11aPlcBuffer.Entry(sourceRegistry.source(e.source()).nativeAddress(),e.vramAddress())).toList());
        var sections=plan.sections().stream().map(x->new CdSectionArtKernel.Section(x.rightThresholdWord(),x.initialPlcId(),x.updatePlcId())).toList();
        kernel=new CdSectionArtKernel<>(binding,lists,sections,sources,new KernelStates(),CLEARED);
        var immediateSources=new HashMap<Long,CdSectionArtKernel.Source>();for(var input:sources)immediateSources.put(input.nativeAddress(),input);
        // Existing pure format product; no extra persistent block bank or replacement decoder.
        var product=assets.blocksDecode().bytes();
        immediate=new CdImmediateArtKernel<>(lists,immediateSources,blocksInput,()->product,lifeIcon,
                plan.lifeIconNativeAddress(),new ImmediateStates());
    }

    /** Binds authenticated views and the existing pure block product; no native execution or known residency. */
    public static CdR31aSectionArt fromAssets(CdR31aAssets assets) throws java.io.IOException { return new CdR31aSectionArt(assets); }
    public boolean ownsAssets(CdR31aAssets assets) { return this.assets == assets; }
    public CdR31aAssets.Identity identity() { return assets.identity(); }

    /**
     * Explicit complete value ingress, not a clear or lifecycle producer. All native fields and the
     * actual queue/table/current references must be supplied. A present active typed decoder must
     * match this source's finite native view, current table, remaining count and saved queue cursor.
     * Same-value ingress grants no World invocation, history or restoration authority.
     */
    public State fromValues(CdAssetBytes table512, CdR31aArtResidency.State resident, int rawSectionWord,
                            CdR11aPlcBuffer.State queue, Optional<CdNemesisPlcDecoder.State> decoder,
                            SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                            int hintWord, int padWord) {
        Objects.requireNonNull(table512, "table512"); Objects.requireNonNull(resident, "resident");
        Objects.requireNonNull(queue, "queue"); Objects.requireNonNull(decoder, "typed decoder presence");
        Objects.requireNonNull(savedDecoder, "saved decoder fields");
        if (!resident.ownsAssets(assets)) throw new IllegalArgumentException("Foreign original R31A current-pattern owner");
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
                throw new IllegalArgumentException("Incoming decoder does not belong to the bound R31A source view/count");
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
    public State initSection(State state,int cameraXWord) { return kernel.loadSection(state,cameraXWord); }
    public State updateSection(State state,int cameraXWord) { return kernel.updateSection(state,cameraXWord); }
    public State prepare(State state) { return kernel.prepare(state); }
    public State serviceFast(State state) { return kernel.serviceFast(state); }
    public State serviceSlow(State state, int scrollLockWORD) { return kernel.serviceSlow(state, scrollLockWORD); }

    /** Pure explicit InitVDP-pattern transform, not an actual clear or a Sonic/plane writer. */
    public State clearCurrentPatterns(State state) {
        checked(state);
        return withValues(state, state.codeTable, CdR31aArtResidency.clear(state.residency));
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

    private static SavedDecoder saved(CdNemesisPlcDecoder.State state) {
        return new SavedDecoder(state.xorMode() ? Writer.XOR : Writer.NORMAL, state.repeatWord(), state.pixelNibble(),
                state.previousRow(), state.readWord(), state.shiftWord());
    }

    private State withValues(State state, CdAssetBytes table, CdR31aArtResidency.State resident) {
        return new State(this, state.sectionWord, state.queue, table, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, resident, state.stopped);
    }

    private void admitDestination(State state, int destination, Operation operation, long source) {
        for (var region : state.residency.windows()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        try { CdR31aArtResidency.readTile(state.residency, destination >>> 5); }
        catch (CdR31aArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
        throw new IllegalStateException("Residency extent and reader disagree");
    }

    private CdR31aArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                                                     Operation operation, long source) {
        try { return CdR31aArtResidency.withTile(state.residency, destination, bytes); }
        catch (CdR31aArtResidency.OwnershipBoundary fault) {
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

    private final class ImmediateStates implements CdImmediateArtKernel.States<State, CdR31aArtResidency.State> {
        @Override public void checked(State state) { CdR31aSectionArt.this.checked(state); }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdR31aArtResidency.State residency(State state) { return state.residency; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public State withValues(State state, CdAssetBytes table, CdR31aArtResidency.State resident) {
            return CdR31aSectionArt.this.withValues(state, table, resident);
        }
        @Override public SectionArtException failure(CdImmediateArtKernel.Reason reason, State state,
                CdImmediateArtKernel.Operation operation, long source, int destination, CdAssetBytes prefix, Throwable cause) {
            return CdR31aSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.valueOf(operation.name()), source, destination, prefix, cause);
        }
        @Override public void admitDestination(State state, int destination, CdImmediateArtKernel.Operation operation, long source) {
            CdR31aSectionArt.this.admitDestination(state, destination, Operation.valueOf(operation.name()), source);
        }
        @Override public CdR31aArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                CdImmediateArtKernel.Operation operation, long source) {
            return CdR31aSectionArt.this.commitTile(state, destination, bytes, Operation.valueOf(operation.name()), source);
        }
    }

    private final class KernelStates implements CdSectionArtKernel.States<State, CdR31aArtResidency.State> {
        @Override public void checked(State state) { CdR31aSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR31aArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }
        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remaining,
                                    int budget, int hint, int pad, CdR31aArtResidency.State resident, boolean stopped) {
            return new State(CdR31aSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remaining, budget, hint, pad, resident, stopped);
        }
        @Override public SectionArtException failure(CdR11aSectionArt.Reason reason, State state,
                                                     long source, int destination, Throwable cause) {
            return CdR31aSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.QUEUED,
                    source, destination, null, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR31aSectionArt.this.admitDestination(state, destination, Operation.QUEUED, state.decoder.sourcePointer());
        }
        @Override public CdR31aArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
            return commitTile(state, destination, bytes, Operation.QUEUED, state.decoder.sourcePointer());
        }
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
