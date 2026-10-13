package com.openggf.sprites.playable;

/** Native HurtStop writes shared by terrain and explicit object landing recovery. */
final class PlayableHurtRecovery {
    private PlayableHurtRecovery() { }

    static void clearObjectControlAfterLanding(AbstractPlayableSprite sprite) {
        // Sonic_HurtStop/Tails_HurtStop clear obj_control in S2 (s2.asm:38223,41110)
        // and S3K (sonic3k.asm:24541,29286). S1 HurtStop preserves its control byte.
        var rules = sprite.getGameRules();
        if (rules != null && rules.playerMovement() != null
                && rules.playerMovement().landing().hurtLandingClearsObjectControl()) {
            ObjectControlState.none().applyTo(sprite);
        }
    }

    static void stop(AbstractPlayableSprite sprite) {
        clearObjectControlAfterLanding(sprite);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setSpindash(false);
    }
}
