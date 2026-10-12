package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Native ROM pixels plus reviewed anatomical masks; gameplay dimensions never change. */
public final class BigHead {
    private BigHead() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("big-head", "Big Head Mode",
                "Enlarge selected heads; body and hitbox stay native. Mix targets independently.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent", "Head size", "100 to 200 percent about the neck. With Scale with rings, this is the size at 100 rings.",
                                MutatorScope.LIVE, 150, 100, 200, 10, "%"),
                        new MutatorOption.Checkbox("rings", "Scale with rings", "Normal size at 0 rings, growing to Head size at 100 rings. Losing rings shrinks it.",
                                MutatorScope.LIVE, false),
                        new MutatorOption.Checkbox("player", "Player", "Affect the character you control. Stealth also hides its head.",
                                MutatorScope.LIVE, true, "Afflicts:"),
                        new MutatorOption.Checkbox("sidekicks", "Sidekicks", "Affect team companions, independently of the player.",
                                MutatorScope.LIVE, false, "Afflicts:",
                                new MutatorOption.LegacyChoice("target", "all_team", Set.of("leader", "all_team"))),
                        new MutatorOption.Checkbox("badniks", "Badniks", "Affect robot enemy heads; projectiles and collision stay native.",
                                MutatorScope.LIVE, false, "Afflicts:"),
                        new MutatorOption.Checkbox("bosses", "Bosses", "Affect boss heads; vehicles, weapons and collision stay native.",
                                MutatorScope.LIVE, false, "Afflicts:")),
                Set.of(MutatorCapability.BIG_HEAD), values -> List.of(new MutatorPolicy.BigHead(values.integer("percent"),
                        targets(values),
                        values.checkbox("rings"))));
    }
    private static Set<MutatorPolicy.HeadTarget> targets(MutatorOptions values) {
        var selected = java.util.EnumSet.noneOf(MutatorPolicy.HeadTarget.class);
        if (values.checkbox("player")) selected.add(MutatorPolicy.HeadTarget.PLAYER);
        if (values.checkbox("sidekicks")) selected.add(MutatorPolicy.HeadTarget.SIDEKICKS);
        if (values.checkbox("badniks")) selected.add(MutatorPolicy.HeadTarget.BADNIKS);
        if (values.checkbox("bosses")) selected.add(MutatorPolicy.HeadTarget.BOSSES);
        return Set.copyOf(selected);
    }
}
