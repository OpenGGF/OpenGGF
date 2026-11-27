package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Knuckles flies with the leftover flight from the Sonic 3 Winter CES cartridge instead of gliding. */
public final class BetaFlight {
    private BetaFlight() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("beta-flight", "Beta Flight",
                "Knuckles flies like the unused object in the Dec 1993 Sonic 3 CES cartridge: press jump to flap, no time limit. Steering only turns him; he keeps his launch speed.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.Checkbox("fixed-steering", "Fixed Steering",
                        "Left and right speed him up and slow him down in flight. Off keeps the cartridge's steering, which only turns him.",
                        MutatorScope.LIVE, false)),
                Set.of(MutatorCapability.BETA_FLIGHT),
                values -> List.of(new MutatorPolicy.BetaFlight(values.checkbox("fixed-steering"))));
    }
}
