package com.openggf.level;

/**
 * Rewindable result context for an HPZ super-emerald special-stage return.
 *
 * @param stageIndex exact pedestal/special-stage index
 * @param succeeded whether the special stage awarded that super emerald
 */
@com.openggf.game.ModApi
public record SanctuaryReturnContext(int stageIndex, boolean succeeded) {
    public SanctuaryReturnContext {
        if (stageIndex < 0 || stageIndex >= 7) {
            throw new IllegalArgumentException("stageIndex");
        }
    }
}
