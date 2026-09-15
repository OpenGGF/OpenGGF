package com.openggf.game.sonic3k.objects;

import com.openggf.game.*;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.*;
import java.util.List;

/** Actual child SST roles from ChildObjDat_773D6–ChildObjDat_7740A. */
final class SozMinibossChild extends SozMinibossSprite implements RewindRecreatable,TouchResponseProvider,TouchResponseListener {
    static final int HITBOX=0,PART=1,COVER=2,DUST=3,COLLAPSE_DUST=4;
    private SozMinibossInstance owner;
    private int kind;
    private int index;
    private int routine;
    private int timer;
    private int collision;
    private int collisionProperty;
    private boolean debris;
    private boolean flicker;
    SozMinibossChild(ObjectSpawn spawn){this(spawn,null,0,0);}
    SozMinibossChild(ObjectSpawn spawn,SozMinibossInstance owner,int kind,int index){
        super(spawn,"EggGolemChild",kind>=DUST?Sonic3kObjectArtKeys.SOZ_MINIBOSS_DUST:Sonic3kObjectArtKeys.SOZ_MINIBOSS);
        this.owner=owner;this.kind=kind;this.index=index;
    }
    @Override public SozMinibossChild recreateForRewind(RewindRecreateContext context){return new SozMinibossChild(context.spawn());}
    @Override public void update(int vIntRunCount,PlayableEntity player){
        if(debris){move(0x38);flicker=!flicker;visible=!flicker;
            int dx=((x&0xFF80)-((cameraLeft()-0x80)&0xFF80))&65535,dy=(y-cameraTop()+0x80)&65535;
            if(dx>0x80+viewportWidth()+0xC0 || dy>0x200)setDestroyed(true);
            updateDynamicSpawn(x,y);return;
        }
        if(kind<DUST && (owner==null || owner.isDestroyed())){setDestroyed(true);return;}
        if(kind==HITBOX){
            x=owner.getX()+(index==0?(owner.flip?12:-12):0);y=owner.getY()+(index==0?-28:0);flip=owner.flip;
            if(routine==0){frame=0x1A;collision=index==0?0:0xA8;routine=index==0?4:2;}
            else if(routine==4){if(owner.vulnerable()){routine=6;collision=0xD7;collisionProperty=0;}}
            else if(routine==6){processTouch();}
            else if(routine==8 && animateRaw()){frame=0x1A;owner.headRecovered();routine=4;collision=0;}
        }else if(kind==PART){
            if(routine==0)routine=2;
            if(owner.collapsing()){
                debris=true;collision=0;
                int[] vx={-0x200,0x200,-0x300,0x300,-0x200,0,-0x400,0x400};
                int[] vy={-0x200,-0x200,-0x200,-0x200,-0x200,-0x200,-0x300,-0x300};
                xVel=flip?-vx[index]:vx[index];yVel=vy[index];
            }else{
                int address=0x7717E+index*40+owner.pose()*4;
                int dx=(byte)romByte(address),dy=(byte)romByte(address+1);
                frame=romByte(address+2);flip=owner.flip;
                x=owner.getX()+(flip?-dx:dx);y=owner.getY()+dy;
                if(romByte(address+3)!=0)flip=!flip;
            }
        }else if(kind==COVER){
            if(routine==0){routine=2;y+=0x40;frame=0x18;flip=owner.flip;yVel=-0x100;timer=0x3F;}
            else if(--timer<0)setDestroyed(true);else move(0);
        }else{
            if(routine==0){
                routine=2;priority=3;visible=false;
                int dx=kind==DUST?-4:0;int offset=(byte)romByte(0x770B8+index*2);
                x=owner.getX()+(owner.flip?-dx:dx)+offset;y=owner.getY()+(kind==DUST?0x38:4);
                flip=offset>=0;timer=(byte)romByte(0x770B9+index*2);script=0x7749B;
            }else if(routine==2){timer=(byte)(timer-1);if(timer<0)routine=4;}
            else{visible=true;if(animateMulti(false)<0)setDestroyed(true);}
        }
        updateDynamicSpawn(x,y);
    }
    private void processTouch(){
        int property=collisionProperty;collisionProperty=0;if(property==0)return;
        PlayableEntity entity=property==1?services().playerQuery().mainPlayerOrNull():services().playerQuery().nativeP2OrNull();
        if(!(entity instanceof AbstractPlayableSprite player))return;
        if(attacks(player)){
            player.setXSpeed((short)-player.getXSpeed());player.setYSpeed((short)-player.getYSpeed());player.setGSpeed((short)-player.getGSpeed());
            routine=8;script=0x77495;owner.hitBy(player);
        }else if(!player.getInvulnerable())player.applyHurtOrDeath(x,DamageCause.NORMAL,player.getRingCount()>0);
    }
    private boolean attacks(AbstractPlayableSprite player){
        if(player.isSuperSonic() || player.getInvincibleFrames()>0 || player.getAnimationId()==9 || player.getAnimationId()==2)return true;
        if(player instanceof Knuckles)return player.getDoubleJumpFlag()==1 || player.getDoubleJumpFlag()==3;
        if(player instanceof Tails && player.getDoubleJumpFlag()!=0 && !player.isInWater()){
            int angle=TrigLookupTable.calcAngle((short)(player.getCentreX()-x),(short)(player.getCentreY()-y));return ((angle-0x20)&255)<0x40;
        }
        return false;
    }
    @Override public int getCollisionFlags(){return isDestroyed()?0:collision;}
    @Override public int getCollisionProperty(){return collisionProperty;}
    @Override public boolean usesS3kTouchSpecialPropertyResponse(){return true;}
    @Override public boolean requiresContinuousTouchCallbacks(){return true;}
    @Override public void onTouchResponse(PlayableEntity player,TouchResponseResult result,int vIntRunCount){
        if(collision!=0xD7)return;
        if(player==services().playerQuery().mainPlayerOrNull())collisionProperty=(collisionProperty+1)&255;
        else if(player==services().playerQuery().nativeP2OrNull())collisionProperty=(collisionProperty+2)&255;
    }
    @Override public int getOnScreenHalfWidth(){return kind==COVER?0x34:kind==HITBOX?12:kind==PART?20:8;}
    @Override public int getOnScreenHalfHeight(){return kind==COVER?0x34:kind==HITBOX?16:kind==PART?20:8;}

    /** Obj_FadeSelectedFromBlack: eight RGB component increments, once every four ticks. */
    static final class PaletteFade extends SozMinibossSprite implements SpawnRewindRecreatable {
        private int timer;
        private int steps=8;
        private int[] colors=new int[16];
        private boolean initialized;
        public PaletteFade(ObjectSpawn spawn){super(spawn,"EggGolemPaletteFade",Sonic3kObjectArtKeys.SOZ_MINIBOSS);visible=false;}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            var registry=services().paletteOwnershipRegistryOrNull();
            if(!initialized){initialized=true;for(int i=0;i<16;i++)colors[i]=romByte(0x77412+i*2)*256+romByte(0x77413+i*2);if(registry!=null)registry.setPaletteRotationDisabled(true);}
            if(--timer>=0)return;timer=3;byte[] bytes=new byte[32];
            for(int i=0;i<16;i++){
                int target=romByte(0x77432+i*2)*256+romByte(0x77433+i*2),value=colors[i];
                for(int shift:new int[]{0,4,8})if(((value>>shift)&14)<((target>>shift)&14))value+=2<<shift;
                colors[i]=value;bytes[i*2]=(byte)(value>>8);bytes[i*2+1]=(byte)value;
            }
            S3kPaletteWriteSupport.applyLine(registry,services().currentLevel(),services().graphicsManager(),"s3k.soz.miniboss",S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,1,bytes);
            if(--steps==0){if(registry!=null)registry.setPaletteRotationDisabled(false);setDestroyed(true);}
        }
    }
    /** CreateBossExp04 selects Obj_WaitForParent. Negative $80 means continuous explosions. */
    static final class Explosions extends SozMinibossSprite implements RewindRecreatable {
        private SozMinibossInstance owner;
        private int timer;
        Explosions(ObjectSpawn spawn){this(spawn,null);}
        Explosions(ObjectSpawn spawn,SozMinibossInstance owner){super(spawn,"EggGolemExplosions",Sonic3kObjectArtKeys.SOZ_MINIBOSS);this.owner=owner;visible=false;}
        @Override public Explosions recreateForRewind(RewindRecreateContext context){return new Explosions(context.spawn());}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            if(owner==null || owner.isDestroyed()){setDestroyed(true);return;}
            x=owner.getX();y=owner.getY();
            if(--timer>=0)return;timer=2;
            var manager=services().objectManager();
            int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,getSlotIndex());
            if(slot<0)return;
            int random=services().rng().nextRaw();
            S3kBossExplosionChild child;
            try{
                child=ObjectConstructionContext.with(services(),slot,
                        ()->S3kBossExplosionChild.createWithNativeInitSfx(x+(random&0x3F)-0x20,y+((random>>>16)&0x3F)-0x20));
            }catch(RuntimeException|Error failure){manager.releaseDynamicSlot(slot);throw failure;}
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager,child,slot);
        }
    }
    /** loc_76E4E waits for results to rise then fall before native P1 alignment. */
    static final class Alignment extends SozMinibossSprite implements RewindRecreatable {
        private int phase;
        private int minX;
        private int maxX;
        private boolean fromRight;
        Alignment(ObjectSpawn spawn){this(spawn,0,0);}
        Alignment(ObjectSpawn spawn,int minX,int maxX){super(spawn,"EggGolemAlignment",Sonic3kObjectArtKeys.SOZ_MINIBOSS);this.minX=minX;this.maxX=maxX;visible=false;}
        @Override public Alignment recreateForRewind(RewindRecreateContext context){return new Alignment(context.spawn());}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity entity){
            if(phase==0){if(services().gameState().isEndOfLevelActive())phase=1;return;}
            if(phase==1){
                if(services().gameState().isEndOfLevelActive())return;
                phase=2;
                if(entity instanceof AbstractPlayableSprite player){player.setControlLocked(true);fromRight=(player.getCentreX()&65535)>=0x43A0;}
                var minimum=spawnChild(() -> new Bounds(getSpawn(), minX, true));
                if(minimum!=null && !minimum.isDestroyed())
                    spawnChild(() -> new Bounds(getSpawn(), maxX, false));
                spawnFreeChild(() -> new P2Hold(getSpawn()));
            }
            if(!(entity instanceof AbstractPlayableSprite player))return;
            boolean reached=fromRight?player.getCentreX()<=0x43A0:player.getCentreX()>=0x43A0;
            if(!reached){player.setForcedInputMask((fromRight?AbstractPlayableSprite.INPUT_LEFT:AbstractPlayableSprite.INPUT_RIGHT)|(player.getPushing()?AbstractPlayableSprite.INPUT_JUMP:0));return;}
            NativePositionOps.writeXPosPreserveSubpixel(player,0x43A0);player.clearForcedInputMask();player.setDirection(com.openggf.physics.Direction.RIGHT);
            player.setXSpeed((short)0);player.setYSpeed((short)0);player.setGSpeed((short)0);
            ((SozZoneRuntimeState)services().zoneRuntimeState()).requestMinibossPostResultsAlignmentComplete();setDestroyed(true);
        }
    }

    /** The native gradual resize workers outlive the alignment controller. */
    static final class Bounds extends SozMinibossSprite implements RewindRecreatable {
        private int target;
        private boolean minimum;
        private int acceleration;
        Bounds(ObjectSpawn spawn){this(spawn,0,false);}
        Bounds(ObjectSpawn spawn,int target,boolean minimum){super(spawn,"EggGolemBounds",Sonic3kObjectArtKeys.SOZ_MINIBOSS);this.target=target&65535;this.minimum=minimum;visible=false;}
        @Override public Bounds recreateForRewind(RewindRecreateContext context){return new Bounds(context.spawn());}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            acceleration+=0x4000;var camera=services().camera();
            if(minimum){int next=Math.max(target,(camera.getMinX()&65535)-(acceleration>>16));camera.setMinX((short)next);if(next==target)setDestroyed(true);}
            else{int next=Math.min(target,(camera.getMaxX()&65535)+(acceleration>>16));camera.setMaxX((short)next);if(next==target)setDestroyed(true);}
        }
    }
    /** loc_863C0 positive Ctrl_2_locked suppresses human input, not native CPU follow. */
    static final class P2Hold extends SozMinibossSprite implements SpawnRewindRecreatable {
        private boolean initialized;
        public P2Hold(ObjectSpawn spawn){super(spawn,"EggGolemP2Hold",Sonic3kObjectArtKeys.SOZ_MINIBOSS);visible=false;}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity leader){
            if(!(services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite player)){setDestroyed(true);return;}
            if(!initialized){initialized=true;player.setControlLocked(true);if(player.getCpuController()!=null)player.getCpuController().clearManualControlTimer();}
            if(!player.isControlLocked()){setDestroyed(true);return;}
            if(player.getCpuController()!=null)player.getCpuController().clearController2LogicalLatch();
        }
    }
}
