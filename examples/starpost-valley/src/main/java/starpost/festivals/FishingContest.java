package starpost.festivals;

import java.util.function.IntConsumer;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;

/**
 * The seam for the Ice Cap Festival's fishing contest (design doc §18). The fishing system sets
 * {@link Festivals#fishingContest} when it installs; while it is unset the festival's contest is
 * the snowboard run down the winter slopes instead.
 */
public interface FishingContest {
    /**
     * Runs the contest for {@code seconds} on the lake from {@code play} (pushing or going to its
     * own screen), then calls {@code done} with the farmer's catch score and returns to
     * {@code play} with {@code shell.go(play)}.
     */
    void start(Shell shell, PlayScreen play, int seconds, IntConsumer done);

    /** Frost's record catch score to beat, in the contest's own units. */
    default int recordToBeat() {
        return 40;
    }
}
