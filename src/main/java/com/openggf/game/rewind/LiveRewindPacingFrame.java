package com.openggf.game.rewind;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.level.Level;

/** Internal running-owner history, separate from held-only BK2 and comparison/trace rows. */
record LiveRewindPacingFrame(GameplayModeContext context, WorldSession world, Level level,
                             SpecialStageProvider special, long specialEpoch,
                             BonusStageProvider bonus, long bonusEpoch,
                             GameplayMutatorPacing pacing, PlayerInputState player1, PlayerInputState player2,
                             GameplayMutatorPacing.Snapshot afterTick) {
    static LiveRewindPacingFrame capture(GameMode mode, GameplayModeContext context,
                                         SpecialStageProvider special, InputHandler input) {
        if (context == null || !context.isGameplayRuntimeReady()) return null;
        var world = context.getWorldSession();
        var pacing = WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
        if (pacing == null || world.getGameModule().gameplayFrameController() != null) return null;
        var specialOwner = NativeStagePacingOwners.special(special);
        var bonus = mode == GameMode.BONUS_STAGE ? context.getActiveBonusStageProvider() : null;
        var bonusOwner = NativeStagePacingOwners.bonus(bonus);
        if (mode == GameMode.SPECIAL_STAGE && specialOwner == null
                || mode == GameMode.BONUS_STAGE && bonusOwner == null
                || mode != GameMode.LEVEL && mode != GameMode.SPECIAL_STAGE && mode != GameMode.BONUS_STAGE)
            return null;
        return new LiveRewindPacingFrame(context, world, context.getLevelManager().getCurrentLevel(),
                special, specialOwner == null ? 0 : specialOwner.pacingState().entryEpoch(),
                bonus, bonusOwner == null ? 0 : bonusOwner.pacingEntryEpoch(), pacing,
                input.logical().player1(), input.logical().player2(), pacing.capture());
    }

    LogicalInputSnapshot admitted(LogicalInputSnapshot recorded, GameplayModeContext current,
                                  SpecialStageProvider provider) {
        if (!matches(current, provider)) return recorded;
        return new LogicalInputSnapshot(player1, player2, recorded.menuUp(), recorded.menuDown(),
                recorded.menuLeft(), recorded.menuRight(), recorded.menuAccept(), recorded.menuBack(),
                recorded.menuStart(), recorded.anyActionPressed(), recorded.debugModeTogglePressed(),
                recorded.debugShiftDown(), recorded.debugControlDown(), recorded.debugAltDown(), recorded.debugSuperDown());
    }

    void restoreAfterTick(GameplayModeContext current, SpecialStageProvider provider) {
        if (matches(current, provider)) pacing.restore(afterTick);
    }

    private boolean matches(GameplayModeContext current, SpecialStageProvider provider) {
        if (current != context || !current.isGameplayRuntimeReady() || current.getWorldSession() != world
                || current.getLevelManager().getCurrentLevel() != level || provider != special
                || WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class) != pacing) return false;
        var specialOwner = NativeStagePacingOwners.special(provider);
        if (special != null && (specialOwner == null || specialOwner.pacingState().entryEpoch() != specialEpoch))
            return false;
        if (bonus == null) return true;
        var bonusOwner = NativeStagePacingOwners.bonus(bonus);
        return current.getActiveBonusStageProvider() == bonus && bonusOwner != null
                && bonusOwner.pacingEntryEpoch() == bonusEpoch;
    }
}
