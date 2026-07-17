package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GroundMode;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** Locked-on {@code Obj_FBZWireCage} ($6F), sonic3k.asm $39F2E-$3A220. */
public final class FbzWireCageObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final com.openggf.level.objects.ObjectPlayerQuery.PlayerVisitor<FbzWireCageObjectInstance> UPDATE_PLAYER=(cage,entity)->{if(entity instanceof AbstractPlayableSprite player)cage.updatePlayer(player,cage.participants.slot(player));};
    private static final int[] VERTICAL_FRAMES = {
            0x6D,0x6D,0x6E,0x6E,0x6F,0x6F,0x70,0x70,0x71,0x71,0x72,0x72,0x73,
            0x73,0x74,0x74,0x75,0x75,0x76,0x76,0x77,0x77,0x6C,0x6C,0x6D,0x6D};
    private final int rangePixels;
    private final boolean verticalMode;
    private final FbzParticipantStateTable participants=new FbzParticipantStateTable(3);

    public FbzWireCageObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZWireCage");
        rangePixels = (spawn.subtype() & 0x7F) << 3;
        verticalMode = (spawn.subtype() & 0x80) != 0;
    }

    @Override public void update(int frameCounter, PlayableEntity ignored) {
        services().playerQuery().visitPlayers(participationPolicy(),this,UPDATE_PLAYER);
        if (!isOnScreen(0x180) && !anyHeld()) setDestroyedByOffscreen();
    }

    private void updatePlayer(AbstractPlayableSprite p, int index) {
        if(verticalMode)updateVertical(p,index);else updateHorizontal(p,index);
    }

    private void updateHorizontal(AbstractPlayableSprite p,int i){
        int dx=(short)(p.getCentreX()-spawn.x()),dy=(short)(p.getCentreY()-spawn.y());
        if(!participants.flag(i,2)){
            if(((dx+rangePixels)&0xFFFF)>=rangePixels*2)return;
            if(((dy+0x50)&0xFFFF)<0x18)participants.set(i,1,0x28);
            int cooldown=participants.get(i,1);if(cooldown!=0){participants.set(i,1,cooldown-1);return;}
            // sub_39F7E: BHI rejects positive d0, then CMP #-$10/BLO also
            // rejects zero in unsigned ordering.  Only -$10..-$01 lands.
            int gap=spawn.y()+0x3C-(p.getCentreY()+p.getYRadius()+4);if(gap>=0||gap< -0x10)return;
            NativePositionOps.writeYPosPreserveSubpixel(p,p.getCentreY()+gap+3);
            applyRideObjectSetRide(p);
            p.setFlipType(0x80);p.setAnimationId(1);participants.set(i,0,0);participants.flag(i,2,true);if(p.getGSpeed()==0)p.setGSpeed((short)1);return;
        }
        if(p.getAir()){int next=(participants.get(i,0)+0x20)&0xFF;if(next<0x40)p.setYSpeed((short)(p.getYSpeed()>>1));else p.setYSpeed((short)0);release(p,i);return;}
        if(((dx+rangePixels)&0xFFFF)>=rangePixels*2){release(p,i);return;}if(!p.isOnObject())return;
        int a=participants.get(i,0)&0xFF;int ny=spawn.y()+((TrigLookupTable.cosHex(a)*0x28)>>8)-(p.getYRadius()-0x13);
        NativePositionOps.writeYPosPreserveSubpixel(p,ny);p.setFlipAngle(a);participants.set(i,0,(a+4)&0xFF);if(p.getGSpeed()==0)p.setGSpeed((short)1);
    }

    private void applyRideObjectSetRide(AbstractPlayableSprite player) {
        boolean wasAirborne = player.getAir();
        int savedDoubleJumpFlag = player.getDoubleJumpFlag();
        transferStandingOwner(player);
        player.setLatchedSolidObject(spawn.objectId(), this);
        player.setInteractSlotIndex(getSlotIndex());
        player.setAngle((byte) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed(player.getXSpeed());
        player.setOnObject(true);
        player.setAir(false);
        if (wasAirborne) {
            int landingCentreY = player.getCentreY();
            int landingYRadius = player.getYRadius();
            boolean wasRolling = player.getRolling();
            player.setRolling(false);
            player.applyStandingRadii(false);
            if (wasRolling) {
                // Sonic_ResetOnFloor adjusts native y_pos by the live-to-
                // standing radius delta. setRolling(false) changes the
                // engine's top-left-backed dimensions, so anchor this write
                // to the pre-change native centre rather than the shifted
                // centre produced by the dimension change.
                NativePositionOps.writeYPosPreserveSubpixel(player,
                        landingCentreY + landingYRadius - player.getStandYRadius());
            }
            player.setGroundMode(GroundMode.GROUND);
            player.setPushing(false);
            player.setRollingJump(false);
            player.applyPostObjectLandingAbilities(savedDoubleJumpFlag);
        }
    }

    private void updateVertical(AbstractPlayableSprite p,int i){
        int dx=(short)(p.getCentreX()-spawn.x()),dy=(short)(p.getCentreY()-spawn.y());
        if(!participants.flag(i,2)){
            int band=(dx+0x80)&0xFFFF;if(band>=0x100||((dy+rangePixels)&0xFFFF)>=rangePixels*2||p.isObjectControlled()||(band>=0x20&&band<0xE0))return;
            transferStandingOwner(p);
            if(p.getAir()){p.setXSpeed((short)0);p.setAir(false);}p.setOnObject(true);p.setLatchedSolidObject(spawn.objectId(),this);p.setDirection(com.openggf.physics.Direction.RIGHT);
            int angle=dx<0?0x80:0;int speed=-p.getYSpeed();if(dy<0){angle=0x40;speed=-speed;}participants.set(i,0,angle);p.setGSpeed((short)(speed==0?1:speed));
            p.setAngle((byte)(dy<0?0x40:0xC0));
            ObjectControlState.nativeBits0To6CpuAllowedMovementActive().applyTo(p);p.setSuppressGroundWallCollision(true);p.setAnimationId(1);p.setFlipAngle(0);p.setObjectMappingFrameControl(true);participants.flag(i,2,true);
        }
        dy=(short)(p.getCentreY()-spawn.y());if(((dy+rangePixels)&0xFFFF)>=rangePixels*2){release(p,i);return;}if(!p.isOnObject())return;
        p.setRenderFlips(p.getRenderHFlip(),p.getYSpeed()>=0);
        int a=participants.get(i,0)&0xFF;int nx=spawn.x()+((TrigLookupTable.cosHex(a)*0x68)>>8)-(p.getYRadius()-0x13);
        NativePositionOps.writeXPosPreserveSubpixel(p,nx);p.setMappingFrame(verticalPlayerFrame((a&0xFF)/0x0B));participants.set(i,0,(a+4)&0xFF);
    }

    private void transferStandingOwner(AbstractPlayableSprite player) {
        if(!player.isOnObject())return;
        ObjectInstance previous=player.getLatchedSolidObjectInstance();
        if(previous==null||previous==this)return;
        ObjectManager objectManager=services().objectManager();
        if(objectManager!=null)objectManager.releaseRidingObject(player,previous);
        if(previous instanceof FbzWireCageObjectInstance cage&&cage!=this)cage.clearStandingOwner(player);
        else if(previous instanceof FbzWireCageStationaryObjectInstance cage)cage.clearStandingOwner(player);
        else if(previous instanceof FbzChainLinkObjectInstance chain)chain.clearForStandingTransfer(player,0);
    }

    void clearStandingOwner(AbstractPlayableSprite player){int i=participants.slot(player);participants.flag(i,2,false);}

    private void release(AbstractPlayableSprite p, int index) {
        participants.flag(index,2,false);
        participants.set(index,0,0);participants.set(index,1,0);
        p.setOnObject(false);
        p.setAir(true);
        p.setFlipsRemaining(0);p.setFlipSpeed(4);p.setFlipType(0);if(verticalMode)p.setFlipAngle(1);p.setObjectMappingFrameControl(false);
        if(verticalMode)p.setSuppressGroundWallCollision(false);
        ObjectControlState.none().applyTo(p);
        ObjectManager objectManager=services().objectManager();
        if(objectManager!=null)objectManager.releaseRidingObject(p,this);
    }

    private boolean anyHeld() { for(int i=0;i<participants.size();i++)if(participants.flag(i,2))return true;return false; }
    int rangePixels() { return rangePixels; }
    boolean verticalMode() { return verticalMode; }
    ObjectPlayerParticipationPolicy participationPolicy() {
        return ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED;
    }
    boolean heldByParticipant(int index) { return participants.flag(index,2); }
    int angleForParticipant(int index) { return participants.get(index,0)&0xFF; }
    static int verticalPlayerFrame(int index) { return VERTICAL_FRAMES[Math.floorMod(index, VERTICAL_FRAMES.length)]; }
    @Override public int romObjectCodePointerHighWord() { return 0x0003; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
