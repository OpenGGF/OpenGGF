package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR11bLoadPlan;
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

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;

/**
 * Pure actual-USA Past source adapter over the shared queued PLC algorithm.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Section Art Load.asm:7-46;
 * Functions (General).asm:401-579,588-816; Main.asm:599-614;
 * Palmtree Panic/Load Level Data.asm:11-22; Data (Act 1 Past).asm:47-131.
 *
 * One immutable aggregate owns its queue/table/current-pattern value. This
 * component creates no frame, clock, registered capture owner, bank, GPU work
 * or native invocation authority. Factory binding neither decodes nor prepares
 * future headers. The caller owns actual entry clears, source order and adoption.
 * Immediate/RAM operations retain complete-tile prefixes only: intra-tile rows
 * on a fault remain decoder diagnostics, never a resumable partial State.
 */
public final class CdR11bSectionArt {
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
            super("R11B section art " + reason + " at " + operation + " source $"
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
        private final CdR11bSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR11bCameraArtResidency.State residency;
        private final boolean stopped;

        private State(CdR11bSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR11bCameraArtResidency.State residency,
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
        public CdR11bCameraArtResidency.State residency() { return residency; }
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
    private final CdR11bAssets assets;
    private final Map<CdR11bAssets.Asset, CdSectionArtKernel.Source> inputs;
    private final CdSectionArtKernel.Source blocksInput;
    private final CdAssetBytes lifeIcon;
    private final CdSectionArtKernel<State, CdR11bCameraArtResidency.State> kernel;

    private CdR11bSectionArt(CdR11bAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual Past assets");
        var identity = assets.identity();
        if (identity.file() != CdNativeFile.R11B || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || assets.loadPlan().plcs().size() != 19 || assets.loadPlan().sections().size() != 4) {
            throw new IllegalArgumentException("Actual original R11B source/plan required");
        }
        var binding = new CdSectionArtKernel.Binding(identity, identity.file(), identity.domain());
        var sources = new ArrayList<CdSectionArtKernel.Source>();
        var inputMap = new EnumMap<CdR11bAssets.Asset, CdSectionArtKernel.Source>(CdR11bAssets.Asset.class);
        for (var asset : CdR11bAssets.Asset.values()) if (asset.isPlcSource()) {
            var source = source(assets, binding, asset);
            sources.add(source); inputMap.put(asset, source);
        }
        if (sources.size() != 36) throw new IllegalArgumentException("Thirty-six finite Past PLC sources required");
        inputs = Map.copyOf(inputMap);
        blocksInput = source(assets, binding, LEVEL_BLOCKS);
        var icon = assets.view(ART_LIFE_ICON);
        if (!icon.range().equals(ART_LIFE_ICON.range()) || icon.bytes().length() != 384) {
            throw new IllegalArgumentException("Exact original raw life-icon view required");
        }
        lifeIcon = icon.bytes();
        var lists = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var list : assets.loadPlan().plcs()) {
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                if (!inputs.containsKey(entry.source()) || !entry.sourceRange().equals(entry.source().range())) {
                    throw new IllegalArgumentException("Past list and source registry differ");
                }
                rows.add(new CdR11aPlcBuffer.Entry(entry.sourceRange().nativeAddress(), entry.vramAddress()));
            }
            if (lists.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null) {
                throw new IllegalArgumentException("Duplicate native PLC slot");
            }
        }
        var sections = new ArrayList<CdSectionArtKernel.Section>();
        for (var section : assets.loadPlan().sections()) {
            sections.add(new CdSectionArtKernel.Section(section.rightThresholdWord(), section.initialPlcId(), section.updatePlcId()));
        }
        kernel = new CdSectionArtKernel<>(binding, lists, sections, sources, new KernelStates(), CLEARED);
    }

    /** Binds authenticated input views only; asset identity is not execution or known residency. */
    public static CdR11bSectionArt fromAssets(CdR11bAssets assets) { return new CdR11bSectionArt(assets); }

    /** Explicit value ingress. The caller must establish actual table/current/section history. */
    public State fromValues(CdAssetBytes table512, CdR11bCameraArtResidency.State resident, int rawSectionWord) {
        Objects.requireNonNull(table512, "table512"); Objects.requireNonNull(resident, "resident");
        word(rawSectionWord);
        if (table512.length() != 512) throw new IllegalArgumentException("Explicit 512-byte code table required");
        return new State(this, rawSectionWord, CdR11aPlcBuffer.cleared(), table512, null,
                CLEARED, 0, 0, 0, 0, resident, false);
    }

    public State clearPlcs(State state) { return kernel.clearPlcs(state); }
    public State initList(State state, int id) { return kernel.initList(state, id); }
    public State appendList(State state, int id) { return kernel.appendList(state, id); }
    public State loadSection(State state, int cameraXword) { return kernel.loadSection(state, cameraXword); }
    public State updateSection(State state, int cameraXword) { return kernel.updateSection(state, cameraXword); }
    public State prepare(State state) { return kernel.prepare(state); }
    public State serviceFast(State state) { return kernel.serviceFast(state); }
    public State serviceSlow(State state, int scrollLockWORD) { return kernel.serviceSlow(state, scrollLockWORD); }

    /** Pure explicit InitVDP-pattern transform, not an actual clear or a Sonic/plane writer. */
    public State clearCurrentPatterns(State state) {
        checked(state);
        return withValues(state, state.codeTable, CdR11bCameraArtResidency.clear(state.residency));
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
                CdR11bCameraArtResidency.writeTile(state.residency, destinationWord, tileBytes));
    }

    /**
     * Native LoadPLCImm, not InitPLC/append/service/POP. Active queued decoding is
     * unowned here because its saved continuation would need the overwritten table.
     * A merely pending inactive descriptor is untouched and later prepares with the new table.
     */
    public State loadImmediateList(State state, int id) {
        checked(state); word(id); requireInactive(state, Operation.IMMEDIATE_LIST);
        CdR11bLoadPlan.PlcList list = null;
        for (var candidate : assets.loadPlan().plcs()) if (candidate.nativeId() == id) { list = candidate; break; }
        if (list == null) throw failure(Reason.PLC_LIST, state, Operation.IMMEDIATE_LIST, 0, 0, null, null);
        State current = state;
        for (var entry : list.entries()) {
            var source = inputs.get(entry.source());
            var decoder = prepareImmediate(current, source, Operation.IMMEDIATE_LIST, entry.vramAddress(), null);
            current = withValues(current, decoder.codeTable(), current.residency);
            int destination = entry.vramAddress();
            while (decoder.remainingTiles() != 0) {
                admitDestination(current, destination, Operation.IMMEDIATE_LIST, decoder.sourcePointer());
                CdNemesisPlcDecoder.TileResult tile;
                try { tile = CdNemesisPlcDecoder.decodeTile(decoder); }
                catch (CdNemesisPlcDecoder.DecodeException fault) {
                    throw failure(Reason.DECODE_TILE, current, Operation.IMMEDIATE_LIST,
                            fault.diagnostic().sourcePointer(), destination, null, fault);
                }
                var resident = commitTile(current, destination, tile.bytes(), Operation.IMMEDIATE_LIST, decoder.sourcePointer());
                current = withValues(current, current.codeTable, resident);
                decoder = tile.state();
                destination = (destination + 32) & 0xFFFF;
            }
        }
        return current;
    }

    /**
     * Native NemDecToRAM for LevelBlocks. Full successful output is compared with
     * the already authenticated immutable format product, then that same value is
     * returned; no second persistent block bank or pattern write is created.
     */
    public BlocksResult decodeBlocksToRam(State state) {
        checked(state); requireInactive(state, Operation.BLOCKS_RAM);
        var empty = CdAssetBytes.copyOf(new byte[0]);
        var decoder = prepareImmediate(state, blocksInput, Operation.BLOCKS_RAM, 0, empty);
        State current = withValues(state, decoder.codeTable(), state.residency);
        byte[] bytes = new byte[decoder.remainingTiles() * 32];
        int at = 0;
        while (decoder.remainingTiles() != 0) {
            CdNemesisPlcDecoder.TileResult tile;
            try { tile = CdNemesisPlcDecoder.decodeTile(decoder); }
            catch (CdNemesisPlcDecoder.DecodeException fault) {
                throw failure(Reason.DECODE_TILE, current, Operation.BLOCKS_RAM, fault.diagnostic().sourcePointer(),
                        at, CdAssetBytes.copyOf(Arrays.copyOf(bytes, at)), fault);
            }
            System.arraycopy(tile.bytes().copy(), 0, bytes, at, 32);
            at += 32; decoder = tile.state();
        }
        var actual = CdAssetBytes.copyOf(bytes);
        var product = assets.blocksDecode().bytes();
        if (!Arrays.equals(bytes, product.copy())) {
            throw failure(Reason.BLOCK_PRODUCT_MISMATCH, current, Operation.BLOCKS_RAM, decoder.sourcePointer(),
                    at, actual, null);
        }
        return new BlocksResult(current, product);
    }

    /** Actual scratch (raw BYTE &7F)*128; raw period and every queued/table field are nonwrites. */
    public State loadLifeIcon(State state, int rawTimeZoneByte) {
        checked(state); nativeByte(rawTimeZoneByte);
        int sourceOffset = (rawTimeZoneByte & 0x7F) * 128;
        if (sourceOffset > lifeIcon.length() - 128) {
            throw failure(Reason.LIFE_ICON_SOURCE, state, Operation.LIFE_ICON,
                    Integer.toUnsignedLong(ART_LIFE_ICON.range().nativeAddress()) + sourceOffset, 0xB420, null, null);
        }
        State current = state;
        for (int tile = 0; tile < 4; tile++) {
            var resident = commitTile(current, 0xB420 + tile * 32, lifeIcon.slice(sourceOffset + tile * 32, 32),
                    Operation.LIFE_ICON, Integer.toUnsignedLong(ART_LIFE_ICON.range().nativeAddress()) + sourceOffset + tile * 32L);
            current = withValues(current, current.codeTable, resident);
        }
        return current;
    }

    private CdNemesisPlcDecoder.State prepareImmediate(State state, CdSectionArtKernel.Source source,
                                                       Operation operation, int destination, CdAssetBytes ramPrefix) {
        // NemDecMain403-410: row count is a WORD. 0 or >=8192 needs a separate
        // native wrapping-row admission, not the queued zero-count skip.
        int count = source.readerBytes().unsignedWord(0) & 0x7FFF;
        if (count == 0 || count >= 8192) {
            throw failure(Reason.IMMEDIATE_HEADER_COUNT, state, operation, source.nativeAddress(), destination, ramPrefix, null);
        }
        try { return CdNemesisPlcDecoder.prepare(source.readerBytes(), source.nativeAddress(), state.codeTable); }
        catch (CdNemesisPlcDecoder.DecodeException fault) {
            State prefix = withValues(state, fault.diagnostic().codeTable(), state.residency);
            throw failure(Reason.DECODE_PREPARE, prefix, operation, fault.diagnostic().sourcePointer(), destination, ramPrefix, fault);
        }
    }

    private void requireInactive(State state, Operation operation) {
        if (state.remainingTilesWord != 0) {
            throw failure(Reason.ACTIVE_QUEUED_DECODE, state, operation, state.queue.headSource(),
                    state.queue.headDestination(), null, null);
        }
    }

    private static CdSectionArtKernel.Source source(CdR11bAssets assets, CdSectionArtKernel.Binding binding,
                                                    CdR11bAssets.Asset asset) {
        var profile = asset.readerProfile().orElseThrow();
        var view = assets.readerView(asset, CdR11bAssets.ReaderKind.NATIVE);
        var blob = asset.range();
        if (view.asset() != asset || !view.range().equals(profile.nativeInputRange())
                || view.bytes().length() != profile.nativeInputRange().length()) {
            throw new IllegalArgumentException("Named Past native reader differs from bound metadata");
        }
        return new CdSectionArtKernel.Source(binding, asset, blob.file(), blob.domain(), blob.fileOffset(),
                blob.length(), Integer.toUnsignedLong(blob.nativeAddress()), view.bytes());
    }

    private State withValues(State state, CdAssetBytes table, CdR11bCameraArtResidency.State resident) {
        return new State(this, state.sectionWord, state.queue, table, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, resident, state.stopped);
    }

    private void admitDestination(State state, int destination, Operation operation, long source) {
        for (var region : CdR11bCameraArtResidency.Region.values()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        try { CdR11bCameraArtResidency.readTile(state.residency, destination >>> 5); }
        catch (CdR11bCameraArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
        throw new IllegalStateException("Residency extent and reader disagree");
    }

    private CdR11bCameraArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                                                     Operation operation, long source) {
        try { return CdR11bCameraArtResidency.writeTile(state.residency, destination, bytes); }
        catch (CdR11bCameraArtResidency.OwnershipBoundary fault) {
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

    private final class KernelStates implements CdSectionArtKernel.States<State, CdR11bCameraArtResidency.State> {
        @Override public void checked(State state) { CdR11bSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR11bCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }
        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remaining,
                                    int budget, int hint, int pad, CdR11bCameraArtResidency.State resident, boolean stopped) {
            return new State(CdR11bSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remaining, budget, hint, pad, resident, stopped);
        }
        @Override public SectionArtException failure(CdR11aSectionArt.Reason reason, State state,
                                                     long source, int destination, Throwable cause) {
            return CdR11bSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.QUEUED,
                    source, destination, null, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR11bSectionArt.this.admitDestination(state, destination, Operation.QUEUED, state.decoder.sourcePointer());
        }
        @Override public CdR11bCameraArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
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
