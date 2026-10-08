package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Bounded native defeat rebound; decorative explosions and other hazards stay native. */
public final class ViolentExplosions {
    private ViolentExplosions() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("violent-explosions", "Violent Explosions",
                "Badnik defeats kick you back harder vertically. Decorative blasts, bosses and unrelated hazards stay native.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Defeat knockback", "150 to 300 percent of the resolved native vertical rebound. Horizontal speed stays native.",
                                MutatorScope.LIVE, 150, 150, 300, 25, "%"),
                        new MutatorOption.IntegerSlider("cap", "Vertical speed cap", "Bound the amplified vertical rebound in pixels per native tick.",
                                MutatorScope.LIVE, 12, 1, 32, 1, " px/tick")),
                Set.of(MutatorCapability.DEFEAT_KNOCKBACK),
                values -> List.of(new MutatorPolicy.DefeatKnockback(values.integer("percent"), values.integer("cap") << 8)));
    }
}
