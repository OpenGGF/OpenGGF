package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.RewindRecreateContext;

/**
 * MGZ2 level-results variant.
 *
 * <p>ROM: the MGZ floating capsule starts {@code Obj_LevelResults} from
 * {@code sub_86984} while {@code Flying_carrying_Sonic_flag} can still be set.
 * The results object must therefore leave Sonic/Tails' carry control intact;
 * {@code loc_6D104}'s palette fade and level transition run after results.
 */
public class Mgz2ResultsScreenObjectInstance extends S3kResultsScreenObjectInstance {

    public Mgz2ResultsScreenObjectInstance(PlayerCharacter character, int act) {
        super(character, act);
    }

    // Probe-only constructor used by RewindRecreatable generic recreate.
    private Mgz2ResultsScreenObjectInstance() {
        this(PlayerCharacter.SONIC_AND_TAILS, 0);
    }

    @Override
    public Mgz2ResultsScreenObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new Mgz2ResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
    }

    @Override
    protected boolean shouldRestorePlayerControlsOnExit() {
        return false;
    }
}
