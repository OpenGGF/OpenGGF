package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

/** Touch_EnemyNormal retains native status7 and mapping until the explosion's own INIT. */
public final class MonkeyDudeExplosionObjectInstance extends ExplosionObjectInstance {
    private boolean nativeInitPending=true;
    private boolean heldFirstUpdate;
    private int displacedMapping;

    /** Pure shell; all native pre-INIT state and shared explosion state are captured. */
    public MonkeyDudeExplosionObjectInstance(ObjectSpawn spawn,ObjectServices services) {
        super(spawn,services);
    }
    MonkeyDudeExplosionObjectInstance(int x,int y,ObjectServices services,int points,int displacedMapping) {
        super(0x27,x,y,services.renderManager(),
                AbstractS3kBadnikInstance.S3K_DESTRUCTION_CONFIG.animalFactory(),
                AbstractS3kBadnikInstance.S3K_DESTRUCTION_CONFIG.pointsFactory(),points,false);
        this.displacedMapping=displacedMapping;
    }
    @Override public void delayFirstUpdateForPassedSlot() {
        super.delayFirstUpdateForPassedSlot();heldFirstUpdate=true;
    }
    @Override public void update(int vIntRunCount,PlayableEntity main) {
        boolean held=heldFirstUpdate;
        super.update(vIntRunCount,main);
        heldFirstUpdate=false;
        if (!held)nativeInitPending=false;
    }
    int nativeMappingFrame() {
        // Touch_EnemyNormal writes only code/routine/status; loc_1E626 replaces
        // the old mapping byte when this physical SST receives its first INIT.
        return nativeInitPending?displacedMapping:currentMappingFrame();
    }
}
