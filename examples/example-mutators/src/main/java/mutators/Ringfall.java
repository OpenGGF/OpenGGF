package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** A spill plan is resolved before native allocation; inventory loss stays independent. */
public final class Ringfall {
    private Ringfall() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("ringfall", "Ringfall Manipulator",
                "Choose how many recoverable rings you drop when hurt. You still lose your full ring inventory.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Rings dropped", "Percentage of the native scatter (at most 32), or of every ring held with Drop full inventory.",
                                MutatorScope.LIVE, 100, 10, 100, 5, "%"),
                        new MutatorOption.Checkbox("full", "Drop full inventory", "Remove the native 32-ring limit: every ring you held can bounce out to be recollected.",
                                MutatorScope.LIVE, false),
                        new MutatorOption.Checkbox("capped", "Use a hard cap", "Limit the recoverable scatter even when the percentage would produce more.",
                                MutatorScope.LIVE, false),
                        new MutatorOption.IntegerSlider("cap", "Maximum dropped", "Used only while the hard-cap checkbox is on. Applies to each new spill.",
                                MutatorScope.LIVE, 32, 1, 32, 1, " rings")),
                Set.of(MutatorCapability.RINGFALL),
                values -> List.of(new MutatorPolicy.Ringfall(values.integer("percent"),
                        values.checkbox("capped") ? values.integer("cap") : 0, values.checkbox("full"))));
    }
}
