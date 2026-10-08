package hardened;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * One launch's encounter ledger. The module owns this service and registers it with
 * the gameplay rewind registry; objects never retain a global launch selection.
 * Timing advances only on admitted native gameplay ticks, never presentation rows.
 */
public final class EncounterState implements RewindSnapshottable<EncounterState.Snapshot> {
    public enum Status { READY, ACTIVE, CLEARED, FAILED, ABORTED }
    public enum Phase { WAITING, TELL, LOCKED, VOLLEY_ONE, VOLLEY_TWO, RECOVERY, RESTING }

    private Status status = Status.READY;
    private Phase phase = Phase.WAITING;
    private int phaseTick;
    private int aimX;
    private int aimY;
    private int volleys;
    private int ticks;
    private boolean checkpointTouched;
    private String failureReason = "";

    public Status status() { return status; }
    public Phase phase() { return phase; }
    public int phaseTick() { return phaseTick; }
    public int aimX() { return aimX; }
    public int aimY() { return aimY; }
    public int volleys() { return volleys; }
    public int ticks() { return ticks; }
    public boolean checkpointTouched() { return checkpointTouched; }
    public String failureReason() { return failureReason; }
    public boolean active() { return status == Status.ACTIVE; }
    public boolean cleared() { return status == Status.CLEARED; }
    public boolean failed() { return status == Status.FAILED || status == Status.ABORTED; }
    /** A fault or closed session, as opposed to an ordinary caught run. */
    public boolean aborted() { return status == Status.ABORTED; }

    /** A native full load installs a fresh graph, so its ledger starts a fresh timeline. */
    public void resetForLoad() {
        status = Status.READY;
        phase = Phase.WAITING;
        phaseTick = aimX = aimY = volleys = ticks = 0;
        checkpointTouched = false;
        failureReason = "";
    }

    /** Called once when the entry presentation releases native movement. */
    public void begin() {
        if (status != Status.READY) throw new IllegalStateException("Encounter already started");
        status = Status.ACTIVE;
    }

    /** Owner/controller faults end the run; already published hazards become harmless. */
    public void abort(String reason) {
        status = Status.ABORTED;
        failureReason = reason == null || reason.isBlank() ? "Encounter unavailable" : reason;
    }

    /** The sentry publishes only its authored pattern state for player feedback. */
    void publish(Phase next, int phaseTick, int aimX, int aimY, int volleys) {
        if (!active()) return;
        phase = next;
        this.phaseTick = phaseTick;
        this.aimX = aimX;
        this.aimY = aimY;
        this.volleys = volleys;
    }

    /**
     * Controller call after one admitted native tick. Checkpoint activation is observed
     * from the physical ROM post, and completion is position plus both readable volleys.
     * This never supplies player physics, rings, shield, checkpoint or camera values.
     */
    public void afterGameplayTick(PlayableEntity player, boolean checkpointActive) {
        if (!active() || player == null) return;
        ticks++;
        checkpointTouched |= checkpointActive;
        if (player.getDead()) {
            status = Status.FAILED;
            failureReason = "The ambush caught you";
            return;
        }
        int x = player.getCentreX() & 0xffff;
        int y = player.getCentreY() & 0xffff;
        if (checkpointTouched && volleys == EncounterPlan.VOLLEY_COUNT && phase == Phase.RESTING
                && x >= EncounterPlan.EXIT_X && x <= EncounterPlan.ROOM_RIGHT
                && y >= EncounterPlan.ROOM_TOP && y <= EncounterPlan.ROOM_BOTTOM) {
            status = Status.CLEARED;
        }
    }

    @Override public String key() { return "hardened-s3k:encounter"; }
    @Override public Snapshot capture() {
        return new Snapshot(status, phase, phaseTick, aimX, aimY, volleys, ticks,
                checkpointTouched, failureReason);
    }
    @Override public void restore(Snapshot snapshot) {
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        status = snapshot.status(); phase = snapshot.phase(); phaseTick = snapshot.phaseTick();
        aimX = snapshot.aimX(); aimY = snapshot.aimY(); volleys = snapshot.volleys();
        ticks = snapshot.ticks(); checkpointTouched = snapshot.checkpointTouched();
        failureReason = snapshot.failureReason();
    }
    @Override public void resetForMissingSnapshot() { resetForLoad(); }

    public record Snapshot(Status status, Phase phase, int phaseTick, int aimX, int aimY,
                           int volleys, int ticks, boolean checkpointTouched, String failureReason) { }
}
