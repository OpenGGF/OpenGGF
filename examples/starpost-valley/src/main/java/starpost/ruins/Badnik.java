package starpost.ruins;

import java.util.List;

/**
 * A Sonic 1 badnik in a chamber, with a short, readable version of its ROM behaviour (speeds and
 * ranges from the s1disasm objects named on each kind). Engine-free: {@link #update} moves it;
 * the play screen asks {@link #body} and {@link #harm} for contact and draws it.
 *
 * <p>Positions are centres. A bop (a rolling or jumping Sonic touching {@link #body}) pops a
 * badnik that {@link #bopable()}; the Bomb cannot be popped (its collision is col_hurt), the
 * Caterkiller's body and the Orbinaut's spike balls always hurt, and the Yadrin's spiky back
 * hurts from above.
 */
public final class Badnik {
    public static final int CATERKILLER = 0;   // 78 Badnik - Caterkiller
    public static final int BATBRAIN = 1;      // 55 Badnik - Basaran
    public static final int BUZZ_BOMBER = 2;   // 22 Badnik - Buzz Bomber
    public static final int YADRIN = 3;        // 50 Badnik - Yadrin
    public static final int JAWS = 4;          // 2C Badnik - Jaws
    public static final int BURROBOT = 5;      // 2D Badnik - Burrobot
    public static final int ORBINAUT = 6;      // 60 Badnik - Orbinaut
    public static final int BOMB = 7;          // 5F Badnik - Walking Bomb
    public static final int BALL_HOG = 8;      // 1E Badnik - Ball Hog
    public static final int KINDS = 9;

    private static final float GRAVITY_18 = 0x18 / 256f;

    /** A projectile or piece a badnik throws: missile, cannonball, shrapnel or spike ball. */
    public static final class Shot {
        public static final int MISSILE = 0;
        public static final int CANNONBALL = 1;
        public static final int SHRAPNEL = 2;
        public static final int SPIKEBALL = 3;
        public final int kind;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public int life;
        public boolean alive = true;

        public Shot(int kind, float x, float y, float vx, float vy, int life) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
        }

        /** Moves the shot; cannonballs bounce on floors, shrapnel and spike balls fall. */
        public void update(Chamber chamber) {
            x += vx;
            y += vy;
            if (kind == CANNONBALL || kind == SHRAPNEL) {
                vy += GRAVITY_18;
            }
            if (kind == CANNONBALL && vy > 0 && chamber.floor(Math.round(x), Math.round(y) + 6)) {
                y = chamber.floorBelow(Math.round(x), Math.round(y) - 4) - 6;
                vy = -0x300 / 256f;   // CBall: bounce at -$300
            }
            if (kind != CANNONBALL && chamber.solid(Math.round(x), Math.round(y))) {
                alive = false;
            }
            if (--life <= 0 || y > chamber.height + 32 || x < -32 || x > chamber.width + 32) {
                alive = false;
            }
        }

        public int half() {
            return kind == SHRAPNEL || kind == SPIKEBALL ? 4 : 6;
        }
    }

    public final int kind;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public boolean facingLeft = true;
    public boolean alive = true;
    public int state;
    public int timer;
    public int age;
    /** The Orbinaut's spike balls' angle; the Bomb's fuse; the Batbrain's roost. */
    public float angle;
    public float homeY;
    /** A walker's pause after turning round. */
    private int pause;

    public Badnik(int kind, float x, float y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.homeY = y;
        this.timer = kind == BOMB ? 3 * 60 - 1 : 60;
    }

    /** The badniks of a band (S1's own zones): Marble, Labyrinth, Scrap Brain. */
    public static int[] roster(int band) {
        return switch (band) {
            case RuinsRules.MARBLE -> new int[] {BATBRAIN, CATERKILLER, BUZZ_BOMBER, YADRIN};
            case RuinsRules.LABYRINTH -> new int[] {JAWS, BURROBOT, ORBINAUT};
            default -> new int[] {CATERKILLER, BOMB, BALL_HOG};
        };
    }

    public static String name(int kind) {
        return switch (kind) {
            case CATERKILLER -> "CATERKILLER";
            case BATBRAIN -> "BATBRAIN";
            case BUZZ_BOMBER -> "BUZZ BOMBER";
            case YADRIN -> "YADRIN";
            case JAWS -> "JAWS";
            case BURROBOT -> "BURROBOT";
            case ORBINAUT -> "ORBINAUT";
            case BOMB -> "BOMB";
            default -> "BALL HOG";
        };
    }

    /** Whether the kind walks on floors (placed on a floor spot with room to patrol). */
    public static boolean walker(int kind) {
        return kind == CATERKILLER || kind == YADRIN || kind == BURROBOT || kind == BOMB || kind == BALL_HOG;
    }

    /**
     * Collision half sizes (width, height) from each object's obColType: Caterkiller head and
     * Batbrain and Orbinaut 16x16, Buzz Bomber 48x24, Yadrin 40x32, Jaws 32x24, Burrobot and
     * Ball Hog 24x36, Bomb 24x24.
     */
    public static int halfWidth(int kind) {
        return switch (kind) {
            case BUZZ_BOMBER -> 24;
            case YADRIN -> 20;
            case JAWS -> 16;
            case BURROBOT, BALL_HOG, BOMB -> 12;
            default -> 8;
        };
    }

    public static int halfHeight(int kind) {
        return switch (kind) {
            case BUZZ_BOMBER, JAWS, BOMB -> 12;
            case YADRIN -> 16;
            case BURROBOT, BALL_HOG -> 18;
            default -> 8;
        };
    }

    /**
     * Where a badnik of this kind lives near a reached floor spot ({@code {x, y}}, centre), or
     * null when it cannot: walkers stand on the floor, flyers hover clear of the walls, the
     * Batbrain hangs under a ceiling, Jaws swims under water.
     */
    public static int[] home(int kind, Chamber chamber, int x, int floorY) {
        switch (kind) {
            case BATBRAIN -> {
                for (int y = floorY - 48; y > floorY - 200 && y > 0; y--) {
                    if (chamber.ceiling(x, y)) {
                        return new int[] {x, y + 9};
                    }
                }
                return null;
            }
            case BUZZ_BOMBER, ORBINAUT -> {
                int cy = floorY - (kind == BUZZ_BOMBER ? 72 : 48);
                return open(chamber, x, cy, halfWidth(kind) + 8, halfHeight(kind) + 8) ? new int[] {x, cy} : null;
            }
            case JAWS -> {
                int cy = floorY - 28;
                return chamber.waterY != Chamber.NO_WATER && cy - 16 > chamber.waterY
                        && open(chamber, x, cy, 24, 16) ? new int[] {x, cy} : null;
            }
            default -> {
                return new int[] {x, floorY - halfHeight(kind)};
            }
        }
    }

    private static boolean open(Chamber chamber, int cx, int cy, int hw, int hh) {
        for (int y = cy - hh; y <= cy + hh; y += 4) {
            for (int x = cx - hw; x <= cx + hw; x += 4) {
                if (chamber.floor(x, y) || y < 0 || x < 0 || x >= chamber.width) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean bopable() {
        return kind != BOMB;
    }

    /** The body's box {cx, cy, halfWidth, halfHeight} (the Caterkiller's: its head). */
    public float[] body() {
        return new float[] {x, y, halfWidth(kind), halfHeight(kind)};
    }

    /**
     * Parts that hurt whatever Sonic is doing, as boxes {cx, cy, halfWidth, halfHeight}: the
     * Caterkiller's three body segments and the Orbinaut's four spike balls.
     */
    public void harm(List<float[]> out) {
        if (kind == CATERKILLER) {
            for (int i = 1; i <= 3; i++) {
                float[] s = segment(i);
                out.add(new float[] {s[0], s[1], 8, 8});
            }
        } else if (kind == ORBINAUT) {
            for (int i = 0; i < 4; i++) {
                float[] b = ball(i);
                out.add(new float[] {b[0], b[1], 4, 4});
            }
        }
    }

    /** A Caterkiller segment's centre (1-3 trail the head, rising and falling as it crawls). */
    public float[] segment(int i) {
        float dir = facingLeft ? 1 : -1;
        float hump = (float) Math.max(0, Math.sin((age / 16.0 + i * 0.7) * Math.PI)) * (state == 1 ? 4 : 0);
        return new float[] {x + dir * 12 * i, y - hump};
    }

    /** An Orbinaut spike ball's centre, 16 pixels out. */
    public float[] ball(int i) {
        double a = angle + i * Math.PI / 2;
        return new float[] {x + (float) Math.cos(a) * 16, y + (float) Math.sin(a) * 16};
    }

    /** One frame of behaviour, given where Sonic's centre is. */
    public void update(Chamber chamber, float sx, float sy, List<Shot> shots) {
        age++;
        switch (kind) {
            case CATERKILLER -> crawl(chamber);
            case BATBRAIN -> batbrain(chamber, sx, sy);
            case BUZZ_BOMBER -> buzzBomber(sx, sy, shots);
            case YADRIN -> patrol(chamber, 0x100 / 256f, 60);
            case JAWS -> jaws(chamber);
            case BURROBOT -> burrobot(chamber, sx);
            case ORBINAUT -> orbinaut(chamber);
            case BOMB -> bomb(chamber, sx, sy, shots);
            default -> ballHog(sx, shots);
        }
    }

    /** Walks at {@code speed}, turning (after a pause) at walls and ledges. */
    private void patrol(Chamber chamber, float speed, int pauseAfterTurn) {
        if (pause > 0) {
            pause--;
            vx = 0;
            return;
        }
        vx = facingLeft ? -speed : speed;
        float nx = x + vx;
        int feet = Math.round(y + halfHeight(kind));
        int ahead = Math.round(nx + (facingLeft ? -halfWidth(kind) : halfWidth(kind)));
        int floor = chamber.floorBelow(ahead, feet - 8);
        boolean wall = chamber.solid(ahead, feet - 12) || ahead < 4 || ahead > chamber.width - 4;
        if (wall || floor > feet + 12) {
            facingLeft = !facingLeft;
            pause = pauseAfterTurn;
            return;
        }
        x = nx;
        int under = chamber.floorBelow(Math.round(x), feet - 8);
        if (Math.abs(under - feet) <= 8) {
            y = under - halfHeight(kind);
        }
    }

    /** Caterkiller: crawls in pulses (its body bunches and stretches), up to $C0 a frame. */
    private void crawl(Chamber chamber) {
        state = (age / 16) % 2;
        if (state == 1) {
            patrol(chamber, 0xC0 / 256f, 8);
        }
    }

    /**
     * Basaran: hangs until Sonic passes within $80 pixels below, drops toward him ($18 gravity),
     * flies across at $100, then climbs back to its roost.
     */
    private void batbrain(Chamber chamber, float sx, float sy) {
        switch (state) {
            case 0 -> {
                if (Math.abs(sx - x) < 0x80 && sy > y && sy - y < 0xC0) {
                    state = 1;
                    facingLeft = sx < x;
                }
            }
            case 1 -> {
                vy += GRAVITY_18;
                y += vy;
                x += (sx - x) * 0.02f;
                if (y >= sy - 8 || chamber.floor(Math.round(x), Math.round(y) + 10)) {
                    state = 2;
                    vy = 0;
                    vx = facingLeft ? -1 : 1;
                    timer = 64;
                }
            }
            case 2 -> {
                x += vx;
                if (--timer <= 0 || chamber.solid(Math.round(x + vx * 10), Math.round(y))) {
                    state = 3;
                }
            }
            default -> {
                vy = Math.max(-2, vy - GRAVITY_18);
                y += vy;
                if (y <= homeY || chamber.ceiling(Math.round(x), Math.round(y) - 10)) {
                    y = Math.max(y, homeY);
                    vy = 0;
                    state = 0;
                    homeY = y;
                }
            }
        }
    }

    /**
     * Buzz Bomber: patrols at $100 for two seconds each way; when Sonic is within $60 pixels
     * across, it stops, aims and fires a missile down at ($200, $200), then flies on.
     */
    private void buzzBomber(float sx, float sy, List<Shot> shots) {
        if (state == 1) {
            if (--timer == 20) {
                float dir = facingLeft ? -1 : 1;
                shots.add(new Shot(Shot.MISSILE, x + dir * 16, y + 14, dir * 2, 2, 120));
            }
            if (timer <= 0) {
                state = 2;
                timer = 90;
            }
            return;
        }
        if (state == 2 && --timer <= 0) {
            state = 0;
        }
        x += facingLeft ? -1 : 1;
        if (Math.abs(x - homeYAsX()) > 0x80) {
            facingLeft = x > homeYAsX();
        }
        if (state == 0 && Math.abs(sx - x) < 0x60 && sy > y) {
            state = 1;
            timer = 50;
            facingLeft = sx < x;
        }
    }

    /** The Buzz Bomber patrols around where it was placed. */
    private float patrolCentre = Float.NaN;

    private float homeYAsX() {
        if (Float.isNaN(patrolCentre)) {
            patrolCentre = x;
        }
        return patrolCentre;
    }

    /** Jaws: swims at $40 a frame, turning about every two seconds or at a wall, under water only. */
    private void jaws(Chamber chamber) {
        if (--timer <= 0 || chamber.solid(Math.round(x + (facingLeft ? -18 : 18)), Math.round(y))
                || x < 20 || x > chamber.width - 20) {
            facingLeft = !facingLeft;
            timer = 128;
        }
        x += facingLeft ? -0x40 / 256f : 0x40 / 256f;
        y = homeY + (float) Math.sin(age / 20.0) * 2;
        if (chamber.waterY != Chamber.NO_WATER && y - 12 < chamber.waterY) {
            y = chamber.waterY + 12;
        }
    }

    /** Burrobot: walks at $80, and every four seconds or so jumps (-$400, $18 gravity). */
    private void burrobot(Chamber chamber, float sx) {
        if (state == 1) {
            vy += GRAVITY_18;
            y += vy;
            int feet = Math.round(y + halfHeight(kind));
            int floor = chamber.floorBelow(Math.round(x), feet - Math.max(8, Math.round(vy) + 2));
            if (vy > 0 && feet >= floor) {
                y = floor - halfHeight(kind);
                vy = 0;
                state = 0;
                facingLeft = sx < x;
            }
            return;
        }
        patrol(chamber, 0x80 / 256f, 30);
        if (age % 255 == 0 || age % 255 == 128 && Math.abs(sx - x) < 0x60) {
            state = 1;
            vy = -0x400 / 256f;
        }
    }

    /** Orbinaut: drifts at $40, its four spike balls circling. */
    private void orbinaut(Chamber chamber) {
        angle += 0.05f;
        if (--timer <= 0 || chamber.floor(Math.round(x + (facingLeft ? -26 : 26)), Math.round(y))
                || x < 30 || x > chamber.width - 30) {
            facingLeft = !facingLeft;
            timer = 160;
        }
        x += facingLeft ? -0x40 / 256f : 0x40 / 256f;
    }

    /**
     * Walking Bomb: walks at $10 for 25.6 seconds, waits 3; within 96 pixels of Sonic it lights
     * a fuse of 143 frames and bursts into four pieces of shrapnel (Bom_ShrSpeed).
     */
    private void bomb(Chamber chamber, float sx, float sy, List<Shot> shots) {
        if (state == 2) {
            if (--timer <= 0) {
                alive = false;
                shots.add(new Shot(Shot.SHRAPNEL, x, y, -0x200 / 256f, -0x300 / 256f, 180));
                shots.add(new Shot(Shot.SHRAPNEL, x, y, -0x100 / 256f, -0x200 / 256f, 180));
                shots.add(new Shot(Shot.SHRAPNEL, x, y, 0x200 / 256f, -0x300 / 256f, 180));
                shots.add(new Shot(Shot.SHRAPNEL, x, y, 0x100 / 256f, -0x200 / 256f, 180));
            }
            return;
        }
        if (Math.abs(sx - x) < 96 && Math.abs(sy - y) < 96) {
            state = 2;
            timer = 2 * 60 + 24 - 1;
            return;
        }
        if (state == 0) {
            if (--timer <= 0) {
                state = 1;
                timer = 25 * 60 + 36 - 1;
                facingLeft = !facingLeft;
            }
        } else {
            if (--timer <= 0) {
                state = 0;
                timer = 3 * 60 - 1;
            }
            patrol(chamber, 0x10 / 256f, 0);
        }
    }

    /** Ball Hog: faces Sonic and every two seconds hops and drops a cannonball that bounces at $100. */
    private void ballHog(float sx, List<Shot> shots) {
        facingLeft = sx < x;
        timer--;
        if (timer == 12) {
            state = 1;
        }
        if (timer <= 0) {
            state = 0;
            timer = 120;
            float dir = facingLeft ? -1 : 1;
            Shot ball = new Shot(Shot.CANNONBALL, x + dir * 12, y, dir * 0x100 / 256f, 0, 3 * 60);
            shots.add(ball);
        }
    }

    /** Whether this badnik has hatched its spiky top (Yadrin): a hit from above hurts. */
    public boolean spikyTop() {
        return kind == YADRIN;
    }
}
