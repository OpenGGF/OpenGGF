package com.openggf.game.sonic2.levelselect;

import com.openggf.audio.GameSound;
import com.openggf.game.GameServices;
import com.openggf.game.LevelDebugPlacement;

/** Sonic 2 level-select sound-test cheat. */
public class Sonic2LevelSelect extends LevelSelectManager {
    private static final int[] RETAIL_DEBUG_CODE = {1, 9, 9, 2, 1, 1, 2, 4};

    private final int[] debugCode;
    private int matchedDebugSounds;

    public Sonic2LevelSelect() {
        this(RETAIL_DEBUG_CODE);
    }

    protected Sonic2LevelSelect(int[] debugCode) {
        this.debugCode = debugCode.clone();
    }

    @Override
    public void initialize() {
        matchedDebugSounds = 0;
        super.initialize();
    }

    @Override protected boolean usesControllerSoundTestButtons() { return true; }

    @Override
    protected void onSoundTestPlayed(int value) {
        // CheckCheats (s2.asm:12876-12891), debug_cheat (s2.asm:12933-12937).
        if (value != debugCode[matchedDebugSounds]) {
            matchedDebugSounds = 0;
        } else if (++matchedDebugSounds == debugCode.length) {
            matchedDebugSounds = 0;
            LevelDebugPlacement.of(GameServices.gameState()).unlockCheat();
            GameServices.audio().playSfx(GameSound.RING);
        }
    }
}
