package com.openggf.game.session;

import com.openggf.game.timing.VIntRunCounter;

/**
 * Engine-internal access to timing owners held by {@link EngineContext}. Kept out
 * of {@code EngineContext}'s public (Mod API) surface: creator code never reads the
 * power-on {@code V_int_run_count}.
 */
public final class EngineTiming {

    private EngineTiming() {
    }

    /** The power-on {@code V_int_run_count} owner of {@code context}. */
    public static VIntRunCounter vIntRunCounter(EngineContext context) {
        return context.vIntRunCounter();
    }

    /** The power-on {@code V_int_run_count} owner of the current engine context. */
    public static VIntRunCounter vIntRunCounter() {
        return EngineServices.current().vIntRunCounter();
    }
}
