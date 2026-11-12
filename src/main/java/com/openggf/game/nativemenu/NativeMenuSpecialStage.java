package com.openggf.game.nativemenu;

/**
 * A special-stage provider whose current stage was launched by the open native
 * screen. Such a stage has no level to return to: when it ends, the launching
 * screen resumes in its own mode and decides what follows.
 */
public interface NativeMenuSpecialStage {
    /** True while the active stage was launched by a native screen. */
    boolean returnsToNativeMenu();

    /**
     * Reports the finished stage to the screen that launched it and forgets the
     * route. Called once, after the stage's exit fade, before the mode changes.
     *
     * @param cleared the stage ended on its goal rather than a failure
     * @return the mode of the screen that resumes
     */
    com.openggf.game.GameMode finishNativeMenuStage(boolean cleared);
}
