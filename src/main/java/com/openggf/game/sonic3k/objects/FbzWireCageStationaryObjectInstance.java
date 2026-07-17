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
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Locked-on {@code Obj_FBZWireCageStationary} ($70), sonic3k.asm $3A238-$3A568. */
public final class FbzWireCageStationaryObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final com.openggf.level.objects.ObjectPlayerQuery.PlayerVisitor<FbzWireCageStationaryObjectInstance> UPDATE_PLAYER=(cage,entity)->{if(entity instanceof AbstractPlayableSprite player)cage.updateVisitedPlayer(player);};
    private static final com.openggf.level.objects.ObjectPlayerQuery.PlayerVisitor<FbzWireCageStationaryObjectInstance> BIND_PLAYER=(cage,entity)->{if(entity instanceof AbstractPlayableSprite player)cage.participants.slot(player);};
    private static final int[] TRACK_HEIGHT = {4,0,0,0,0,0,0,0,0,0,0,4};
    private static final int[] ENTRY_FRAMES = {0x49,0x54,0x53,0x52,0x53,0x52,0x53,0x52,0x53,0x52,0x54,0x49};
    private static final int[] LOOP_FRAMES = {0x70,0x70,0x71,0x71,0x72,0x72,0x73,0x73,0x74,0x74,0x75,0x75,0x76,0x76,0x77,0x77,0x6C,0x6C,0x6D,0x6D,0x6E,0x6E,0x6F,0x6F,0x70,0x70,0x71,0x71,0x72,0x72,0x73,0x73,0x74,0x74,0x75,0x75};
    private final int travelAngle;
    private final int travelExtent;
    private final FbzParticipantStateTable participants=new FbzParticipantStateTable(4); // phase, side, vertical base, held
    private boolean nativeP2AliasStanding;
    private transient int visitOrdinal;
    private transient boolean primaryDplcDirtiedD6;

    public FbzWireCageStationaryObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZWireCageStationary");
        travelAngle = (spawn.subtype() & 0xFF) << 8;
        travelExtent = travelAngle << 2;
    }

    @Override public void update(int frameCounter, PlayableEntity ignored) {
        visitOrdinal=0;primaryDplcDirtiedD6=false;
        try{services().playerQuery().visitPlayers(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,this,UPDATE_PLAYER);}
        finally{visitOrdinal=0;primaryDplcDirtiedD6=false;}
        if (!isInRangeAt(spawn.x())) setDestroyedByOffscreen();
    }

    private void updateVisitedPlayer(AbstractPlayableSprite player){
        int ordinal=visitOrdinal++,slot=participants.slot(player);
        if(ordinal==0){
            int previousMappingFrame=player.getMappingFrame();
            updatePlayer(player,slot,false,false);
            // The unpatched ROM increments d6 for P2 instead of restoring it.
            // Every mapping frame written by Obj70 has a non-empty Sonic,
            // Tails, and Knuckles DPLC, so a changed P1 frame leaves d6 at the
            // player-art base (low bits 0). The increment therefore aliases
            // P2's standing-bit test/set/clear from bit 4 to object-status bit
            // 1. BizHawk f16894: d6 $00100000 -> $00100001.
            primaryDplcDirtiedD6=player.getMappingFrame()!=previousMappingFrame;
            return;
        }
        updatePlayer(player,slot,ordinal==1,primaryDplcDirtiedD6);
    }

    private void updatePlayer(AbstractPlayableSprite p,int i,boolean nativeP2,boolean dirtyP2StandingBit){
        int dx=(short)(p.getCentreX()-spawn.x()),dy=(short)(p.getCentreY()-spawn.y());
        if(!selectedStanding(i,nativeP2,dirtyP2StandingBit)){
            boolean side=p.getXSpeed()<0;
            boolean xOk=side?(dx>=0xB0&&dx<=0xC0):(dx>=-0xC0&&dx<=-0xB0);
            boolean subtypeSide=(spawn.subtype()&0xFF)==0||(((spawn.renderFlags()&1)!=0)==side);
            if(!p.getAir()&&xOk&&dy>=-0x10&&dy<0x10&&Math.abs(p.getGSpeed())>=0x400&&!p.isObjectControlled()&&subtypeSide){
                setSelectedStanding(i,nativeP2,dirtyP2StandingBit,true);participants.set(i,1,side?1:0);participants.set(i,0,0);participants.set(i,2,0);
                applyForcedRideObjectSetRide(p);
                p.setObjectMappingFrameControl(true);
                // loc_3A2F0 writes object_control=$42: bit 6 suppresses the
                // forward terrain probe, while bit 0 remains clear so normal
                // ground movement continues through the stationary cage.
                ObjectControlState.nativeBits0To6CpuAllowedMovementActive().applyTo(p);
                p.setSuppressGroundWallCollision(true);
            }else return;
        }
        if(Math.abs(p.getGSpeed())<0x400||p.getAir()){release(p,i,true,nativeP2,dirtyP2StandingBit);return;}
        int d=dx+0xC0;
        int track=participants.get(i,0);if(track==0&&(d<0||d>=0x180)){release(p,i,false,nativeP2,dirtyP2StandingBit);return;}
        boolean advanceTrack=track!=0;
        if(track==0&&travelExtent!=0){
            if(participants.get(i,1)==0&&d>=0xC0&&p.getGSpeed()>=0){track=(d-0xC0)<<16;participants.set(i,2,0);advanceTrack=true;}
            else if(participants.get(i,1)!=0&&d<0xC0&&p.getGSpeed()<0){track=(d-0xC0+travelExtent)<<16;participants.set(i,2,-travelAngle);advanceTrack=true;}
            participants.set(i,0,track);
        }
        track=participants.get(i,0);if(advanceTrack){
            track+=p.getGSpeed()<<8;participants.set(i,0,track);int phase=track>>16;
            if(phase<0){participants.set(i,0,0);participants.set(i,1,0);participants.set(i,2,-travelAngle);updatePlayer(p,i,nativeP2,dirtyP2StandingBit);return;}
            if(phase>=travelExtent){participants.set(i,0,0);participants.set(i,1,1);participants.set(i,2,travelAngle);updatePlayer(p,i,nativeP2,dirtyP2StandingBit);return;}
            int oldX=p.getCentreX(),oldY=p.getCentreY();int angle=(phase>>1)&0xFF;
            int nx=spawn.x()+((TrigLookupTable.sinHex(angle)*0x68)>>8);int ny=spawn.y()+(phase>>2)+participants.get(i,2);
            NativePositionOps.writeXPosPreserveSubpixel(p,nx);NativePositionOps.writeYPosPreserveSubpixel(p,ny);
            p.setXSpeed((short)((nx-oldX)<<8));p.setYSpeed((short)((ny-oldY)<<8));
            int fi=((phase>>1)&0xFF);if(p.getGSpeed()<0)fi=(-fi)&0xFF;p.setMappingFrame(loopFrame(fi/0x0B));p.setAnimationId(0);return;
        }
        int idx=((d>>5)&0xF);if(idx>11)idx=11;int ny=spawn.y()+participants.get(i,2)+TRACK_HEIGHT[idx]-(p.getYRadius()-0x13);
        NativePositionOps.writeYPosPreserveSubpixel(p,ny);p.setMappingFrame(ENTRY_FRAMES[idx]);p.setAnimationId(0);
    }

    private boolean selectedStanding(int participant,boolean nativeP2,boolean dirtyP2StandingBit){return nativeP2&&dirtyP2StandingBit?nativeP2AliasStanding:participants.flag(participant,3);}
    private void setSelectedStanding(int participant,boolean nativeP2,boolean dirtyP2StandingBit,boolean standing){if(nativeP2&&dirtyP2StandingBit)nativeP2AliasStanding=standing;else participants.flag(participant,3,standing);}

    private void applyForcedRideObjectSetRide(AbstractPlayableSprite player) {
        // loc_3A2F0 sets Status_InAir before RideObject_SetRide, so its
        // Player_TouchFloor branch always runs even though entry required a
        // grounded player (sonic3k.asm:77949-77955,42027-42048).
        int savedDoubleJumpFlag=player.getDoubleJumpFlag();
        int oldYRadius=player.getYRadius();
        int centreY=player.getCentreY();
        boolean wasRolling=player.getRolling();
        transferStandingOwner(player);
        player.setLatchedSolidObject(spawn.objectId(),this);player.setInteractSlotIndex(getSlotIndex());
        player.setAngle((byte)0);player.setYSpeed((short)0);player.setGSpeed(player.getXSpeed());player.setOnObject(true);
        boolean preserveHurtStop=player.isHurt();
        player.setAir(true);
        if(preserveHurtStop)player.setAirAfterObjectHurtLanding();else player.setAir(false);
        player.setRolling(false);player.restoreDefaultRadii();player.setGroundMode(GroundMode.GROUND);
        if(wasRolling){
            int delta=oldYRadius-player.getStandYRadius();
            if(services().gameState()!=null&&services().gameState().isReverseGravityActive())delta=-delta;
            NativePositionOps.writeYPosPreserveSubpixel(player,centreY+delta);
        }
        player.setPushing(false);player.setRollingJump(false);player.setJumping(false);
        player.setFlipAngle(0);player.setFlipType(0);player.setFlipsRemaining(0);
        player.applyPostObjectLandingAbilities(savedDoubleJumpFlag);
    }
    private void release(AbstractPlayableSprite p,int i,boolean slow,boolean nativeP2,boolean dirtyP2StandingBit){setSelectedStanding(i,nativeP2,dirtyP2StandingBit,false);if(slow){p.setXSpeed((short)(p.getXSpeed()>>1));p.setAir(true);p.setFlipAngle(0xC0);p.setFlipsRemaining(0);p.setFlipSpeed(4);}p.setOnObject(false);p.setRolling(false);p.applyStandingRadii(false);p.setAnimationId(1);p.setObjectMappingFrameControl(false);p.setSuppressGroundWallCollision(false);ObjectControlState.none().applyTo(p);ObjectManager objectManager=services().objectManager();if(objectManager!=null)objectManager.releaseRidingObject(p,this);}
    private void transferStandingOwner(AbstractPlayableSprite player){if(!player.isOnObject())return;ObjectInstance previous=player.getLatchedSolidObjectInstance();if(previous==null||previous==this)return;ObjectManager objectManager=services().objectManager();if(objectManager!=null)objectManager.releaseRidingObject(player,previous);if(previous instanceof FbzWireCageObjectInstance cage)cage.clearStandingOwner(player);else if(previous instanceof FbzWireCageStationaryObjectInstance cage)cage.clearStandingOwner(player);else if(previous instanceof FbzChainLinkObjectInstance chain)chain.clearForStandingTransfer(player,0);}
    void clearStandingOwner(AbstractPlayableSprite player){
        // Rewind restores primitive participant columns but deliberately drops
        // object identities. Rebind the complete player query in canonical
        // P1/P2/extended order before interpreting native P2's aliased bit.
        services().playerQuery().visitPlayers(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                this,BIND_PLAYER);
        int participant=participants.slot(player);participants.flag(participant,3,false);if(participant==1)nativeP2AliasStanding=false;
    }
    private static int loopFrame(int i){return LOOP_FRAMES[Math.min(i,LOOP_FRAMES.length-1)];}

    int travelAngle() { return travelAngle; }
    int travelExtent() { return travelExtent; }
    int trackPositionForParticipant(int index) { return participants.get(index,0); }
    boolean heldByParticipant(int index) { return participants.flag(index,3)||(index==1&&nativeP2AliasStanding); }
    boolean normalStandingForParticipant(int index) { return participants.flag(index,3); }
    boolean nativeP2AliasStanding() { return nativeP2AliasStanding; }
    static int trackHeight(int index) { return TRACK_HEIGHT[index]; }
    static int entryFrame(int index) { return ENTRY_FRAMES[index]; }
    @Override public int romObjectCodePointerHighWord() { return 0x0003; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
