package openggf.timeattack;

import com.openggf.mods.run.TrackKind;
import java.util.List;
import java.util.Objects;

/**
 * Display-only race snapshot shared by the local and multiplayer overlays.
 * Lap frame counts are durations; currentLap/lapCount are zero on acts. bestLapFrames is -1
 * before a lap closes. estimatedPosition is one-based (zero means unknown); fieldSize is the
 * participant count. Position never decides placement. cutoffRemainingMillis is shown only
 * while cutoffArmed; timeToStartMillis is the host-scheduled GO countdown. RELEASED is the
 * neutral phase for existing Time Attack paths. unverified labels the entire standings plate.
 */
public record CompetitionHudState(CompetitionRoomSettings.Mode mode, TrackKind trackKind,
        int currentLap, int lapCount, int currentLapFrames, List<Integer> completedLapFrames,
        int bestLapFrames, int estimatedPosition, int fieldSize, boolean cutoffArmed,
        long cutoffRemainingMillis, MassStartPhase massStartPhase, long timeToStartMillis,
        SwapNotice swapNotice, List<PlacementRow> placements, boolean unverified) {
    public enum MassStartPhase { LOADING, HELD, COUNTDOWN, RELEASED }
    public enum Outcome { FINISHED, DNF, DNS }
    public enum VerifyState { PENDING, VERIFIED, UNVERIFIED }

    /** Empty partner means the solo fallback (SWAP: NONE); zero frames hides the notice. */
    public record SwapNotice(String partnerName, int remainingFrames) {
        public SwapNotice {
            Objects.requireNonNull(partnerName);
            if (remainingFrames < 0) throw new IllegalArgumentException("negative notice duration");
        }
        public static SwapNotice none() { return new SwapNotice("", 0); }
        public static SwapNotice applied(String partnerName) { return new SwapNotice(partnerName, 90); }
        public static SwapNotice fallback() { return new SwapNotice("", 90); }
    }

    /** Zero total/best lap means unavailable for a non-finisher; reason explains DNF/DNS. */
    public record PlacementRow(int rank, String name, String character, int totalFrames,
            int bestLapFrames, List<Integer> lapFrames, Outcome outcome, String reason,
            VerifyState verifyState, boolean taintedSwap) {
        public PlacementRow {
            Objects.requireNonNull(name);
            Objects.requireNonNull(character);
            Objects.requireNonNull(outcome);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(verifyState);
            lapFrames = List.copyOf(lapFrames);
        }
        public boolean unverified() { return verifyState == VerifyState.UNVERIFIED; }
    }

    public CompetitionHudState {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(trackKind);
        Objects.requireNonNull(massStartPhase);
        Objects.requireNonNull(swapNotice);
        completedLapFrames = List.copyOf(completedLapFrames);
        placements = List.copyOf(placements);
    }

    /** Existing Time Attack paths have no lap, position, cutoff, start gate or swap overlay. */
    public static CompetitionHudState inactive() {
        return new CompetitionHudState(CompetitionRoomSettings.Mode.TIME_ATTACK, TrackKind.ACT,
                0, 0, 0, List.of(), -1, 0, 0, false, 0,
                MassStartPhase.RELEASED, 0, SwapNotice.none(), List.of(), false);
    }
}
