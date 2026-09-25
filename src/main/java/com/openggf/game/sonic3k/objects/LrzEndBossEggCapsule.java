package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.*;

/** loc_79998's inverted Obj_EggCapsule, retaining the results owner as completion authority. */
public final class LrzEndBossEggCapsule extends AbstractS3kFloatingEndEggCapsuleInstance
        implements ZeroArgRewindRecreatable {
    public LrzEndBossEggCapsule() { super(0,0,"LRZEndBossEggCapsule",true); }
    @Override protected void onParentOpen() {
        S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct().setCapsuleOpened(true);
    }
    @Override protected boolean shouldStartResults(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // sub_868F8 waits for living P1 to land; Set_PlayerEndingPose clears velocity afterward.
        return !player.getAir() && !player.getDead();
    }
    @Override protected void spawnResultsScreen() { spawnFreeChild(this::createResultsScreen); }
    @Override protected AbstractObjectInstance createResultsScreen() {
        return ObjectConstructionContext.construct(services(),()->new Results(getPlayerCharacter(),services().currentAct()));
    }
    public static final class Results extends S3kResultsScreenObjectInstance {
        Results(com.openggf.game.PlayerCharacter character,int act) { super(character,act); }
        private Results() { super(true); }
        @Override protected boolean shouldRestorePlayerControlsOnExit() { return false; }
        @Override protected boolean shouldRestoreCameraBoundsOnExit(int zone,int act) { return false; }
        @Override public Results recreateForRewind(RewindRecreateContext context) {
            return ObjectConstructionContext.construct(context.objectServices(),Results::new);
        }
    }
}
