package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.sonic3k.S3kSpriteMaskSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.util.IdentityHashMap;
import java.util.List;

/** Native SOZ2 shell, linked limbs and laser SSTs ($77AB4-$78032). */
public final class SozEndBossChild extends AbstractObjectInstance
        implements SpawnRewindRecreatable, SolidObjectProvider, TouchResponseProvider {
    static final int UPPER=0, LOWER=1, REAR=2, OVERLAY=3, FRONT=4, PILOT=5;
    static final int SHOULDER=6, ELBOW=7, HAND=8, BACK_SHOULDER=9, BACK_ELBOW=10, BACK_HAND=11;
    static final int CHARGE=12, PARTICLE=13, PROJECTILE=14, MASK=15, FLAME=16;
    private SozEndBossInstance boss;
    private SozEndBossChild parent, terminal;
    private int role, subtype, x, y, xFraction, yFraction, dx, dy, vx, vy;
    private int terminalSlot=-1;
    private int phase, timer, frame, animationTimer, animationCursor;
    private boolean initialized, dying, retired, visible, yCentered, shellWaiting, detached, detachedDying;
    private int detachedX, detachedY;

    SozEndBossChild(SozEndBossInstance boss, SozEndBossChild parent, int role) {
        this(new ObjectSpawn(boss.getX(),boss.getY(),0x98,role,0,false,0));
        // loc_77A6E is allocated independently and never receives a parent pointer.
        this.boss=role==17?null:boss;this.parent=parent;
        if(parent!=null){x=parent.x;y=parent.y;}
        // CreateChild1_Normal publishes signed byte offsets at allocation;
        // CreateChild6/8 copy the parent position without an offset.
        dx=switch(role){case REAR->32;case CHARGE->-24;case PROJECTILE->4;case FLAME->30;default->0;};
        dy=switch(role){case UPPER,MASK->-20;case LOWER->64;case FRONT->36;case PILOT->-28;
            case CHARGE->-46;case PROJECTILE->-10;default->0;};
        x=(x+dx)&65535;y=(y+dy)&65535;
        subtype=role>=SHOULDER&&role<=BACK_HAND?(role-SHOULDER)*2:role<6?role*2:0;
    }
    private SozEndBossChild(SozEndBossInstance boss, SozEndBossChild parent, int role, int subtype) {
        this(boss,parent,role);this.subtype=subtype;
    }
    public SozEndBossChild(ObjectSpawn spawn) {
        super(spawn,"SOZEndBossChild");role=spawn.subtype();x=spawn.x();y=spawn.y();
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        visible=false;
        if(retired){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if(dying){move();vy=(short)(vy+0x38);visible=(phase++&1)!=0;
            if(isCoarseXOutOfRange(x,services().camera().getX(),0x280)
                    || ((y-services().camera().getY()+0x80)&0xFFFF)>0x200)ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if(role==17){followFallingPlayer();return;}
        if(boss==null){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if(!initialized){initialized=true;if(initialize(leader))return;}
        switch(role) {
            case UPPER,LOWER,REAR -> shell(vIntRunCount);
            case OVERLAY -> {visible=true;if(boss.defeated()||x<0x5180)ObjectLifetimeOps.deleteNoRespawn(this);}
            case FRONT -> {follow();visible=true;if(boss.dismantling())startFlicker();}
            case PILOT -> pilot();
            case SHOULDER,ELBOW,HAND,BACK_SHOULDER,BACK_ELBOW,BACK_HAND -> limb(vIntRunCount);
            case CHARGE -> charge(vIntRunCount);
            case PARTICLE -> particle();
            case PROJECTILE -> projectile();
            case MASK -> {follow();visible=true;if(--timer<0)ObjectLifetimeOps.deleteNoRespawn(this);}
            case FLAME -> {dx=boss.flipped()?-30:30;follow();visible=(vIntRunCount&1)==0&&boss.xVelocity()!=0;
                if(boss.dismantling()||boss.hidden())ObjectLifetimeOps.deleteNoRespawn(this);}
            default -> throw new IllegalStateException("SOZ child role "+role);
        }
    }
    /** True means the native init entry returns without dispatching its new code. */
    private boolean initialize(PlayableEntity leader) {
        switch(role) {
            case OVERLAY -> {x=0x5260;y=0x710;}
            case FRONT -> {dy=36;frame=6;return true;}
            case PILOT -> {dy=-28;follow();visible=true;if(boss.knuckles())boss.queuePilotArt(0x15FDDC,0x52E);return true;}
            case SHOULDER,BACK_SHOULDER -> {dy=40;frame=role==SHOULDER?0:3;return true;}
            case ELBOW,BACK_ELBOW -> {frame=role==ELBOW?1:4;return true;}
            case HAND,BACK_HAND -> {frame=role==HAND?2:5;return true;}
            case CHARGE -> {
                dx=-24;dy=-46;frame=13;
                for(int i=0;i<20;i++) {
                    int index=i*2;
                    var child=spawnChild(()->new SozEndBossChild(boss,this,PARTICLE,index));
                    if(child==null||child.getSlotIndex()<0){
                        // AllocateObjectAfterCurrent returns the last scanned SST in
                        // A1 on failure. loc_77DF8 stores it even for a partial table.
                        terminal=null;terminalSlot=services().objectManager().getObjectSlotCapacity()-1;break;
                    }
                    terminal=child;terminalSlot=child.getSlotIndex();
                }
            }
            case PARTICLE -> {frame=10;int address=0x781BC+subtype;
                dx=(byte)boss.byteAt(address)<<8;dy=(byte)boss.byteAt(address+1)<<8;timer=(subtype+2)*4;}
            case PROJECTILE -> {dx=4;dy=-10;frame=14;timer=7;vx=-0x400;vy=0x400;
                if(leader==null||Math.abs((short)(x-leader.getCentreX()))>=0x80){frame=15;dx=8;dy=-8;vy=0x300;}
                return true;}
            case MASK -> {dy=-20;timer=8;frame=4;}
            case FLAME -> {frame=6;return true;}
            default -> { }
        }
        return false;
    }
    @Override protected void onRemovedFromObjectManager() {
        // A removed SST is not a rewind identity. Children keep reading the values it last published.
        var manager=services().objectManager();
        if(manager!=null)for(var child:manager.activeObjectsOfType(SozEndBossChild.class))
            if(child.parent==this){child.detachedX=x;child.detachedY=y;child.detachedDying=dying;child.detached=true;child.parent=null;}
    }
    private boolean hasParent(){return parent!=null||detached;}
    private int parentX(){return parent!=null?parent.x:detachedX;}
    private int parentY(){return parent!=null?parent.y:detachedY;}
    private boolean parentDying(){return parent!=null?parent.dying:detachedDying;}
    private void follow(){x=((hasParent()?parentX():boss.getX())+(byte)dx)&0xFFFF;
        y=((hasParent()?parentY():boss.getY())+(byte)dy)&0xFFFF;}
    private void limb(int vIntRunCount) {
        if(role==SHOULDER||role==BACK_SHOULDER){dx=role==SHOULDER?boss.limbAngle()>>2:0;follow();}
        else {
            int angle=(role>=BACK_SHOULDER?-boss.limbAngle():boss.limbAngle())&255;
            int address=(role==ELBOW||role==BACK_ELBOW)?0x184E1C:0x184DDC;
            int i=angle&63,a=boss.byteAt(address+i),b=boss.byteAt(address+63-i);
            int ox=switch(angle>>>6){case 0->a;case 1->b;case 2->-a;default->-b;};
            int oy=switch(angle>>>6){case 0->b;case 1->-a;case 2->-b;default->a;};
            x=((hasParent()?parentX():boss.getX())+ox)&0xFFFF;
            y=((hasParent()?parentY():boss.getY())+oy)&0xFFFF;
            if(role==HAND||role==BACK_HAND){x=(x-28)&0xFFFF;y=(y-4)&0xFFFF;}
        }
        visible=role!=BACK_SHOULDER;
        if(role<=HAND){var contacts=checkpointAll();
            if(role==HAND&&!boss.defeated()&&contacts!=null)contacts.perPlayer().forEach((p,c)->{
                // sub_78178 reads the player's $34 invulnerability timer.
                if(c.kind()==ContactKind.SIDE&&p.getCentreX()<=x&&!p.getInvulnerable())hurt(p,vIntRunCount);
            });}
        if((role==SHOULDER||role==BACK_SHOULDER)?boss.dismantling():hasParent()&&parentDying()) {
            releaseRiders();startFlicker();
        }
    }
    private void shell(int vIntRunCount) {
        if(role==UPPER&&shellWaiting){if(!boss.shellOpen())shellWaiting=false;
            if(boss.defeated()){releaseRiders();ObjectLifetimeOps.deleteNoRespawn(this);}return;}
        if(role==LOWER&&boss.shellOpen()){releaseRiders();shellWaiting=true;
            if(boss.defeated())ObjectLifetimeOps.deleteNoRespawn(this);return;}
        shellWaiting=false;dx=role==REAR?32:0;dy=role==UPPER?-20:role==LOWER?68:0;follow();
        var contacts=checkpointAll();
        if(contacts!=null)contacts.perPlayer().forEach((p,c)->{
            if(role==UPPER&&c.kind()==ContactKind.SIDE){
                if(p.getCentreX()>x)hurt(p,vIntRunCount);
                else if(!isKnuckles(p)||p.getAnimationId()==2
                        ||p instanceof AbstractPlayableSprite a&&a.getDoubleJumpFlag()==1){
                    boss.openShell(p);shellWaiting=true;releaseRiders();}
            }
        });
        if(role==LOWER){
            // sub_78120/sub_78136 run after SolidObjectFull2: a side push, or the standing bit it
            // just set on landing, clears Status_OnObj and that standing bit before sub_24280.
            var manager=services().objectManager();
            for(var p:players()){
                var c=contacts==null?null:contacts.perPlayer().get(p);
                boolean side=c!=null&&c.kind()==ContactKind.SIDE;
                boolean nowStanding=manager!=null&&manager.hasObjectStandingBit(p,this);
                if(!side&&!nowStanding)continue;
                if(!side)manager.clearRidingObject(p);
                p.setOnObject(false);if(!p.getInvulnerable())hurt(p,vIntRunCount);
            }
        }
        if(role==REAR?boss.dismantling():boss.defeated()){releaseRiders();ObjectLifetimeOps.deleteNoRespawn(this);}
    }
    private boolean isKnuckles(PlayableEntity p){return p instanceof AbstractPlayableSprite a
            &&com.openggf.game.CharacterKey.KNUCKLES.equals(a.characterKey());}
    private Iterable<? extends PlayableEntity> players(){return services().playerQuery().playersFor(
            ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);}
    private void releaseRiders(){var manager=services().objectManager();if(manager==null)return;
        for(var p:players())if(manager.hasObjectStandingBit(p,this)){manager.clearRidingObject(p);p.setOnObject(false);p.setAir(true);}}
    private void hurt(PlayableEntity p,int vIntRunCount){if(p.getDead()||p.getInvulnerable())return;
        if(p instanceof AbstractPlayableSprite a){int pos=(a.getCentreY()<<16|a.getYSubpixelRaw())-(a.getYSpeed()<<8);
            NativePositionOps.writeYPosPreserveSubpixel(a,pos>>16);a.setSubpixelRaw(a.getXSubpixelRaw(),pos&0xFFFF);}
        if(p.isCpuControlled()){p.applyHurt(x,DamageCause.NORMAL);return;}
        boolean rings=p.getRingCount()>0;if(rings&&!p.hasShield())services().spawnLostRings(p,vIntRunCount);
        p.applyHurtOrDeath(x,DamageCause.NORMAL,rings);}
    private void startFlicker(){parent=null;detached=false;dying=true;visible=true;vx=boss.word(0x852F4+subtype*2);vy=boss.word(0x852F6+subtype*2);}
    private void pilot(){follow();visible=true;
        if(boss.hidden()){ObjectLifetimeOps.deleteNoRespawn(this);return;}
        if(phase==1&&boss.defeated()){frame=3;return;}
        if(--animationTimer<0){animationTimer=phase==1?5:boss.knuckles()?15:5;animationCursor^=1;frame=animationCursor;}
        if(boss.defeated()){frame=3;phase=1;}else if(phase==0&&boss.hurtFlash())frame=2;
    }
    private boolean terminalFinished(){
        if(terminal!=null)return terminal.retired;
        if(terminalSlot<0)return false;
        for(var object:services().objectManager().getActiveObjects()) {
            if(object instanceof AbstractObjectInstance instance&&instance.getSlotIndex()==terminalSlot){
                if(object instanceof SozEndBossChild child)return child.retired;
                if(object instanceof SozEndBossInstance root)return root.dismantling()||root.hidden();
                // A failed native allocation may point at an unrelated live SST.
                // Arbitrary foreign $38 bytes have no ObjectServices representation;
                // retain the slot, but never substitute the last successful particle.
                return false;
            }
        }
        return false;
    }
    private void charge(int vIntRunCount){
        if(phase==0&&terminalFinished()){phase=1;terminal=null;timer=8;services().playSfx(Sonic3kSfx.LASER.id);
            var mask=spawnChild(()->new SozEndBossChild(boss,this,MASK));
            if(mask!=null&&mask.getSlotIndex()>=0)spawnChild(()->new SozEndBossChild(boss,this,PROJECTILE));}
        if(phase!=0&&--timer<0){dying=true;ObjectLifetimeOps.deleteNoRespawn(this);return;}
        follow();if(boss.defeated()||boss.shellOpen()){dying=true;ObjectLifetimeOps.deleteNoRespawn(this);return;}
        visible=(vIntRunCount&1)==0;
    }
    private void particle(){
        if(phase==0){if(--timer>=0){if(parentDying()||boss.shellOpen())ObjectLifetimeOps.deleteNoRespawn(this);return;}phase=1;}
        // Animate_RawNoSST pre-increments anim_frame and reads 1(a1,d0): byte_78348's first pass
        // shows byte 2, so the F4 retire is the fourth step, not the fifth.
        if(--animationTimer<0){animationTimer=7;
            if(animationCursor==3){retired=true;}else frame=boss.byteAt(0x7834A+animationCursor++);}
        vx=(short)(vx+0x10);dx=(short)(dx+vx);
        if(!yCentered){vy=(short)(vy+(dy<0?0x10:-0x10));int next=(short)(dy+vy);
            if((next^dy)<0){next=0;yCentered=true;}dy=next;}
        x=(parentX()+(byte)(dx>>>8))&0xFFFF;y=(parentY()+(byte)(dy>>>8))&0xFFFF;
        if(parentDying()||boss.shellOpen()){dying=true;ObjectLifetimeOps.deleteNoRespawn(this);}visible=true;
    }
    private void projectile(){visible=true;
        if(phase==0&&--timer>=0){dx=(byte)(dx+(vx>>8));dy=(byte)(dy+(vy>>8));follow();
            if(parentDying())ObjectLifetimeOps.deleteNoRespawn(this);return;}
        phase=1;parent=null;detached=false;move();if(isCoarseXOutOfRange(x,services().camera().getX(),0x280))ObjectLifetimeOps.deleteNoRespawn(this);
    }
    /** loc_77A6E/77A98 is an independently allocated owner, later than the root. */
    private void followFallingPlayer(){
        if(phase==0&&!((com.openggf.game.sonic3k.runtime.SozZoneRuntimeState)
                services().zoneRuntimeState()).events().endBossFallStarted())return;
        var main=services().playerQuery().mainPlayerOrNull();if(main==null)return;
        // loc_77A98 owns Player_2; each engine follower receives the same P2 role.
        for(var other:services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED))
            if(other!=main && other instanceof AbstractPlayableSprite sprite){
            if(phase==0){sprite.setHighPriority(false);ObjectControlState.nativeBit7FullControl().applyTo(sprite);
                sprite.setAnimationId(0x1A);sprite.setControlLocked(false);}
            NativePositionOps.writeXPosPreserveSubpixel(sprite,main.getCentreX());
            NativePositionOps.writeYPosPreserveSubpixel(sprite,main.getCentreY()-32);
        }
        phase=1;
    }
    private void move(){int px=(x<<16|xFraction)+(vx<<8),py=(y<<16|yFraction)+(vy<<8);
        x=px>>>16;y=py>>>16;xFraction=px&65535;yFraction=py&65535;}
    @Override public boolean isSolidFor(PlayableEntity p){return !dying&&!isDestroyed()&&role<=HAND
            &&(role<=REAR||role>=SHOULDER)&&!shellWaiting;}
    @Override public SolidObjectParams getSolidParams(){return switch(role){
        case UPPER->new SolidObjectParams(0x2B,0x28,0x28);case LOWER->new SolidObjectParams(0x3B,0x30,0x30);
        case REAR->new SolidObjectParams(0x2B,0x200,0x200);case SHOULDER->new SolidObjectParams(0x1F,0x14,0x14);
        case ELBOW->new SolidObjectParams(0x1B,0x10,0x10);default->new SolidObjectParams(0x2B,0x14,0xC);};}
    // loc_1E154 re-reads the SetUp_ObjAttributes3 width_pixels (word_782AE/BA/C6), not d1-$B.
    @Override public int getTopLandingHalfWidth(PlayableEntity player,int collisionHalfWidth){return switch(role){
        case SHOULDER->0x18;case ELBOW->0x14;case HAND->0x28;default->SolidObjectProvider.super.getTopLandingHalfWidth(player,collisionHalfWidth);};}
    // Limbs call SolidObjectFull: a set standing bit with Status_InAir takes loc_1DCF0 and returns.
    @Override public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player){return role>=SHOULDER&&role<=HAND;}
    @Override public SolidExecutionMode solidExecutionMode(){return SolidExecutionMode.MANUAL_CHECKPOINT;}
    @Override public boolean bypassesOffscreenSolidGate(){return role<=REAR;}
    @Override public boolean usesInstanceSolidStateLatchKey(){return true;}
    @Override public int getCollisionProperty(){return 0;}
    @Override public int getCollisionFlags(){return role==PROJECTILE&&phase==1&&!isDestroyed()?0x98:0;}
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getOnScreenHalfWidth(){return switch(role){case UPPER,REAR,MASK->32;case LOWER->48;
        case OVERLAY,PILOT->16;case FRONT,SHOULDER,BACK_SHOULDER->24;case HAND,BACK_HAND->40;
        case CHARGE,FLAME->8;case PARTICLE->4;default->20;};}
    @Override public int getOnScreenHalfHeight(){return switch(role){case UPPER->80;case LOWER,OVERLAY->48;case REAR->255;
        case FRONT->28;case PILOT,CHARGE->8;case SHOULDER,BACK_SHOULDER->24;case PARTICLE,FLAME->4;case MASK->32;default->20;};}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return role>=SHOULDER&&role<=HAND||role==CHARGE||role==PILOT&&boss.isHighPriority();}
    @Override public int getPriorityBucket(){return switch(role){case OVERLAY->0;case HAND->2;case ELBOW,CHARGE->3;
        case SHOULDER,PARTICLE->4;case FRONT,MASK,FLAME->5;default->6;};}
    @Override public void appendRenderCommands(List<GLCommand> commands){if(!visible||isDestroyed())return;
        if(role==MASK){S3kSpriteMaskSupport.submitFrame4(services().graphicsManager(),x,y);return;}
        String key=role==OVERLAY?Sonic3kObjectArtKeys.SOZ_END_BOSS_BODY:
                role==PILOT&&boss.knuckles()?Sonic3kObjectArtKeys.FBZ_EGGROBO_HEAD:
                role==PILOT||role==FLAME?Sonic3kObjectArtKeys.ROBOTNIK_SHIP:Sonic3kObjectArtKeys.SOZ_END_BOSS;
        var renderer=getRenderer(key);if(renderer!=null&&renderer.isReady())
            renderer.drawFrameIndexWithPaletteBase(frame,x,y,(role==PILOT||role==FLAME)&&boss.flipped(),false,
                    role>=SHOULDER&&role<=BACK_HAND?3:role==PILOT||role==FLAME||role==OVERLAY?0:1);
    }
}
