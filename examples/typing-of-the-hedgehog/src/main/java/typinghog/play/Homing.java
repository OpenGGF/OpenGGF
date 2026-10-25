package typinghog.play;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.ArrayDeque;
import java.util.function.IntSupplier;

/**
 * Sonic's homing attack. A completed word sends him curled up at its badnik, monitor or boss: he
 * dashes to it, strikes it (the kill lands on contact), bounces back over an arc and lands on his
 * stand spot before the next queued strike. Only during a stand, while the course holds the native
 * Sonic; the stand's checkpoint restore puts everything back for the rail.
 */
final class Homing {
    /** Rows of the dash per pixel of distance (about seven pixels a row), clamped. */
    static final int DASH_PIXELS_PER_ROW = 7;
    static final int DASH_MIN_ROWS = 6;
    static final int DASH_MAX_ROWS = 16;
    static final int BOUNCE_ROWS = 22;
    static final int BOUNCE_HEIGHT = 40;

    /** One strike: where the target is now, and what its hit does. */
    record Strike(IntSupplier x, IntSupplier y, Runnable onHit, int dodgeRows) { }

    private enum Leg { DASH, BOUNCE, DODGE }

    private final ArrayDeque<Strike> queue = new ArrayDeque<>();
    private Strike current;
    private Leg leg;
    private int legRow;
    private int legRows;
    private int fromX;
    private int fromY;
    private int homeX;
    private int homeY;
    private boolean anchored;
    private Direction homeFacing = Direction.RIGHT;

    /**
     * Records Sonic's stand spot; strikes return him there. A fight can open with him still curled
     * from the run: he stands up for it, and the spot is his standing centre (a roll's centre sits
     * lower by the difference of the two Y radii).
     */
    void anchor(int x, int y) {
        if (anchored) return;
        anchored = true;
        homeX = x;
        homeY = y;
        if (GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic) {
            homeFacing = sonic.getDirection();
            if (sonic.getRolling()) {
                homeY -= sonic.getStandYRadius() - sonic.getRollYRadius();
                land();
            }
        }
    }

    /** Turns Sonic on his spot to face {@code facing} (toward a boss), and keeps that after strikes. */
    void face(Direction facing) {
        homeFacing = facing;
        if (current == null && GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic) {
            sonic.setDirection(facing);
        }
    }

    boolean anchored() { return anchored; }
    int homeX() { return homeX; }
    int homeY() { return homeY; }

    void strike(IntSupplier x, IntSupplier y, Runnable onHit) {
        queue.add(new Strike(x, y, onHit, 0));
    }

    /** Mod-designed dodge: hop aside, let the sourced hazard pass, then hop back standing. */
    void dodge(int offsetX, int hazardRows) {
        queue.add(new Strike(() -> homeX + offsetX, () -> homeY, () -> { }, hazardRows));
    }

    /** True while a strike is in flight or queued. */
    boolean busy() { return current != null || !queue.isEmpty(); }

    /**
     * A hit ends the volley: every pending strike lands at once (its badnik was already doomed, so
     * its kill must still happen) and Sonic is put back on his spot.
     */
    void cancel() {
        // Take the volley first: a landing strike may itself hit Sonic (a decoy) and cancel again,
        // which must find nothing left to land rather than re-run the strike in flight.
        Strike flying = current;
        boolean dashing = flying != null && leg == Leg.DASH;
        current = null;
        java.util.List<Strike> pending = new java.util.ArrayList<>(queue);
        queue.clear();
        if (dashing) flying.onHit().run();
        for (Strike strike : pending) strike.onHit().run();
        if (flying != null) land();
    }

    /** Forgets the stand spot (after the stand's restore). */
    void release() {
        queue.clear();
        current = null;
        anchored = false;
    }

    void advance() {
        if (!anchored) return;
        if (current == null) {
            current = queue.poll();
            if (current == null) return;
            leg = current.dodgeRows() > 0 ? Leg.DODGE : Leg.DASH;
            legRow = 0;
            fromX = homeX;
            fromY = homeY;
            int distance = (int) Math.hypot(current.x().getAsInt() - fromX, current.y().getAsInt() - fromY);
            legRows = leg == Leg.DODGE ? current.dodgeRows() + BOUNCE_ROWS
                    : Math.max(DASH_MIN_ROWS, Math.min(DASH_MAX_ROWS, distance / DASH_PIXELS_PER_ROW));
        }
        legRow++;
        double t = Math.min(1, legRow / (double) legRows);
        if (leg == Leg.DODGE) {
            int offset = current.x().getAsInt() - homeX;
            double hop = Math.min(1, legRow / (double) BOUNCE_ROWS);
            if (legRow > current.dodgeRows()) hop = Math.max(0, (legRows - legRow) / (double) BOUNCE_ROWS);
            place(homeX + (int) Math.round(offset * hop),
                    homeY - (int) Math.round(Math.sin(Math.PI * hop) * BOUNCE_HEIGHT), homeFacing, false);
            if (legRow >= legRows) { current = null; land(); }
        } else if (leg == Leg.DASH) {
            // The target may still be moving; home on where it is now, accelerating in.
            int tx = current.x().getAsInt(), ty = current.y().getAsInt();
            double e = t * t;
            place(fromX + (int) Math.round((tx - fromX) * e), fromY + (int) Math.round((ty - fromY) * e),
                    tx < fromX ? Direction.LEFT : Direction.RIGHT, true);
            if (legRow >= legRows) {
                current.onHit().run();
                leg = Leg.BOUNCE;
                legRow = 0;
                legRows = BOUNCE_ROWS;
                fromX = tx;
                fromY = ty;
            }
        } else {
            double e = t * t * (3 - 2 * t);
            int x = fromX + (int) Math.round((homeX - fromX) * e);
            int y = fromY + (int) Math.round((homeY - fromY) * e) - (int) Math.round(Math.sin(Math.PI * t) * BOUNCE_HEIGHT);
            place(x, y, homeX < fromX ? Direction.LEFT : Direction.RIGHT, true);
            if (legRow >= legRows) {
                current = null;
                if (queue.isEmpty()) land();
            }
        }
    }

    /** True while Sonic should stay a rolling ball (the frame controller's retainRolling). */
    boolean rolling() { return current != null && leg != Leg.DODGE; }

    /** Puts Sonic back on his spot, standing: object control keeps the engine from choosing his pose. */
    private void land() {
        place(homeX, homeY, homeFacing, false);
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic)) return;
        sonic.setAnimationId(sonic.resolveAnimationId(CanonicalAnimation.WAIT));
        sonic.setAnimationFrameIndex(0);
        sonic.setAnimationTick(0);
    }

    private void place(int x, int y, Direction facing, boolean ball) {
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic)) return;
        // The roll changes his box from the top-left, which moves the centre; set the pose first so
        // the written centre is the one that stays.
        sonic.setRolling(ball);
        sonic.setAir(ball || (current != null && leg == Leg.DODGE));
        NativePositionOps.writeXPosPreserveSubpixel(sonic, x);
        NativePositionOps.writeYPosPreserveSubpixel(sonic, y);
        sonic.setDirection(facing);
    }
}
