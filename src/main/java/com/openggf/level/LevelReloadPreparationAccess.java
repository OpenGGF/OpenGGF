package com.openggf.level;

/**
 * Internal host-loop entry for decoding a committed reload during its fade
 * (engine only; not part of the compiled-mod API).
 */
public final class LevelReloadPreparationAccess {
    private LevelReloadPreparationAccess() { }

    /**
     * The game has committed to reloading the current act (a death respawn's fade to
     * black has started): decode it on the preparer thread while the screen fades.
     * See {@link LevelManager#prepareCurrentLevelReload()}.
     */
    public static boolean prepareCurrentLevelReload(LevelManager level) {
        return level != null && level.prepareCurrentLevelReload();
    }
}
