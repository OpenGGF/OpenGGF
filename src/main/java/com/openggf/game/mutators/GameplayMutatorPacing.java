package com.openggf.game.mutators;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/** Whole native ticks and retained player edges, owned and restored by one world. */
public final class GameplayMutatorPacing implements RewindSnapshottable<GameplayMutatorPacing.Snapshot> {
    private final GameplayMutatorPolicySource policies;
    private int remainder;
    private PlayerInputState player1 = PlayerInputState.neutral();
    private PlayerInputState player2 = PlayerInputState.neutral();
    private boolean pending;

    public GameplayMutatorPacing(GameplayMutatorPolicySource policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public GameplayMutatorPolicy policy() { return Objects.requireNonNull(policies.policy(), "policy"); }

    /** Called once per eligible interactive presentation, never canonical tool/trace step(). */
    public int stepsForPresentation() {
        int total = remainder + policy().gameplaySpeedPercent();
        remainder = total % 100;
        return total / 100;
    }

    /** Latest held input wins; unconsumed press edges survive a press/release between ticks. */
    public void retain(LogicalInputSnapshot input) {
        player1 = retain(player1, input.player1(), false);
        player2 = retain(player2, input.player2(), true);
        pending = true;
    }

    private static PlayerInputState retain(PlayerInputState old, PlayerInputState fresh, boolean p2) {
        return PlayerInputState.of(fresh.heldMask(), old.pressedMask() | fresh.pressedMask(),
                fresh.actionHeldMask(), old.actionPressedMask() | fresh.actionPressedMask(),
                p2 && fresh.startHeld(), p2 && (old.startPressed() || fresh.startPressed()));
    }

    public boolean hasPendingInput() { return pending; }
    public PlayerInputState pendingPlayer1() { return player1; }
    public PlayerInputState pendingPlayer2() { return player2; }

    public void clearPendingInput() {
        player1 = PlayerInputState.neutral();
        player2 = PlayerInputState.neutral();
        pending = false;
    }

    /** Qualifying world assembly/retirement resets time and prevents input crossing a boundary. */
    public void reset() { remainder = 0; clearPendingInput(); }
    @Override public String key() { return "gameplay-mutator-pacing"; }
    @Override public Snapshot capture() { return new Snapshot(remainder, player1, player2, pending); }
    @Override public void restore(Snapshot snapshot) {
        remainder = snapshot.remainder();
        player1 = snapshot.player1();
        player2 = snapshot.player2();
        pending = snapshot.pending();
    }
    @Override public void resetForMissingSnapshot() { reset(); }

    public record Snapshot(int remainder, PlayerInputState player1, PlayerInputState player2, boolean pending) {
        public Snapshot {
            if (remainder < 0 || remainder >= 100) throw new IllegalArgumentException("Invalid pacing remainder");
            Objects.requireNonNull(player1, "player1");
            Objects.requireNonNull(player2, "player2");
        }
    }
}
