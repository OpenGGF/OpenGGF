package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Selective immutable presentation. Native admission, collisions and sound still run. */
public final class Stealth {
    private Stealth() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("stealth", "Stealth", "Hide the player body and appendage; attached effects are a separate choice. Objects, HUD and sound stay visible/audible.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.Choice("target", "Target", "Leader or all team. This certified slice is solo Sonic, so both select that player.",
                                MutatorScope.LIVE, "leader", List.of("leader", "all_team")),
                        new MutatorOption.Checkbox("effects", "Hide attached effects", "Also hide shield, spinning dust and invincibility stars. Deposited world puffs remain.",
                                MutatorScope.LIVE, false)),
                Set.of(MutatorCapability.PLAYER_STEALTH),
                values -> List.of(new MutatorPolicy.PlayerStealth(true, true,
                        values.choice("target").equals("leader") ? MutatorPolicy.Target.LEADER : MutatorPolicy.Target.ALL_TEAM,
                        values.checkbox("effects"))));
    }
}
