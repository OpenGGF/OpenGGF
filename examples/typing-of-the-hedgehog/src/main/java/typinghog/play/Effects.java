package typinghog.play;

import java.util.ArrayList;
import java.util.List;

/**
 * Short-lived feedback: ring sparks flying from Sonic to the letters he types, the ROM explosion,
 * freed animal and score sprite of a kill, and the letter-rings a hit spills. ROM-art effects are
 * {@link Stagehand} visuals; sparks and letter-rings are drawn by the {@link Hud} as fills. All
 * advance with the native world.
 */
public final class Effects {
    /** A spark in flight, world pixels. */
    static final class Spark {
        int x0, y0, x1, y1, age;
        Spark(int x0, int y0, int x1, int y1) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; }
    }

    /** A spilled ring carrying one letter, world pixels in 8.8. */
    static final class LetterRing {
        int x, y, vx, vy, life;
        final char letter;
        LetterRing(int x, int y, int vx, int vy, char letter) {
            this.x = x << 8; this.y = y << 8; this.vx = vx; this.vy = vy; this.letter = letter; this.life = SPILL_LIFE;
        }
        int px() { return x >> 8; }
        int py() { return y >> 8; }
    }

    /** A ROM-art effect with a scripted life. */
    static final class Prop {
        final Stagehand.Visual visual;
        final Kind kind;
        int age;
        int vx, vy;
        int x, y;
        Prop(Stagehand.Visual visual, Kind kind) { this.visual = visual; this.kind = kind; x = visual.x << 8; y = visual.y << 8; }
    }

    enum Kind { EXPLOSION, ANIMAL, POINTS }

    /** Spilled rings last as long as the ROM's scattered rings (a 255-frame timer). */
    static final int SPILL_LIFE = 255;
    static final int SPARK_ROWS = 6;
    static final int EXPLOSION_FRAMES = 5;
    static final int EXPLOSION_DELAY = 4;
    static final String LETTERS = "asdfghjklqwertyuiopzxcvbnm";

    final List<Spark> sparks = new ArrayList<>();
    final List<LetterRing> rings = new ArrayList<>();
    final List<Prop> props = new ArrayList<>();
    int impactRows;

    boolean idle() {
        return props.isEmpty() && sparks.isEmpty();
    }

    void spark(Course course, int tx, int ty) {
        sparks.add(new Spark(course.playerX(), course.playerY() - 8, tx, ty));
    }

    void impact(Course course, int x, int y) {
        impactRows = 10;
    }

    void kill(Course course, int x, int y, int points) {
        var stagehand = course.stagehand();
        var boom = stagehand.add("explosion", x, y);
        boom.bucket = 5;
        props.add(new Prop(boom, Kind.EXPLOSION));
        var animal = stagehand.add("animal", x, y);
        Prop hop = new Prop(animal, Kind.ANIMAL);
        hop.vx = course.random().nextBoolean() ? 0x180 : -0x180;
        hop.vy = -0x400;
        props.add(hop);
        var score = stagehand.add("points", x, y - 8);
        score.bucket = 6;
        score.frame = switch (points) { case 100 -> 0; case 200 -> 1; case 500 -> 2; default -> 3; };
        props.add(new Prop(score, Kind.POINTS));
    }

    /** Spills {@code count} letter-rings from Sonic in the ROM's alternating fan. */
    void spill(Course course, int count) {
        int px = course.playerX(), py = course.playerY();
        var random = course.random();
        for (int i = 0; i < count; i++) {
            int angle = 0x88 + (i / 2) * 0x10;
            double radians = (angle & 0xFF) * Math.PI / 128;
            int speed = i < 16 ? 0x400 : 0x200;
            int vx = (int) (Math.cos(radians) * speed) * ((i & 1) == 0 ? 1 : -1);
            int vy = (int) (Math.sin(radians) * speed);
            char letter = LETTERS.charAt(random.nextInt(LETTERS.length()));
            rings.add(new LetterRing(px, py, vx, vy, letter));
        }
    }

    /** Collects the nearest spilled ring carrying {@code codePoint}; true when one was collected. */
    boolean collectRing(Course course, int codePoint) {
        char c = Character.toLowerCase((char) codePoint);
        LetterRing best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (LetterRing ring : rings) {
            if (ring.letter != c) continue;
            int d = Math.abs(ring.px() - course.playerX()) + Math.abs(ring.py() - course.playerY());
            if (d < bestDistance) { best = ring; bestDistance = d; }
        }
        if (best == null) return false;
        rings.remove(best);
        course.addRings(1);
        course.card().keystroke();
        sparks.add(new Spark(best.px(), best.py(), course.playerX(), course.playerY()));
        return true;
    }

    void advance(Course course) {
        if (impactRows > 0) impactRows--;
        sparks.removeIf(spark -> ++spark.age > SPARK_ROWS);
        int floor = course.playerY() + 19;
        for (LetterRing ring : rings) {
            ring.vy += 0x18;
            ring.x += ring.vx;
            ring.y += ring.vy;
            if (ring.py() > floor - 8 && ring.vy > 0) {
                ring.y = (floor - 8) << 8;
                ring.vy = -ring.vy * 3 / 4;
            }
            ring.life--;
        }
        rings.removeIf(ring -> ring.life <= 0);
        var stagehand = course.stagehand();
        for (Prop prop : List.copyOf(props)) {
            prop.age++;
            switch (prop.kind) {
                case EXPLOSION -> {
                    prop.visual.frame = Math.min(EXPLOSION_FRAMES - 1, prop.age / EXPLOSION_DELAY);
                    if (prop.age >= EXPLOSION_FRAMES * EXPLOSION_DELAY) remove(stagehand, prop);
                }
                case ANIMAL -> {
                    prop.vy += 0x18;
                    prop.x += prop.vx;
                    prop.y += prop.vy;
                    if ((prop.y >> 8) > floor - 8 && prop.vy > 0) prop.vy = -0x300;
                    prop.visual.x = prop.x >> 8;
                    prop.visual.y = prop.y >> 8;
                    prop.visual.flipX = prop.vx > 0;
                    prop.visual.frame = prop.vy < 0 ? 1 : 0;
                    if (prop.age > 120) remove(stagehand, prop);
                }
                case POINTS -> {
                    if (prop.age < 24) prop.visual.y--;
                    if (prop.age > 40) remove(stagehand, prop);
                }
            }
        }
    }

    private void remove(Stagehand stagehand, Prop prop) {
        stagehand.remove(prop.visual);
        props.remove(prop);
    }
}
