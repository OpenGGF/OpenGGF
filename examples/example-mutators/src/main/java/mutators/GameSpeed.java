package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Fractional whole native step budgets, with optional soundtrack speed/pitch. */
public final class GameSpeed {
    private GameSpeed() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("game-speed", "Game Speed Modifier",
                "Quarter speed up to four times speed. Physics run in whole native steps.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Game speed", "25 to 400 percent. Jump presses wait for a native step during slow motion.",
                                MutatorScope.LIVE, 100, 25, 400, 25, "%"),
                        new MutatorOption.Checkbox("audio", "Audio follows speed", "Change music and sound speed/pitch with play. Off keeps audio at its normal rate.",
                                MutatorScope.LIVE, false)),
                Set.of(MutatorCapability.GAMEPLAY_SPEED),
                values -> List.of(new MutatorPolicy.GameplaySpeed(values.integer("percent"), values.checkbox("audio"))));
    }
}
