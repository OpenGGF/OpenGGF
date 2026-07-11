package com.openggf.game.rules;

@com.openggf.game.ModApi
public record DrowningBubbleRules(
        int initialDrowningCountdownFrameTimer,
        int mouthBubbleTimerBias,
        boolean breathingBubbleDefersFirstObjectPass,
        int mouthBubbleRiseVelocity) {
}
