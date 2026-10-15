package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameplayObservation;

/** Native coordinator authority, including bonus stages that share the ordinary level executor. */
public final class NativeGameplayPhase {
    private NativeGameplayPhase() { }
    public static GameplayObservation.Phase of(LevelFrameContext context) {
        if(context.bonusStageProvider()!=null && context.bonusStageProvider().getActiveType()!=BonusStageType.NONE)
            return GameplayObservation.Phase.STAGE;
        var leader=context.spriteManager()==null?null:context.spriteManager().getMainPlayable();
        return leader!=null && leader.getDead() || context.levelManager()!=null && context.levelManager().isLevelInactiveForTransition()
                ? GameplayObservation.Phase.TRANSITION:GameplayObservation.Phase.ACTIVE;
    }
}
