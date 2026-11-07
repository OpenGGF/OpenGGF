package com.openggf;

import com.openggf.game.EndingProvider;

/** Executes the demo loop selected by its production ending owner. */
final class EndingDemoGameplay {
    private EndingDemoGameplay() { }

    static void run(GameLoopEndingSession.Host host, EndingProvider provider,
                    boolean nativeDemoLoop, boolean frozenAtEntry) {
        // Level_MainLoop (sonic.asm:2998-3006) increments the level clock before
        // ExecuteObjects. The unowned legacy caller keeps its original order.
        if (nativeDemoLoop && !frozenAtEntry) host.level().advanceLevelFrameCounter();
        host.sprites().publishHeldInputForLevelEvents(host.input());
        // Level_FDLoop (sonic.asm:3073-3090) runs objects and placement while
        // omitting water features, events and the level-loop oscillator.
        if (!nativeDemoLoop || !frozenAtEntry) {
            host.level().updateZoneFeaturesPrePhysics();
            if (host.level().advanceWaterLevelBeforePlayerPhysics())
                host.level().advanceDynamicWaterLevel();
        }
        host.sprites().update(host.input());
        if (nativeDemoLoop)
            host.level().updateObjectPositionsPostPhysicsWithoutTouches(null, !frozenAtEntry);
        else host.level().updateObjectPositionsPostPhysicsWithoutTouches();
        boolean scrollFrozen = nativeDemoLoop ? frozenAtEntry : provider.isScrollFrozen();
        if (!scrollFrozen) {
            host.camera().updatePosition();
            host.camera().captureRenderCopy();
        }
        var events = host.module().getLevelEventProvider();
        if ((!nativeDemoLoop || !scrollFrozen) && events != null) events.update();
        if (!scrollFrozen) {
            host.camera().updateBoundaryEasing();
            if (events != null) events.updateAfterCameraBoundaryEasing();
            host.level().postCameraObjectPlacementSync();
            if (!nativeDemoLoop) host.level().advanceLevelFrameCounter();
            host.level().update();
            host.level().refreshObjectPostCameraRenderState();
        }
    }
}
