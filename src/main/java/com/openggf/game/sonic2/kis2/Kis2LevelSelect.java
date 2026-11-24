package com.openggf.game.sonic2.kis2;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.levelselect.Sonic2LevelSelect;

/** Chip-program level select sound-test Super code and launch handoff. */
public final class Kis2LevelSelect extends Sonic2LevelSelect {
    public Kis2LevelSelect() {
        // debug_cheat (c336fede s2.asm), including October's zero digit.
        super(new int[] {1, 9, 9, 4, 1, 0, 1, 8});
    }
    private final SuperCheat cheat = new SuperCheat();

    @Override public void initialize() {
        cheat.resetSequence(); // MenuScreen_LevelSelect clears Correct_cheat_entries_2.
        super.initialize();
    }
    @Override protected void onSoundTestPlayed(int value) {
        super.onSoundTestPlayed(value);
        if (cheat.accept(value)) GameServices.audio().playMusic(Sonic2Music.GOT_EMERALD.id);
    }
    @Override public void onGameplayStart() {
        cheat.apply(GameServices.gameState());
    }

    /** CheckCheats resets progress on mismatch; a $FF terminator permits sound zero. */
    static final class SuperCheat {
        private static final int[] CODE = {1,6,7,7,7,2,1,6};
        private int matched;
        private boolean unlocked;
        void resetSequence() { matched = 0; }
        boolean accept(int sound) {
            if (sound != CODE[matched]) { matched = 0; return false; }
            if (++matched < CODE.length) return false;
            matched = 0;
            unlocked = true;
            return true;
        }
        void apply(GameStateManager state) {
            if (unlocked) for (int i = 0; i < 7; i++) state.markEmeraldCollected(i);
        }
    }
}
