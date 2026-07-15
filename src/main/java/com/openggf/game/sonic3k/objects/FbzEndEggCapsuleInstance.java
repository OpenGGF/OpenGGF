package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

/** FBZ2's fixed-position generic upright Obj_EggCapsule. */
public final class FbzEndEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable {
    public FbzEndEggCapsuleInstance(int x, int y) { super(x, y, "FBZEndEggCapsule"); }
    private FbzEndEggCapsuleInstance() { this(0, 0); }

    @Override protected void updateAfterResultsStarted(int frameCounter, PlayableEntity player) {
        if (services().gameState() != null && services().gameState().isEndOfLevelFlag()) {
            services().gameState().setEndOfLevelFlag(false);
            services().gameState().setEndOfLevelActive(false);
        }
    }
}
