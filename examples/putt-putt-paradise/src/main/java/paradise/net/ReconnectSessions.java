package paradise.net;

import java.time.Duration;
import java.util.*;
import static paradise.net.GolfPacket.*;

/** Two caller-owned room seats. Time arguments use one caller-supplied monotonic seconds clock. */
public final class ReconnectSessions {
    public static final Duration DEFAULT_WINDOW = Duration.ofSeconds(30);
    private record Seat(UUID match, UUID token, Long disconnectedAt) { }
    private final Seat[] seats = new Seat[2];
    private final long windowSeconds;
    public ReconnectSessions() { this(DEFAULT_WINDOW); }
    public ReconnectSessions(Duration window) {
        Objects.requireNonNull(window);
        if (window.isNegative() || window.isZero() || window.compareTo(DEFAULT_WINDOW) > 0 || window.getNano() != 0)
            throw new IllegalArgumentException("reconnect window must be whole seconds in 1..30");
        windowSeconds = window.toSeconds();
    }
    public UUID register(UUID match, int owner) {
        owner(owner); Objects.requireNonNull(match);
        if (seats[owner] != null) throw new IllegalStateException("seat already assigned");
        UUID token = UUID.randomUUID(); seats[owner] = new Seat(match, token, null); return token;
    }
    public void disconnected(int owner, long nowSeconds) {
        owner(owner); clock(nowSeconds);
        Seat seat = seats[owner];
        if (seat != null && seat.disconnectedAt == null) seats[owner] = new Seat(seat.match, seat.token, nowSeconds);
    }
    public boolean reconnect(Reconnect request, long nowSeconds) {
        Objects.requireNonNull(request); clock(nowSeconds);
        Seat seat = seats[request.owner()];
        if (seat == null || !seat.match.equals(request.match()) || !seat.token.equals(request.roomToken())
                || seat.disconnectedAt == null || nowSeconds < seat.disconnectedAt) return false;
        if (nowSeconds - seat.disconnectedAt >= windowSeconds) { seats[request.owner()] = null; return false; }
        seats[request.owner()] = new Seat(seat.match, seat.token, null); return true;
    }
    /** Expired seats tell the room owner which retained gameplay/receipt state to abandon. */
    public List<Integer> expire(long nowSeconds) {
        clock(nowSeconds); var expired = new ArrayList<Integer>(2);
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat != null && seat.disconnectedAt != null && nowSeconds >= seat.disconnectedAt
                    && nowSeconds - seat.disconnectedAt >= windowSeconds) { seats[i] = null; expired.add(i); }
        }
        return List.copyOf(expired);
    }
    public void leave(int owner) { owner(owner); seats[owner] = null; }
    public void clear() { Arrays.fill(seats, null); }
    private static void owner(int owner) { if (owner < 0 || owner > 1) throw new IllegalArgumentException("owner"); }
    private static void clock(long nowSeconds) { if (nowSeconds < 0) throw new IllegalArgumentException("clock"); }
}
