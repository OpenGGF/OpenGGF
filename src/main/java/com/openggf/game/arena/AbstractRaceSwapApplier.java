package com.openggf.game.arena;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.mods.run.RaceEvent;
import com.openggf.mods.run.RaceEventResult;

/** One admitted external event, consumed before gameplay owners run in that executed step. */
public abstract class AbstractRaceSwapApplier implements RaceSwapApplier {
    @RewindTransient(reason="Run/replay input transfer, consumed at the admitted step boundary; never captured")
    private RaceEvent queued;
    @RewindTransient(reason="Observation delivered with the current RunStep, not simulation state")
    private RaceEventResult applied = RaceEventResult.NONE;

    @Override public final boolean hasQueuedEvent() { return queued!=null; }
    @Override public final void queueForStep(RaceEvent event) {
        java.util.Objects.requireNonNull(event);
        if (queued != null) throw new IllegalStateException("A race event is already queued for this step");
        queued = event;
    }
    @Override public final void executeQueued(GameplayModeContext context) {
        var event = queued; queued = null;
        if (event != null) applied = apply(event,context);
    }
    @Override public final RaceEventResult consumeApplied() {
        var result=applied; applied=RaceEventResult.NONE; return result;
    }
    protected final void clearEvents() { queued=null; applied=RaceEventResult.NONE; }
    protected static long swapId(RaceEvent event) {
        return event instanceof RaceEvent.SwapApply apply ? apply.swapId() : ((RaceEvent.SwapFallback)event).swapId();
    }
}
