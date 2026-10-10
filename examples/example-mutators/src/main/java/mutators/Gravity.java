package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** The normal dry fall acceleration owner; jump impulse/hurt/water/scripted motion stay native. */
public final class Gravity {
    private Gravity() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("gravity", "Gravity", "Scale Sonic's dry fall acceleration. Jumps, water, hurt and scripted motion stay native.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Fall acceleration", "25 to 200 percent of native fall acceleration (gold mark: 100). Jump impulse stays native.",
                        MutatorScope.LIVE, 100, 25, 200, 5, "%")),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                values -> List.of(new MutatorPolicy.DrySonicGravity(values.integer("percent"))));
    }
}
