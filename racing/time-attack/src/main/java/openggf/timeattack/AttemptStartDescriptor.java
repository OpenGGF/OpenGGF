package openggf.timeattack;

import com.openggf.game.arena.MonitorRule;
import com.openggf.mods.run.TrackKind;
import com.openggf.game.session.GameplayRunPolicy;

/** Canonical immutable start state; all rules participate in the recording hash. */
public record AttemptStartDescriptor(String gameId, int zone, int act, String character,
        String fingerprint, TrackKind trackKind, CompetitionRoomSettings.Mode mode,
        MonitorRule monitors, GameplayRunPolicy.DeathRestart deathRestart, boolean swapEnabled) {
    public AttemptStartDescriptor(String gameId, int zone, int act, String character, String fingerprint) {
        this(gameId, zone, act, character, fingerprint, TrackKind.ACT,
                CompetitionRoomSettings.Mode.TIME_ATTACK, MonitorRule.NORMAL,
                GameplayRunPolicy.DeathRestart.FAST_FROM_START, false);
    }
}
