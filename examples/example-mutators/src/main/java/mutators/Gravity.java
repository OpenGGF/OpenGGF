package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** The normal dry fall acceleration owner; jump impulse/hurt/water/scripted motion stay native. */
public final class Gravity {
    private Gravity() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("gravity", "Dry Gravity", "Change ordinary dry airborne acceleration, retaining native integration and jump impulse.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Fall acceleration", "25..200 percent; native at 100. No change to the initial jump impulse.",
                        MutatorScope.LIVE, 100, 25, 200, 5, "%")),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                values -> List.of(new MutatorPolicy.DrySonicGravity(values.integer("percent"))));
    }
}
