package com.openggf;

import com.openggf.game.TitleCardProvider;
import com.openggf.game.internal.LockedTitleCardLevelStepGate;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

/** Owns the gameplay-word mirrors at title release, independently of V-int. */
final class TitleCardLevelClock {
    private TitleCardLevelClock() { }

    /**
     * Level (s2.asm:4772-4773) clears the word before the locked title passes;
     * Level_MainLoop (s2.asm:5088-5095) first increments it before the object pass.
     * S2/KiS2 locked passes use the shared level step, so discard that mirror's
     * title debt at release along with the sprite mirror's existing reset.
     * ObjC8 reads its high byte in loc_3D3A4 (s2.asm:82596-82616); ARZ's word
     * read drives the shake and the next BuildSprites solid-visibility gate.
     */
    static void resetAtRelease(TitleCardProvider titleCard,
            SpriteManager spriteManager, LevelManager levelManager) {
        spriteManager.setFrameCounter(0);
        if (titleCard instanceof LockedTitleCardLevelStepGate) {
            levelManager.setFrameCounter(0);
        }
    }
}
