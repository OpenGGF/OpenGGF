package infinite;

import com.openggf.game.rewind.ScriptedRewind;
import java.util.function.Supplier;

/**
 * CONTINUE's way back: the engine's own rewind, run by the course rather than the rewind key.
 * It winds the run back (fast through the death and menu, at a brisk tape speed through play)
 * to the first moment at least a second before the death that makes a fair restart, which the
 * course decides ({@link CourseController#resumableHere()}).
 *
 * <p>Not part of rewind: the rewind decides where the restored game stops, and the spent life
 * and the ring lives already awarded must outlast the restore. Reset by every level load.
 */
public final class CourseRewind implements ScriptedRewind {
    // Mod design: rewound play goes back at 3x, the death and menu at 12x so a long wait is quick.
    static final int PLAY_STEPS = 3, DEAD_STEPS = 12;
    // Mod design: wind back at least one second of living play, so the restart is not the frame
    // before an unavoidable hit.
    static final int MIN_PLAY_STEPS = 60;
    /** Phases: no CONTINUE, winding back, wound back (awaiting the course's next update). */
    static final int IDLE = 0, REWINDING = 1, LANDED = 2;
    private final Supplier<CourseController> course;
    private int phase;
    private int livesAfter;
    private int playSteps;
    private boolean atTarget;
    // The most ring lives (100-ring crossings) this run has been awarded, which a rewound
    // count must exceed before it earns another.
    private int ringLivesAwarded;

    public CourseRewind(Supplier<CourseController> course) { this.course = course; }

    /** A level load (a fresh run or RESTART) starts over. */
    public void reset() {
        phase = IDLE;
        livesAfter = 0;
        playSteps = 0;
        atTarget = false;
        ringLivesAwarded = 0;
    }

    /** CONTINUE: wind back, leaving {@code spareLives} once it lands. */
    public void start(int spareLives) {
        phase = REWINDING;
        livesAfter = Math.max(0, spareLives);
        playSteps = 0;
        atTarget = false;
    }

    public boolean rewinding() { return phase == REWINDING; }
    public boolean landed() { return phase == LANDED; }
    /** Spare lives once the life is spent. */
    public int livesAfter() { return livesAfter; }
    /** Whether the rewind stopped at a fair restart, rather than at the oldest history. */
    public boolean atTarget() { return atTarget; }
    /** The course has taken over from the restored state. */
    public void finish() { phase = IDLE; }

    /**
     * The new ring lives due for a run that has crossed {@code crossings} hundreds of rings, and
     * records them. Rings won back after a rewind do not pay again.
     */
    public int awardRingLives(int crossings) {
        int due = Math.max(0, crossings - ringLivesAwarded);
        ringLivesAwarded = Math.max(ringLivesAwarded, crossings);
        return due;
    }

    @Override public boolean requested() { return phase == REWINDING; }

    @Override public int stepsThisFrame() {
        var controller = course.get();
        return controller == null || controller.resumeAlive() ? PLAY_STEPS : DEAD_STEPS;
    }

    @Override public boolean reachedTarget() {
        var controller = course.get();
        if (controller == null || !controller.resumeAlive()) return false;
        return ++playSteps > MIN_PLAY_STEPS && controller.resumableHere();
    }

    @Override public void ended(boolean reached) {
        phase = LANDED;
        atTarget = reached;
    }
}
