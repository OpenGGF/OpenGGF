package com.openggf.game.internal;

import com.openggf.control.PlayerInputState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Optional native level-placement rules supplied by the active module. */
public interface LevelDebugPlacementProvider {
    record Entry(int objectId, int subtype, Preview preview) {
        public ObjectSpawn at(int x, int y, int flipFlags) {
            return new ObjectSpawn(x, y, objectId, subtype, flipFlags, false, y);
        }
    }

    @FunctionalInterface
    interface Preview {
        void draw(LevelManager level, int x, int y, boolean flipX, boolean flipY);
    }

    boolean canEnter(LevelManager level, AbstractPlayableSprite player,
                     PlayerInputState input, boolean cheatEnabled);

    List<Entry> entries(LevelManager level);

    default boolean preventsTimeOver() { return false; }

    default void initializeCursor(LevelManager level, AbstractPlayableSprite player) { }

    default void resetPlayer(AbstractPlayableSprite player) { }
}
