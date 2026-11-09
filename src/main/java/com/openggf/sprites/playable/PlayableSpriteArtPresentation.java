package com.openggf.sprites.playable;

/** Playable art submission and native display eligibility, without owning sprite state or resources. */
final class PlayableSpriteArtPresentation {
    private PlayableSpriteArtPresentation() { }

    /** Keep the playable's virtual pose getters and renderer lifetime at their existing draw boundary. */
    static void drawStandardArt(AbstractPlayableSprite sprite) {
        // Dust is a separate ROM object, independent of the player's sheet:
        // InitPlayers (s2.asm:5177-5182), loc_6AB8 (sonic3k.asm:8384-8392).
        if (sprite.isHidden()) return;
        if (sprite.getSpindashDustController() != null) sprite.getSpindashDustController().draw();
        if (sprite.getSpriteRenderer() != null) {
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
