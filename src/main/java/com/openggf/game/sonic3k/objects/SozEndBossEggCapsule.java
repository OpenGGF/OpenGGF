package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
/** Native loc_77848 AllocateObject Obj_EggCapsule at $5360,$720. */
public final class SozEndBossEggCapsule extends AbstractS3kUprightEggCapsuleInstance implements SpawnCoordinateRewindRecreatable {
    public SozEndBossEggCapsule(int x,int y){super(x,y,"SOZEndBossEggCapsule");}
    @Override protected S3kResultsScreenObjectInstance createResultsScreen(PlayerCharacter character,int act){return new SozEndBossResults(character,act);}
    // soz_completerun allocates Obj_LevelResults in slot 6, behind the capsule: its init runs next pass.
    private static final class SozEndBossResults extends S3kResultsScreenObjectInstance {
        SozEndBossResults(PlayerCharacter character,int act){super(character,act);}
        private SozEndBossResults(){super(true);}
        @Override protected boolean shouldRestorePlayerControlsOnExit(){return false;}
        @Override protected boolean shouldRestoreCameraBoundsOnExit(int zone,int act){return false;}
        @Override public SozEndBossResults recreateForRewind(com.openggf.level.objects.RewindRecreateContext context){
            return com.openggf.level.objects.ObjectConstructionContext.construct(context.objectServices(),SozEndBossResults::new);
        }
    }
}
