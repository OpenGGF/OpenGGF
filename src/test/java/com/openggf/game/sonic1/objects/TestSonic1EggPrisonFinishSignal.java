package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A boss act ends at the prison capsule switch: Pri_Switch stops the clock there
 * ({@code clr.b (f_timecount).w}, 3E Prison Capsule.asm:103) as Sign_Touch does in a
 * signpost act, so the switch raises the game-agnostic act-completion signal that
 * Time Attack finishes on. Like the signpost it must not set the ROM Level_end_flag.
 */
class TestSonic1EggPrisonFinishSignal {
    @Test
    void pressingTheSwitchRaisesActCompletionSignal() {
        GameStateManager gameState = new GameStateManager();
        Sonic1EggPrisonObjectInstance prison = new Sonic1EggPrisonObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic1ObjectIds.EGG_PRISON, 0, 0, false, 0));
        prison.setServices(new StubObjectServices() {
            @Override
            public GameStateManager gameState() {
                return gameState;
            }
        });

        assertFalse(gameState.isActCompletionSignalActive());
        prison.onButtonTriggered();
        assertTrue(gameState.isActCompletionSignalActive(),
                "the prison switch ends a boss act for act-completion observers");
        assertFalse(gameState.isEndOfLevelActive(),
                "S1 never sets the ROM Level_end_flag (physics reads it)");
    }
}
