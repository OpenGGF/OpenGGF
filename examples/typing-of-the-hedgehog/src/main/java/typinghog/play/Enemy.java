package typinghog.play;

import java.util.ArrayList;
import java.util.List;
import typinghog.core.Prompt;

/**
 * One badnik on the course, owned by the controller. A badnik has one or more typed parts
 * (body, segments, orbiting balls, shell) each with its own word; it dies when every part is
 * typed. Positions are world pixels in 8.8 fixed point; it advances only on rows where the
 * native world advances, so slow motion slows it with everything else.
 */
public final class Enemy {
    public enum State { APPROACH, RECOIL, DYING, GONE }

    /** One typed piece of a badnik. */
    public static final class Part {
        final Prompt prompt;
        final Stagehand.Visual visual;
        int targetId = -1;
        int dx;
        int dy;
        boolean typed;
        /** Parts typed in order: this part becomes typeable only once {@code after} is typed. */
        Part after;
        Part(Prompt prompt, Stagehand.Visual visual) { this.prompt = prompt; this.visual = visual; }
        public Prompt prompt() { return prompt; }
        public int targetId() { return targetId; }
        public boolean typed() { return typed; }
    }

    final Species species;
    final List<Part> parts = new ArrayList<>();
    final boolean elite;
    final boolean airborne;
    State state = State.APPROACH;
    int x;
    int y;
    int speed;
    int direction;
    int tick;
    int stateRows;
    int homeY;

    Enemy(Species species, boolean elite, boolean airborne, int x, int y, int speed, int direction) {
        this.species = species;
        this.elite = elite;
        this.airborne = airborne;
        this.x = x << 8;
        this.y = y << 8;
        this.homeY = y;
        this.speed = speed;
        this.direction = direction;
    }

    public int x() { return x >> 8; }
    public int y() { return y >> 8; }
    public List<Part> parts() { return parts; }
    public boolean elite() { return elite; }
    public State state() { return state; }
    public Species species() { return species; }

    boolean allTyped() {
        for (Part part : parts) if (!part.typed) return false;
        return true;
    }

    /** Rows until this badnik reaches {@code targetX}; the field's urgency. */
    int rowsToReach(int targetX) {
        int distance = Math.abs(targetX - x());
        return speed <= 0 ? 9999 : (distance << 8) / speed;
    }
}
