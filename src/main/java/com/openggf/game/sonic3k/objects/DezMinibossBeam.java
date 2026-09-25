package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;

/** loc_7E97E: flickering charge, growing beam, then direct native-P1/P2 damage. */
final class DezMinibossBeam extends DezMinibossSprite implements RewindRecreatable {
    private DezMinibossSprite parent;
    private int arenaMinY;
    private int state;
    private int timer;
    private DezMinibossBeam(ObjectSpawn spawn) { super(spawn,"DEZMinibossBeam"); }
    DezMinibossBeam(DezMinibossSprite parent,int arenaMinY) {
        this(new ObjectSpawn(parent.getX(),parent.getY()+0x1E,0,0,0,false,0));
        this.parent=parent; this.arenaMinY=arenaMinY;
    }
    @Override public DezMinibossBeam recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossBeam(context.spawn());
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(state==0) { state=1; priority=7; halfWidth=0x10; halfHeight=0x80; frame=6; childDy=0x1E; }
        switch(state) {
            case 1 -> {
                follow();
                if(parentDead()) return;
                if((vIntRunCount&1)!=0) return;
                childDy=(childDy+1)&0xFF;
                if(childDy>=0x24) { state=2; timer=0x3F; }
                visible=true;
            }
            case 2 -> {
                follow();
                if(--timer<0) { state=3; frame=0x1B; childDy=(childDy-4)&0xFF; }
                if(!parentDead()) visible=(vIntRunCount&1)==0;
            }
            case 3 -> {
                if(++frame>=0x22) {
                    state=4;
                    for(int i=0;i<2;i++) {
                        int subtype=i*2;
                        var foot=spawnChild(()->new Foot(this,subtype));
                        if(foot==null || foot.isDestroyed()) break;
                    }
                }
                childDy=(childDy+8)&0xFF; follow();
                if(!parentDead()) visible=true;
            }
            case 4 -> {
                if((parent.control&2)==0) { goDelete(); return; }
                follow();
                if((vIntRunCount&0x1F)==0) services().playSfx(Sonic3kSfx.BOSS_PANIC.id);
                // sub_7EB34 tests centres (not radii) and visits P1 before native P2.
                hurt(services().playerQuery().mainPlayerOrNull(),vIntRunCount);
                hurt(services().playerQuery().nativeP2OrNull(),vIntRunCount);
                if(!parentDead()) visible=true;
            }
            default -> throw new IllegalStateException("Unknown DEZ beam routine "+state);
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void follow() { writeX(parent.getX()); writeY(parent.getY()+(byte)childDy); }
    private void goDelete() { status|=0x80; pendingDelete=true; }
    private boolean parentDead() { if((parent.status&0x80)==0) return false; goDelete(); return true; }
    private void hurt(PlayableEntity player,int vIntRunCount) {
        if(player==null || player.getDead() || player.getInvulnerable() || player.isOnObject()) return;
        int x=player.getCentreX()&0xFFFF,y=player.getCentreY()&0xFFFF;
        if(x<((getX()-0x18)&0xFFFF) || x>=((getX()+0x18)&0xFFFF)
                || y<((getY()-0x48)&0xFFFF) || y>=((getY()+0x48)&0xFFFF)) return;
        if(player.isCpuControlled()) player.applyHurt(getX(),DamageCause.NORMAL);
        else {
            boolean rings=player.getRingCount()>0;
            if(rings && !player.hasShield()) services().spawnLostRings(player,vIntRunCount);
            player.applyHurtOrDeath(getX(),DamageCause.NORMAL,rings);
        }
    }
    DezMinibossSprite parentForTest() { return parent; }
    int stateForTest() { return state; }

    static final class Foot extends DezMinibossSprite implements RewindRecreatable {
        private DezMinibossBeam parent;
        private boolean initialized;
        private int animationCursor;
        private Foot(ObjectSpawn spawn) { super(spawn,"DEZMinibossBeamFoot"); }
        Foot(DezMinibossBeam parent,int subtype) {
            this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0)); this.parent=parent;
        }
        @Override public Foot recreateForRewind(RewindRecreateContext context) { return new Foot(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) {
            visible=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if(parent==null) return;
            if(!initialized) {
                initialized=true; priority=6; halfWidth=8; halfHeight=4; frame=8;
                writeY(parent.arenaMinY+0xB0); word3A=spawn.subtype()==0?-0x10:0x10; flipX=spawn.subtype()!=0;
            }
            int value=romByte(0x7EFF5+(++animationCursor));
            if(value==0xFC) { animationCursor=0; value=romByte(0x7EFF5); }
            frame=value; writeX(parent.getX()+word3A);
            if((parent.status&0x80)!=0) { status|=0x80; pendingDelete=true; return; }
            visible=true; updateDynamicSpawn(getX(),getY());
        }
        DezMinibossBeam parentForTest() { return parent; }
    }
}
