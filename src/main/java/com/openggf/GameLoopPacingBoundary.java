package com.openggf;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.level.Level;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Identity-only pump boundary: no native gameplay state is used to schedule individual ticks. */
record GameLoopPacingBoundary(GameMode mode, GameplayModeContext context, WorldSession world, Level level,
                              SpecialStageProvider special, long specialEntryEpoch,
                              BonusStageProvider bonus, Object bonusEntryState, long bonusEntryEpoch) {
    static GameLoopPacingBoundary capture(GameLoop loop, GameplayModeContext context, Level level) {
        var mode = loop.getCurrentGameMode();
        var special = mode == GameMode.SPECIAL_STAGE ? loop.getActiveSpecialStageProvider() : null;
        var bonus = mode == GameMode.BONUS_STAGE && context != null ? context.getActiveBonusStageProvider() : null;
        var specialOwner = NativeStagePacingOwners.special(special);
        var bonusOwner = NativeStagePacingOwners.bonus(bonus);
        return new GameLoopPacingBoundary(mode, context, context == null ? null : context.getWorldSession(), level,
                special, specialOwner == null ? 0 : specialOwner.pacingState().entryEpoch(),
                bonus, bonus == null ? null : bonus.getSavedState(),
                bonusOwner == null ? 0 : bonusOwner.pacingEntryEpoch());
    }
    boolean same(GameLoop loop, Supplier<Level> currentLevel, BooleanSupplier eligible) {
        if (!eligible.getAsBoolean() || loop.getCurrentGameMode() != mode
                || loop.resolveGameplayModeContext() != context || currentLevel.get() != level
                || context != null && (!context.isGameplayRuntimeReady() || context.getWorldSession() != world)) return false;
        var specialOwner = NativeStagePacingOwners.special(special);
        var bonusOwner = NativeStagePacingOwners.bonus(bonus);
        if (special != null && (loop.getActiveSpecialStageProvider() != special
                || specialOwner == null
                || specialOwner.pacingState().entryEpoch() != specialEntryEpoch)) return false;
        return bonus == null || context.getActiveBonusStageProvider() == bonus && bonus.getSavedState() == bonusEntryState
                && bonusOwner != null && bonusOwner.pacingEntryEpoch() == bonusEntryEpoch;
    }
}
