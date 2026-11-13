package openggf.timeattack.ui;

import com.openggf.mods.run.TrackKind;
import com.openggf.net.client.RemoteGhostRegistry;
import openggf.timeattack.CompetitionHudState;
import openggf.timeattack.CompetitionHudState.MassStartPhase;
import openggf.timeattack.CompetitionHudState.Outcome;
import openggf.timeattack.CompetitionHudState.PlacementRow;
import openggf.timeattack.CompetitionHudState.SwapNotice;
import openggf.timeattack.CompetitionHudState.VerifyState;
import openggf.timeattack.CompetitionRoomSettings;
import openggf.timeattack.mp.MultiplayerHudState;

import java.util.List;
import java.util.Map;

/**
 * Competition display states for the GUI tests and design captures: a 37-racer arena round in lap 3
 * (lap {@code 0'21"50}, best {@code 0'19"83}), and its variants. Frame counts are chosen so the ROM
 * display reads as written: 1290 frames is 21 s and 30 frames, which byte_37E52 shows as 50 hundredths.
 */
public final class CompetitionSamples {
    /** {@code 0'19"83}: 19 s and 50 frames (50 * 100 / 60 = 83). */
    public static final int BEST_LAP = 19 * 60 + 50;
    /** {@code 0'21"50}. */
    public static final int CURRENT_LAP = 21 * 60 + 30;

    private CompetitionSamples() { }

    public static CompetitionHudState arena(boolean cutoff, SwapNotice swap, boolean unverified) {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, TrackKind.ARENA, 3, 5, CURRENT_LAP,
                List.of(20 * 60 + 12, BEST_LAP), BEST_LAP, 4, 37, cutoff, 41_200, MassStartPhase.RELEASED, 0, swap,
                List.of(), unverified, Map.of(0, 480, 3, 610, 4, 455, 5, 120), false);
    }

    public static CompetitionHudState act(boolean cutoff) {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, TrackKind.ACT, 0, 0, 0, List.of(),
                -1, 2, 6, cutoff, 41_200, MassStartPhase.RELEASED, 0, SwapNotice.none(), List.of(), false);
    }

    public static CompetitionHudState start(TrackKind kind, MassStartPhase phase) {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, kind, 0, kind == TrackKind.ARENA ? 5 : 0,
                0, List.of(), -1, 0, 37, false, 0, phase, 7_400, SwapNotice.none(), List.of(), false);
    }

    public static CompetitionHudState lateJoin() {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, TrackKind.ARENA, 0, 5, 0, List.of(),
                -1, 0, 37, false, 0, MassStartPhase.RELEASED, 0, SwapNotice.none(), List.of(), false, Map.of(), true);
    }

    /** Six rows given out of order: two finishers, a swap-tainted finisher, a DNS, a DNF and a pending row. */
    public static List<PlacementRow> placements(VerifyState verify) {
        List<Integer> winner = List.of(1210, 1190, 1250, 1201, 1222);
        List<Integer> second = List.of(1300, 1260, 1240, 1288, 1275);
        List<Integer> third = List.of(1330, 1291, 1300, 1301, 1340);
        return List.of(
                new PlacementRow(0, "Spectator Seven", "sonic", 0, 0, List.of(), Outcome.DNS,
                        "No start within 2 s", verify, false, 6),
                new PlacementRow(2, "Knuckles Fan", "knuckles", sum(second), 1240, second, Outcome.FINISHED, "",
                        verify, true, 3),
                new PlacementRow(0, "Quit Early", "tails", 0, 1270, List.of(1290, 1270), Outcome.DNF,
                        "Left the race", verify, false, 5),
                new PlacementRow(1, "Tails", "tails", sum(winner), 1190, winner, Outcome.FINISHED, "", verify, false, 0),
                new PlacementRow(3, "Amy", "sonic", sum(third), 1291, third, Outcome.FINISHED, "",
                        verify == VerifyState.VERIFIED ? VerifyState.PENDING : verify, false, 4));
    }

    public static CompetitionHudState results(boolean unverified) {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.COMPETITION, TrackKind.ARENA, 5, 5, 0,
                List.of(1210, 1190, 1250, 1201, 1222), 1190, 1, 5, false, 0, MassStartPhase.RELEASED, 0,
                SwapNotice.none(), placements(unverified ? VerifyState.UNVERIFIED : VerifyState.VERIFIED), unverified);
    }

    /** A running room snapshot carrying {@code competition}, with three rivals on the progress strip. */
    public static MultiplayerHudState room(String phase, CompetitionHudState competition) {
        List<RemoteGhostRegistry.FarPlayer> players = List.of(
                new RemoteGhostRegistry.FarPlayer(3, "Knuckles Fan", "knuckles", 90, 4, 1),
                new RemoteGhostRegistry.FarPlayer(4, "Amy", "sonic", 60, 4, 1),
                new RemoteGhostRegistry.FarPlayer(5, "Quit Early", "tails", 20, 4, -1));
        return new MultiplayerHudState(true, phase, 120_000, 0, List.of(), List.of(), false, null, 37, players,
                "OPEN", List.of(), List.of(), 0, null, List.of(), -1, Map.of(3, 0xFFFF6060, 4, 0xFFFF9EE0), 0, 0,
                Map.of(), competition);
    }

    private static int sum(List<Integer> laps) {
        return laps.stream().mapToInt(Integer::intValue).sum();
    }
}
