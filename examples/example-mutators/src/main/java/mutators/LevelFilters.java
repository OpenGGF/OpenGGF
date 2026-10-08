package mutators;

import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Small no-option examples still declare independent toggle lifecycle boundaries. */
public final class LevelFilters {
    private LevelFilters() { }
    public static MutatorDefinition checkpoints() {
        return toggle("no-checkpoints", "No Checkpoints", "Death returns to the act start. Posts and independent stage-entry/return positions still work.",
                MutatorScope.LOAD, new MutatorPolicy.NoCheckpoints());
    }
    public static MutatorDefinition rings() {
        return toggle("no-rings", "No Rings", "Remove main-level rings and ring rewards on the next full load. Special/bonus puzzles keep their rings.",
                MutatorScope.LOAD, new MutatorPolicy.NoRings());
    }
    public static MutatorDefinition specialStages() {
        return toggle("no-special-stages", "No Special Stages", "Prevent new special-stage or sanctuary entry. An entry already accepted finishes normally.",
                MutatorScope.LIVE, new MutatorPolicy.NoSpecialStages());
    }
    public static MutatorDefinition bonusStages() {
        return toggle("no-bonus-stages", "No Bonus Stages", "Prevent new Sonic 3 & Knuckles bonus-stage entry independently of checkpoint banking and special stages.",
                MutatorScope.LIVE, new MutatorPolicy.NoBonusStages());
    }
    private static MutatorDefinition toggle(String id, String title, String help, MutatorScope scope, MutatorPolicy policy) {
        return new MutatorDefinition(id, title, help, scope, scope, List.of(), Set.of(policy.capability()),
                options -> List.of(policy));
    }
}
