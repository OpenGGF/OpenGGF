package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
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

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;

/**
 * Pure original-USA R12A source adapter over the shared queued PLC algorithm.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Section Art Load.asm:7-46;
 * Functions (General).asm:401-579,588-816; Main.asm:599-614;
 * Palmtree Panic/Load Level Data.asm:11-22; Graphics Lists (Act 2 Present).asm:7-26 and complete bodies.
 *
 * One immutable aggregate owns its queue/table/current-pattern value. This
 * component creates no frame, clock, registered capture owner, bank, GPU work
 * or native invocation authority. Factory binding neither decodes nor prepares
 * future headers. The caller owns actual entry clears, source order and adoption.
 * Immediate/RAM operations retain complete-tile prefixes only: intra-tile rows
 * on a fault remain decoder diagnostics, never a resumable partial State.
 */
public final class CdR12aSectionArt {
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
            super("R12A section art " + reason + " at " + operation + " source $"
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
        private final CdR12aSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR12aCameraArtResidency.State residency;
        private final boolean stopped;

        private State(CdR12aSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR12aCameraArtResidency.State residency,
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
        public CdR12aCameraArtResidency.State residency() { return residency; }
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
    private final CdR12aTerrainAssets assets;
    private final CdR12aObjectAssets objects;
    private final Map<CdR12aProfile.Asset, CdSectionArtKernel.Source> inputs;
    private final CdSectionArtKernel.Source blocksInput;
    private final CdAssetBytes lifeIcon;
    private final CdSectionArtKernel<State, CdR12aCameraArtResidency.State> kernel;

    private CdR12aSectionArt(CdR12aTerrainAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual R12A terrain assets");
        objects = Objects.requireNonNull(assets.objects(), "actual detached R12A resources");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !identity.stage().equals(CdStagePayload.R12A.stage()) || !identity.equals(objects.identity())
                || assets.profile() != objects.profile() || objects.plcLists().size() != 19 || objects.sections().size() != 5)
            throw new IllegalArgumentException("Actual original USA R12A source/plan required");
        var binding = new CdSectionArtKernel.Binding(identity, identity.file(), identity.domain());
        var sources = new ArrayList<CdSectionArtKernel.Source>();
        var inputMap = new EnumMap<CdR12aProfile.Asset, CdSectionArtKernel.Source>(CdR12aProfile.Asset.class);
        for (var reader : objects.readerProfiles()) {
            var source = source(objects, binding, reader);
            if (inputMap.putIfAbsent(reader.asset(), source) != null)
                throw new IllegalArgumentException("Duplicate R12A native source metadata");
            sources.add(source);
        }
        if (sources.size() != 35) throw new IllegalArgumentException("Thirty-five finite R12A PLC sources required");
        inputs = Map.copyOf(inputMap);
        var blocks = assets.profile().range(LEVEL_BLOCKS); var blocksView = objects.blocksNativeReaderView();
        if (blocks.length() != 4600 || blocksView.length() != 4601)
            throw new IllegalArgumentException("Exact canonical4600/native4601 block source required");
        blocksInput = new CdSectionArtKernel.Source(binding, LEVEL_BLOCKS, blocks.file(), blocks.domain(),
                blocks.fileOffset(), blocks.length(), Integer.toUnsignedLong(blocks.nativeAddress()), blocksView);
        lifeIcon = objects.view(ART_LIFE_ICON);
        if (lifeIcon.length() != 384) throw new IllegalArgumentException("Exact original raw384-byte life-icon view required");
        var lists = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var list : objects.plcLists()) {
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                if (!inputs.containsKey(entry.source()) || !entry.sourceRange().equals(assets.profile().range(entry.source())))
                    throw new IllegalArgumentException("R12A list and source registry differ");
                rows.add(entry.descriptor());
            }
            if (lists.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null)
                throw new IllegalArgumentException("Duplicate native R12A PLC slot");
        }
        var sections = new ArrayList<CdSectionArtKernel.Section>();
        for (var section : objects.sections())
            sections.add(new CdSectionArtKernel.Section(section.rightThresholdWord(), section.initialPlcId(), section.updatePlcId()));
        kernel = new CdSectionArtKernel<>(binding, lists, sections, sources, new KernelStates(), CLEARED);
    }

    /** Binds authenticated input views only; asset identity is not execution or known residency. */
    public static CdR12aSectionArt fromAssets(CdR12aTerrainAssets assets) { return new CdR12aSectionArt(assets); }

    /**
     * Explicit complete value ingress, not a clear or lifecycle producer. All native fields and the
     * actual queue/table/current references must be supplied. A present active typed decoder must
     * match this source's finite native view, current table, remaining count and saved queue cursor.
     * Same-value ingress grants no World invocation, history or restoration authority.
     */
    public State fromValues(CdAssetBytes table512, CdR12aCameraArtResidency.State resident, int rawSectionWord,
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
                throw new IllegalArgumentException("Incoming decoder does not belong to the bound R12A source view/count");
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
    public State loadSection(State state, int cameraXword) { return kernel.loadSection(state, cameraXword); }
    public State updateSection(State state, int cameraXword) { return kernel.updateSection(state, cameraXword); }
    public State prepare(State state) { return kernel.prepare(state); }
    public State serviceFast(State state) { return kernel.serviceFast(state); }
    public State serviceSlow(State state, int scrollLockWORD) { return kernel.serviceSlow(state, scrollLockWORD); }

    /** Pure explicit InitVDP-pattern transform, not an actual clear or a Sonic/plane writer. */
    public State clearCurrentPatterns(State state) {
        checked(state);
        return withValues(state, state.codeTable, CdR12aCameraArtResidency.clear(state.residency));
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
                CdR12aCameraArtResidency.withTile(state.residency, destinationWord, tileBytes));
    }

    /**
     * Native LoadPLCImm, not InitPLC/append/service/POP. Active queued decoding is
     * unowned here because its saved continuation would need the overwritten table.
     * A merely pending inactive descriptor is untouched and later prepares with the new table.
     */
    public State loadImmediateList(State state, int id) {
        checked(state); word(id); requireInactive(state, Operation.IMMEDIATE_LIST);
        CdR12aObjectAssets.PlcList list = null;
        for (var candidate : objects.plcLists()) if (candidate.nativeId() == id) { list = candidate; break; }
        if (list == null) throw failure(Reason.PLC_LIST, state, Operation.IMMEDIATE_LIST, 0, 0, null, null);
        State current = state;
        for (var entry : list.entries()) {
            var source = inputs.get(entry.source());
            var decoder = prepareImmediate(current, source, Operation.IMMEDIATE_LIST, entry.destinationWord(), null);
            current = withValues(current, decoder.codeTable(), current.residency);
            int destination = entry.destinationWord();
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
                    Integer.toUnsignedLong(assets.profile().range(ART_LIFE_ICON).nativeAddress()) + sourceOffset, 0xB420, null, null);
        }
        State current = state;
        for (int tile = 0; tile < 4; tile++) {
            var resident = commitTile(current, 0xB420 + tile * 32, lifeIcon.slice(sourceOffset + tile * 32, 32),
                    Operation.LIFE_ICON, Integer.toUnsignedLong(assets.profile().range(ART_LIFE_ICON).nativeAddress()) + sourceOffset + tile * 32L);
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

    private static CdSectionArtKernel.Source source(CdR12aObjectAssets assets, CdSectionArtKernel.Binding binding,
                                                    CdR12aObjectAssets.ReaderProfile profile) {
        var bytes = assets.readerView(profile.asset(), CdR12aObjectAssets.ReaderKind.NATIVE);
        var blob = profile.sourceBlobRange();
        if (!blob.equals(assets.profile().range(profile.asset())) || bytes.length() != profile.nativeInputRange().length()
                || profile.nativeInputRange().fileOffset() != blob.fileOffset())
            throw new IllegalArgumentException("Named R12A native reader differs from bound metadata");
        return new CdSectionArtKernel.Source(binding, profile.asset(), blob.file(), blob.domain(), blob.fileOffset(),
                blob.length(), Integer.toUnsignedLong(blob.nativeAddress()), bytes);
    }

    private static SavedDecoder saved(CdNemesisPlcDecoder.State state) {
        return new SavedDecoder(state.xorMode() ? Writer.XOR : Writer.NORMAL, state.repeatWord(), state.pixelNibble(),
                state.previousRow(), state.readWord(), state.shiftWord());
    }

    private State withValues(State state, CdAssetBytes table, CdR12aCameraArtResidency.State resident) {
        return new State(this, state.sectionWord, state.queue, table, state.decoder, state.savedDecoder,
                state.remainingTilesWord, state.processBudgetWord, state.hintWord, state.padWord, resident, state.stopped);
    }

    private void admitDestination(State state, int destination, Operation operation, long source) {
        for (var region : CdR12aCameraArtResidency.Region.values()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        try { CdR12aCameraArtResidency.readTile(state.residency, destination >>> 5); }
        catch (CdR12aCameraArtResidency.OwnershipBoundary fault) {
            throw failure(Reason.RESIDENCY, state, operation, source, destination, null, fault);
        }
        throw new IllegalStateException("Residency extent and reader disagree");
    }

    private CdR12aCameraArtResidency.State commitTile(State state, int destination, CdAssetBytes bytes,
                                                     Operation operation, long source) {
        try { return CdR12aCameraArtResidency.withTile(state.residency, destination, bytes); }
        catch (CdR12aCameraArtResidency.OwnershipBoundary fault) {
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

    private final class KernelStates implements CdSectionArtKernel.States<State, CdR12aCameraArtResidency.State> {
        @Override public void checked(State state) { CdR12aSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR12aCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }
        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remaining,
                                    int budget, int hint, int pad, CdR12aCameraArtResidency.State resident, boolean stopped) {
            return new State(CdR12aSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remaining, budget, hint, pad, resident, stopped);
        }
        @Override public SectionArtException failure(CdR11aSectionArt.Reason reason, State state,
                                                     long source, int destination, Throwable cause) {
            return CdR12aSectionArt.this.failure(Reason.valueOf(reason.name()), state, Operation.QUEUED,
                    source, destination, null, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR12aSectionArt.this.admitDestination(state, destination, Operation.QUEUED, state.decoder.sourcePointer());
        }
        @Override public CdR12aCameraArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
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
