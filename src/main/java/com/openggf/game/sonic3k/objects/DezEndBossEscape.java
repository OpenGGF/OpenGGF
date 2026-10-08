package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Knuckles;
import java.util.List;

/** Ordered SST children of the Act 2 boss's entry and defeat tables. */
final class DezEndBossEscape extends DezEndBossSprite implements RewindRecreatable {
    static final int ROBOTNIK=0,DOOR=1,PATH=2,DEBRIS=3;
    private DezEndBossInstance parent;
    private int kind;
    private int state;
    private int timer;
    private int animationCursor;
    private int animationTimer;
    private boolean eggRoboRun;
    private DezEndBossEscape(ObjectSpawn spawn) { super(spawn,"DEZEndBossEscape"); }
    DezEndBossEscape(DezEndBossInstance parent,int kind,int subtype) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0));
        this.parent=parent; this.kind=kind;
    }
    @Override public DezEndBossEscape recreateForRewind(RewindRecreateContext context) {
        return new DezEndBossEscape(context.spawn());
    }
    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        boolean released = (kind==ROBOTNIK && state>=3) || (kind==DOOR && state>=2);
        if(parent==null && !released) return;
        switch(kind) {
            case ROBOTNIK -> robotnik();
            case DOOR -> door();
            case PATH -> path();
            case DEBRIS -> debris();
            default -> throw new IllegalStateException("Unknown DEZ escape child "+kind);
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void robotnik() {
        if(state==0) {
            state=1; priority=4; halfWidth=halfHeight=0x20; frame=0;
            writeX(0x3610); writeY(0x324); visible=true; return;
        }
        if(state==1) {
            animationTimer=(byte)(animationTimer-1);
            if(animationTimer<0) {
                animationCursor=(animationCursor+2)&255;
                int next=romByte(0x7FCE2+animationCursor);
                if(next==0xFC) { animationCursor=0; next=romByte(0x7FCE2); }
                frame=next; animationTimer=romByte(0x7FCE3+animationCursor);
            }
            if((parent.status&0x40)!=0) frame=2;
            if((parent.status&0x80)!=0) { state=2; frame=3; }
        } else if(state==2) {
            if((parent.control&0x10)!=0) {
                state=3; writeY(getY()-4); xVelocity=0x200;
                frame=animationCursor=animationTimer=0; flipX=true;
                eggRoboRun=services().playerQuery().mainPlayerOrNull() instanceof Knuckles;
                // loc_70068 hands off to loc_7F74C: the runner reads Player_1,
                // not parent3. Release the Java identity before StartNewLevel
                // deletes the boss; retained native address bytes are never read.
                parent=null;
            }
        } else {
            var player=services().playerQuery().mainPlayerOrNull();
            if(player!=null) {
                int distance=(short)(getX()-player.getCentreX());
                if(distance>=0&&distance<0x40) writeX(player.getCentreX()+0x40);
            }
            animationTimer=(byte)(animationTimer-1);
            if(animationTimer<0) {
                animationCursor=(animationCursor+1)&255;
                int next=romByte(0x7041A+animationCursor);
                if(next==0xFC) { animationCursor=0; next=romByte(0x7041A); }
                frame=next; animationTimer=romByte(0x70419);
            }
            move(0);
            if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())) {
                ObjectLifetimeOps.deleteNoRespawn(this); return;
            }
        }
        visible=true;
    }
    private void door() {
        if(state==0) {
            state=1; priority=3; halfWidth=0xC; halfHeight=0x14; frame=0x14;
            writeX(0x3600); writeY(0x32C);
        }
        if(state==1&&(parent.control&0x10)!=0) {
            state=2; frame=0x15;
            // loc_7F79C switches to Sprite_OnScreen_Test after the signal.
            // That routine no longer dereferences the boss's parent3 address.
            parent=null;
            spawnChild(()->new DezMinibossExplosionController(getX(),getY(),6));
        } else if(state==2&&isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())) {
            ObjectLifetimeOps.deleteNoRespawn(this); return;
        }
        visible=true;
    }
    private void path() {
        if(state==0) {
            state=1; writeY(getY()+0x38); xVelocity=spawn.subtype()==0?2:-2;
            timer=spawn.subtype()==0?4:2;
        }
        timer=(byte)(timer-1);
        if(timer<0) { timer=3; makeExplosion(); }
        if(state==1) {
            writeX(getX()+xVelocity);
            int end=xVelocity<0?0x3450:0x35D0;
            if(xVelocity<0?getX()<=end:getX()>=end) {
                writeX(end); state=2; yVelocity=-2;
                if(spawn.subtype()==0) {
                    var runtime=parent.runtime(); runtime.setBossSignals(runtime.bossSignals()|2);
                }
            }
        } else {
            writeY(getY()+yVelocity);
            if(getY()<0x240) ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }
    private void makeExplosion() {
        var manager=services().objectManager();
        int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,getSlotIndex());
        if(slot<0) return;
        try {
            int random=services().rng().nextRaw();
            int x=(getX()+(random&31)-16)&0xFFFF, y=(getY()+((random>>>16)&31)-16)&0xFFFF;
            var child=ObjectConstructionContext.with(services(),slot,()->S3kBossExplosionChild.createWithNativeInitSfx(x,y));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager,child,slot);
        } catch(RuntimeException|Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }
    private void debris() {
        if(state==0) {
            state=1; priority=0; halfWidth=halfHeight=0x28;
            frame=0x22+spawn.subtype()/2;
            int velocity=0x852F4+8+spawn.subtype()*2;
            xVelocity=(short)romWord(velocity); yVelocity=(short)romWord(velocity+2);
            visible=true; return;
        }
        move(0x38);
        if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
            status|=0x80; control|=0x10; pendingDelete=true; return;
        }
        visible=(control&0x40)!=0; control^=0x40;
    }
    @Override public boolean participatesInRomWorldTransitionOffset() { return kind!=PATH; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(kind!=ROBOTNIK) { super.appendRenderCommands(commands); return; }
        if(!visible||isDestroyed()) return;
        String key=state<3?Sonic3kObjectArtKeys.DEZ_ROBOTNIK_STAND:
                eggRoboRun?Sonic3kObjectArtKeys.DEZ_EGGROBO_RUN:Sonic3kObjectArtKeys.DEZ_ROBOTNIK_RUN;
        var renderer=getRenderer(key);
        if(renderer!=null&&renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),flipX,false,-1,true);
    }
    DezEndBossInstance parentForTest() { return parent; }
    int kindForTest() { return kind; }

    /** loc_7FC3E deliberately remains alive and clears every own pass until the full load. */
    static final class GravityClearer extends DezEndBossSprite implements SpawnRewindRecreatable {
        GravityClearer() { this(new ObjectSpawn(0,0,0,0,0,false,0)); }
        GravityClearer(ObjectSpawn spawn) { super(spawn,"DEZBossGravityClearer"); }
        @Override public void update(int vIntRunCount,PlayableEntity player) { services().gameState().setReverseGravityActive(false); }
        @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
    }
}
