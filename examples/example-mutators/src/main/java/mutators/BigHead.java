package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Native ROM pixels plus reviewed anatomical masks; gameplay dimensions never change. */
public final class BigHead {
    private BigHead() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("big-head", "Big Head Mode",
                "Enlarge Sonic's head while his body and hitbox stay native. Curled and unreviewed art stays normal.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Head size", "100 to 200 percent about the native neck anchor. Feet, body and collisions stay unchanged.",
                                MutatorScope.LIVE, 150, 100, 200, 10, "%"),
                        new MutatorOption.Choice("target", "Target", "Leader or all team members with supported Sonic art. Stealth hides the enlarged head too.",
                                MutatorScope.LIVE, "leader", List.of("leader", "all_team"))),
                Set.of(MutatorCapability.BIG_HEAD), values -> List.of(new MutatorPolicy.BigHead(values.integer("percent"),
                        values.choice("target").equals("leader") ? MutatorPolicy.Target.LEADER : MutatorPolicy.Target.ALL_TEAM)));
    }
}
