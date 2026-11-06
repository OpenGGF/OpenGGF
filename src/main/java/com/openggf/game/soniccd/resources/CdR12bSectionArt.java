package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bSectionSources;
import com.openggf.game.soniccd.level.CdR12bDirectArtSources;
import com.openggf.game.soniccd.level.CdR12bLoadPlan;
import com.openggf.game.soniccd.level.CdR12bProfile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
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

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.LEVEL_BLOCKS;

/**
 * Pure original-USA R12B source adapter over the shared queued PLC algorithm.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Section Art Load.asm:7-46;
 * Functions (General).asm:401-579,588-816; Main.asm:599-614;
 * Palmtree Panic/Load Level Data.asm:11-22; Graphics Lists (Act 2 Past).asm:7-26 and complete bodies.
 *
 * One immutable aggregate owns its queue/table/current-pattern value. This
 * component creates no frame, clock, registered capture owner, bank, GPU work
 * or native invocation authority. Factory binding neither decodes nor prepares
 * future headers. The caller owns actual entry clears, source order and adoption.
 * Immediate/RAM operations retain complete-tile prefixes only: intra-tile rows
 * on a fault remain decoder diagnostics, never a resumable partial State.
 */
public final class CdR12bSectionArt {
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
            super("R12B section art " + reason + " at " + operation + " source $"
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
        private final CdR12bSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR12bCameraArtResidency.State residency;
        private final boolean stopped;

        private State(CdR12bSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR12bCameraArtResidency.State residency,
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
        public CdR12bCameraArtResidency.State residency() { return residency; }
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
    private final CdR12bTerrainAssets assets;
    private final CdR12bSectionSources sourceRegistry;
    private final Map<CdR12bSectionSources.SourceId, CdSectionArtKernel.Source> inputs;
    private final CdSectionArtKernel.Source blocksInput;
    private final CdAssetBytes lifeIcon;
    private final CdSectionArtKernel<State, CdR12bCameraArtResidency.State> kernel;
    private final CdImmediateArtKernel<State, CdR12bCameraArtResidency.State> immediate;

    private CdR12bSectionArt(CdR12bTerrainAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual R12B terrain assets");
        sourceRegistry = Objects.requireNonNull(assets.sectionSources(), "actual detached R12B source registry");
        var direct = Objects.requireNonNull(assets.directArtSources(), "actual detached R12B raw writers");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !identity.stage().equals(CdStagePayload.R12B.stage()) || !identity.equals(sourceRegistry.identity())
                || !identity.equals(direct.identity()) || sourceRegistry.sources().size() != 37
                || assets.loadPlan().plcs().size() != 19 || assets.loadPlan().sections().size() != 5)
            throw new IllegalArgumentException("Actual original USA R12B source/plan required");
        var binding = new CdSectionArtKernel.Binding(identity, identity.file(), identity.domain());
        var sources = new ArrayList<CdSectionArtKernel.Source>();
        var inputMap = new EnumMap<CdR12bSectionSources.SourceId, CdSectionArtKernel.Source>(CdR12bSectionSources.SourceId.class);
        for (var reader : sourceRegistry.sources()) {
            var source = source(sourceRegistry, binding, reader);
            if (inputMap.putIfAbsent(reader.id(), source) != null)
                throw new IllegalArgumentException("Duplicate R12B native source metadata");
            sources.add(source);
        }
        if (sources.size() != 37) throw new IllegalArgumentException("Thirty-seven finite R12B PLC sources required");
        inputs = Map.copyOf(inputMap);
        var blocks = assets.profile().range(LEVEL_BLOCKS); var blocksView = assets.view(LEVEL_BLOCKS);
        // R12B's actual native refill extent is4468, unlike R12A's4601; no extra byte is added.
        if (blocks.length() != 4468 || blocksView.length() != 4468)
            throw new IllegalArgumentException("Exact original4468-byte R12B native block view required");
        blocksInput = new CdSectionArtKernel.Source(binding, LEVEL_BLOCKS, blocks.file(), blocks.domain(),
                blocks.fileOffset(), blocks.length(), Integer.toUnsignedLong(blocks.nativeAddress()), blocksView);
        lifeIcon = direct.view(CdR12bDirectArtSources.View.LIFE_ICON);
        if (lifeIcon.length() != 384) throw new IllegalArgumentException("Exact original raw384-byte R12B life-icon view required");
        var lists = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var list : assets.loadPlan().plcs()) {
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                var descriptor = sourceRegistry.source(entry.sourceNativeAddress());
                if (!inputs.containsKey(descriptor.id()))
                    throw new IllegalArgumentException("R12B list and source registry differ");
                rows.add(new CdR11aPlcBuffer.Entry(entry.sourceNativeAddress(), entry.vramAddress()));
            }
            if (lists.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null)
                throw new IllegalArgumentException("Duplicate native R12B PLC slot");
        }
        var sections = new ArrayList<CdSectionArtKernel.Section>();
        for (var section : assets.loadPlan().sections())
            sections.add(new CdSectionArtKernel.Section(section.rightThresholdWord(), section.initialPlcId(), section.updatePlcId()));
        kernel = new CdSectionArtKernel<>(binding, lists, sections, sources, new KernelStates(), CLEARED);
        var immediateSources = new HashMap<Long, CdSectionArtKernel.Source>();
        for (var source : sources) immediateSources.put(source.nativeAddress(), source);
        immediate = new CdImmediateArtKernel<>(lists, immediateSources, blocksInput, () -> assets.blocksDecode().bytes(),
                lifeIcon, Integer.toUnsignedLong(CdR12bDirectArtSources.View.LIFE_ICON.range().nativeAddress()), new ImmediateStates());
    }

    /** Binds authenticated input views only; asset identity is not execution or known residency. */
    public static CdR12bSectionArt fromAssets(CdR12bTerrainAssets assets) { return new CdR12bSectionArt(assets); }

    /**
     * Explicit complete value ingress, not a clear or lifecycle producer. All native fields and the
     * actual queue/table/current references must be supplied. A present active typed decoder must
     * match this source's finite native view, current table, remaining count and saved queue cursor.
     * Same-value ingress grants no World invocation, history or restoration authority.
     */
    public State fromValues(CdAssetBytes table512, CdR12bCameraArtResidency.State resident, int rawSectionWord,
                            CdR11aPlcBuffer.State queue, Optional<CdNemesisPlcDecoder.State> decoder,
                            SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                            int hintWord, int padWord) {
        Objects.requireNonNull(table512, "table512"); Objects.requireNonNull(resident, "resident");
        Objects.requireNonNull(queue, "queue"); Objects.requireNonNull(decoder, "typed decoder presence");
        Objects.requireNonNull(savedDecoder, "saved decoder fields");
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
                throw new IllegalArgumentException("Incoming decoder does not belong to the bound R12B source view/count");
            if (remainingTilesWord != 0 && (queue.headSource() != live.sourcePointer()
                    || !Arrays.equals(table512.copy(), live.codeTable().copy()) || !savedDecoder.equals(saved(live))))
                throw new IllegalArgumentException("Incoming active decoder/table/saved/cursor values differ");
        }
        return new State(this, rawSectionWord, queue, table512, live, savedDecoder, remainingTilesWord,
                processBudgetWord, hintWord, padWord, resident, false);
    }

    /**
     * Pure full-WORD source value, Results/Main.asm:282 at pin1ecc5f3a. The caller owns the
     * actual programme producer/order/adoption. Retain all queued continuation, table and
     * current-pattern values exactly; do not clear/reprepare/service PLCs or infer residency.
     */
    public State withSectionWord(State state, int rawSectionWord) {
        checked(state); word(rawSectionWord);
        if (state.sectionWord == rawSectionWord) return state;
        return new State(this, rawSectionWord, state.queue, state.codeTable, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, state.residency, state.stopped);
    }

    public State clearPlcs(State state) { return kernel.clearPlcs(state); }
    public State initList(State state, int id) { return kernel.initList(state, id); }
    public State appendList(State state, int id) { return kernel.appendList(state, id); }
    /**
     * Pure validation of ONLY the actual cue12 partial-descriptor failure. No append/service,
     * setter, continuation or success receipt is executed. Return the ORIGINAL terminal image.
     * Native LoadPLC, Functions (General).asm:588-614; PP Graphics Lists (Act 2 Past):249-256.
     */
    public State validateGoalAppendFailure(State before, SectionArtException failure) {
        checked(before);Objects.requireNonNull(failure,"original cue12 failure");var prefix=failure.state();
        if(failure.reason()!=Reason.DESCRIPTOR_BUFFER||failure.operation()!=Operation.QUEUED
                ||!(failure.getCause() instanceof CdR11aPlcBuffer.BufferBoundary boundary)
                ||boundary.access()!=CdR11aPlcBuffer.Access.SOURCE_WRITE||boundary.byteOffset()!=96
                ||prefix.owner!=this||!prefix.stopped||prefix.queue!=boundary.state()
                ||failure.completedRamPrefix().isPresent()||failure.sourcePointer()!=prefix.queue.headSource()
                ||failure.destinationByteOffset()!=prefix.queue.headDestination()
                ||prefix.sectionWord!=before.sectionWord||prefix.codeTable!=before.codeTable
                ||prefix.decoder!=before.decoder||prefix.savedDecoder!=before.savedDecoder
                ||prefix.remainingTilesWord!=before.remainingTilesWord||prefix.processBudgetWord!=before.processBudgetWord
                ||prefix.hintWord!=before.hintWord||prefix.padWord!=before.padWord||prefix.residency!=before.residency)
            throw new IllegalArgumentException("Not this owner's original cue12 descriptor-only failure");
        int free=-1;for(int at=0;at+4<=96;at+=6)if(before.queue.bytes().unsignedLong(at)==0){free=at;break;}
        if(free!=84&&free!=90)throw new IllegalArgumentException("Not a partial cue12 append at the actual free row");
        var list=assets.loadPlan().plcs().get(0x12);
        if(list.nativeId()!=0x12||list.entries().size()!=3)throw new IllegalStateException("Actual cue12 source differs");
        byte[] expected=before.queue.bytes().copy();int complete=(96-free)/6;
        for(int row=0;row<complete;row++){var entry=list.entries().get(row);int at=free+row*6;
            long source=entry.sourceNativeAddress();for(int n=0;n<4;n++)expected[at+n]=(byte)(source>>>(24-8*n));
            expected[at+4]=(byte)(entry.vramAddress()>>>8);expected[at+5]=(byte)entry.vramAddress();}
        if(!Arrays.equals(expected,prefix.queue.bytes().copy()))throw new IllegalArgumentException("Cue12 terminal descriptor prefix differs");
        return prefix;
    }

    public State loadSection(State state, int cameraXword) { return kernel.loadSection(state, cameraXword); }
    public State updateSection(State state, int cameraXword) { return kernel.updateSection(state, cameraXword); }
    public State prepare(State state) { return kernel.prepare(state); }
    public State serviceFast(State state) { return kernel.serviceFast(state); }
    public State serviceSlow(State state, int scrollLockWORD) { return kernel.serviceSlow(state, scrollLockWORD); }

    /** Pure explicit InitVDP-pattern transform, not an actual clear or a Sonic/plane writer. */
    public State clearCurrentPatterns(State state) {
        checked(state);
        return withValues(state, state.codeTable, CdR12bCameraArtResidency.clear(state.residency));
    }

    /**
     * Pure complete-tile adoption at an aligned unsigned VRAM byte address, not a
     * native pattern index. The actual producer owns source order and publication.
     * Queue, decoder, table and all unrelated current bytes/knowledge remain intact;
     * this operation neither services PLCs nor grants HUD, frame or GPU authority.
     */
    public State writeCurrentTile(State state, int destinationWord, CdAssetBytes tileBytes) {
        checked(state);
        return withValues(state, state.codeTable,
                CdR12bCameraArtResidency.withTile(state.residency, destinationWord, tileBytes));
    }

    /**
     * Pure adoption of one already completed shared LoadShieldArt upload. The request is
     * a value, not invocation authority. Its128-byte CPU tail must come from the caller's
     * actual scratch through CdPowerupArtRuntime.prepareActiveCopy, never a source/default.
     * All queue/table/decoder fields are nonwrites; only A880..AD00 becomes known.
     */
    public State writeShieldUpload(State state, CdPowerupArtRuntime.DmaRequest request) {
        checked(state);
        var bytes = Objects.requireNonNull(request, "completed shield upload").bytes();
        if (bytes.length() != 0x480) throw new IllegalArgumentException("Complete1152-byte shield upload required");
        State current = state;
        for (int at = 0; at < bytes.length(); at += 32)
            current = writeCurrentTile(current, 0xA880 + at, bytes.slice(at, 32));
        return current;
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

    private static CdSectionArtKernel.Source source(CdR12bSectionSources registry, CdSectionArtKernel.Binding binding,
                                                    CdR12bSectionSources.Source profile) {
        var bytes = registry.view(profile.id(), CdR12bSectionSources.ReaderKind.NATIVE);
        var blob = profile.blobRange();
        if (bytes.length() != profile.nativeReaderRange().length()
                || profile.nativeReaderRange().fileOffset() != blob.fileOffset())
            throw new IllegalArgumentException("Named R12B native reader differs from bound metadata");
        return new CdSectionArtKernel.Source(binding, profile.id(), blob.file(), blob.domain(), blob.fileOffset(),
                blob.length(), profile.nativeAddress(), bytes);
    }

    private static SavedDecoder saved(CdNemesisPlcDecoder.State state) {
        return new SavedDecoder(state.xorMode() ? Writer.XOR : Writer.NORMAL, state.repeatWord(), state.pixelNibble(),
                state.previousRow(), state.readWord(), state.shiftWord());
    }

    private State withValues(State state, CdAssetBytes table, CdR12bCameraArtResidency.State resident) {
        return new State(this, state.sectionWord, state.queue, table, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, resident, state.stopped);
    }

    private void admitDestination(State state, int destination, Operation operation, long source) {
        for (var region : CdR12bCameraArtResidency.Region.values()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        try { CdR12bCameraArtResidency.readTile(state.residency, destination >>> 5); }
        catch (CdR12bCameraArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
        throw new IllegalStateException("Residency extent and reader disagree");
    }

    private CdR12bCameraArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                                                     Operation operation, long source) {
        try { return CdR12bCameraArtResidency.withTile(state.residency, destination, bytes); }
        catch (CdR12bCameraArtResidency.OwnershipBoundary fault) {
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

    private final class ImmediateStates implements CdImmediateArtKernel.States<State, CdR12bCameraArtResidency.State> {
        @Override public void checked(State state) { CdR12bSectionArt.this.checked(state); }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdR12bCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public State withValues(State state, CdAssetBytes table, CdR12bCameraArtResidency.State resident) {
            return CdR12bSectionArt.this.withValues(state, table, resident);
        }
        @Override public SectionArtException failure(CdImmediateArtKernel.Reason reason, State state,
                CdImmediateArtKernel.Operation operation, long source, int destination, CdAssetBytes prefix, Throwable cause) {
            return CdR12bSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.valueOf(operation.name()), source, destination, prefix, cause);
        }
        @Override public void admitDestination(State state, int destination, CdImmediateArtKernel.Operation operation, long source) {
            CdR12bSectionArt.this.admitDestination(state, destination, Operation.valueOf(operation.name()), source);
        }
        @Override public CdR12bCameraArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                CdImmediateArtKernel.Operation operation, long source) {
            return CdR12bSectionArt.this.commitTile(state, destination, bytes, Operation.valueOf(operation.name()), source);
        }
    }

    private final class KernelStates implements CdSectionArtKernel.States<State, CdR12bCameraArtResidency.State> {
        @Override public void checked(State state) { CdR12bSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR12bCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }
        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remaining,
                                    int budget, int hint, int pad, CdR12bCameraArtResidency.State resident, boolean stopped) {
            return new State(CdR12bSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remaining, budget, hint, pad, resident, stopped);
        }
        @Override public SectionArtException failure(CdR11aSectionArt.Reason reason, State state,
                                                     long source, int destination, Throwable cause) {
            return CdR12bSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.QUEUED,
                    source, destination, null, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR12bSectionArt.this.admitDestination(state, destination, Operation.QUEUED, state.decoder.sourcePointer());
        }
        @Override public CdR12bCameraArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
            return commitTile(state, destination, bytes, Operation.QUEUED, state.decoder.sourcePointer());
        }
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
    private static void nativeByte(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Native BYTE must be unsigned8");
    }
}
