package com.openggf.game.internal;

/**
 * A level-select program whose exit can return to the title screen instead of starting a stage or a
 * special stage: Sonic CD's stage select, whose rows that name another program run it and return to
 * IPX's {@code .GameLoop} ({@code StageSelect}, CD Initial Program/IPX.asm:710-728).
 */
public interface TitleReturningLevelSelect {
    /** True once the exiting selection returns to the title. */
    boolean returnsToTitle();

    /** When {@code levelSelect} returns to the title, resets it and answers true. */
    static boolean exitToTitle(com.openggf.game.LevelSelectProvider levelSelect) {
        if (!(levelSelect instanceof TitleReturningLevelSelect title) || !title.returnsToTitle()) return false;
        levelSelect.reset();
        return true;
    }
}
