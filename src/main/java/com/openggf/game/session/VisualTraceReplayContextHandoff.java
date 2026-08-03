package com.openggf.game.session;

import com.openggf.game.GameModule;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;

import java.util.Objects;
import java.util.function.Consumer;

/** Replaces disposable visual presentation state with a clean replay context. */
public final class VisualTraceReplayContextHandoff {
    private VisualTraceReplayContextHandoff() {
    }

    public static GameplayModeContext reopen(
            HardwareReadinessAdmissionPolicy admissionPolicy,
            TitleCardProvider presentationTitleCard,
            Runnable resetModuleScopedProviders,
            Consumer<GameplayModeContext> bindGameplayMode) {
        Objects.requireNonNull(admissionPolicy, "admissionPolicy");
        Objects.requireNonNull(resetModuleScopedProviders,
                "resetModuleScopedProviders");
        Objects.requireNonNull(bindGameplayMode, "bindGameplayMode");
        GameModule module = SessionManager.requireCurrentGameModule();

        if (presentationTitleCard != null) {
            presentationTitleCard.reset();
        }
        for (var adapter : module.rewindAdapters()) {
            adapter.resetForMissingSnapshot();
        }

        resetModuleScopedProviders.run();
        GameplayModeContext replay =
                SessionManager.reopenGameplaySession(admissionPolicy);
        GameplaySessionFactory.attachManagers(replay, EngineServices.current());
        replay.getGameStateManager().configureSpecialStageProgress(
                module.getSpecialStageCycleCount(),
                module.getChaosEmeraldCount());
        bindGameplayMode.accept(replay);
        return replay;
    }
}
