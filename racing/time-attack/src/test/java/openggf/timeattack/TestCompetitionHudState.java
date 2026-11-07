package openggf.timeattack;

import com.openggf.mods.run.TrackKind;
import openggf.timeattack.mp.MultiplayerHudState;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestCompetitionHudState {
    @Test void legacyHudConstructorsKeepNeutralDefaults() {
        assertEquals(CompetitionHudState.inactive(), TimeAttackHudState.inactive().competition());
        var mp = new MultiplayerHudState(false, "IDLE", 0, 0, List.of(), List.of(), false, "",
                0, List.of(), "FREE", List.of(), List.of(), 0, "", List.of(), 0, Map.of());
        assertEquals(CompetitionHudState.inactive(), mp.competition());
        assertEquals(CompetitionRoomSettings.Mode.TIME_ATTACK, mp.competition().mode());
        assertFalse(mp.competition().cutoffArmed());
        assertEquals(CompetitionHudState.MassStartPhase.RELEASED, mp.competition().massStartPhase());
    }

    @Test void raceSnapshotRetainsLapsResultsAndFeedbackImmutably() {
        var laps = new ArrayList<>(List.of(100, 120));
        var row = new CompetitionHudState.PlacementRow(1, "Amy", "sonic", 220, 100, laps,
                CompetitionHudState.Outcome.FINISHED, "", CompetitionHudState.VerifyState.UNVERIFIED, true);
        var rows = new ArrayList<>(List.of(row));
        var state = new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, TrackKind.ARENA,
                3, 5, 50, laps, 100, 4, 37, true, 42000,
                CompetitionHudState.MassStartPhase.COUNTDOWN, 8000,
                CompetitionHudState.SwapNotice.applied("Tails"), rows, true);
        laps.clear(); rows.clear();
        assertEquals(List.of(100, 120), state.completedLapFrames());
        assertEquals(List.of(100, 120), row.lapFrames());
        assertEquals(List.of(row), state.placements());
        assertEquals(4, state.estimatedPosition());
        assertEquals(37, state.fieldSize());
        assertTrue(row.unverified());
        assertEquals("Tails", state.swapNotice().partnerName());
        assertEquals(90, state.swapNotice().remainingFrames());
        assertEquals("", CompetitionHudState.SwapNotice.fallback().partnerName());
        assertThrows(UnsupportedOperationException.class, () -> state.completedLapFrames().add(1));
        for (var outcome : List.of(CompetitionHudState.Outcome.DNF, CompetitionHudState.Outcome.DNS)) {
            assertEquals("Left room", new CompetitionHudState.PlacementRow(37, "Amy", "sonic", 0, 0,
                    List.of(), outcome, "Left room", CompetitionHudState.VerifyState.PENDING, false).reason());
        }
    }
}
