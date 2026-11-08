package com.openggf.game.internal;

/** Semantic scene form operations, independent of playable/controller implementation. */
public interface EndingFormController {
    void advanceEndingPaletteAtAnPal();
    void initializeEndingSuperSonic();
    void initializeEndingHyperSonic();
    void initializeEndingSuperTails();
    void holdEndingTailsCompanionPaletteTimer();
    void holdEndingRingDrain();
    void beginEndingRescueRevert();
    void completeEndingRescueRevertMappings();
    void startEndingReunionTransformation();
    void clearEndingPaletteStatus();
}
