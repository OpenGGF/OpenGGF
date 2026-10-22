package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Event-owned {@code Load_PLC_2 #3} from {@code loc_12478}
 * (docs/skdisasm/sonic3k.asm:24636-24643).
 *
 * <p>The supplied FIFO is the same physical {@code Nem_decomp_queue} used by
 * the title/results owner, which captures it once for rewind. Resident decoded
 * rendering assets do not bypass this production submission or readiness gate.
 */
public final class Sonic3kGameOverPlcService implements PlcLifecycleService {
    private final Rom rom;
    private final NemesisPlcServiceQueue queue;

    Sonic3kGameOverPlcService(Rom rom, NemesisPlcServiceQueue queue) {
        this.rom = Objects.requireNonNull(rom);
        this.queue = Objects.requireNonNull(queue);
    }

    /** Clears both queued and active work, then submits the ROM's PLC3. */
    public void beginGameOver() throws IOException {
        var definition = Sonic3kPlcLoader.parsePlc(rom, 3);
        var patterns = NemesisPlcPatternCounts.derive(rom, definition);
        // Load_PLC_2 calls Clear_Nem_Queue, including the active decoder's
        // variables (sonic3k.asm:2095-2130). replaceQueued deliberately cannot
        // interrupt a decoder, so the full queue snapshot must be cleared.
        queue.restore(new NemesisPlcQueueSnapshot(null, List.of()));
        queue.append(definition, patterns);
    }

    public boolean isBusy() {
        return queue.isBusy();
    }

    @Override
    public void serviceVBlank(PlcLifecyclePhase phase) {
        if (phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == PlcLifecyclePhase.NORMAL_PAUSE) {
            // VInt_8 -> Do_Updates -> Process_Nem_Queue_2, three patterns.
            // Pause_Loop's VInt_10 falls into VInt_8 outside special stages
            // (sonic3k.asm:719-723, 806-815, 1572-1574, 2200-2206).
            queue.servicePatterns(3);
        } else if (phase == PlcLifecyclePhase.PALETTE_FADE) {
            // Blocking fades arm VInt_12 -> Process_Nem_Queue, six patterns
            // (sonic3k.asm:871-874, 2174-2189, 4938-4943).
            queue.servicePatterns(6);
        }
    }

    @Override
    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == PlcLifecyclePhase.PALETTE_FADE;
    }

    @Override
    public void prepareAfterLoop(PlcLifecyclePhase phase) {
        if (hasPreparationBoundary(phase)) {
            // LevelLoop prepares after Process_Sprites; blocking fade loops
            // also prepare after their VBlank. Pause_Loop has no such call
            // (sonic3k.asm:7916-7940, 4938-4943, 1572-1617).
            queue.prepareHead();
        }
    }
}
