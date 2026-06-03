package com.openggf.testmode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

/**
 * Resolves trace render-visibility decisions from config as three independent
 * master gates. Honored by both live Trace Test Mode and the headless capture
 * recorder (Plan 2 wires the gates at the render sites).
 *
 * <p>{@code showDebugHud()} is a master gate only — it does NOT mutate
 * {@code DebugOverlayManager}. When true, the render site renders the debug HUD
 * and the existing per-element {@code DebugOverlayToggle} states decide which
 * panels show; when false, the debug HUD is skipped without touching any toggle
 * state. (Driving per-panel selection from capture config in headless mode is a
 * Plan 2 concern.)
 */
public final class TraceRenderVisibility {

    private final boolean showGhosts;
    private final boolean showGameHud;
    private final boolean showDebugHud;

    private TraceRenderVisibility(boolean showGhosts, boolean showGameHud, boolean showDebugHud) {
        this.showGhosts = showGhosts;
        this.showGameHud = showGameHud;
        this.showDebugHud = showDebugHud;
    }

    public static TraceRenderVisibility fromConfig(SonicConfigurationService config) {
        return new TraceRenderVisibility(
                config.getBoolean(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS),
                config.getBoolean(SonicConfiguration.TRACE_SHOW_GAME_HUD),
                config.getBoolean(SonicConfiguration.TRACE_SHOW_DEBUG_HUD));
    }

    public boolean showGhosts() { return showGhosts; }

    public boolean showGameHud() { return showGameHud; }

    public boolean showDebugHud() { return showDebugHud; }
}
