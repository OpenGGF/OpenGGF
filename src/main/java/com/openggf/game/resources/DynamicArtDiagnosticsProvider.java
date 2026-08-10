package com.openggf.game.resources;

import java.util.List;

/**
 * Read-only production diagnostics surface for player dynamic-art work.
 *
 * <p>Lifecycle mutation stays on {@link DynamicArtLifecycleService}; trace
 * comparison receives only immutable snapshots from this narrow interface.
 */
public interface DynamicArtDiagnosticsProvider {
    DynamicArtDiagnosticsSnapshot latestSnapshot();

    List<DynamicArtGapTransition> gapTransitions();

    DynamicArtGapDiagnosticsSnapshot gapSnapshot();
}
