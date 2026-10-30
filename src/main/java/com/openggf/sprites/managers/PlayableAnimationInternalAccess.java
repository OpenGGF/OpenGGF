package com.openggf.sprites.managers;

/** Engine-only private ghost continuity, outside the creator-facing animation API. */
public final class PlayableAnimationInternalAccess {
    private PlayableAnimationInternalAccess() { }

    /** Private animation continuity, including transient dispatch state for exception rollback. */
    public record PresentationState(int lastAnimationId, int lastGroundMovementAnimId,
            int groundMovementAnimSpeedSnapshot, boolean groundMovementAnimationSuppressed,
            boolean nextUpdateSuppressed, boolean animationUpdateSuppressedThisFrame,
            boolean bootstrapDynamicArtPrime, boolean mappingPublishedThisUpdate,
            int levelFrameCounterThisTick) { }

    public static PresentationState captureGhostPresentation(PlayableSpriteAnimation animation) {
        return animation.capturePresentationState();
    }

    public static void restoreGhostPresentation(PlayableSpriteAnimation animation, PresentationState state) {
        animation.restorePresentationState(state);
    }
}
