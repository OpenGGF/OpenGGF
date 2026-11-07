package openggf.timeattack;

import com.openggf.mods.run.MonitorRule;
import com.openggf.mods.run.TrackKind;

/** Frozen local launch settings, including the room's competition rules. */
public record TimeAttackLaunchRequest(String gameId, int zone, int act, String character,
        java.util.List<java.nio.file.Path> extraGhosts, TrackKind kind,
        CompetitionRoomSettings.Mode mode, MonitorRule monitors, boolean swapEnabled) {
    public TimeAttackLaunchRequest(String gameId, int zone, int act, String character,
            java.util.List<java.nio.file.Path> extraGhosts) {
        this(gameId, zone, act, character, extraGhosts, TrackKind.ACT,
                CompetitionRoomSettings.Mode.TIME_ATTACK, MonitorRule.NORMAL, false);
    }
    public TimeAttackLaunchRequest {
        extraGhosts = java.util.List.copyOf(extraGhosts);
        java.util.Objects.requireNonNull(kind);
        java.util.Objects.requireNonNull(mode);
        java.util.Objects.requireNonNull(monitors);
    }
    public boolean competition() { return mode == CompetitionRoomSettings.Mode.COMPETITION; }
}
