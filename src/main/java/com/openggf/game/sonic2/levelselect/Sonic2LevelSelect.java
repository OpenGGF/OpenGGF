package com.openggf.game.sonic2.levelselect;

import com.openggf.audio.GameSound;
import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.LevelDebugPlacement;

/** Level-select sound-test cheat and launch-time A latch. */
public class Sonic2LevelSelect extends LevelSelectManager {
    private static final int[] RETAIL_DEBUG_CODE = {1, 9, 9, 2, 1, 1, 2, 4};

    private final int[] debugCode;
    private int matchedDebugSounds;
    private boolean debugOptions;
    private boolean launchWithDebug;

    public Sonic2LevelSelect() {
        this(RETAIL_DEBUG_CODE);
    }

    protected Sonic2LevelSelect(int[] debugCode) {
        this.debugCode = debugCode.clone();
    }

    @Override
    public void initialize() {
        matchedDebugSounds = 0;
        debugOptions = false;
        launchWithDebug = false;
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
            debugOptions = true;
            GameServices.audio().playSfx(GameSound.RING);
        }
    }

    @Override
    protected void onLevelSelected(PlayerInputState input) {
        // LoadLevel_VDP (s2.asm:4839-4847) requires held A after the sound code.
        launchWithDebug = debugOptions && (input.actionHeldMask() & InputActionMasks.ACTION_A) != 0;
    }

    @Override
    public void onGameplayStart() {
        if (launchWithDebug) LevelDebugPlacement.of(GameServices.gameState()).enableCheat();
        launchWithDebug = false;
    }
}
