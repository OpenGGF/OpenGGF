package com.openggf.game.sonic2.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A boss act ends at the egg prison button: loc_3F354 (s2.asm:85064-85078) stops the
 * clock there ({@code clr.b (Update_HUD_timer).w}) as Load_EndOfAct does for a signpost
 * act, so the button raises the game-agnostic act-completion signal that Time Attack
 * finishes on. Like the signpost it must not set the ROM Level_end_flag.
 */
class TestSonic2EggPrisonFinishSignal {
    @Test
    void pressingTheButtonRaisesActCompletionSignal() {
        GameStateManager gameState = new GameStateManager();
        EggPrisonObjectInstance prison = new EggPrisonObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x3E, 0, 0, false, 0), "EggPrison");
        prison.setServices(new StubObjectServices() {
            @Override
            public GameStateManager gameState() {
                return gameState;
            }
        });

        assertFalse(gameState.isActCompletionSignalActive());
        prison.onButtonTriggered();
        assertTrue(gameState.isActCompletionSignalActive(),
                "the prison button ends a boss act for act-completion observers");
        assertFalse(gameState.isEndOfLevelActive(),
                "S2 never sets the ROM Level_end_flag (physics reads it)");
    }
}
