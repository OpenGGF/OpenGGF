package flappytails;

import java.util.ArrayList;
import java.util.List;

/**
 * One flight, from GET READY to the crash: the rules, with no drawing and no audio. The scene
 * feeds it one input a tick and turns the {@link Event}s it reports into sound and effects, so
 * tests and the autopilot can run a whole flight without a screen.
 */
final class Run {
    /** Tails' screen column (his centre), a little left of a third of the way across. */
    static final int TAILS_X = 112;
    static final int START_Y = 100;
    /** Tails' touch box ({@code Touch_NoInstaShield}): x - 8 for 16 pixels, y_radius - 3 above and below. */
    static final int TOUCH_HALF_WIDTH = 8;
    static final int TOUCH_HALF_HEIGHT = 0x0F - 3;
    /** Tails' standing y_radius, $F: his feet meet the ground this far below his centre. */
    static final int Y_RADIUS = 0x0F;
    /** {@code HurtCharacter}: knocked up at -$400, with $30 hurt gravity a frame ({@code Tails_Hurt}). */
    static final int HURT_SPEED = -0x400;
    static final int HURT_GRAVITY = 0x30;
    /** {@code Kill_Character}: the death hop at -$700, falling under normal gravity, $38. */
    static final int DEATH_SPEED = -0x700;
    static final int GRAVITY = 0x38;
    /** {@code invulnerability_timer} after a hurt: $78 frames of blinking. */
    static final int INVULNERABLE_FRAMES = 0x78;
    /** Spilled rings can be caught once the first 30 frames of blinking have passed. */
    static final int SPILL_CATCH_DELAY = 30;
    /** A ring in Sonic rules gives back this much flight time, a little over a second. */
    static final int RING_FLIGHT = 0x28;
    /** Through a gap with this little room to spare (or less) is a close call. */
    static final int CLOSE_CALL = 3;
    /** How long Super Tails lasts after a lap of the tour: twenty seconds, well past the next zone's card. */
    static final int SUPER_FRAMES = 1200;
    /** Speed eases towards a new zone's pace by this much a frame (1/256 pixel). */
    private static final int SPEED_EASE = 2;

    enum Phase { READY, FLYING, HURT, CRASHED, OVER }

    enum Type { SFX, MUSIC, GATE, RING, SPILL, HIT, CRASH, ZONE, ZONE_CLEAR, FLAP, SUPER, SUPER_END, SMASH, CLOSE_CALL }

    /** Something that happened this tick: a sound to play, or a moment to dress up on screen. */
    record Event(Type type, int value, int x, int y) { }

    /** A ring knocked loose by a hit ({@code Obj_Bouncing_Ring}), in course pixels times 256. */
    static final class Spill {
        int x;
        int y;
        int xVel;
        int yVel;
        final int index;
        final long born;
        long takenAt = -1;

        Spill(int x, int y, int xVel, int yVel, int index, long born) {
            this.x = x;
            this.y = y;
            this.xVel = xVel;
            this.yVel = yVel;
            this.index = index;
            this.born = born;
        }
    }

    final Mode mode;
    final long seed;
    final int firstGate;
    final boolean practice;
    final Course course = new Course();
    final Flight flight = new Flight();
    private final List<Spill> spills = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();

    private Phase phase = Phase.READY;
    private long ticks;
    private long phaseTick;
    private long scenery;       // 1/256 pixel: the backdrop and ground keep moving during GET READY
    private int speed;
    private int score;
    private int rings;
    private int ringsTaken;
    private int tourIndex;
    private long zoneShownAt;
    private int invulnerable;
    private int deathY;         // 1/256 pixel, for the crash fall
    private int deathVel;
    private int superTicks;
    private String crashCause = "";

    Run(Mode mode, long seed, int firstGate, boolean practice) {
        this.mode = mode;
        this.seed = seed;
        this.firstGate = firstGate;
        this.practice = practice;
        course.reset(seed, firstGate);
        tourIndex = Zone.tourIndex(firstGate);
        speed = zoneAhead().speed();
        flight.start(START_Y);
        zoneShownAt = 0;
    }

    /** Advances one tick. {@code pressed}: A, B, C or a click went down this tick. */
    void tick(boolean pressed) {
        events.clear();
        ticks++;
        switch (phase) {
            case READY -> ready(pressed);
            case FLYING, HURT -> fly(pressed);
            case CRASHED -> crashed();
            case OVER -> { }
        }
    }

    private void ready(boolean pressed) {
        scenery += speed;
        if (pressed) {
            phase = Phase.FLYING;
            phaseTick = ticks;
            flight.start(readyBobY());
            fly(true);
        }
    }

    private void fly(boolean pressed) {
        int target = zoneAhead().speed();
        speed += Integer.signum(target - speed) * Math.min(SPEED_EASE, Math.abs(target - speed));
        course.advance(speed);
        scenery += speed;

        if (phase == Phase.HURT) {
            // Knocked back: no control until the hop peaks, as the player is in the ROM's hurt routine.
            flight.ballistic(HURT_GRAVITY, 0x10);
            if (flight.yVel() >= 0) {
                phase = Phase.FLYING;
                flight.refill(Flight.FULL_TIMER);
            }
        } else {
            boolean flap = flight.tick(pressed, (ticks & 1) == 1, 0);
            if (!mode.sonicRules) {
                flight.refill(Flight.FULL_TIMER);   // the native sample's endless flight
            }
            if (flap) events.add(new Event(Type.FLAP, 0, TAILS_X, flight.y()));
        }
        if (Flight.buzzDue(ticks)) {
            events.add(new Event(Type.SFX, flight.tired() ? Sounds.SFX_FLY_TIRED : Sounds.SFX_FLYING, 0, 0));
        }
        if (invulnerable > 0) invulnerable--;
        if (superTicks > 0) {
            flight.refill(Flight.FULL_TIMER);
            if (--superTicks == 0) {
                events.add(new Event(Type.SUPER_END, 0, TAILS_X, flight.y()));
                events.add(new Event(Type.MUSIC, zoneShown().music(), 0, 0));
            }
        }

        passGates();
        switchZoneWhenClear();
        collectRings();
        moveSpills();
        if (phase == Phase.FLYING || phase == Phase.HURT) {
            checkHits();
        }
    }

    private void passGates() {
        int tailsX = tailsCourseX();
        int ty = flight.y();
        for (Course.Gate gate : course.gates()) {
            if (gate.smashed || gate.judged) continue;
            if (tailsX + TOUCH_HALF_WIDTH > gate.x && tailsX - TOUCH_HALF_WIDTH < gate.right()) {
                int room = Math.min(ty - TOUCH_HALF_HEIGHT - gate.top(), gate.bottom() - ty - TOUCH_HALF_HEIGHT);
                gate.closest = Math.min(gate.closest, room);
            } else if (tailsX - TOUCH_HALF_WIDTH >= gate.right()) {
                gate.judged = true;
                // A negative margin means Tails touched the pillar (a hit, or a pass while blinking).
                if (gate.closest >= 0 && gate.closest <= CLOSE_CALL && phase == Phase.FLYING) {
                    events.add(new Event(Type.CLOSE_CALL, gate.closest, TAILS_X, ty));
                }
            }
        }
        for (Course.Gate gate : course.gates()) {
            if (!gate.passed && gate.x + ZoneArt.PILLAR_WIDTH / 2 < tailsX) {
                gate.passed = true;
                score++;
                events.add(new Event(Type.GATE, score, gate.right() - course.scrollX(), gate.centre));
                events.add(new Event(Type.SFX, Sounds.SFX_GATE, 0, 0));
                if (score % Zone.GATES == 0) {
                    events.add(new Event(Type.ZONE_CLEAR, score, 0, 0));
                }
                if ((firstGate + score) % (Zone.GATES * Zone.TOUR_LENGTH) == 0) {
                    goSuper();
                }
            }
        }
    }

    /** Shows the next zone once the last pillar of the current one has left the screen. */
    private void switchZoneWhenClear() {
        int ahead = Zone.tourIndex(nextGateNumber());
        if (ahead == tourIndex) return;
        for (Course.Gate gate : course.gates()) {
            if (gate.tourIndex == tourIndex && gate.right() >= course.scrollX()) return;
        }
        tourIndex = ahead;
        zoneShownAt = ticks;
        Zone zone = zoneShown();
        events.add(new Event(Type.ZONE, tourIndex, 0, 0));
        if (superTicks == 0) {
            events.add(new Event(Type.MUSIC, zone.music(), 0, 0));   // Super Tails' theme plays on; its end brings the zone's
        }
    }

    private void collectRings() {
        int tx = tailsCourseX();
        int ty = flight.y();
        for (Course.Ring ring : course.rings()) {
            if (ring.takenAt < 0 && Math.abs(ring.x - tx) < TOUCH_HALF_WIDTH + 6
                    && Math.abs(ring.y - ty) < TOUCH_HALF_HEIGHT + 6 && phase != Phase.CRASHED) {
                ring.takenAt = ticks;
                takeRing(ring.x - course.scrollX(), ring.y);
            }
        }
        for (Spill spill : spills) {
            if (spill.takenAt < 0 && invulnerable <= INVULNERABLE_FRAMES - SPILL_CATCH_DELAY
                    && Math.abs((spill.x >> 8) - tx) < TOUCH_HALF_WIDTH + 6
                    && Math.abs((spill.y >> 8) - ty) < TOUCH_HALF_HEIGHT + 6) {
                spill.takenAt = ticks;
                takeRing((spill.x >> 8) - course.scrollX(), spill.y >> 8);
            }
        }
    }

    private void takeRing(int screenX, int y) {
        rings++;
        ringsTaken++;
        if (mode.sonicRules) flight.refill(RING_FLIGHT);
        events.add(new Event(Type.RING, rings, screenX, y));
        events.add(new Event(Type.SFX, Sounds.SFX_RING, 0, 0));
        if (rings % 100 == 0) {
            events.add(new Event(Type.MUSIC, Sounds.MUS_EXTRA_LIFE, 0, 0));
        }
    }

    /**
     * Spilled rings fly and bounce as {@code Obj_Bouncing_Ring} does: $18 gravity a frame, and
     * on every eighth frame (staggered by ring) a falling ring that has reached the floor loses
     * a quarter of its speed and bounces. They vanish after 255 frames
     * ({@code Ring_spill_anim_counter}).
     */
    private void moveSpills() {
        for (Spill spill : spills) {
            spill.x += spill.xVel;
            spill.y += spill.yVel;
            spill.yVel += 0x18;
            if (spill.yVel >= 0 && ((ticks + spill.index) & 7) == 0 && (spill.y >> 8) + 8 >= Course.GROUND_Y) {
                spill.y = (Course.GROUND_Y - 8) << 8;
                spill.yVel -= spill.yVel >> 2;
                spill.yVel = -spill.yVel;
            }
        }
        spills.removeIf(s -> ticks - s.born > 0xFF || (s.takenAt >= 0 && ticks - s.takenAt > 24));
    }

    /**
     * A whole lap of the tour turns Tails Super for twenty seconds, as fifty rings and the emeralds
     * do in the game ({@code Tails_Transform}: the transformation sound and the invincibility
     * theme): he glows, Flickies circle him, and pillars and badniks burst when he hits them.
     */
    private void goSuper() {
        superTicks = SUPER_FRAMES;
        events.add(new Event(Type.SUPER, 0, TAILS_X, flight.y()));
        events.add(new Event(Type.SFX, Sounds.SFX_SUPER, 0, 0));
        events.add(new Event(Type.MUSIC, Sounds.MUS_INVINCIBLE, 0, 0));
    }

    private void checkHits() {
        int tx = tailsCourseX();
        int ty = flight.y();
        if (ty + Y_RADIUS >= Course.GROUND_Y) {
            if (superTicks > 0) {
                flight.setY(Course.GROUND_Y - Y_RADIUS - 1);
                flight.setYVel(HURT_SPEED);
                return;
            }
            hit("THE GROUND", true);
            return;
        }
        if (superTicks > 0) {
            smash(tx, ty);
            return;
        }
        if (invulnerable > 0) return;
        for (Course.Gate gate : course.gates()) {
            if (!gate.smashed && tx + TOUCH_HALF_WIDTH > gate.x && tx - TOUCH_HALF_WIDTH < gate.right()
                    && (ty - TOUCH_HALF_HEIGHT < gate.top() || ty + TOUCH_HALF_HEIGHT > gate.bottom())) {
                hit("A PILLAR", false);
                return;
            }
        }
        for (Course.Badnik badnik : course.badniks()) {
            if (badnik.destroyedAt < 0 && Math.abs(badnik.x - tx) < TOUCH_HALF_WIDTH + 10
                    && Math.abs(badnik.y(ticks) - ty) < TOUCH_HALF_HEIGHT + 10) {
                hit("A BADNIK", false);
                return;
            }
        }
    }

    /** Super Tails bursts whatever he touches. */
    private void smash(int tx, int ty) {
        for (Course.Gate gate : course.gates()) {
            if (!gate.smashed && tx + TOUCH_HALF_WIDTH > gate.x && tx - TOUCH_HALF_WIDTH < gate.right()
                    && (ty - TOUCH_HALF_HEIGHT < gate.top() || ty + TOUCH_HALF_HEIGHT > gate.bottom())) {
                gate.smashed = true;
                events.add(new Event(Type.SMASH, gate.tourIndex, gate.x + ZoneArt.PILLAR_WIDTH / 2 - course.scrollX(),
                        ty < gate.top() + TOUCH_HALF_HEIGHT ? gate.top() : gate.bottom()));
                events.add(new Event(Type.SFX, Sounds.SFX_EXPLODE, 0, 0));
            }
        }
        for (Course.Badnik badnik : course.badniks()) {
            if (badnik.destroyedAt < 0 && Math.abs(badnik.x - tx) < TOUCH_HALF_WIDTH + 10
                    && Math.abs(badnik.y(ticks) - ty) < TOUCH_HALF_HEIGHT + 10) {
                badnik.destroyedAt = ticks;
                events.add(new Event(Type.SMASH, -1, badnik.x - course.scrollX(), badnik.y(ticks)));
                events.add(new Event(Type.SFX, Sounds.SFX_EXPLODE, 0, 0));
            }
        }
    }

    private void hit(String cause, boolean ground) {
        if (mode.sonicRules && rings > 0) {
            spill();
            invulnerable = INVULNERABLE_FRAMES;
            phase = Phase.HURT;
            flight.setYVel(HURT_SPEED);
            if (ground) flight.setY(Course.GROUND_Y - Y_RADIUS - 1);
            events.add(new Event(Type.HIT, 0, TAILS_X, flight.y()));
            return;
        }
        if (ground && invulnerable > 0 && mode.sonicRules) {
            // Still blinking from the last hit with no rings to spare: the ground stays solid.
            flight.setY(Course.GROUND_Y - Y_RADIUS - 1);
            flight.setYVel(HURT_SPEED / 2);
            return;
        }
        crashCause = cause;
        phase = Phase.CRASHED;
        phaseTick = ticks;
        deathY = flight.y() << 8;
        deathVel = DEATH_SPEED;
        events.add(new Event(Type.CRASH, score, TAILS_X, flight.y()));
        events.add(new Event(Type.SFX, Sounds.SFX_DEATH, 0, 0));
    }

    /**
     * Scatters up to 32 rings in the pattern {@code Obj_Bouncing_Ring} uses: pairs mirrored left
     * and right, the angle stepping by $10 from $88, sixteen rings at speed shift 2 and then
     * sixteen more at shift 1.
     */
    private void spill() {
        int count = Math.min(rings, 32);
        int d4 = 0x288;            // high byte: speed shift; low byte: angle
        int xVel = 0;
        int yVel = 0;
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                double radians = (d4 & 0xFF) * Math.PI * 2 / 256;   // GetSineCosine's 256-step angle
                int shift = d4 >> 8;
                xVel = (int) Math.round(Math.sin(radians) * 256) << shift;
                yVel = (int) Math.round(Math.cos(radians) * 256) << shift;
                int angle = (d4 & 0xFF) + 0x10;                     // addi.b #$10,d4
                d4 = (d4 & 0xFF00) | (angle & 0xFF);
                if (angle > 0xFF) {                                  // carry: subi.w #$80,d4
                    d4 -= 0x80;
                    if (d4 < 0) d4 = 0x288;                          // borrow: start over
                }
            } else {
                xVel = -xVel;                                        // the pair's mirror image
            }
            spills.add(new Spill(tailsCourseX() << 8, flight.y() << 8, xVel + speed, yVel, i, ticks));
        }
        rings = 0;
        events.add(new Event(Type.SPILL, count, TAILS_X, flight.y()));
        events.add(new Event(Type.SFX, Sounds.SFX_RING_LOSS, 0, 0));
    }

    private void crashed() {
        if (ticks - phaseTick < 12) return;            // a beat of stillness: the hit lands
        deathY += deathVel;
        deathVel += GRAVITY;
        moveSpills();
        if ((deathY >> 8) > 224 + 48) {
            phase = Phase.OVER;
            phaseTick = ticks;
        }
    }

    // ---- what the scene reads ----

    Phase phase() { return phase; }
    long ticks() { return ticks; }
    long phaseTicks() { return ticks - phaseTick; }
    int score() { return score; }
    int rings() { return rings; }
    int ringsTaken() { return ringsTaken; }
    int speed() { return speed; }
    int invulnerable() { return invulnerable; }
    int superTicks() { return superTicks; }
    boolean isSuper() { return superTicks > 0; }
    String crashCause() { return crashCause; }
    List<Event> events() { return events; }
    List<Spill> spills() { return spills; }
    int tourIndex() { return tourIndex; }
    long zoneShownAt() { return zoneShownAt; }
    double sceneryX() { return scenery / 256.0; }
    int lap() { return Zone.lap(Math.max(firstGate, nextGateNumber() - 1)); }

    Zone zoneShown() { return Zone.tour().get(tourIndex); }

    /** The zone of the next gate to pass, with its lap's pace. */
    Zone zoneAhead() {
        int n = nextGateNumber();
        return Zone.at(n).forLap(Zone.lap(n));
    }

    int nextGateNumber() {
        for (Course.Gate gate : course.gates()) {
            if (!gate.passed) return gate.number;
        }
        return firstGate + score;
    }

    int tailsCourseX() { return course.scrollX() + TAILS_X; }

    /** Tails' centre row on screen. */
    int tailsY() {
        return switch (phase) {
            case READY -> readyBobY();
            case CRASHED, OVER -> deathY >> 8;
            default -> flight.y();
        };
    }

    /** His animation: flying (or tired) while in the air, hurt while knocked back, death once crashed. */
    int tailsAnimation() {
        return switch (phase) {
            case READY -> TailsArt.ANIM_FLY;
            case HURT -> TailsArt.ANIM_HURT;
            case CRASHED, OVER -> TailsArt.ANIM_DEATH;
            default -> flight.animation();
        };
    }

    /** Whether Tails is drawn this tick: he blinks while invulnerable, as {@code Tails_Display} draws only when bit 2 of the timer is set. */
    boolean tailsVisible() {
        return invulnerable == 0 || (invulnerable & 4) != 0;
    }

    private int readyBobY() {
        return START_Y + (int) Math.round(Math.sin(ticks * Math.PI * 2 / 64) * 4);
    }

    /** Debug and test hooks: jump the score (and course) forward as if gates had been passed. */
    void debugSetRings(int value) { rings = Math.max(0, value); }
}
