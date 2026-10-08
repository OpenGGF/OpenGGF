package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.*;

/** loc_79998's inverted Obj_EggCapsule, retaining the results owner as completion authority. */
public final class LrzEndBossEggCapsule extends AbstractS3kFloatingEndEggCapsuleInstance
        implements ZeroArgRewindRecreatable {
    public LrzEndBossEggCapsule() { super(0,0,"LRZEndBossEggCapsule",true); }
    @Override protected int patrolCameraX() {
        var camera = services().camera();
        var state = S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow();
        // Obj_EggCapsule loc_8657A/loc_8662A uses Camera_X+$A0 for spawn
        // and Camera_X+$30..$110 for patrol. In the ROM, Camera_X is $A00.
        // Our widescreen arena centres that native window by shifting the
        // visible camera left; using it here shifted the capsule into the mask.
        // Recover the native origin only while that presentation mode is active.
        return state.centerNativeArenaCamera()
                ? com.openggf.camera.NativeViewportFraming.nativeLeft(camera.getX(), camera.getWidth())
                : camera.getX();
    }
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
