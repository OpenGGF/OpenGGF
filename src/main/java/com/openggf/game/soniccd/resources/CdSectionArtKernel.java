package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Reason;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared immutable section/PLC/complete-tile algorithm, not a second state or art owner.
 * The stage facade supplies its own immutable State and typed residency/failure adapters.
 * Existing SavedDecoder/Writer/Reason are semantic value vocabularies despite historical names.
 * The bound origin and complete source metadata are configuration, not execution authority.
 * Sources: N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Section Art Load.asm:7-46; Level/Functions (General).asm:588-785, FixBugs=0.
 * No clock, stage admission, asset decode/preload, capture registration, GPU or runtime callback.
 */
final class CdSectionArtKernel<S, R> {
    /** Actual assets use their full immutable identity; declared fixtures use their facade identity. */
    record Binding(Object identity, CdNativeFile file, CdMmdImage.Domain domain) {
        Binding {
            Objects.requireNonNull(identity, "binding identity");
            Objects.requireNonNull(file, "binding file");
            Objects.requireNonNull(domain, "binding domain");
        }
    }

    record Section(int rightThresholdWord, int initialPlcId, int updatePlcId) { }

    /** Native source blob extent and finite reader bytes remain distinct; no destination is canonical here. */
    record Source(Binding binding, Object producer, CdNativeFile file, CdMmdImage.Domain domain,
                  int blobOffset, int blobLength, long nativeAddress, CdAssetBytes readerBytes) {
        Source {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(producer, "producer");
            Objects.requireNonNull(file, "source file");
            Objects.requireNonNull(domain, "source domain");
            Objects.requireNonNull(readerBytes, "readerBytes");
            if (file != binding.file() || domain != binding.domain()
                    || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || blobOffset < 0 || blobLength < 0
                    || (long) blobOffset + blobLength > 0x100000000L - CdMmdHeader.WORD_RAM_ADDRESS
                    || nativeAddress != (long) CdMmdHeader.WORD_RAM_ADDRESS + blobOffset) {
                throw new IllegalArgumentException("Bound original-program source metadata differs");
            }
        }
    }

    /** Package-private pure value adaptation; implementations never publish to another runtime owner. */
    interface States<S, R> {
        void checked(S state);
        int sectionWord(S state);
        CdR11aPlcBuffer.State queue(S state);
        CdAssetBytes codeTable(S state);
        CdNemesisPlcDecoder.State decoder(S state);
        SavedDecoder savedDecoder(S state);
        int remainingTilesWord(S state);
        int processBudgetWord(S state);
        int hintWord(S state);
        int padWord(S state);
        R residency(S state);
        boolean stopped(S state);
        S make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
               CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder, int remainingTilesWord,
               int processBudgetWord, int hintWord, int padWord, R resident, boolean stopped);
        RuntimeException failure(Reason reason, S state, long source, int destination, Throwable cause);
        void admitDestination(S state, int destination);
        R writeTile(S state, int destination, CdAssetBytes bytes);
    }

    private final Binding binding;
    private final Map<Integer, List<CdR11aPlcBuffer.Entry>> lists;
    private final List<Section> sections;
    private final Map<Long, Source> programs;
    private final States<S, R> states;
    private final SavedDecoder clearedDecoder;

    CdSectionArtKernel(Binding binding, Map<Integer, List<CdR11aPlcBuffer.Entry>> lists,
                       List<Section> sections, List<Source> programs, States<S, R> states,
                       SavedDecoder clearedDecoder) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.states = Objects.requireNonNull(states, "states");
        this.clearedDecoder = Objects.requireNonNull(clearedDecoder, "clearedDecoder");
        var copiedLists = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var entry : Objects.requireNonNull(lists, "lists").entrySet()) {
            word(entry.getKey());
            copiedLists.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.lists = Map.copyOf(copiedLists);
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
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
        var sourceMap = new HashMap<Long, Source>();
        for (var source : List.copyOf(Objects.requireNonNull(programs, "programs"))) {
            if (source.binding() != this.binding) {
                throw new IllegalArgumentException("Foreign program/source binding");
            }
            if (sourceMap.putIfAbsent(source.nativeAddress(), source) != null) {
                throw new IllegalArgumentException("Duplicate native source program");
            }
        }
        this.programs = Map.copyOf(sourceMap);
    }

    S updateSection(S state, int cameraXword) {
        states.checked(state); word(cameraXword);
        int index = sectionIndex(state, cameraXword);
        int selector = index * 2;
        if ((states.sectionWord(state) >>> 8) == selector) return state;
        S selected = withSection(state, (selector << 8) | (states.sectionWord(state) & 0xFF));
        return initList(selected, sections.get(index).updatePlcId());
    }

    S loadSection(S state, int cameraXword) {
        states.checked(state); word(cameraXword);
        int index = sectionIndex(state, cameraXword);
        S selected = withSection(state, ((index * 2) << 8) | (states.sectionWord(state) & 0xFF));
        return appendList(selected, sections.get(index).initialPlcId());
    }

    S clearPlcs(S state) {
        states.checked(state);
        return states.make(states.sectionWord(state), CdR11aPlcBuffer.cleared(), states.codeTable(state),
                null, clearedDecoder, 0, 0, 0, 0, states.residency(state), false);
    }

    S initList(S state, int id) {
        states.checked(state); word(id);
        var rows = resolveList(state, id);
        S cleared = states.make(states.sectionWord(state), CdR11aPlcBuffer.cleared(), states.codeTable(state),
                null, clearedDecoder, 0, 0, 0, 0, states.residency(state), false);
        try {
            return withQueue(cleared, CdR11aPlcBuffer.replace(states.queue(cleared), rows));
        } catch (CdR11aPlcBuffer.BufferBoundary boundary) {
            throw states.failure(Reason.DESCRIPTOR_BUFFER, withQueue(cleared, boundary.state()),
                    boundary.state().headSource(), boundary.state().headDestination(), boundary);
        }
    }

    S appendList(S state, int id) {
        states.checked(state); word(id);
        var rows = resolveList(state, id);
        try {
            return withQueue(state, CdR11aPlcBuffer.append(states.queue(state), rows));
        } catch (CdR11aPlcBuffer.BufferBoundary boundary) {
            throw states.failure(Reason.DESCRIPTOR_BUFFER, withQueue(state, boundary.state()),
                    boundary.state().headSource(), boundary.state().headDestination(), boundary);
        }
    }

    S prepare(S state) {
        states.checked(state);
        if (states.queue(state).headSource() == 0 || states.remainingTilesWord(state) != 0) return state;
        long source = states.queue(state).headSource();
        var program = programs.get(source);
        if (program == null) {
            throw states.failure(Reason.UNKNOWN_SOURCE, state, source, states.queue(state).headDestination(), null);
        }
        // Native MOVE.W/ANDI.W/store precedes table building. An unaligned or
        // incomplete atomic header read must not publish even a partial count.
        int headerCount = (source & 1) == 0 && program.readerBytes().length() >= 2
                ? program.readerBytes().unsignedWord(0) & 0x7FFF : 0;
        try {
            var decoder = CdNemesisPlcDecoder.prepare(program.readerBytes(), source, states.codeTable(state));
            return states.make(states.sectionWord(state),
                    CdR11aPlcBuffer.withHeadSource(states.queue(state), decoder.sourcePointer()),
                    decoder.codeTable(), decoder, saved(decoder), decoder.remainingTiles(),
                    states.processBudgetWord(state), states.hintWord(state), states.padWord(state), states.residency(state), false);
        } catch (CdNemesisPlcDecoder.DecodeException boundary) {
            S prefix = states.make(states.sectionWord(state), states.queue(state), boundary.diagnostic().codeTable(),
                    null, states.savedDecoder(state), headerCount, states.processBudgetWord(state),
                    states.hintWord(state), states.padWord(state), states.residency(state), false);
            throw states.failure(Reason.DECODE_PREPARE, prefix, boundary.diagnostic().sourcePointer(),
                    states.queue(state).headDestination(), boundary);
        }
    }

    S serviceSlow(S state, int scrollLockWORD) {
        states.checked(state); word(scrollLockWORD);
        return service(state, (scrollLockWORD & 0xFF00) != 0 ? 18 : 3);
    }

    S serviceFast(S state) {
        states.checked(state);
        return service(state, 18);
    }

    private S service(S before, int budget) {
        if (states.remainingTilesWord(before) == 0) return before;
        if (states.decoder(before) == null || states.decoder(before).remainingTiles() != states.remainingTilesWord(before)) {
            throw new IllegalStateException("No matching complete-tile decoder continuation");
        }
        int destination = states.queue(before).headDestination();
        // Native preadvance is by the WHOLE chosen budget, before any decode.
        var advanced = CdR11aPlcBuffer.withHeadDestination(states.queue(before), (destination + budget * 32) & 0xFFFF);
        S current = states.make(states.sectionWord(before), advanced, states.codeTable(before),
                states.decoder(before), states.savedDecoder(before), states.remainingTilesWord(before), budget,
                states.hintWord(before), states.padWord(before), states.residency(before), false);
        while (true) {
            states.admitDestination(current, destination);
            CdNemesisPlcDecoder.TileResult tile;
            try {
                tile = CdNemesisPlcDecoder.decodeTile(states.decoder(current));
            } catch (CdNemesisPlcDecoder.DecodeException boundary) {
                // Only completed earlier tiles are resident. emittedRows is
                // diagnostic evidence, not an admitted partial-tile commit.
                throw states.failure(Reason.DECODE_TILE, current, boundary.diagnostic().sourcePointer(), destination, boundary);
            }
            R resident = states.writeTile(current, destination, tile.bytes());
            var decoder = tile.state();
            int remaining = decoder.remainingTiles(); // Sole counter: NEVER independently decrement.
            if (remaining == 0) {
                // FixBugs=0: native88-byte POP, no erase, NO final decoder RAM
                // store and NO final budget decrement. Next head waits for CPU.
                return states.make(states.sectionWord(current), CdR11aPlcBuffer.pop(states.queue(current)),
                        states.codeTable(current), decoder, states.savedDecoder(current), remaining, states.processBudgetWord(current),
                        states.hintWord(current), states.padWord(current), resident, false);
            }
            int left = states.processBudgetWord(current) - 1;
            if (left == 0) {
                return states.make(states.sectionWord(current),
                        CdR11aPlcBuffer.withHeadSource(states.queue(current), decoder.sourcePointer()),
                        states.codeTable(current), decoder, saved(decoder), remaining, 0,
                        states.hintWord(current), states.padWord(current), resident, false);
            }
            current = states.make(states.sectionWord(current), states.queue(current), states.codeTable(current),
                    decoder, states.savedDecoder(current), remaining, left, states.hintWord(current),
                    states.padWord(current), resident, false);
            destination = (destination + 32) & 0xFFFF;
        }
    }

    private int sectionIndex(S state, int camera) {
        for (int i = 0; i < sections.size(); i++) {
            if (camera < sections.get(i).rightThresholdWord()) return i;
        }
        // FFFF equality reaches an unowned next table word before section write.
        throw states.failure(Reason.SECTION_TABLE, state, 0, 0, null);
    }

    private List<CdR11aPlcBuffer.Entry> resolveList(S state, int id) {
        var rows = lists.get(id);
        if (rows == null) throw states.failure(Reason.PLC_LIST, state, states.queue(state).headSource(), states.queue(state).headDestination(), null);
        return rows;
    }

    private S withSection(S state, int section) {
        return states.make(section, states.queue(state), states.codeTable(state), states.decoder(state), states.savedDecoder(state),
                states.remainingTilesWord(state), states.processBudgetWord(state), states.hintWord(state), states.padWord(state),
                states.residency(state), states.stopped(state));
    }

    private S withQueue(S state, CdR11aPlcBuffer.State queue) {
        return states.make(states.sectionWord(state), queue, states.codeTable(state), states.decoder(state), states.savedDecoder(state),
                states.remainingTilesWord(state), states.processBudgetWord(state), states.hintWord(state), states.padWord(state),
                states.residency(state), states.stopped(state));
    }

    private static SavedDecoder saved(CdNemesisPlcDecoder.State decoder) {
        return new SavedDecoder(decoder.xorMode() ? Writer.XOR : Writer.NORMAL, decoder.repeatWord(),
                decoder.pixelNibble(), decoder.previousRow(), decoder.readWord(), decoder.shiftWord());
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
