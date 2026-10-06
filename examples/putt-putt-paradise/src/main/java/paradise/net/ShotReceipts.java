package paradise.net;

import java.util.Objects;
import java.util.Optional;
import static paradise.net.GolfPacket.*;

/** Caller-thread host ledger: only current and last committed shots are retained. Never simulates a shot. */
public final class ShotReceipts {
    public enum Status { ACCEPTED, DUPLICATE, CONFLICT, WRONG_TURN }
    public record Receipt(ShotRequest request, ShotAccepted accepted, TurnCommitted committed) { }
    public record Result(Status status, Receipt receipt) {
        public boolean newlyAccepted() { return status == Status.ACCEPTED; }
    }
    private ShotId open;
    private Receipt current;
    private Receipt last;

    public void openTurn(ShotId id) {
        Objects.requireNonNull(id);
        if (id.equals(open)) return;
        if (open != null && !id.match().equals(open.match())) clear();
        if (current != null && current.committed() == null) throw new IllegalStateException("accepted shot is unresolved");
        if (open != null && (id.hole() < open.hole() || id.hole() == open.hole() && id.turn() <= open.turn()))
            throw new IllegalArgumentException("turn must advance");
        if (current != null) last = current;
        current = null; open = id;
    }

    /** The caller must bind senderOwner to the ready/reconnected socket, rather than trusting the packet owner. */
    public Result accept(ShotRequest request, int senderOwner, long acceptedTick) {
        Result inspection = inspect(request, senderOwner);
        if (inspection.status() != Status.ACCEPTED) return inspection;
        var ack = new ShotAccepted(request.id(), acceptedTick, request.normalizedPower());
        current = new Receipt(request, ack, null);
        return new Result(Status.ACCEPTED, current);
    }
    /** Identity/duplicate validation without committing a stroke; the model owner decides when to accept. */
    public Result inspect(ShotRequest request, int senderOwner) {
        Objects.requireNonNull(request);
        if (senderOwner != request.id().owner()) return new Result(Status.WRONG_TURN, null);
        for (Receipt receipt : new Receipt[]{current, last}) {
            if (receipt != null && receipt.request().id().equals(request.id())) {
                return new Result(receipt.request().equals(request) ? Status.DUPLICATE : Status.CONFLICT, receipt);
            }
        }
        if (!request.id().equals(open)) return new Result(Status.WRONG_TURN, null);
        return new Result(Status.ACCEPTED, null);
    }

    /** Local host shot convenience. Remote callers should use the sender-bound overload. */
    public Result accept(ShotRequest request, long acceptedTick) { return accept(request, request.id().owner(), acceptedTick); }

    public void commit(TurnCommitted result) {
        Objects.requireNonNull(result);
        if (current == null || !current.request().id().equals(result.id())) throw new IllegalStateException("no matching accepted shot");
        if (current.committed() != null) {
            if (!current.committed().equals(result)) throw new IllegalStateException("contradictory result");
            return;
        }
        current = new Receipt(current.request(), current.accepted(), result);
    }
    public Optional<Receipt> current() { return Optional.ofNullable(current); }
    public Optional<Receipt> last() { return Optional.ofNullable(last); }
    public Optional<ShotId> openTurn() { return Optional.ofNullable(open); }
    public void clear() { open = null; current = null; last = null; }
}
