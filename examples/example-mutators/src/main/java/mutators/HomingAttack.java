package mutators;

import com.openggf.mods.mutators.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Jump again in the air to home in on a target; native contact, breakage and defeat decide the hit. */
public final class HomingAttack {
    private HomingAttack() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("homing-attack", "Homing Attack",
                "Jump again in the air to home in on the nearest boss, badnik or breakable. No target: forward air dash.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.Choice("mode", "Mode",
                                "Homing strikes like a jump. Instagib adds a fire trail, one-hit bosses and enemies blown apart.",
                                MutatorScope.LIVE, "homing", List.of("homing", "instagib")),
                        new MutatorOption.Checkbox("sonic", "Sonic", "Sonic can home in while leading the team.",
                                MutatorScope.LIVE, true, "Characters:"),
                        new MutatorOption.Checkbox("tails", "Tails", "Tails can home in while leading; with no target he still flies.",
                                MutatorScope.LIVE, false, "Characters:"),
                        new MutatorOption.Checkbox("knuckles", "Knuckles", "Knuckles can home in while leading; with no target he still glides.",
                                MutatorScope.LIVE, false, "Characters:")),
                Set.of(MutatorCapability.HOMING_ATTACK),
                values -> List.of(new MutatorPolicy.HomingAttack(
                        values.choice("mode").equals("instagib") ? MutatorPolicy.HomingMode.INSTAGIB : MutatorPolicy.HomingMode.HOMING,
                        characters(values))));
    }
    private static Set<MutatorPolicy.HomingCharacter> characters(MutatorOptions values) {
        var selected = EnumSet.noneOf(MutatorPolicy.HomingCharacter.class);
        if (values.checkbox("sonic")) selected.add(MutatorPolicy.HomingCharacter.SONIC);
        if (values.checkbox("tails")) selected.add(MutatorPolicy.HomingCharacter.TAILS);
        if (values.checkbox("knuckles")) selected.add(MutatorPolicy.HomingCharacter.KNUCKLES);
        return Set.copyOf(selected);
    }
}
