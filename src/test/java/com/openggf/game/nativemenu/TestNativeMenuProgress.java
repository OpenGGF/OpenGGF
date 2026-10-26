package com.openggf.game.nativemenu;

import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeMenuProgress {
    @Test
    void optionsLaunchRetainsEmeraldsAndCursorWithoutRevivingOldGameplayState() {
        var previous = new GameStateManager();
        previous.markEmeraldCollected(1);
        previous.markEmeraldCollected(4);
        previous.consumeCurrentSpecialStageIndexAndAdvance();
        previous.consumeCurrentSpecialStageIndexAndAdvance();
        previous.addScore(16000);
        previous.addLife();
        previous.setCurrentBossId(9);
        previous.setEndOfLevelActive(true);
        var fresh = new GameStateManager();

        NativeMenuProgress.retainSpecialStageProgress(fresh, previous.capture());

        assertEquals(2, fresh.getCurrentSpecialStageIndex());
        assertEquals(2, fresh.getEmeraldCount());
        assertTrue(fresh.hasEmerald(1));
        assertTrue(fresh.hasEmerald(4));
        assertFalse(fresh.hasEmerald(0));
        assertEquals(0, fresh.getScore());
        assertEquals(3, fresh.getLives());
        assertEquals(0, fresh.getCurrentBossId());
        assertFalse(fresh.isEndOfLevelActive());
        previous.markEmeraldCollected(0);
        assertFalse(fresh.hasEmerald(0), "a new session owns its own inventory arrays");
    }
}
