package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Selective immutable presentation. Native admission, collisions and sound still run. */
public final class Stealth {
    private Stealth() { }
    public static MutatorDefinition definition() {
        return new MutatorDefinition("stealth", "Stealth", "Hide the player sprite; attached effects are a separate option. Hits, HUD and sound remain.",
                MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.Choice("target", "Target", "Leader or whole team. In this solo-Sonic slice both choices hide Sonic.",
                                MutatorScope.LIVE, "leader", List.of("leader", "all_team")),
                        new MutatorOption.Checkbox("effects", "Hide attached effects", "Also hide shield, spindash dust and invincibility stars; skid puffs stay.",
                                MutatorScope.LIVE, false)),
                Set.of(MutatorCapability.PLAYER_STEALTH),
                values -> List.of(new MutatorPolicy.PlayerStealth(true, true,
                        values.choice("target").equals("leader") ? MutatorPolicy.Target.LEADER : MutatorPolicy.Target.ALL_TEAM,
                        values.checkbox("effects"))));
    }
}
