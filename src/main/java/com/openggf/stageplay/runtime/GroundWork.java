package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayGround.*;
import java.util.function.BooleanSupplier;

/** One budget covers context preparation and every native scan/clearance loop in a batch. */
final class GroundWork {
    static final class Stopped extends RuntimeException {}
    private final Batch batch;
    private final BooleanSupplier cancelled;
    private final long started = System.nanoTime();
    private int scans, ticks;
    private StopReason stopped = StopReason.NONE;
    GroundWork(Batch batch, BooleanSupplier cancelled) { this.batch = batch; this.cancelled = cancelled; }
    void check() {
        if (stopped != StopReason.NONE) throw new Stopped();
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) stop(StopReason.CANCELLED);
        if ((System.nanoTime()-started)/1_000_000 >= batch.maxMillis()) stop(StopReason.TIME_LIMIT);
    }
    void scan() { check(); if (scans >= batch.maxScans()) stop(StopReason.SCAN_LIMIT); scans++; }
    void tick() { check(); if (ticks >= batch.maxPreparationTicks()) stop(StopReason.PREPARATION_LIMIT); ticks++; }
    private void stop(StopReason reason) { stopped = reason; throw new Stopped(); }
    Work snapshot() { return new Work(scans,ticks,(System.nanoTime()-started)/1_000_000,stopped); }
}
