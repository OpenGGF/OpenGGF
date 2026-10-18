package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Logical Nemesis FIFO for fresh-zone Level/loc_62CC and results loc_2E0C4 waits,
 * plus raw event-owned descriptor submission. Title/results assets are decoded by
 * the ROM resource plan; this owner preserves their independent readiness gate.
 * Raw append does not publish art or qualify ordinary gameplay queue servicing.
 */
public final class Sonic3kLevelTitlePlcService
        implements PlcLifecycleService, RewindSnapshottable<NemesisPlcQueueSnapshot> {
    private final Rom rom;
    private final NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();

    public Sonic3kLevelTitlePlcService(Rom rom) {
        this.rom = Objects.requireNonNull(rom);
    }

    /** Level: clears the queue, appends the primary level PLC, then the character PLC. */
    public void beginFreshZoneTitle(int zone, int act, String mainCharacter) throws IOException {
        resetForMissingSnapshot();
        var bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        int index = Sonic3k.resolveLevelLoadBlockIndex(zone, act, bootstrap);
        int primary = rom.readByte(Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + index * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE) & 0xFF;
        if (primary != 0) append(primary);
        // Level/loc_60B4: native Player_mode selects the common HUD/life PLC.
        append("knuckles".equalsIgnoreCase(mainCharacter) ? 5
                : "tails".equalsIgnoreCase(mainCharacter) ? 7 : 1);
    }

    /** SpecialStage_Results: Load_PLC_Raw PLC_SpecialResultsText before loc_2E0C4. */
    public void beginSpecialStageResults() throws IOException {
        resetForMissingSnapshot();
        var definition = new PlcDefinition(-1,
                List.of(new PlcEntry(
                        Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR,
                        Sonic3kConstants.VRAM_SS_RESULTS_HUD_TEXT)));
        queue.append(definition, NemesisPlcPatternCounts.derive(rom, definition));
    }

    /**
     * Load_PLC_Raw appends the complete ROM definition without replacing either
     * the prepared head or preceding descriptors. Decode validates workload only;
     * no patterns are applied here. Ordinary service/publication remains separate.
     * The logical queue supports at most fifteen occupied entries; the shipped
     * FixBugs=0 retained-sixteenth-slot behavior is not modeled by this owner.
     */
    public void appendRaw(int definitionAddress) throws IOException {
        long size = rom.getSize();
        if (definitionAddress < 0 || (definitionAddress & 1) != 0
                || (long) definitionAddress + 2 > size) {
            throw new IOException("Raw Nemesis PLC definition is outside aligned ROM data");
        }
        int countMinusOne = (short) rom.read16BitAddr(definitionAddress);
        if (countMinusOne < 0) return; // Native signed count: an empty definition appends nothing.
        int count = countMinusOne + 1;
        long end = (long) definitionAddress + 2 + (long) count * 6;
        if (end > size) {
            throw new IOException("Raw Nemesis PLC descriptors exceed ROM data");
        }
        int occupied = queue.queuedEntryCount() + (queue.capture().activeEntry() != null ? 1 : 0);
        if (count > 15 - occupied) {
            throw new IllegalStateException("Raw Nemesis PLC exceeds supported logical queue occupancy");
        }
        var entries = new ArrayList<PlcEntry>(count);
        for (int index = 0; index < count; index++) {
            long address = (long) definitionAddress + 2 + (long) index * 6;
            int source = rom.read32BitAddr(address) & 0x00FFFFFF;
            int destinationBytes = rom.read16BitAddr(address + 4) & 0xFFFF;
            if ((destinationBytes & 31) != 0) {
                throw new IOException("Raw Nemesis PLC destination is not representable as whole tiles");
            }
            entries.add(new PlcEntry(source, destinationBytes / 32));
        }
        // -1 is the existing unindexed/raw definition marker, not a native PLC ID.
        var definition = new PlcDefinition(-1, List.copyOf(entries));
        var patternCounts = NemesisPlcPatternCounts.derive(rom, definition);
        // All descriptor reads, decode/count derivation and capacity checks precede mutation.
        queue.append(definition, patternCounts);
    }

    private void append(int plcId) throws IOException {
        var definition = Sonic3kPlcLoader.parsePlc(rom, plcId);
        // FixBugs=0 leaves the sixteenth descriptor retained after shifting.
        // Fresh-level recipes must stay within the retail fifteen usable slots.
        if (queue.queuedEntryCount() + definition.entries().size() > 15) {
            throw new IllegalStateException("fresh title PLCs occupy the retained sixteenth slot");
        }
        queue.append(definition, NemesisPlcPatternCounts.derive(rom, definition));
    }

    public boolean isBusy() {
        return queue.isBusy();
    }

    @Override
    public void serviceVBlank(PlcLifecyclePhase phase) {
        if (phase == PlcLifecyclePhase.LEVEL_TITLE_CARD) {
            // VInt_A_C -> Process_Nem_Queue: six patterns from the prepared
            // head. A VInt_0 lag closure services none and cannot arm a head.
            queue.servicePatterns(6);
        } else if (phase == PlcLifecyclePhase.SPECIAL_STAGE_RESULTS) {
            // VInt_1E -> Process_Nem_Queue_2: three patterns per prepared head.
            queue.servicePatterns(3);
        }
    }

    @Override
    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.LEVEL_TITLE_CARD
                || phase == PlcLifecyclePhase.SPECIAL_STAGE_RESULTS;
    }

    @Override
    public void prepareAfterLoop(PlcLifecyclePhase phase) {
        if (hasPreparationBoundary(phase)) {
            // loc_62CC and loc_2E0C4 call Process_Nem_Queue_Init after VBlank.
            queue.prepareHead();
        }
    }

    @Override
    public String key() { return "s3k-level-title-plc"; }

    @Override
    public NemesisPlcQueueSnapshot capture() { return queue.capture(); }

    @Override
    public void restore(NemesisPlcQueueSnapshot snapshot) { queue.restore(snapshot); }

    @Override
    public void resetForMissingSnapshot() {
        queue.restore(new NemesisPlcQueueSnapshot(null, List.of()));
    }
}
