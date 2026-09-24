package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;

/** loc_807BC: charging sprite, foreground laser publication and mouth release. */
final class DezFinalBeam extends DezFinalBossSprite implements RewindRecreatable {
    private DezFinalBossSprite parent;
    private DezFinalMouth mouth;
    private int routine;
    private int timer;
    private int offsetX;
    private int offsetY;
    private int animationCursor;
    private int animationTimer;
    private DezFinalBeam(ObjectSpawn spawn) { super(spawn,"DEZFinalBeam"); }
    DezFinalBeam(DezFinalBossSprite parent,DezFinalMouth mouth) {
        // AllocateObject clears the SST: loc_807BC does not copy root position.
        this(new ObjectSpawn(0,0,0,0,0,false,0)); this.parent=parent; this.mouth=mouth;
    }
    @Override public DezFinalBeam recreateForRewind(RewindRecreateContext context) { return new DezFinalBeam(context.spawn()); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(routine==0) {
            routine=1; priority=3; halfWidth=0x10; halfHeight=0x24; highPriority=false;
            frame=0x1A; offsetX=0x58; offsetY=4; services().playSfx(Sonic3kSfx.CHARGING.id);
        }
        switch(routine) {
            case 1 -> { particles(vIntRunCount); animate(0x8135A); track(); visible=true; }
            case 2 -> {
                timer=(short)(timer-1);
                if(timer<0) {
                    routine=3; offsetX+=0x34; offsetY+=4; animationCursor=animationTimer=0;
                    services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id); laser(vIntRunCount);
                } else {
                    // Below $28, d0 still contains the even dispatch address $80810.
                    // Otherwise sub_80FFA returns VInt&3, 0 on successful allocation,
                    // or $FFFF on exhausted AllocateObjectAfterCurrent (FixBugs=0).
                    int d0=timer>=0x28?particles(vIntRunCount):0;
                    frame=(d0&1)!=0?0x1A:0x1F; track(); visible=true;
                }
            }
            case 3 -> laser(vIntRunCount);
            case 4 -> releaseWait();
            default -> throw new IllegalStateException("DEZ beam routine "+routine);
        }
        if(!isDestroyed() && (parent.status&0x80)!=0) {
            pendingDelete=true; status|=0x80; parent=null; mouth=null; visible=false;
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void track() { writeX(parent.getX()+offsetX); writeY(parent.getY()+offsetY); }
    private int particles(int vIntRunCount) {
        int phase=vIntRunCount&3;
        if(phase!=0) return phase;
        var child=spawnChild(()->new Particle(this));
        return child==null || child.isDestroyed()?0xFFFF:0;
    }
    private void laser(int vIntRunCount) {
        if(animate(0x81397)) ((DezFinalBossZoneRuntimeState)services().zoneRuntimeState()).laserOffset(frame);
        // sub_80FA6 precedes sub_81024, so damage uses the previous position words.
        if(frame==0x1E) {
            hurt(services().playerQuery().mainPlayerOrNull(),vIntRunCount);
            hurt(services().playerQuery().nativeP2OrNull(),vIntRunCount);
        }
        track();
    }
    private boolean animate(int script) {
        animationTimer=(byte)(animationTimer-1);
        if(animationTimer>=0) return false;
        animationCursor=(animationCursor+2)&0xFF;
        int next=romByte(script+animationCursor);
        if(next==0xF4) {
            animationTimer=animationCursor=0;
            if(routine==1) { routine=2; timer=120; }
            else { routine=4; timer=0x5F; releaseWait(); }
        } else { frame=next; animationTimer=romByte(script+animationCursor+1); }
        return true; // callback also returns nonzero d2 ($FFFFFFFF).
    }
    private void releaseWait() {
        timer=(short)(timer-1);
        if(timer<0) {
            if(mouth!=null) mouth.control&=~4;
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }
    private void hurt(PlayableEntity player,int vIntRunCount) {
        if(player==null || player.getDead() || player.getInvulnerable()) return;
        int x=player.getCentreX()&0xFFFF,y=player.getCentreY()&0xFFFF;
        if(x<getX() || x>=((getX()+0x120)&0xFFFF)
                || y<((getY()-0x20)&0xFFFF) || y>=((getY()+0x20)&0xFFFF)) return;
        if(player.isCpuControlled()) player.applyHurt(getX(),DamageCause.NORMAL);
        else {
            boolean rings=player.getRingCount()>0;
            if(rings && !player.hasShield()) services().spawnLostRings(player,vIntRunCount);
            player.applyHurtOrDeath(getX(),DamageCause.NORMAL,rings);
        }
    }

    /** loc_808AE: forward child follows the beam's SST address, even after retirement. */
    static final class Particle extends DezFinalBossSprite implements RewindRecreatable {
        private int parentSlot=-1;
        private boolean initialized;
        private int animationCursor;
        private int animationTimer;
        private Particle(ObjectSpawn spawn) { super(spawn,"DEZFinalChargeParticle"); }
        Particle(DezFinalBeam parent) {
            this(new ObjectSpawn(parent.getX(),parent.getY(),0,0,0,false,0)); parentSlot=parent.getSlotIndex();
        }
        @Override public Particle recreateForRewind(RewindRecreateContext context) { return new Particle(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) {
            visible=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            ObjectInstance parent=null;
            for(var candidate:services().objectManager().getActiveObjects()) {
                if(candidate instanceof AbstractObjectInstance object && !object.isDestroyed() && object.getSlotIndex()==parentSlot) { parent=object; break; }
            }
            int px=parent==null?0:parent.getX(),py=parent==null?0:parent.getY();
            if(!initialized) {
                initialized=true; priority=4; halfWidth=halfHeight=4; frame=0x1B; highPriority=false;
                int random=services().rng().nextRaw();
                writeX(px+0x64+(random&0x1F)); writeY(py+4+((random>>>16)&0x7F)-0x40);
                updateDynamicSpawn(getX(),getY()); return;
            }
            animationTimer=(byte)(animationTimer-1);
            if(animationTimer<0) {
                animationCursor=(animationCursor+2)&0xFF;
                int next=romByte(0x8138E+animationCursor);
                if(next==0xF4) { pendingDelete=true; status|=0x80; animationCursor=animationTimer=0; }
                else { frame=next; animationTimer=romByte(0x8138F+animationCursor); }
            }
            xVelocity=(short)(xVelocity-0x40);
            yVelocity=(short)(TrigLookupTable.sinHex((py-getY())&0xFF)*2); move(0);
            if(((px+0x3C)&0xFFFF)>=getX()) { pendingDelete=true; status|=0x80; }
            visible=true; updateDynamicSpawn(getX(),getY());
        }
    }
}
