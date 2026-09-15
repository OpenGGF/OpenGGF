package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The Nemesis FIFO drained by a fresh zone's Level/loc_62CC title wait.
 * Assets are decoded by the ROM resource plan; this owner preserves the
 * independent readiness gate. It does not model later event-owned PLCs.
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
        }
    }

    @Override
    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
    }

    @Override
    public void prepareAfterLoop(PlcLifecyclePhase phase) {
        if (hasPreparationBoundary(phase)) {
            // loc_62CC calls Process_Nem_Queue_Init after Process_Sprites.
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
