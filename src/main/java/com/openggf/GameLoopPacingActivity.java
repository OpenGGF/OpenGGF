package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.level.LevelManager;

/** Native stage control and presentation owners qualify interactive pacing; host exclusions stay in GameLoop. */
final class GameLoopPacingActivity {
    private GameLoopPacingActivity() { }
    static boolean allows(GameMode mode, SpecialStageProvider special, BonusStageProvider bonus,
                          TitleCardProvider title, Camera camera, LevelManager level, boolean specialRewindBoundary) {
        if (mode == GameMode.SPECIAL_STAGE) {
            var owner = NativeStagePacingOwners.special(special);
            return !specialRewindBoundary && owner != null && owner.pacingState().interactive();
        }
        boolean alive = camera == null || camera.getFocusedSprite() == null || !camera.getFocusedSprite().getDead();
        if (mode == GameMode.BONUS_STAGE) return alive && NativeStagePacingOwners.bonus(bonus) != null
                && (level == null || !level.hasPendingInitialProcessSpritesPass())
                && bonus.getActiveType() != BonusStageType.NONE && !bonus.isStageComplete()
                && (title == null || !title.isOverlayActive());
        return mode == GameMode.LEVEL && alive;
    }
}
