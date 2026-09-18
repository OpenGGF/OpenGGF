package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRomOffsets;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** SKL $48, Obj_SOZRapelWire ($4A9A8)..sub_4AF80: ratchet and 17 real linked SSTs. */
public final class SozRapelWireObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int IDLE=0, EXTEND=1, WAIT=2, VERTICAL=3, FINAL_VERTICAL_INIT=4,
            FINAL_VERTICAL=5, HORIZONTAL=6, FINAL_HORIZONTAL=7, FINAL_SWING=8, RETRACT=9;
    private int routine, length=0x30, remaining, angle, tailAngle, flipAngle, mode, timer, extra;
    private int xFixed, xVelocity;
    private boolean flipped, initialized;
    private byte[] scalars;
    private Segment first, endpoint;
    public SozRapelWireObjectInstance(ObjectSpawn spawn) {
        super(spawn,"SOZRapelWire");
        xFixed=spawn.x()<<16; remaining=spawn.subtype()&15; flipped=(spawn.renderFlags()&1)!=0;
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!initialized) {
            initialized=true;
            try { scalars=services().rom().readBytes(Sonic3kSpecialStageRomOffsets.SCALAR_TABLE,512); }
            catch(IOException e) { throw new UncheckedIOException(e); }
            Segment previous=null;
            for(int i=1;i<=17;i++) {
                final Segment preceding=previous; final int index=i;
                Segment child=spawnAfterCurrentSibling(() -> new Segment(spawn,this,preceding,index));
                if(child==null || child.isDestroyed()) break;
                if(previous==null) first=child; else previous.next=child;
                previous=child;
            }
            if(previous!=null && previous.index==17) endpoint=previous;
        }
        boolean held=endpoint!=null && endpoint.mainHeld();
        boolean jump=leader instanceof AbstractPlayableSprite p && p.isLogicalJumpPressActive();
        switch(routine) {
            case IDLE -> { if(held) routine=EXTEND; }
            case EXTEND -> {
                if(!held) routine=RETRACT;
                length=(length+2)&0xFFFF;
                if(length==0xC0) { sound(Sonic3kSfx.GLIDE_LAND); routine=WAIT; mode=1; }
            }
            case WAIT -> {
                if(!held) routine=RETRACT;
                if(jump) beginStep();
            }
            case VERTICAL, FINAL_VERTICAL_INIT, FINAL_VERTICAL -> {
                if(routine==FINAL_VERTICAL_INIT) { mode=2; routine=FINAL_VERTICAL; }
                boolean finalStep=routine==FINAL_VERTICAL;
                if(!held) routine=RETRACT;
                angle=(angle+2)&255; length=(length+2)&0xFFFF;
                if(--timer<0) {
                    if(finalStep) { tailAngle=angle; routine=FINAL_SWING; }
                    else { sound(Sonic3kSfx.GLIDE_LAND); routine=WAIT; }
                }
            }
            case HORIZONTAL, FINAL_HORIZONTAL -> {
                boolean finalStep=routine==FINAL_HORIZONTAL;
                xFixed+=xVelocity<<8;
                if(!held) routine=RETRACT;
                if(timer>=0x1A) {
                    if(timer==0x1A && !finalStep) mode=1;
                    flipAngle=(flipAngle+4)&255;
                }
                angle=(angle+2)&255; length=(length+1)&0xFFFF;
                if(--timer<0) {
                    xVelocity=0;
                    if(finalStep) { mode=2; tailAngle=angle; routine=FINAL_SWING; }
                    else { sound(Sonic3kSfx.GLIDE_LAND); routine=WAIT; }
                }
                if(extra!=0) { extra--; length=(length+1)&0xFFFF; angle=(angle+2)&255; }
            }
            case FINAL_SWING -> {
                // loc_4AC98: $46 is parent3, initialized to the endpoint SST.
                // Keep swinging while its P1 capture byte is set; after release,
                // select retract but finish this pass's angle update first.
                if(!held) routine=RETRACT;
                tailAngle=(tailAngle+2)&255;
                if(tailAngle>=0xFC || tailAngle<0x86) angle=tailAngle;
            }
            case RETRACT -> retract();
            default -> throw new IllegalStateException("wire routine "+routine);
        }
    }
    private void beginStep() {
        sound(Sonic3kSfx.JUMP);
        int horizontal=spawn.subtype()&0xF0;
        remaining=(remaining-1)&255;
        if(horizontal==0) {
            timer=0x3F; angle=0; routine=remaining==0?FINAL_VERTICAL_INIT:VERTICAL;
        } else {
            timer=(horizontal<<1)-7; routine=remaining==0?FINAL_HORIZONTAL:HORIZONTAL;
            angle=0x80; flipAngle=flipped?0x80:0;
            xVelocity=flipped?0x400:-0x400; flipped=!flipped; mode=3; extra=6;
        }
    }
    private void retract() {
        if(angle!=0) {
            if(tailAngle>=0x86) {
                angle=0xFC; tailAngle=(tailAngle+2)&255;
                if((byte)tailAngle<0) return;
            } else {
                angle=(angle+2)&255;
                if(angle>=0x80 && angle<0xFC) angle=0;
                tailAngle=0;
            }
        }
        length=(length-4)&0xFFFF;
        if(length<=0x30) { length=0x30; mode=0; remaining=spawn.subtype()&15; routine=IDLE; }
    }
    private int scalar(int a) { int i=(a&255)*2; return (short)((scalars[i]&255)<<8 | scalars[i+1]&255); }
    private void sound(Sonic3kSfx sfx) { services().playSfx(sfx.id); }
    @Override public int getX() { return (xFixed>>16)&0xFFFF; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        int reference=routine==IDLE?spawn.x():getX();
        boolean out=isCoarseXOutOfRange(reference,cameraX,coarseXCullRange());
        if(out) for(Segment s=first;s!=null;s=s.next) ObjectLifetimeOps.deleteNoRespawn(s);
        return out;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var r=getRenderer(Sonic3kObjectArtKeys.SOZ_RAPEL_WIRE);
        if(r!=null && r.isReady()) r.drawFrameIndex(0x20,getX(),getY(),false,false);
    }
    public boolean isPlayerHeld(PlayableEntity player) { return endpoint!=null && endpoint.held(player); }
    public int handleX() { return endpoint==null?getX():endpoint.getX(); }
    public int handleY() { return endpoint==null?getY()+0x30:endpoint.getY(); }

    /** Each link runs after its predecessor; the seventeenth link owns both native captures. */
    public static final class Segment extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private static final int[] HANDLE={0x24,0x25,0x26,0x26,0x21,0x22,0x22,0x23,0x24,0x25,0x26,0x26,0x21,0x22,0x22,0x23};
        private static final int[] HANG={0x93,0x92,0x92,0x92,0x91,0x91,0x90,0x90,0x90,0x90,0x90,0x91,0x91,0x92,0x92,0x92};
        private static final int[] SWING={0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x91,0x92,0x92,0x92,0x92,0x92,0x92,0x91};
        private static final int[] FLIP={0xE4,0xE5,0xE6,0xE6,0xE7,0xE6,0xE6,0xE5,0xE4,0xE8,0xE9,0xE9,0xEA,0xE9,0xE9,0xE8};
        private static final int[] FACING={0,0,0,0,0,1,1,1,1,0,0,0,0,1,1,1};
        private SozRapelWireObjectInstance owner;
        private Segment preceding,next;
        private int index,xFixed,yFixed,xVelocity,yVelocity,frame,previousX,previousY;
        private final FbzParticipantStateTable players=new FbzParticipantStateTable(2);
        private boolean p1Held;
        public Segment(ObjectSpawn spawn) { this(spawn,null,null,0); }
        private Segment(ObjectSpawn spawn,SozRapelWireObjectInstance owner,Segment preceding,int index) {
            super(spawn,"SOZRapelWireLink");this.owner=owner;this.preceding=preceding;this.index=index;
            xFixed=spawn.x()<<16;yFixed=(spawn.y()+(index-1)*16)<<16;
            previousX=spawn.x();previousY=spawn.y(); frame=index==17?0x21:0;
        }
        @Override public void update(int vIntRunCount,PlayableEntity leader) {
            if(owner==null || owner.isDestroyed()) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            int oldX=getX(),oldY=getY();
            if(index==1) {
                xVelocity=(short)(owner.scalar(owner.angle+(owner.flipped?0:0x80))*0x20/owner.length);
                int a=TrigLookupTable.calcAngle((short)(xVelocity>>4),(short)owner.length);
                yVelocity=(short)(owner.scalar(a)>>2);
                // loc_4AD9A..4AE10 fixed point remainder and whole-link loops reduce
                // exactly to velocity * 16 * (length - $100), retaining all fractions.
                xFixed=owner.xFixed+xVelocity*16*(owner.length-0x100);
                yFixed=(spawn.y()<<16)+yVelocity*16*(owner.length-0x100);
                frame=linkFrame(xVelocity,yVelocity);
            } else {
                xVelocity=preceding.xVelocity;yVelocity=preceding.yVelocity;
                xFixed=preceding.xFixed+(xVelocity<<8);yFixed=preceding.yFixed+(yVelocity<<8);
                frame=preceding.frame;
                if(index==13 && owner.mode==2 && owner.tailAngle>=0x86) {
                    int a=owner.tailAngle+(owner.flipped?0x80:0);
                    int bend=(short)(TrigLookupTable.sinHex(a)*0x3C);
                    a=(bend>>8)+0x40;
                    xVelocity=(short)(owner.scalar(a+0x40)>>2);yVelocity=(short)(owner.scalar(a)>>2);
                    frame=linkFrame(xVelocity,yVelocity);
                }
            }
            if(index==17) {
                if(oldX!=getX()) previousX=oldX;
                if(oldY!=getY()) previousY=oldY;
                if(leader instanceof AbstractPlayableSprite p) { updatePlayer(p); p1Held=held(p); }
                for(PlayableEntity participant:services().playerQuery().playersFor(
                        ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
                    if(participant!=leader && participant instanceof AbstractPlayableSprite p) updatePlayer(p);
                }
                frame=owner.mode==3?HANDLE[((owner.flipAngle+4)&255)>>4]:0x21;
            }
        }
        private static int linkFrame(int vx,int vy) { return ((TrigLookupTable.calcAngle((short)vx,(short)vy)-0x40+4)&255)>>3; }
        private boolean mainHeld() { return p1Held; }
        private boolean held(PlayableEntity p) { return players.flag(players.slot(p),0); }
        private void updatePlayer(AbstractPlayableSprite p) {
            int slot=players.slot(p);
            if(players.flag(slot,0)) {
                if(!p.isRenderFlagOnScreen() || p.getDead() || p.isHurt() || p.isDebugMode()) { release(p,slot); return; }
                if(owner.mode==2 && p.isLogicalJumpPressActive()) {
                    release(p,slot);
                    int vx=(short)((getX()-previousX)<<8),vy=(short)((getY()-previousY)<<8);
                    if(p.isLeftPressed()) vx=-0x200;
                    if(p.isRightPressed()) vx=0x200;
                    p.setXSpeed((short)vx);p.setYSpeed((short)(vy-0x380));
                    p.setAir(true);p.setJumping(true);
                    // Native status/radius writes do not move x_pos/y_pos.
                    int cx=p.getCentreX(),cy=p.getCentreY();p.setRolling(true);
                    NativePositionOps.writeXPosPreserveSubpixel(p,cx);NativePositionOps.writeYPosPreserveSubpixel(p,cy);
                    p.applyCustomRadii(7,0xE);p.setAnimationId(2);
                    owner.sound(Sonic3kSfx.JUMP);return;
                }
                positionPlayer(p);
                return;
            }
            int cooldown=players.get(slot,1);
            if(cooldown>0) { players.set(slot,1,--cooldown);if(cooldown!=0)return; }
            if(((p.getCentreX()-getX()+0x10)&0xFFFF)>=0x20 ||
                    ((p.getCentreY()-getY())&0xFFFF)>=0x18 || p.isObjectControlled() ||
                    p.getDead() || p.isHurt() || p.isDebugMode()) return;
            p.setXSpeed((short)0);p.setYSpeed((short)0);p.setGSpeed((short)0);
            p.setSpindash(false);p.setAir(false);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
            p.setObjectMappingFrameControl(true);
            p.setDirection(owner.flipped?Direction.LEFT:Direction.RIGHT);p.setRenderFlips(owner.flipped,false);
            players.flag(slot,0,true);
            // loc_4B13E..4B1CE captures position and anim, but preserves the incoming
            // mapping_frame. Only the next held pass (loc_4B04A) selects a frame/DPLC.
            NativePositionOps.writeXPosPreserveSubpixel(p,getX());
            NativePositionOps.writeYPosPreserveSubpixel(p,getY()+0x14);
            p.setAnimationId(0x14);
            owner.sound(Sonic3kSfx.GRAB);
        }
        private void release(AbstractPlayableSprite p,int slot) {
            ObjectControlState.none().applyTo(p);p.setObjectMappingFrameControl(false);
            players.flag(slot,0,false);players.set(slot,1,60);
        }
        private void positionPlayer(AbstractPlayableSprite p) {
            NativePositionOps.writeXPosPreserveSubpixel(p,getX());
            NativePositionOps.writeYPosPreserveSubpixel(p,getY()+0x14);
            p.setAnimationId(owner.mode==3?0:0x14);
            int f=switch(owner.mode) {
                case 0 -> 0x92;
                case 1 -> HANG[(((owner.angle*2)&255)+8)>>4 &15];
                case 2 -> SWING[((owner.angle+8)&255)>>4];
                default -> FLIP[((owner.flipAngle+4)&255)>>4];
            };
            if(owner.mode==3) { boolean flip=FACING[((owner.flipAngle+4)&255)>>4]!=0;
                // loc_4B0DE masks status/render_flags with FC, then writes facing.
                p.setAir(false);
                p.setDirection(flip?Direction.LEFT:Direction.RIGHT);p.setRenderFlips(flip,false); }
            p.setMappingFrame(f);
        }
        @Override public boolean requiresSameFrameUpdate() { return true; }
        @Override public int getX() { return (xFixed>>16)&0xFFFF; }
        @Override public int getY() { return (yFixed>>16)&0xFFFF; }
        @Override public int getPriorityBucket() { return 5; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if(index!=17 && (short)(getY()+0x10)<=(short)spawn.y()) return;
            var r=getRenderer(Sonic3kObjectArtKeys.SOZ_RAPEL_WIRE);
            if(r!=null && r.isReady()) r.drawFrameIndex(frame,getX(),getY(),false,false);
        }
    }
}
