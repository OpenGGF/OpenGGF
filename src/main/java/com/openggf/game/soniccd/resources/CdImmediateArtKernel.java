package com.openggf.game.soniccd.resources;

// Bounded SOURCE packet; authored Java UNRUN. Root owns queued validation.

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Package-private extraction of the original R12B immediate/RAM/raw-life operations.
 * Both R12B and R13 adapt their retained sources and immutable values to these same loops.
 * No queue service, alternate decoder, clock, GPU or registered capture owner is introduced.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Functions (General).asm:401-579;
 * Main.asm:599-614. Complete-tile prefixes and original decoder causes are preserved.
 */
final class CdImmediateArtKernel<S,R> {
    enum Reason { PLC_LIST,DECODE_PREPARE,DECODE_TILE,RESIDENCY,ACTIVE_QUEUED_DECODE,
                  IMMEDIATE_HEADER_COUNT,LIFE_ICON_SOURCE,BLOCK_PRODUCT_MISMATCH }
    enum Operation { IMMEDIATE_LIST,BLOCKS_RAM,LIFE_ICON }
    record Blocks<S>(S state,CdAssetBytes bytes,CdBlockDefinitionStaging blockDefinitions){}
    interface States<S,R> {
        void checked(S state);
        CdAssetBytes codeTable(S state);
        R residency(S state);
        int remainingTilesWord(S state);
        CdR11aPlcBuffer.State queue(S state);
        S withValues(S state,CdAssetBytes table,R residency);
        RuntimeException failure(Reason reason,S state,Operation operation,long source,int destination,CdAssetBytes ramPrefix,Throwable cause);
        void admitDestination(S state,int destination,Operation operation,long source);
        R commitTile(S state,int destination,CdAssetBytes bytes,Operation operation,long source);
    }
    private final Map<Integer,List<CdR11aPlcBuffer.Entry>> lists;
    private final Map<Long,CdSectionArtKernel.Source> sources;
    private final CdSectionArtKernel.Source blocksInput;
    private final java.util.function.Supplier<CdAssetBytes> blocksProduct;
    private final CdAssetBytes lifeIcon;
    private final long lifeNativeAddress;
    private final States<S,R> states;
    CdImmediateArtKernel(Map<Integer,List<CdR11aPlcBuffer.Entry>> lists,Map<Long,CdSectionArtKernel.Source> sources,
                         CdSectionArtKernel.Source blocksInput,java.util.function.Supplier<CdAssetBytes> blocksProduct,CdAssetBytes lifeIcon,
                         long lifeNativeAddress,States<S,R> states){
        var copied=new java.util.HashMap<Integer,List<CdR11aPlcBuffer.Entry>>();
        for(var entry:Objects.requireNonNull(lists).entrySet()){word(entry.getKey());copied.put(entry.getKey(),List.copyOf(entry.getValue()));}
        this.lists=Map.copyOf(copied);this.sources=Map.copyOf(Objects.requireNonNull(sources));
        this.blocksInput=Objects.requireNonNull(blocksInput);this.blocksProduct=Objects.requireNonNull(blocksProduct);
        this.lifeIcon=Objects.requireNonNull(lifeIcon);this.lifeNativeAddress=lifeNativeAddress;this.states=Objects.requireNonNull(states);
    }
    S loadImmediateList(S state, int id) {
        states.checked(state); word(id); requireInactive(state, Operation.IMMEDIATE_LIST);
        var list = lists.get(id);
        if (list == null) throw states.failure(Reason.PLC_LIST, state, Operation.IMMEDIATE_LIST, 0, 0, null, null);
        S current = state;
        for (var entry : list) {
            var source = sources.get(entry.source());
            var decoder = prepareImmediate(current, source, Operation.IMMEDIATE_LIST, entry.destination(), null);
            current = states.withValues(current, decoder.codeTable(), states.residency(current));
            int destination = entry.destination();
            while (decoder.remainingTiles() != 0) {
                states.admitDestination(current, destination, Operation.IMMEDIATE_LIST, decoder.sourcePointer());
                CdNemesisPlcDecoder.TileResult tile;
                try { tile = CdNemesisPlcDecoder.decodeTile(decoder); }
                catch (CdNemesisPlcDecoder.DecodeException fault) {
                    throw states.failure(Reason.DECODE_TILE, current, Operation.IMMEDIATE_LIST,
                            fault.diagnostic().sourcePointer(), destination, null, fault);
                }
                var resident = states.commitTile(current, destination, tile.bytes(), Operation.IMMEDIATE_LIST, decoder.sourcePointer());
                current = states.withValues(current, states.codeTable(current), resident);
                decoder = tile.state();
                destination = (destination + 32) & 0xFFFF;
            }
        }
        return current;
    }

    /**
     * Actual shared NemDecToRAM writer. Both complete tiles and the reached
     * complete LONG rows of a faulting tile are RAM writes. The decoder's private
     * exception can describe these only at this actual call; no public diagnostic
     * or value adoption API is added. Graphics still publish complete tiles only.
     * The produced knowledge has UNKNOWN suffix: the actual lifetime owner must
     * overlay these writes on its own bank (which may have a genuine clear).
     */
    Blocks<S> decodeBlocksToRam(S state) {
        states.checked(state); requireInactive(state, Operation.BLOCKS_RAM);
        var empty = CdAssetBytes.copyOf(new byte[0]);
        var decoder = prepareImmediate(state, blocksInput, Operation.BLOCKS_RAM, 0, empty);
        S current = states.withValues(state, decoder.codeTable(), states.residency(state));
        byte[] bytes = new byte[CdBlockDefinitionStaging.BYTE_LENGTH];
        int at = 0;
        while (decoder.remainingTiles() != 0) {
            // Stop before asking the tile decoder to read beyond this finite
            // writer's owned bank. Oversized products have no supported native
            // continuation here; do not decode a later tile merely to reject it.
            if (at == bytes.length) {
                throw states.failure(Reason.BLOCK_PRODUCT_MISMATCH, current, Operation.BLOCKS_RAM,
                        decoder.sourcePointer(), at, CdAssetBytes.copyOf(bytes),
                        new IndexOutOfBoundsException("Block definitions exceed owned8192 bytes"));
            }
            CdNemesisPlcDecoder.TileResult tile;
            try { tile = CdNemesisPlcDecoder.decodeTile(decoder); }
            catch (CdNemesisPlcDecoder.DecodeException fault) {
                // Only this invocation's actually emitted LONGs. Never append
                // row/pixel scratch or synthesize the remainder of a tile.
                at = retainRamRows(current, bytes, at, fault.diagnostic().emittedRows(),
                        fault.diagnostic().sourcePointer(), fault);
                throw states.failure(Reason.DECODE_TILE, current, Operation.BLOCKS_RAM,
                        fault.diagnostic().sourcePointer(), at,
                        CdAssetBytes.copyOf(Arrays.copyOf(bytes, at)), fault);
            }
            at = retainRamRows(current, bytes, at, tile.bytes(), decoder.sourcePointer(), null);
            decoder = tile.state();
        }
        var actual = CdAssetBytes.copyOf(Arrays.copyOf(bytes, at));
        var product = blocksProduct.get();
        if (!Arrays.equals(actual.copy(), product.copy())) {
            throw states.failure(Reason.BLOCK_PRODUCT_MISMATCH, current, Operation.BLOCKS_RAM,
                    decoder.sourcePointer(), at, actual, null);
        }
        return new Blocks<>(current, product,
                CdBlockDefinitionStaging.unknown().withCompletedPrefix(actual));
    }

    private int retainRamRows(S current, byte[] bytes, int at, CdAssetBytes rows,
                              long source, Throwable decoderCause) {
        for (int row = 0; row < rows.length(); row += 4) {
            if (at > bytes.length - 4) {
                var outside = new IndexOutOfBoundsException("Block-definition row outside owned8192 bytes");
                if (decoderCause != null) outside.initCause(decoderCause);
                throw states.failure(Reason.BLOCK_PRODUCT_MISMATCH, current, Operation.BLOCKS_RAM,
                        source, at, CdAssetBytes.copyOf(Arrays.copyOf(bytes, at)), outside);
            }
            for (int i = 0; i < 4; i++) bytes[at + i] = rows.signedByte(row + i);
            at += 4;
        }
        return at;
    }

    /** Actual scratch (raw BYTE &7F)*128; raw period and every queued/table field are nonwrites. */
    S loadLifeIcon(S state, int rawTimeZoneByte) {
        states.checked(state); nativeByte(rawTimeZoneByte);
        int sourceOffset = (rawTimeZoneByte & 0x7F) * 128;
        if (sourceOffset > lifeIcon.length() - 128) {
            throw states.failure(Reason.LIFE_ICON_SOURCE, state, Operation.LIFE_ICON,
                    lifeNativeAddress + sourceOffset, 0xB420, null, null);
        }
        S current = state;
        for (int tile = 0; tile < 4; tile++) {
            var resident = states.commitTile(current, 0xB420 + tile * 32, lifeIcon.slice(sourceOffset + tile * 32, 32),
                    Operation.LIFE_ICON, lifeNativeAddress + sourceOffset + tile * 32L);
            current = states.withValues(current, states.codeTable(current), resident);
        }
        return current;
    }

    private CdNemesisPlcDecoder.State prepareImmediate(S state, CdSectionArtKernel.Source source,
                                                       Operation operation, int destination, CdAssetBytes ramPrefix) {
        // NemDecMain403-410: row count is a WORD. 0 or >=8192 needs a separate
        // native wrapping-row admission, not the queued zero-count skip.
        int count = source.readerBytes().unsignedWord(0) & 0x7FFF;
        if (count == 0 || count >= 8192) {
            throw states.failure(Reason.IMMEDIATE_HEADER_COUNT, state, operation, source.nativeAddress(), destination, ramPrefix, null);
        }
        try { return CdNemesisPlcDecoder.prepare(source.readerBytes(), source.nativeAddress(), states.codeTable(state)); }
        catch (CdNemesisPlcDecoder.DecodeException fault) {
            S prefix = states.withValues(state, fault.diagnostic().codeTable(), states.residency(state));
            throw states.failure(Reason.DECODE_PREPARE, prefix, operation, fault.diagnostic().sourcePointer(), destination, ramPrefix, fault);
        }
    }

    private void requireInactive(S state, Operation operation) {
        if (states.remainingTilesWord(state) != 0) {
            throw states.failure(Reason.ACTIVE_QUEUED_DECODE, state, operation, states.queue(state).headSource(),
                    states.queue(state).headDestination(), null, null);
        }
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
    private static void nativeByte(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Native BYTE must be unsigned8");
    }
}
