package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;

/** Obj_DEZ3_Boss_Fireball: invisible trail emitter, then floor-running emitter. */
final class DezFinalFireball extends DezFinalBossSprite implements RewindRecreatable {
    private int parentSlot=-1;
    private int routine;
    private int timer;
    private int animation;
    private int offsetX;
    private int offsetY;
    private DezFinalFireball(ObjectSpawn spawn) { super(spawn,"DEZFinalFireball"); }
    DezFinalFireball(DezFinalBossSprite parent) {
        this(new ObjectSpawn(0,0,0,0,0,false,0)); parentSlot=parent.getSlotIndex();
    }
    @Override public DezFinalFireball recreateForRewind(RewindRecreateContext context) { return new DezFinalFireball(context.spawn()); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        if(isDestroyed()) return;
        var parent=slotOccupant(services(),parentSlot);
        int px=parent==null?0:parent.getX(),py=parent==null?0:parent.getY();
        if(routine==0) {
            routine=1; offsetX=0xB0; offsetY=-0x18; services().playSfx(Sonic3kSfx.BLAST.id);
        } else if(routine==1) {
            timer=(short)(timer-1);
            if(timer<0) {
                if(getY()>=0xC7) {
                    routine=2; writeY(0xCF); animation=0; timer=2;
                    writeX(px+offsetX); emit(2); updateDynamicSpawn(getX(),getY());
                    return; // loc_809CA removes the caller return; no sub_81024 Y refresh.
                }
                timer=1; animation=(animation+1)%3;
                // Shipped loc_809A6 indexes with the NEW undoubled d0. The doubled
                // old index in d1 is unused: the pairs are (8,-4),(-4,4),(8,8),
                // not the apparent table pairs (8,8),(-4,4),(12,12).
                offsetX=(short)(offsetX+(byte)romByte(0x809EE+animation));
                offsetY=(short)(offsetY+(byte)romByte(0x809EF+animation));
                emit(0);
            }
        } else {
            timer=(short)(timer-1);
            if(timer<0) {
                timer=2; offsetX=(short)(offsetX+8);
                if((offsetX&0xFFFF)>=0x300) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
                int subtype=animation==0?1:2;
                writeY(getY()+(animation==0?8:-8)); animation^=1; emit(subtype);
            }
            writeX(px+offsetX); updateDynamicSpawn(getX(),getY()); return;
        }
        writeX(px+offsetX); writeY(py+offsetY); updateDynamicSpawn(getX(),getY());
    }
    private void emit(int subtype) {
        // sub_8106E: one forward allocation; failed emissions consume the cadence.
        spawnChild(()->new Fragment(parentSlot,getX(),getY(),offsetX,offsetY,subtype));
    }
    private static ObjectInstance slotOccupant(ObjectServices services,int slot) {
        for(var candidate:services.objectManager().getActiveObjects()) {
            if(candidate instanceof AbstractObjectInstance object && !object.isDestroyed() && object.getSlotIndex()==slot) return object;
        }
        return null;
    }

    /** loc_80A3A: ROM-script flame; subtype 0 follows XY, floor flames follow X only. */
    static final class Fragment extends DezFinalBossSprite implements RewindRecreatable,TouchResponseProvider {
        private int parentSlot=-1;
        private int offsetX;
        private int offsetY;
        private int animationCursor;
        private int animationTimer;
        private int script;
        private boolean initialized;
        private Fragment(ObjectSpawn spawn) { super(spawn,"DEZFinalFlame"); }
        Fragment(int parentSlot,int x,int y,int dx,int dy,int subtype) {
            this(new ObjectSpawn(x,y,0,subtype,0,false,0));
            this.parentSlot=parentSlot; offsetX=dx; offsetY=dy;
        }
        @Override public Fragment recreateForRewind(RewindRecreateContext context) { return new Fragment(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) {
            visible=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if(!initialized) {
                initialized=true; priority=6; halfWidth=halfHeight=0x10; frame=6; highPriority=true;
                flipX=spawn.subtype()==2;
                script=switch(spawn.subtype()) { case 0->0x80AD4; case 1->0x80AEE; case 2->0x80B08;
                    default->throw new IllegalStateException("DEZ fire subtype "+spawn.subtype()); };
            } else {
                animationTimer=(byte)(animationTimer-1);
                if(animationTimer<0) {
                    animationCursor=(animationCursor+4)&0xFF;
                    int next=romByte(script+animationCursor);
                    if((next&0x80)!=0) { pendingDelete=true; status|=0x80; }
                    else {
                        frame=next; animationTimer=romByte(script+animationCursor+1);
                        offsetX=(short)(offsetX+(byte)romByte(script+animationCursor+2));
                        offsetY=(short)(offsetY+(byte)romByte(script+animationCursor+3));
                    }
                }
            }
            // Native trails hold SST addresses, not lifetime-owning Java references.
            // Root deletion clears its position; reuse reads the new occupant.
            var parent=slotOccupant(services(),parentSlot);
            writeX((parent==null?0:parent.getX())+offsetX);
            if(spawn.subtype()==0) writeY((parent==null?0:parent.getY())+offsetY);
            visible=true; updateDynamicSpawn(getX(),getY());
        }
        @Override public int getCollisionFlags() { return visible?0x8B:0; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) { return TouchResponseProfile.fromProvider(this, multiRegionSource); }
        @Override public int getShieldReactionFlags() { return 0x10; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return visible; }
    }
}
