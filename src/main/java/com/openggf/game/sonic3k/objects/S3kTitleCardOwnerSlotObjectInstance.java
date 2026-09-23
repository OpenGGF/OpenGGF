package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/**
 * SST occupant for an event-allocated in-level {@code Obj_TitleCard}. The title card itself is
 * presented by the title-card manager, but the native owner holds its slot until loc_2D86E's
 * allocations run and {@code Delete_Current_Sprite} frees it (sonic3k.asm:62263-62302).
 */
public final class S3kTitleCardOwnerSlotObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean requestsInLevelCard;
    private int cardZone;
    private int cardAct;

    /** Native dynamically allocated Obj_TitleCard with $3E set for an in-level card. */
    public static S3kTitleCardOwnerSlotObjectInstance inLevel(int zone,int act) {
        var owner=new S3kTitleCardOwnerSlotObjectInstance(new ObjectSpawn(0,0,0,0,0,false,0));
        owner.requestsInLevelCard=true; owner.cardZone=zone; owner.cardAct=act;
        return owner;
    }
    public S3kTitleCardOwnerSlotObjectInstance(ObjectSpawn spawn) { super(spawn, "TitleCardOwnerSlot"); }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if(requestsInLevelCard) {
            requestsInLevelCard=false;
            services().levelManager().requestInLevelTitleCard(cardZone,cardAct,true,
                    com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE);
        }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {}
}
