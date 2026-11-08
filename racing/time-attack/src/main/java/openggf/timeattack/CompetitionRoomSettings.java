package openggf.timeattack;

import com.openggf.mods.run.MonitorRule;
import com.openggf.mods.run.TrackKind;
import java.util.List;
import java.util.Objects;

/** Host-editable values; pool legality is checked before presenting or applying a choice. */
public record CompetitionRoomSettings(Mode mode, MonitorRule monitors, int cutoffSeconds,
                                      int startInSeconds) {
    public enum Mode { TIME_ATTACK, COMPETITION }

    public CompetitionRoomSettings {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(monitors);
        if (!cutoffChoices().contains(cutoffSeconds) || !startInChoices().contains(startInSeconds)) {
            throw new IllegalArgumentException("unsupported cutoff or start-in");
        }
        if (mode == Mode.TIME_ATTACK && monitors != MonitorRule.NORMAL) {
            throw new IllegalArgumentException("Time Attack requires normal monitors");
        }
    }

    public static CompetitionRoomSettings defaults() {
        return new CompetitionRoomSettings(Mode.TIME_ATTACK, MonitorRule.NORMAL, 60, 10);
    }

    /** Normal monitors on acts, Competition items on arena-only pools, common choice on mixed pools. */
    public static CompetitionRoomSettings defaultsFor(Mode mode, List<TimeAttackTrackCatalog.Track> pool) {
        List<MonitorRule> choices = legalMonitors(mode, pool);
        if (choices.isEmpty()) throw new IllegalArgumentException("no legal settings for pool");
        return new CompetitionRoomSettings(mode, choices.getFirst(), 60, 10);
    }

    public static List<Integer> cutoffChoices() { return List.of(30, 60, 90, 120); }
    public static List<Integer> startInChoices() { return List.of(5, 10, 15); }

    /** Empty for an empty/illegal pool. Mixed pools expose only their common choices. */
    public static List<MonitorRule> legalMonitors(Mode mode, List<TimeAttackTrackCatalog.Track> pool) {
        Objects.requireNonNull(mode);
        if (pool.isEmpty() || pool.stream().anyMatch(t -> !t.modes().contains(mode))) return List.of();
        return List.of(MonitorRule.values()).stream()
                .filter(rule -> pool.stream().allMatch(t -> permits(mode, t, rule))).toList();
    }

    public boolean legalFor(List<TimeAttackTrackCatalog.Track> pool) {
        return legalMonitors(mode, pool).contains(monitors);
    }

    private static boolean permits(Mode mode, TimeAttackTrackCatalog.Track track, MonitorRule rule) {
        if (mode == Mode.TIME_ATTACK) return rule == MonitorRule.NORMAL;
        if (track.kind() == TrackKind.ARENA) {
            return rule == MonitorRule.COMPETITION_ITEMS || rule == MonitorRule.OFF;
        }
        return rule == MonitorRule.NORMAL || rule == MonitorRule.OFF
                || (track.gameId().equals("s2") && track.finish() == TimeAttackTrackCatalog.Finish.SIGNPOST);
    }
}
