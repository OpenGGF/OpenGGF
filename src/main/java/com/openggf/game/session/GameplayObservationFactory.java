package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation;
import com.openggf.game.LevelLoadCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;

/** Copies native values at the host boundary; observers receive no disposable runtime managers. */
public final class GameplayObservationFactory {
    private GameplayObservationFactory() { }
    public static GameplayObservation capture(WorldSession world,long generation,long visit,long clock,long ticks,
            GameplayModeContext context,LevelFrameContext nativeContext,LogicalInputSnapshot input,
            GameplayObservation.Phase phase,LevelLoadCause cause) {
        var sprites=nativeContext!=null?nativeContext.spriteManager():context==null?null:context.getSpriteManager();
        var camera=context==null?null:context.getCamera();
        var leader=sprites==null?null:sprites.getMainPlayable();
        var team=sprites==null?List.<GameplayObservation.Player>of():sprites.getAllSprites().stream()
                .filter(AbstractPlayableSprite.class::isInstance).map(AbstractPlayableSprite.class::cast)
                .map(GameplayObservationFactory::player).toList();
        var level=nativeContext==null?null:nativeContext.levelManager();
        int width=camera!=null?camera.getWidth():com.openggf.level.LevelManagerObservationAccess.width(level);
        int height=camera!=null?camera.getHeight():com.openggf.level.LevelManagerObservationAccess.height(level);
        return new GameplayObservation(generation,visit,clock,ticks,phase,world.getCurrentZone(),world.getCurrentAct(),
                width,height,Optional.ofNullable(leader).map(GameplayObservationFactory::player),team,input,cause);
    }
    private static GameplayObservation.Player player(AbstractPlayableSprite sprite) {
        return new GameplayObservation.Player(sprite.characterKey().persisted(),sprite.getCentreX(),sprite.getCentreY(),
                sprite.getXSpeed(),sprite.getYSpeed(),sprite.getGSpeed(),sprite.getRingCount(),sprite.getAir(),sprite.getDead(),sprite.isHurt(),
                sprite.isObjectControlled(),sprite.isControlLocked(),sprite.isInWater(),sprite.getShieldType().name());
    }
}
