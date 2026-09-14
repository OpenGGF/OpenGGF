package com.openggf.game.sonic2.kis2;

import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestKis2LevelSelect {
    @Test void soundSequenceSurvivesMenuResetAndAppliesAfterNewGameReset() {
        var cheat = new Kis2LevelSelect.SuperCheat();
        var state = new GameStateManager();
        cheat.accept(1); cheat.accept(0);
        for (int sound : new int[]{1,6,7,7,7,2,1}) assertFalse(cheat.accept(sound));
        assertTrue(cheat.accept(6));
        assertEquals(0, state.getEmeraldCount(), "menu recognition does not mutate a previous gameplay session");
        cheat.resetSequence();
        state.startNewGameFromTitle();
        cheat.apply(state);
        assertEquals(7, state.getEmeraldCount());
        for (int i = 0; i < 7; i++) assertTrue(state.hasEmerald(i));
        cheat.apply(state);
        assertEquals(7, state.getEmeraldCount(), "repeated handoff cannot duplicate rewards");
    }
    @Test void stockCodeAndPartialCodeDoNotGrantEmeralds() {
        var cheat = new Kis2LevelSelect.SuperCheat();
        var state = new GameStateManager();
        for (int sound : new int[]{4,1,2,6}) assertFalse(cheat.accept(sound));
        cheat.accept(1); cheat.accept(6); cheat.resetSequence();
        for (int sound : new int[]{7,7,7,2,1,6}) assertFalse(cheat.accept(sound));
        cheat.apply(state);
        assertEquals(0, state.getEmeraldCount());
    }
}
