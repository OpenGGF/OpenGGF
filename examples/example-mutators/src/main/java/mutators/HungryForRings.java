package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** A grace clock, then a steady ring drain; running low for too long is fatal. */
public final class HungryForRings {
    private HungryForRings() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("hungry-for-rings", "Hungry for Rings",
                "After a grace period you lose rings over time. Stay low on rings too long and you die.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("grace", "Grace period", "Seconds of play at each act and life start before rings drain.",
                                MutatorScope.LIVE, 30, 0, 300, 1, " s"),
                        new MutatorOption.IntegerSlider("loss", "Rings lost", "Rings taken at each drain step.",
                                MutatorScope.LIVE, 1, 1, 100, 1, " rings"),
                        new MutatorOption.IntegerSlider("period", "Drain every", "Seconds between drain steps.",
                                MutatorScope.LIVE, 1, 1, 60, 1, " s"),
                        new MutatorOption.IntegerSlider("threshold", "Starving at", "Holding this many rings or fewer starts the red countdown.",
                                MutatorScope.LIVE, 0, 0, 999, 1, " rings"),
                        new MutatorOption.IntegerSlider("starve", "Starve after", "Seconds at or under the starving count before you die.",
                                MutatorScope.LIVE, 5, 1, 60, 1, " s")),
                Set.of(MutatorCapability.RING_HUNGER),
                values -> List.of(new RingHungerPolicy(values.integer("grace"), values.integer("loss"),
                        values.integer("period"), values.integer("threshold"), values.integer("starve"))));
    }
}
