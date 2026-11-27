package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Starting rings, time, score and lives, each written by the native counter owner at its boundary. */
public final class StartingState {
    private StartingState() { }

    /** Every act start and life restart; the grant never pays a 100/200-ring extra life. */
    public static MutatorDefinition rings() {
        return new MutatorDefinition("start-rings", "Start with Rings",
                "Begin every act and every life with rings. Reaching 100 or 200 this way gives no extra life.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("rings", "Rings", "Rings added at the start of each act and life.",
                                MutatorScope.LIVE, 50, 0, StartingStatePolicy.MAX_RINGS, 1, " rings"),
                        new MutatorOption.IntegerSlider("delay", "Delay", "Seconds of play before the rings arrive. 0 gives them at once.",
                                MutatorScope.LIVE, 0, 0, 300, 1, " s")),
                Set.of(MutatorCapability.STARTING_RINGS),
                values -> List.of(new StartingStatePolicy.Rings(values.integer("rings"), values.integer("delay"))));
    }

    /** Fresh acts and restarts from the act start; checkpoints keep their saved time. */
    public static MutatorDefinition time() {
        return new MutatorDefinition("start-time", "Start with Time",
                "Begin each act with time already on the clock. A checkpoint restart keeps the checkpoint's time.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("seconds", "Time on the clock",
                        "Seconds at the act start: 300 is 5:00, 570 is 9:30, 30 seconds before time over.",
                        MutatorScope.LIVE, 300, 1, StartingStatePolicy.MAX_TIME_SECONDS, 1, " s")),
                Set.of(MutatorCapability.STARTING_TIME),
                values -> List.of(new StartingStatePolicy.Time(values.integer("seconds"))));
    }

    public static MutatorDefinition score() {
        return new MutatorDefinition("start-score", "Start with Score",
                "Begin a new game with points already scored. Applies when a new game starts.",
                MutatorScope.LAUNCH, MutatorScope.LAUNCH,
                List.of(new MutatorOption.IntegerSlider("score", "Score", "Points at the start of a new game, in steps of 10.",
                        MutatorScope.LAUNCH, 0, 0, StartingStatePolicy.MAX_SCORE, 10, "")),
                Set.of(MutatorCapability.STARTING_SCORE),
                values -> List.of(new StartingStatePolicy.Score(values.integer("score"))));
    }

    public static MutatorDefinition lives() {
        return new MutatorDefinition("start-lives", "Start with Lives",
                "Begin a new game with a chosen number of lives. Applies when a new game starts.",
                MutatorScope.LAUNCH, MutatorScope.LAUNCH,
                List.of(new MutatorOption.IntegerSlider("lives", "Lives", "Lives at the start of a new game, up to the counter's 99.",
                        MutatorScope.LAUNCH, 3, 1, StartingStatePolicy.MAX_LIVES, 1, "")),
                Set.of(MutatorCapability.STARTING_LIVES),
                values -> List.of(new StartingStatePolicy.Lives(values.integer("lives"))));
    }
}
