package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Locked-on {@code Obj_FBZWireCageStationary} ($70), sonic3k.asm $3A238-$3A568. */
public final class FbzWireCageStationaryObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final com.openggf.level.objects.ObjectPlayerQuery.PlayerVisitor<FbzWireCageStationaryObjectInstance> UPDATE_PLAYER=(cage,entity)->{if(entity instanceof AbstractPlayableSprite player)cage.updatePlayer(player,cage.participants.slot(player));};
    private static final int[] TRACK_HEIGHT = {4,0,0,0,0,0,0,0,0,0,0,4};
    private static final int[] ENTRY_FRAMES = {0x49,0x54,0x53,0x52,0x53,0x52,0x53,0x52,0x53,0x52,0x54,0x49};
    private static final int[] LOOP_FRAMES = {0x70,0x70,0x71,0x71,0x72,0x72,0x73,0x73,0x74,0x74,0x75,0x75,0x76,0x76,0x77,0x77,0x6C,0x6C,0x6D,0x6D,0x6E,0x6E,0x6F,0x6F,0x70,0x70,0x71,0x71,0x72,0x72,0x73,0x73,0x74,0x74,0x75,0x75};
    @RewindTransient(reason = "Immutable constructor-derived spawn configuration; recreated from ObjectSpawn.")
    private final int travelAngle;
    @RewindTransient(reason = "Immutable constructor-derived spawn configuration; recreated from ObjectSpawn.")
    private final int travelExtent;
    private final FbzParticipantStateTable participants=new FbzParticipantStateTable(4); // phase, side, vertical base, held

    public FbzWireCageStationaryObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZWireCageStationary");
        travelAngle = (spawn.subtype() & 0xFF) << 8;
        travelExtent = travelAngle << 2;
    }

    @Override public void update(int frameCounter, PlayableEntity ignored) {
        services().playerQuery().visitPlayers(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,this,UPDATE_PLAYER);
        if (!isOnScreen(0x180) && !anyHeld()) setDestroyedByOffscreen();
    }

    private void updatePlayer(AbstractPlayableSprite p,int i){
        int dx=(short)(p.getCentreX()-spawn.x()),dy=(short)(p.getCentreY()-spawn.y());
        if(!participants.flag(i,3)){
            boolean side=p.getXSpeed()<0;
            boolean xOk=side?(dx>=0xB0&&dx<=0xC0):(dx>=-0xC0&&dx<=-0xB0);
            boolean subtypeSide=(spawn.subtype()&0xFF)==0||(((spawn.renderFlags()&1)!=0)==side);
            if(!p.getAir()&&xOk&&dy>=-0x10&&dy<0x10&&Math.abs(p.getGSpeed())>=0x400&&!p.isObjectControlled()&&subtypeSide){
                participants.flag(i,3,true);participants.set(i,1,side?1:0);participants.set(i,0,0);participants.set(i,2,0);
                p.setOnObject(true);p.setAir(false);p.setObjectMappingFrameControl(true);ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
            }else return;
        }
        if(Math.abs(p.getGSpeed())<0x400||p.getAir()&&!p.isOnObject()){release(p,i,true);return;}
        int d=dx+0xC0;
        int track=participants.get(i,0);if(track==0&&(d<0||d>=0x180)){release(p,i,false);return;}
        if(track==0&&travelExtent!=0){
            if(participants.get(i,1)==0&&d>=0xC0&&p.getGSpeed()>=0){track=(d-0xC0)<<16;participants.set(i,2,0);}
            else if(participants.get(i,1)!=0&&d<0xC0&&p.getGSpeed()<0){track=(d-0xC0+travelExtent)<<16;participants.set(i,2,-travelAngle);}
            participants.set(i,0,track);
        }
        track=participants.get(i,0);if(track!=0){
            track+=p.getGSpeed()<<8;participants.set(i,0,track);int phase=track>>16;
            if(phase<0){participants.set(i,0,0);participants.set(i,1,0);participants.set(i,2,-travelAngle);updatePlayer(p,i);return;}
            if(phase>=travelExtent){participants.set(i,0,0);participants.set(i,1,1);participants.set(i,2,travelAngle);updatePlayer(p,i);return;}
            int oldX=p.getCentreX(),oldY=p.getCentreY();int angle=(phase>>1)&0xFF;
            int nx=spawn.x()+((TrigLookupTable.sinHex(angle)*0x68)>>8);int ny=spawn.y()+(phase>>2)+participants.get(i,2);
            NativePositionOps.writeXPosPreserveSubpixel(p,nx);NativePositionOps.writeYPosPreserveSubpixel(p,ny);
            p.setXSpeed((short)((nx-oldX)<<8));p.setYSpeed((short)((ny-oldY)<<8));
            int fi=((phase>>1)&0xFF);if(p.getGSpeed()<0)fi=(-fi)&0xFF;p.setMappingFrame(loopFrame(fi/0x0B));p.setAnimationId(0);return;
        }
        int idx=((d>>5)&0xF);if(idx>11)idx=11;int ny=spawn.y()+participants.get(i,2)+TRACK_HEIGHT[idx]-(p.getYRadius()-0x13);
        NativePositionOps.writeYPosPreserveSubpixel(p,ny);p.setMappingFrame(ENTRY_FRAMES[idx]);p.setAnimationId(0);
    }
    private void release(AbstractPlayableSprite p,int i,boolean slow){participants.flag(i,3,false);if(slow){p.setXSpeed((short)(p.getXSpeed()>>1));p.setAir(true);p.setFlipAngle(0xC0);p.setFlipsRemaining(0);p.setFlipSpeed(4);}p.setOnObject(false);p.setRolling(false);p.applyStandingRadii(false);p.setAnimationId(1);p.setObjectMappingFrameControl(false);ObjectControlState.none().applyTo(p);}
    void clearStandingOwner(AbstractPlayableSprite player){participants.flag(participants.slot(player),3,false);}
    private boolean anyHeld(){for(int i=0;i<participants.size();i++)if(participants.flag(i,3))return true;return false;}
    private static int loopFrame(int i){return LOOP_FRAMES[Math.min(i,LOOP_FRAMES.length-1)];}

    int travelAngle() { return travelAngle; }
    int travelExtent() { return travelExtent; }
    int trackPositionForParticipant(int index) { return participants.get(index,0); }
    boolean heldByParticipant(int index) { return participants.flag(index,3); }
    static int trackHeight(int index) { return TRACK_HEIGHT[index]; }
    static int entryFrame(int index) { return ENTRY_FRAMES[index]; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
