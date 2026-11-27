package com.openggf.game.internal;

import com.openggf.camera.Camera;
import com.openggf.control.PlayerInputState;
import com.openggf.game.LevelDebugPlacement;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Optional native level-placement rules supplied by the active module. */
public interface LevelDebugPlacementProvider {
    record CursorMotion(int holdDelay, int entrySpeed, int idleSpeed,
                        int acceleration, int maxSpeed, int speedShift) { }

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

    CursorMotion cursorMotion();

    int cursorTop(Camera camera);

    int cursorBottom(Camera camera);

    default void onLevelLoad(LevelDebugPlacement placement, PlayerInputState input) { }

    default boolean preventsTimeOver() { return false; }

    default void initializeCursor(LevelManager level, AbstractPlayableSprite player) { }

    default void resetPlayer(AbstractPlayableSprite player) { }
}
