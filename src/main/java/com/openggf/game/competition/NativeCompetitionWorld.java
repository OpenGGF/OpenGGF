package com.openggf.game.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.control.InputHandler;
import com.openggf.game.GameModule;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Internal composition owner for one native competition world. A supplied roster
 * replaces campaign/CPU team bootstrap; a native mode still owns its load profile,
 * player routines, views and transitions. Opening this owner advertises no arena.
 */
public final class NativeCompetitionWorld {
    private final GameplayModeContext context;
    private final NativeCompetitionRoster roster;

    private NativeCompetitionWorld(GameplayModeContext context, NativeCompetitionRoster roster) {
        this.context = context;
        this.roster = roster;
    }

    public static NativeCompetitionWorld open(GameModule rootModule, GameModule runtimeModule,
            NativeCompetitionRoster roster, WorldSessionPolicyState policy, EngineContext services,
            Consumer<GameplayModeContext> publishMode) throws IOException {
        Objects.requireNonNull(rootModule, "rootModule");
        Objects.requireNonNull(runtimeModule, "runtimeModule");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(publishMode, "publishMode");
        requireHumanRoster(roster);
        if (policy.getService(NativeCompetitionRoster.class) != roster) {
            throw new IllegalArgumentException("Native world policy must publish its supplied roster");
        }
        // Pin bytes and validate participants before retiring the outgoing world.
        var source = StockGameDataSources.pinned(services.roms().getRom(), rootModule);
        var basePolicies = runtimeModule.getGameService(WorldSessionPolicyProvider.class);
        WorldSessionPolicyProvider policies = WorldSessionPolicyProvider.compose(basePolicies, world -> policy);
        var module = new DelegatingGameModule(runtimeModule, "engine:native-competition") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class ? type.cast(policies) : super.getGameService(type);
            }
        };
        var context = SessionManager.openGameplaySession(rootModule, module, source, null);
        GameplaySessionFactory.attachManagers(context, services);
        var result = new NativeCompetitionWorld(context, roster);
        // Both logic and the outer renderer must stop referencing retired managers.
        publishMode.accept(context);
        for (int slot = 0; slot < 2; slot++) {
            var actor = roster.player(slot);
            if (actor != null) roster.camera(slot).setFocusedSprite(actor);
        }
        return result;
    }

    public GameplayModeContext context() { return context; }
    public NativeCompetitionRoster roster() { return roster; }

    /** Loads through the pinned runtime module's actual ROM asset pipeline. */
    public void load(int zone, int act) throws IOException {
        requireCurrent();
        context.getLevelManager().loadZoneAndAct(zone, act);
    }

    /**
     * Runs one world, never one update per view. The outer GameLoop owns input
     * advancement, general timers and the active PLC token. Miniature modes supply
     * their native player pass instead of invoking ordinary playable movement.
     */
    public LevelFrameResult step(InputHandler input, PlcLifecycleFrame frame, Runnable nativePlayerPass) {
        requireCurrent();
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(nativePlayerPass, "nativePlayerPass");
        context.getSpriteManager().publishHeldInputForLevelEvents(input);
        return LevelFrameStep.execute(LevelFrameContext.from(context), frame,
                PlcLifecyclePhase.ORDINARY_LEVEL, context.getLevelManager(), context.getCamera(),
                nativePlayerPass, LevelFrameStep.DIRECT_WRAPPER);
    }

    /** S2's full-size native player objects use the canonical playable dispatcher. */
    public LevelFrameResult step(InputHandler input, PlcLifecycleFrame frame) {
        return step(input, frame, () -> context.getSpriteManager().update(input));
    }

    private void requireCurrent() {
        if (SessionManager.getCurrentGameplayMode() != context) {
            throw new IllegalStateException("Native competition world has been retired");
        }
    }

    private static void requireHumanRoster(NativeCompetitionRoster roster) {
        if (roster.player(0) == null) throw new IllegalArgumentException("Native world requires controller one");
        if (roster.player(0) == roster.player(1)) throw new IllegalArgumentException("Native slots share an actor");
        for (int slot = 0; slot < 2; slot++) {
            var actor = roster.player(slot);
            if (actor == null) continue;
            if (actor.isCpuControlled()) throw new IllegalArgumentException("Native competition requires human actors");
            Objects.requireNonNull(roster.camera(slot), "native camera");
            Objects.requireNonNull(roster.gameState(slot), "native game state");
            Objects.requireNonNull(roster.levelState(slot), "native level state");
        }
    }
}
