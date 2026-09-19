package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;

/** Native {@code loc_593EC} state that differs from a cold DEZ2 level load. */
record DezActTransitionHandoff(Sonic3kZoneEvents eventAccess)
        implements SeamlessTransitionResourceHandoff {
    @Override
    public DeferredLevelResourceManifest deferredResources() {
        return DeferredLevelResourceManifest.EMPTY;
    }

    @Override
    public void transferAfterTargetInit() {
        var state = S3kRuntimeStates.currentDez(eventAccess.zoneRuntimeRegistry()).orElseThrow();
        if (state.actIndex() != 1) {
            throw new IllegalStateException("DEZ transition did not install Act 2");
        }
        // loc_593EC clears Events_routine_bg and never runs DEZ2_ScreenInit, so both
        // transition-only entry stages remain reachable after Load_Level.
        state.setForegroundRoutine(0);
        state.setBackgroundRoutine(0);
    }
}
