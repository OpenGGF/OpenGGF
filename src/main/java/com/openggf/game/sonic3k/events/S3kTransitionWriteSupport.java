package com.openggf.game.sonic3k.events;

import com.openggf.game.LevelEventProvider;
import com.openggf.level.objects.ObjectServices;

public final class S3kTransitionWriteSupport {
    private S3kTransitionWriteSupport() {
    }

    public static int resultsCreateGateDispatches(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            return bridge.resultsCreateGateDispatches();
        }
        return 9;
    }

    public static void signalActTransition(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            // FBZ's results owner publishes the transition byte several frames
            // before FBZ1BGE_Normal consumes it and synchronously reloads Act 2.
            // Seal rewind history immediately before that publication so no
            // keyframe can seek from a pre-results state into the request window.
            if (services.romZoneId() == 0x04
                    && services.currentAct() == 0
                    && services.levelManager() != null) {
                services.levelManager().markSynchronousSeamlessTransitionBoundary();
            }
            bridge.signalActTransition();
        }
    }

    public static void requestHczPostTransitionCutscene(LevelEventProvider provider) {
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.requestHczPostTransitionCutscene();
        }
    }

    /**
     * Completes any event-owned post-results handoff and reports whether that
     * retained native owner still owns publication of the transition-ready
     * flag. The event provider, rather than the results object, decides this
     * from its live transition state.
     */
    public static boolean completePostResultsHandoff(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            return bridge.restorePendingPostResultsPlayerControl();
        }
        return false;
    }

    public static void preparePreloadedActTitleCardCompletion(LevelEventProvider provider) {
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.preparePreloadedActTitleCardCompletion();
        }
    }

    public static void prepareLbzBigArmFloorTransition(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.prepareLbzBigArmFloorTransition();
        }
    }

    public static void loadLbzBigArmPostGatePlc(ObjectServices services) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.loadLbzBigArmPostGatePlc();
        }
    }

    public static void startLbzBigArmTimedShake(ObjectServices services, int frames) {
        Object provider = services.levelEventProvider();
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.startLbzBigArmTimedShake(frames);
        }
    }

    public static void requestMgzPostTransitionRelease(LevelEventProvider provider) {
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.requestMgzPostTransitionRelease();
        }
    }

    public static void requestCnzPostTransitionRelease(LevelEventProvider provider, int framesUntilRelease) {
        if (provider instanceof S3kTransitionEventBridge bridge) {
            bridge.requestCnzPostTransitionRelease(framesUntilRelease);
        }
    }
}
