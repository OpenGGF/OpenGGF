package com.openggf.game.soniccd;

import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.level.objects.ObjectManager;

import java.util.function.Supplier;

/**
 * The one owner of {@code levelVIntCounter}'s value when a stage starts ({@code Main CPU Variables.inc:155}; the
 * engine's copy is the object clock, {@link ObjectManager#getVblaCounter()}, which every V-INT advances as
 * {@code VInt_Done}'s {@code addq.l #1,levelVIntCounter} does, Level/Main.asm:711-712, lag V-INTs included).
 *
 * <p>Native: {@code LevelStart}'s {@code .ClearFlowers} clears it (Level/Main.asm:178-184, $A00 bytes from
 * {@code flowerPosBuf}); the load then takes V-INTs before {@code Level_MainLoop}'s first {@code VSync}: the
 * {@code .WaitPLC2} loop (Level/Main.asm:253-259), the palette fade (:319, :325) and every V-INT that arrives while
 * the 68000 loads and decompresses with interrupts enabled (from :239). Iteration 1's V-INT then makes it the
 * value row 0 shows (38 to 51 over the 70 stage recordings). How many V-INTs the load takes is hardware time
 * (decompression on a 68000, the PLC queue's per-frame budget); the engine's load is synchronous and models none
 * of them.
 *
 * <p>Production (no trace): the load is submitted to the session's hardware timing service as one
 * {@code SCD_STAGE_LOAD} job ({@link CdLoadTiming#stageLoad}), and the V-INTs the normal-play load-time profile holds
 * it for become the base: under {@code FAST} or {@code PROFILED} the emulator-measured count for this stage file and
 * entry ({@code load-time-profiles/soniccd-v1.json}), under {@code NONE} or with no manifest entry 0 (the base before
 * this mechanism existed). Every stage load, including a death restart and a time-warp arrival, starts from that
 * clear: the counter is not carried from the previous stage ({@link com.openggf.game.internal.LevelLoadSetsVIntCounter}).
 *
 * <p>Trace replay: the hardware-relative initial base of the cross-game timing contract
 * (docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md section 4), the same seed
 * the other games take in {@code TraceReplaySessionBootstrap}: the counter before the V-INT that produced row 0.
 * Sonic CD's load runs that V-INT and iteration 1's object pass itself ({@link CdFirstMainLoopIteration}), so the
 * replay installs the base before the load ({@link #seedNextStageForReplay}); the next load consumes it once and
 * submits nothing, whatever the load-time setting. Nothing here reads trace data.
 */
public final class CdLevelVIntCounter implements com.openggf.game.internal.LevelLoadSetsVIntCounter {
    /** A replay's seed for the next stage load only; {@code null} when production decides. */
    private Integer pendingReplaySeed;

    /**
     * The load's step: {@code .ClearFlowers}'s clear plus the load's V-INTs, before iteration 1's V-INT, which
     * {@link CdFirstMainLoopIteration} services through the object pass. A pending replay seed wins; otherwise the
     * load is submitted to {@code timing} and paced by the normal-play profile ({@code null} timing: 0).
     */
    void establishAtStageStart(ObjectManager objects, HardwareTimingService timing,
                               Supplier<HardwareWorkSubmission> load) {
        int loadVInts;
        if (pendingReplaySeed != null) {
            loadVInts = pendingReplaySeed;
            pendingReplaySeed = null;
        } else {
            HardwareWorkSubmission submission = timing == null ? null : load.get();
            loadVInts = submission == null ? 0 : CdLoadTiming.vintsUntilReady(timing, submission);
        }
        if (objects != null) objects.initVblaCounter(loadVInts);
    }

    /**
     * Section 4's seed, once, before the stage load a trace replay starts with: the counter before the V-INT that
     * produced row 0 ({@code TraceReplaySessionBootstrap.vblankCounterBeforeRowZero}).
     */
    void seedNextStageForReplay(int counterBeforeRowZero) {
        if (counterBeforeRowZero < 0) {
            throw new IllegalArgumentException("levelVIntCounter before row 0 is negative: " + counterBeforeRowZero);
        }
        pendingReplaySeed = counterBeforeRowZero;
    }
}
