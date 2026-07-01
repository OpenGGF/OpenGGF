package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record DrowningBubbleRules(
        int initialDrowningCountdownFrameTimer,
        int mouthBubbleTimerBias,
        boolean breathingBubbleDefersFirstObjectPass,
        int mouthBubbleRiseVelocity) {

    public static DrowningBubbleRules fromLegacy(PhysicsFeatureSet fs) {
        return new DrowningBubbleRules(
                fs.initialDrowningCountdownFrameTimer(),
                fs.mouthBubbleTimerBias(),
                fs.breathingBubbleDefersFirstObjectPass(),
                fs.mouthBubbleRiseVelocity());
    }
}
