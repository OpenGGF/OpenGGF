package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;

import java.util.List;

/** Locked-on {@code Obj_FBZChainLink} ($72), sonic3k.asm $3A7D4-$3AD88. */
public final class FbzChainLinkObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int[][] HAND_FRAMES={{0x85,0x80,0x81,0x91},{0x82,0x83,0x84,0x91}};
    private static final int[][] HAND_DELTAS={{6,10,12,4},{4,12,12,4}};
    private static final com.openggf.level.objects.ObjectPlayerQuery.PlayerVisitor<FbzChainLinkObjectInstance> UPDATE_PLAYER=(chain,entity)->{if(entity instanceof AbstractPlayableSprite player){int i=chain.participants.slot(player);if(chain.participants.get(i,1)!=0)chain.participants.set(i,1,chain.participants.get(i,1)-1);chain.updateParticipant(player,i);}};
    static final int RELEASE_Y_VELOCITY=-0x380, RELEASE_X_VELOCITY=0x200, JUMP_COOLDOWN=0x12,
            DIRECTIONAL_JUMP_COOLDOWN=0x3C;
    @RewindTransient(reason = "Immutable constructor-derived spawn configuration; recreated from ObjectSpawn.")
    private final boolean horizontalMode;
    @RewindTransient(reason = "Immutable constructor-derived spawn configuration; recreated from ObjectSpawn.")
    private final int rangePixels;
    private final FbzParticipantStateTable participants=new FbzParticipantStateTable(6); // grabbed,cooldown,step,timer,direction,phase
    private int currentLength;

    public FbzChainLinkObjectInstance(ObjectSpawn spawn){
        super(spawn,"FBZChainLink"); horizontalMode=(spawn.subtype()&0x80)!=0;
        rangePixels=horizontalMode?(spawn.subtype()&0x3F)<<4:(spawn.subtype()&0x7F)<<3;
        currentLength=horizontalMode?rangePixels:0;
    }
    @Override public void update(int frameCounter, PlayableEntity ignored){
        if(!horizontalMode){currentLength+=anyGrabbed()?2:-2;if(currentLength<0)currentLength=0;if(currentLength>rangePixels)currentLength=rangePixels;}
        services().playerQuery().visitPlayers(participationPolicy(),this,UPDATE_PLAYER);
        if(!isOnScreen(0x180)&&!anyGrabbed())setDestroyedByOffscreen();
    }
    private void updateParticipant(AbstractPlayableSprite p,int i){
        if(participants.flag(i,0)&&invalidHeldState(p)){releaseInvalid(p,i);return;}
        if(!participants.flag(i,0)){
            int objectY=spawn.y()+(horizontalMode?0:currentLength);int dx=(short)(p.getCentreX()-spawn.x()),dy=(short)(p.getCentreY()-objectY);
            boolean contact=horizontalMode?dx>=-rangePixels&&dx<rangePixels&&dy>=0&&dy<0x18:
                    dx>=-0x10&&dx<0x10&&dy>=0x90&&dy<0xA8;
            FbzChainLinkObjectInstance previous=p.getLatchedSolidObjectInstance() instanceof FbzChainLinkObjectInstance chain?chain:null;
            if(horizontalMode&&previous!=null&&!previous.horizontalMode)return;
            boolean transferableVertical=!horizontalMode&&previous!=null&&previous.horizontalMode;
            if(participants.get(i,1)==0&&contact&&(!p.isObjectControlled()||transferableVertical)){
                if(transferableVertical){int old=previous.participants.slot(p);if(previous.participants.get(old,2)!=0)return;previous.clearForStandingTransfer(p,DIRECTIONAL_JUMP_COOLDOWN);}
                participants.flag(i,0,true);p.setOnObject(true);p.setXSpeed((short)0);p.setYSpeed((short)0);p.setGSpeed((short)0);
                p.setLatchedSolidObject(spawn.objectId(),this);
                ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
                if(horizontalMode){int q=(dx+rangePixels)&~0x1F;NativePositionOps.writeXPosPreserveSubpixel(p,spawn.x()+q+0x10-rangePixels);NativePositionOps.writeYPosPreserveSubpixel(p,spawn.y()+0x12);p.setMappingFrame(0x91);p.setObjectMappingFrameControl(true);}
                else{NativePositionOps.writeXPosPreserveSubpixel(p,spawn.x());NativePositionOps.writeYPosPreserveSubpixel(p,objectY+0x9C);}
                p.setAnimationId(0x14);
                services().playSfx(Sonic3kSfx.GRAB.id);
                return;
            }
        }
        if(!participants.flag(i,0))return;
        if(p.isJumpJustPressed()){
            boolean directional=p.isUpPressed()||p.isDownPressed()||p.isLeftPressed()||p.isRightPressed();participants.flag(i,0,false);participants.set(i,1,directional?DIRECTIONAL_JUMP_COOLDOWN:JUMP_COOLDOWN);p.setOnObject(false);p.setAir(true);
            if(p.isLeftPressed())p.setXSpeed((short)-RELEASE_X_VELOCITY);if(p.isRightPressed())p.setXSpeed((short)RELEASE_X_VELOCITY);p.setYSpeed((short)RELEASE_Y_VELOCITY);p.setJumping(true);int centreX=p.getCentreX(),centreY=p.getCentreY();p.applyRollingRadii(false);p.setRolling(true);NativePositionOps.writeXPosPreserveSubpixel(p,centreX);NativePositionOps.writeYPosPreserveSubpixel(p,centreY);p.setAnimationId(2);p.setRollingJump(false);p.setFlipAngle(0);if(horizontalMode)p.setMappingFrame(0x96);p.setObjectMappingFrameControl(false);p.setLatchedSolidObject(0,null);p.setInteractSlotIndex(0);ObjectControlState.none().applyTo(p);return;
        }
        if(horizontalMode){updateHorizontal(p,i);}
        else {if(p.isLeftPressed())p.setDirection(com.openggf.physics.Direction.LEFT);if(p.isRightPressed())p.setDirection(com.openggf.physics.Direction.RIGHT);NativePositionOps.writeYPosPreserveSubpixel(p,spawn.y()+currentLength+0x9C);}
    }
    private void updateHorizontal(AbstractPlayableSprite p,int i){
        p.setObjectMappingFrameControl(true);
        if(p.isLeftPressed())p.setDirection(com.openggf.physics.Direction.LEFT);
        if(p.isRightPressed())p.setDirection(com.openggf.physics.Direction.RIGHT);
        p.setRenderFlips(p.getDirection()==com.openggf.physics.Direction.LEFT,false);
        NativePositionOps.writeYPosPreserveSubpixel(p,spawn.y()+0x12);
        if(participants.get(i,2)==0){if(p.isLeftPressed()){participants.set(i,2,4);participants.set(i,4,1);}if(p.isRightPressed()){participants.set(i,2,4);participants.set(i,4,0);}}
        if(participants.get(i,2)==0)return;
        int timer=(byte)(participants.get(i,3)-1);participants.set(i,3,timer);if(timer>=0)return;participants.set(i,3,7);
        int remaining=participants.get(i,2),index=4-remaining;
        int phase=(participants.get(i,5)&4)==0?0:1;int dx=HAND_DELTAS[phase][index];if(participants.get(i,4)!=0)dx=-dx;
        NativePositionOps.addXPosPreserveSubpixel(p,dx);p.setMappingFrame(HAND_FRAMES[phase][index]);if(remaining==2)services().playSfx(Sonic3kSfx.GRAB.id);
        participants.set(i,2,remaining-1);if(participants.get(i,2)==0){participants.set(i,5,participants.get(i,5)^4);if((spawn.subtype()&0x40)!=0){int rel=p.getCentreX()-spawn.x();if(rel<=-rangePixels+0x10||rel>=rangePixels-0x10){releaseInvalid(p,i);return;}}if(p.isLeftPressed()||p.isRightPressed()){participants.set(i,3,0);updateHorizontal(p,i);}}
    }
    private static boolean invalidHeldState(AbstractPlayableSprite p){return (p.hasRenderFlagOnScreenState()&&!p.isRenderFlagOnScreen())||p.isDebugMode()||p.isHurt()||p.getDead();}
    private void releaseInvalid(AbstractPlayableSprite p,int i){participants.flag(i,0,false);participants.set(i,1,DIRECTIONAL_JUMP_COOLDOWN);p.setOnObject(false);p.setObjectMappingFrameControl(false);p.setLatchedSolidObject(0,null);p.setInteractSlotIndex(0);ObjectControlState.none().applyTo(p);}
    void clearForStandingTransfer(AbstractPlayableSprite player,int cooldown){int i=participants.slot(player);participants.flag(i,0,false);participants.set(i,1,cooldown);}
    private boolean anyGrabbed(){for(int i=0;i<participants.size();i++)if(participants.flag(i,0))return true;return false;}
    boolean horizontalMode(){return horizontalMode;} int rangePixels(){return rangePixels;}
    ObjectPlayerParticipationPolicy participationPolicy(){return ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED;}
    ParticipantState stateForParticipant(int i){return new ParticipantState(participants.flag(i,0),participants.get(i,1),participants.get(i,2),participants.get(i,3));}
    record ParticipantState(boolean grabbed,int cooldown,int handStep,int handTimer){}
    @Override public int getPriorityBucket(){return 1;}
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_CHAIN_LINK);if(r!=null&&r.isReady())r.drawFrameIndex(Math.min(14,(currentLength>>4)+(currentLength==0?0:1)),spawn.x(),spawn.y()+currentLength,false,false);}
}
