package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;

/**
 * CNZ-local wrapper for the standard upright egg capsule.
 *
 * <p>ROM anchor: {@code Obj_EggCapsule} as used by {@code Obj_CNZEndBoss}.
 *
 * <p>CNZ must not reuse {@link Aiz2EndEggCapsuleInstance} because that class
 * models the camera-relative floating route-8 capsule from AIZ2. It also
 * should not inherit the HCZ-specific geyser follow-up from
 * {@code HczEndBossEggCapsuleInstance}. CNZ instead reuses the shared upright
 * button/open/results behavior and reports completion back to the CNZ boss
 * sequence, which owns the later cannon launch into ICZ1.
 */
public final class CnzEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance {
    private final Runnable resultsCompleteCallback;
    private boolean resultsCompleteNotified;

    public CnzEggCapsuleInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    public CnzEggCapsuleInstance(ObjectSpawn spawn, Runnable resultsCompleteCallback) {
        super(spawn, "CNZEggCapsule");
        this.resultsCompleteCallback = resultsCompleteCallback;
    }

    @Override
    protected void updateAfterResultsStarted(int frameCounter, com.openggf.game.PlayableEntity player) {
        if (services().gameState() != null && services().gameState().isEndOfLevelFlag()) {
            notifyResultsComplete();
        }
    }

    public void forceResultsCompleteForTest() {
        notifyResultsComplete();
    }

    private void notifyResultsComplete() {
        if (resultsCompleteNotified) {
            return;
        }
        resultsCompleteNotified = true;
        if (services().gameState() != null) {
            services().gameState().setEndOfLevelFlag(false);
            services().gameState().setEndOfLevelActive(false);
        }
        if (resultsCompleteCallback != null) {
            resultsCompleteCallback.run();
        }
    }
}
