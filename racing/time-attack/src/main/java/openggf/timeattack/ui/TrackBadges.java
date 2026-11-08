package openggf.timeattack.ui;

import com.openggf.mods.run.TrackKind;
import openggf.timeattack.CompetitionRoomSettings;
import openggf.timeattack.TimeAttackTrackCatalog;

import java.util.List;

/**
 * The kind and mode tags a focused track row carries (the track picker and the pool editor): what
 * the track is ({@code ACT}, or an arena's {@code 5 LAPS}) and the modes it races in. They are shown
 * on the focused row only, because most rows of a list share them.
 */
public final class TrackBadges {
    private TrackBadges() { }

    public static List<String> of(TimeAttackTrackCatalog.Track track) {
        String kind = track.kind() == TrackKind.ARENA ? track.lapCount() + " LAPS" : "ACT";
        boolean competition = track.modes().contains(CompetitionRoomSettings.Mode.COMPETITION);
        boolean timeAttack = track.modes().contains(CompetitionRoomSettings.Mode.TIME_ATTACK);
        String modes = competition && timeAttack ? "ANY MODE" : competition ? "COMP ONLY" : "TA ONLY";
        return List.of(kind, modes);
    }
}
