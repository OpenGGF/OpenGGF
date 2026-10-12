package flappytails;

/**
 * A pilot that flies Tails by looking ahead. Each tick it simulates the next second and a bit
 * twice, on copies of the real {@link Flight}, once pressing now and once not, then keeps
 * whichever choice stays clear of the pillars, the badniks and the ground and closest to the
 * middle of the coming gaps. After that first choice both futures follow the same simple habit:
 * press whenever Tails has sunk below where he wants to be.
 *
 * <p>The title screen's attract mode, the tests and the promo all use it. It never sees
 * anything the player cannot: the course ahead is on screen or just beyond it.
 */
final class Autopilot {
    private static final int HORIZON = 110;
    private static final int LATEST_FLAP = 60;
    private int daring;
    private int flip;

    /** 0 flies down the middle; up to 100 shaves the gap edges for show (and sometimes fails). */
    void setDaring(int percent) { daring = Math.max(0, Math.min(100, percent)); }

    /** Whether to press this tick. */
    boolean decide(Run run) {
        if (run.phase() == Run.Phase.READY) {
            return run.ticks() > 40;
        }
        if (run.phase() != Run.Phase.FLYING) return false;
        if (run.flight.flapping() || run.flight.yVel() < Flight.RISE_LIMIT) return false;   // a press would do nothing
        // Plans: the next flap now, or after waiting 2, 4, ... ticks, or not at all; after it the
        // habit takes over. Press now only if flapping now is the best plan.
        double best = Double.MAX_VALUE;
        int bestWait = -1;
        for (int wait = LATEST_FLAP + 2; wait >= 0; wait -= 2) {
            double cost = future(run, run.flight.copy(), wait);
            if (cost < best) {   // ties keep the later flap: never press without a reason
                best = cost;
                bestWait = wait;
            }
        }
        return bestWait == 0;
    }

    /** The cost of flapping first after {@code wait} ticks ({@code > LATEST_FLAP}: never in the horizon). */
    private double future(Run run, Flight flight, int wait) {
        long fine = run.course.scrollFine();
        int speed = run.speed();
        long tick = run.ticks();
        double cost = 0;
        boolean planned = false;
        for (int i = 0; i < HORIZON; i++) {
            fine += speed;
            tick++;
            int courseX = (int) (fine >> 8) + Run.TAILS_X;
            int target = target(run, courseX);
            boolean press;
            if (!planned) {
                press = i == wait;
                planned = press;
            } else {
                press = lookahead(flight) > target && !flight.flapping();
            }
            flight.tick(press, (tick & 1) == 1, 0);
            if (!run.mode.sonicRules) flight.refill(Flight.FULL_TIMER);
            int y = flight.y();
            if (run.isSuper() ? y + Run.Y_RADIUS >= Course.GROUND_Y : hits(run, courseX, y, tick)) {
                return 1e9 - i * 1e6;
            }
            double miss = y - target;
            cost += miss * miss / (1 + i * 0.05);
        }
        return cost;
    }

    /** Where Tails will be in 16 ticks if he only glides: the habit flaps on that, not on now. */
    private static int lookahead(Flight flight) {
        return flight.y() + flight.yVel() / 16 + 4;
    }

    /**
     * Where Tails wants to be at course X: as close to the gap after next as the coming gap
     * safely allows, so he leaves each gap already heading for the next. Daring moves the aim
     * towards the gap's edges.
     */
    private int target(Run run, int courseX) {
        Course.Gate next = null;
        Course.Gate after = null;
        for (Course.Gate gate : run.course.gates()) {
            if (next == null) {
                if (gate.right() + Run.TOUCH_HALF_WIDTH >= courseX) next = gate;
            } else {
                after = gate;
                break;
            }
        }
        if (next == null) return 104;
        if (run.isSuper()) {
            // Super Tails bursts pillars: aim into them for the show. Mostly the bottom one, which
            // Tails can reach every time; every third gate the top one, when it is low enough.
            boolean high = next.number % 3 == 0 && next.top() > 70;
            return high ? next.top() - 6 : Math.min(Course.GROUND_Y - Run.Y_RADIUS - 4, next.bottom() + 6);
        }
        int margin = 6;
        int high = next.top() + Run.TOUCH_HALF_HEIGHT + margin;
        int low = next.bottom() - Run.TOUCH_HALF_HEIGHT - margin;
        int aim = next.centre;
        if (after != null && courseX > next.x - Course.SPACING / 2 && after.number / Zone.GATES == next.number / Zone.GATES) {
            aim = (next.centre + after.centre) / 2;
        }
        int lean = (low - high) / 2 * daring / 100;
        if (((next.number + flip) & 1) == 1) lean = -lean;
        return Math.max(high, Math.min(low, aim + lean));
    }

    private static boolean hits(Run run, int courseX, int y, long tick) {
        if (y + Run.Y_RADIUS >= Course.GROUND_Y) return true;
        for (Course.Gate gate : run.course.gates()) {
            if (gate.smashed) continue;
            if (courseX + Run.TOUCH_HALF_WIDTH > gate.x && courseX - Run.TOUCH_HALF_WIDTH < gate.right()
                    && (y - Run.TOUCH_HALF_HEIGHT < gate.top() || y + Run.TOUCH_HALF_HEIGHT > gate.bottom())) {
                return true;
            }
        }
        for (Course.Badnik badnik : run.course.badniks()) {
            if (badnik.destroyedAt < 0 && Math.abs(badnik.x - courseX) < Run.TOUCH_HALF_WIDTH + 12
                    && Math.abs(badnik.y(tick) - y) < Run.TOUCH_HALF_HEIGHT + 12) {
                return true;
            }
        }
        return false;
    }

    /** Swaps which gaps it leans high on, so two demonstrations differ. */
    void reseed(long seed) { flip = (int) (seed & 1); }
}
