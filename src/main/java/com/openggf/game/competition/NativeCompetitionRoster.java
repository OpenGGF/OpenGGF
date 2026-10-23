package com.openggf.game.competition;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Set;

/**
 * Session-owned native human slots. CPU followers never acquire this authority.
 * Slot zero is controller one; slot one is controller two. Identities survive
 * teleport, which exchanges native actor state rather than the player records.
 */
public interface NativeCompetitionRoster {
    AbstractPlayableSprite player(int slot);
    LevelState levelState(int slot);
    GameStateManager gameState(int slot);
    Camera camera(int slot);

    /**
     * Recreates missing captured native humans before canonical per-sprite restore.
     * The domain supplies constructors and ordinary production wiring; this hook
     * receives identities only, never captured player values. Ordinary rosters
     * and modes without native recreation keep the default no-op.
     */
    default void prepareRestore(GameplayModeContext context, Set<String> capturedCodes) { }

    default int slotOf(AbstractPlayableSprite sprite) {
        if (sprite == null) return -1;
        if (sprite == player(0)) return 0;
        if (sprite == player(1)) return 1;
        return -1;
    }

    static NativeCompetitionRoster from(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, NativeCompetitionRoster.class);
    }
}
