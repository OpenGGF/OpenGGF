package com.openggf;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.managers.SpriteManager;

import java.util.Objects;

public record LevelFrameContext(GameModule gameModule,
                                PhysicsFeatureSet frameOrderFeatureSet,
                                LevelEventProvider levelEventProvider,
                                BonusStageProvider bonusStageProvider,
                                SpriteManager spriteManager,
                                GameStateManager gameStateManager) {

    public LevelFrameContext {
        Objects.requireNonNull(gameModule, "gameModule");
        bonusStageProvider = bonusStageProvider != null
                ? bonusStageProvider
                : NoOpBonusStageProvider.INSTANCE;
    }

    public static LevelFrameContext from(GameplayModeContext context) {
        Objects.requireNonNull(context, "context");
        GameModule module = context.getWorldSession().getGameModule();
        PhysicsProvider physicsProvider = module.getPhysicsProvider();
        PhysicsFeatureSet featureSet = physicsProvider != null ? physicsProvider.getFeatureSet() : null;
        return new LevelFrameContext(
                module,
                featureSet,
                module.getLevelEventProvider(),
                context.getActiveBonusStageProvider(),
                context.getSpriteManager(),
                context.getGameStateManager());
    }
}
