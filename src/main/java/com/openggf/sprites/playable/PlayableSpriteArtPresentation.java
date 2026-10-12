package com.openggf.sprites.playable;

/** Playable art submission and native display eligibility, without owning sprite state or resources. */
final class PlayableSpriteArtPresentation {
    private PlayableSpriteArtPresentation() { }

    /** Keep the playable's virtual pose getters and renderer lifetime at their existing draw boundary. */
    static void drawStandardArt(AbstractPlayableSprite sprite) {
        if (!sprite.isHidden() && sprite.getSpriteRenderer() != null) {
            sprite.getSpriteRenderer().drawFrame(
                    sprite.getMappingFrame(), sprite.getRenderCentreX(), sprite.getRenderCentreY(),
                    sprite.getRenderHFlip(), sprite.getRenderVFlip());
        }
    }

    /** HurtStop owns direct display; the native display routine blinks on its pre-decrement bit 0x04. */
    static boolean nativeBodyVisible(int invulnerableFrames, boolean displayTimerDecremented,
                                     boolean hurt, boolean hurtRoutineOwnedDisplay) {
        int displayTimer = invulnerableFrames + (displayTimerDecremented ? 1 : 0);
        return hurt
                || hurtRoutineOwnedDisplay
                || displayTimer <= 0
                || (displayTimer & 0x04) != 0;
    }
}
