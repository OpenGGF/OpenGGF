package flappytails;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * The obstacle course: pillar pairs, rings and badniks laid out along an endless line that
 * scrolls left past Tails. Everything is in course pixels; {@link #scrollX()} is how far the
 * course has moved, so an object at course X {@code x} is on screen at {@code x - scrollX()}.
 *
 * <p>Gates are numbered from 0 for the whole run. Gate {@code n} belongs to zone
 * {@code n / Zone.GATES} of the tour, and a {@link #BREATHER} of open sky separates the zones,
 * room for the next zone's title card. A gate's position follows from its number alone, so a
 * run can start at any zone.
 *
 * <p>Layout is random but reproducible: one {@link SnapshotRandom} seeded at the start of the
 * run draws every choice in gate order, and each gap is kept within what Tails can fly between
 * two gates at that zone's speed. Tails climbs at most about a pixel a frame (a flap stops
 * pushing at -$100), but falls as fast as gravity takes him, so a gap may drop further than it
 * rises.
 */
final class Course {
    /** Pixels between one pillar's left edge and the next. */
    static final int SPACING = 176;
    /** Extra open sky between zones. */
    static final int BREATHER = 640;
    /** Where gate 0 stands when a run starts, so it slides in from beyond the right edge. */
    static final int START = 520;
    /** The screen row of the ground's surface; touching it is a crash. */
    static final int GROUND_Y = 200;
    /** Gaps never reach higher than this, so the ROM's ceiling ($10 below the camera top) stays clear. */
    static final int TOP_MARGIN = 24;
    /** How far ahead of the screen's right edge the course is laid out. */
    private static final int LOOKAHEAD = 256;

    /** A pillar pair: a bottom pillar rising to the gap and a top pillar hanging down to it. */
    static final class Gate {
        final int number;
        final int x;
        final int centre;
        final int gap;
        final int tourIndex;
        boolean passed;
        /** Smashed by Super Tails: no longer drawn or solid. */
        boolean smashed;
        /** The least room Tails had to spare while inside the gap, in pixels; judged once he is through. */
        int closest = Integer.MAX_VALUE;
        boolean judged;

        Gate(int number, int x, int centre, int gap) {
            this.number = number;
            this.x = x;
            this.centre = centre;
            this.gap = gap;
            this.tourIndex = Zone.tourIndex(number);
        }

        int top() { return centre - gap / 2; }
        int bottom() { return centre + gap / 2; }
        int right() { return x + ZoneArt.PILLAR_WIDTH; }
    }

    /** A ring waiting in the course; {@code takenAt} is the tick it was collected, or -1. */
    static final class Ring {
        final int x;
        final int y;
        long takenAt = -1;

        Ring(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A badnik hovering between gates, bobbing on a sine around {@code baseY}. */
    static final class Badnik {
        final int x;
        final int baseY;
        final int amplitude;
        final int phase;
        final int tourIndex;
        long destroyedAt = -1;

        Badnik(int x, int baseY, int amplitude, int phase, int tourIndex) {
            this.x = x;
            this.baseY = baseY;
            this.amplitude = amplitude;
            this.phase = phase;
            this.tourIndex = tourIndex;
        }

        /** Its centre at {@code ticks}: one bob every 128 ticks. */
        int y(long ticks) {
            double angle = (ticks + phase) * Math.PI * 2 / 128;
            return baseY + (int) Math.round(Math.sin(angle) * amplitude);
        }
    }

    private final SnapshotRandom random = new SnapshotRandom(1);
    private final List<Gate> gates = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Badnik> badniks = new ArrayList<>();
    private long scroll;      // 1/256 pixel
    private int nextGate;
    private int lastCentre;

    /** A fresh course from {@code seed}, starting at gate {@code firstGate} (0, or a later zone's first). */
    void reset(long seed, int firstGate) {
        random.reset(seed);
        gates.clear();
        rings.clear();
        badniks.clear();
        nextGate = firstGate;
        scroll = (long) (gateX(firstGate) - START) << 8;
        lastCentre = (TOP_MARGIN + GROUND_Y) / 2 - 4;
        layOut();
    }

    /** Scrolls {@code speed} (1/256 pixel) and lays out what is about to come on screen. */
    void advance(int speed) {
        scroll += speed;
        layOut();
        int left = scrollX() - 64;
        gates.removeIf(g -> g.right() < left);
        rings.removeIf(r -> r.x < left);
        badniks.removeIf(b -> b.x < left);
    }

    int scrollX() { return (int) (scroll >> 8); }
    long scrollFine() { return scroll; }
    List<Gate> gates() { return gates; }
    List<Ring> rings() { return rings; }
    List<Badnik> badniks() { return badniks; }

    /** The course X of gate {@code n}'s left edge. */
    static int gateX(int n) {
        return START + n * SPACING + (n / Zone.GATES) * BREATHER;
    }

    /** The first gate not yet passed, or null. */
    Gate nextGate(int tailsCourseX) {
        for (Gate gate : gates) {
            if (gate.right() + 12 >= tailsCourseX) return gate;
        }
        return null;
    }

    private void layOut() {
        int horizon = scrollX() + 400 + LOOKAHEAD;
        while (gateX(nextGate) < horizon) {
            addGate(nextGate++);
        }
    }

    private void addGate(int n) {
        Zone zone = Zone.at(n).forLap(Zone.lap(n));
        int x = gateX(n);
        int gap = zone.gap();
        int lowest = TOP_MARGIN + gap / 2;
        int highest = GROUND_Y - 16 - gap / 2;
        boolean firstOfZone = n % Zone.GATES == 0;
        // Frames Tails has between gates at this zone's speed. He climbs under half a pixel a
        // frame once each flap's ramp is counted, and a flap that saves him from one gap's floor
        // carries him upwards for half a second, so a drop is no easier than a climb.
        int frames = SPACING * 256 / zone.speed();
        int maxRise = Math.min(40, frames * 2 / 5);
        int maxDrop = maxRise;
        int from = firstOfZone ? (lowest + highest) / 2 : lastCentre;
        int low = Math.max(lowest, from - maxRise);
        int high = Math.min(highest, from + maxDrop);
        int centre = low + random.nextInt(Math.max(1, high - low + 1));
        if (firstOfZone) centre = (lowest + highest) / 2;
        Gate gate = new Gate(n, x, centre, gap);
        gates.add(gate);
        addRings(gate, from);
        if (zone.badniks() && !firstOfZone && random.nextInt(3) == 0) {
            addBadnik(gate, from, gate.tourIndex);
        }
        if (n % Zone.GATES == Zone.GATES - 1) {
            addBreatherRings(gate);
        }
        lastCentre = centre;
    }

    /** Rings in or just before the gap: none, one, a column of three, or an arc from the last gap. */
    private void addRings(Gate gate, int previousCentre) {
        int pattern = random.nextInt(10);
        int cx = gate.x + ZoneArt.PILLAR_WIDTH / 2;
        if (pattern < 3) {
            return;
        } else if (pattern < 6) {
            rings.add(new Ring(cx, gate.centre));
        } else if (pattern < 8) {
            for (int i = -1; i <= 1; i++) rings.add(new Ring(cx, gate.centre + i * 20));
        } else {
            // Five rings along the line from the previous gap to this one, ending just before it.
            for (int i = 1; i <= 5; i++) {
                int x = gate.x - SPACING + ZoneArt.PILLAR_WIDTH + i * (SPACING - ZoneArt.PILLAR_WIDTH) / 6;
                int y = previousCentre + (gate.centre - previousCentre) * i / 6;
                rings.add(new Ring(x, y));
            }
        }
    }

    /** A wave of rings through the open sky after a zone's last gate. */
    private void addBreatherRings(Gate last) {
        int start = last.right() + 120;
        for (int i = 0; i < 16; i++) {
            int x = start + i * 24;
            int y = 104 + (int) Math.round(Math.sin(i * Math.PI / 5) * 36);
            rings.add(new Ring(x, y));
        }
    }

    /**
     * A badnik in the open air between the previous gate and this one, held at least 44 pixels
     * off the straight line Tails would fly between the two gaps, so it threatens a lazy line
     * without closing the way.
     */
    private void addBadnik(Gate gate, int previousCentre, int tourIndex) {
        int x = gate.x - SPACING / 2 + ZoneArt.PILLAR_WIDTH / 2;
        int line = (previousCentre + gate.centre) / 2;
        boolean above = random.nextInt(2) == 0;
        int y = above ? line - 48 : line + 48;
        if (y < TOP_MARGIN + 8 || y > GROUND_Y - 24) {
            y = above ? line + 48 : line - 48;
        }
        if (y < TOP_MARGIN + 8 || y > GROUND_Y - 24) return;
        badniks.add(new Badnik(x, y, 6, random.nextInt(128), tourIndex));
    }
}
