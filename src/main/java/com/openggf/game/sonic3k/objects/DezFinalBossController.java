package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.PlayableEntity;
import com.openggf.game.CharacterKey;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

/** Obj_DEZ3_Boss: background-plane owner, not a player-damage target. */
public final class DezFinalBossController extends DezFinalBossSprite
        implements RewindRecreatable, DezFinalHand.Owner {
    private int routine;
    private int timer;
    private int callback;
    private int walkMode;
    private int walkStep;
    private int walkAngle;
    private int savedY;
    /** _unkFA82 is consumed only by this root; each hand writes one byte. */
    private int destroyedHands;

    public DezFinalBossController(ObjectSpawn spawn) { super(spawn,"DEZFinalBossController"); codePointer=0x7FD68; }
    public DezFinalBossController() { this(new ObjectSpawn(0x3C0,0xF8,0,0,0,false,0)); }
    @Override public DezFinalBossController recreateForRewind(RewindRecreateContext context) { return new DezFinalBossController(context.spawn()); }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)services().zoneRuntimeState(); }
    private int cameraX() { return DezFinalCamera.nativeX(services().camera()); }
    @Override public void update(int clock,PlayableEntity ignored) {
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        state().art().service(services());
        if(codePointer==0x80102) { sinkAndHandOff(); return; }
        switch(routine) {
            case 0 -> initialize();
            case 2 -> {
                int x=walkPlayers();
                if(x>=((cameraX()+0x98)&0xFFFF)) {
                    routine=4; services().camera().setScrollLocked(false);
                    spawnFreeChild(()->SongFadeTransitionInstance.transitionTo(Sonic3kMusic.FINAL_BOSS.id));
                }
            }
            case 4 -> {
                if(walkPlayers()>=0x360) { routine=6; stop(p1()); stop(p2()); }
            }
            case 6 -> {
                if((state().bossSignals()&2)!=0) {
                    routine=8; timer=0xBF; yVelocity=-0x80; release(p1()); release(p2());
                    spawnFreeChild(DezFinalArenaSignal::quake);
                    state().art().loadRaw(services(),0xD73CE,0x500);
                    spawnFreeChild(DezFinalArenaSignal::camera);
                }
            }
            case 8 -> { move(0); timer=(short)(timer-1); if(timer<0) routine=0xA; }
            case 0xA -> {
                if((state().bossSignals()&4)!=0) {
                    routine=0xC; savedY=getY(); walkStep=1;
                    spawnFreeChild(()->new DezFinalCore(this));
                    spawnFreeChild(()->new DezFinalEmerald(this,0,0,0));
                    for(int i=0;i<2;i++) {
                        final int subtype=i*2; var hand=spawnChild(()->new DezFinalHand(this,subtype));
                        if(hand==null || hand.isDestroyed()) break;
                    }
                }
            }
            case 0xC -> walk();
            case 0xE -> waitCallback();
            case 0x10 -> {
                if(destroyedHands==0xFFFF) {
                    routine=0x12; control|=0x20; spawnFreeChild(DezFinalArenaSignal::quake);
                    services().camera().setMinX((short)cameraX()); services().camera().setMaxX((short)cameraX());
                } else if((control&2)==0) trackPlayer();
            }
            case 0x12 -> {
                writeY(getY()+1);
                if(getY()>=0x18F) { routine=0x14; state().windowBase(0x6C0); writeX(cameraX()-0x40); }
            }
            case 0x14 -> {
                writeY(getY()-1);
                if(getY()<=0xAF) {
                    walkMode=8; fireClock=0x80; savedY=getY(); walkAngle=0;
                    services().camera().setScrollLocked(true);
                    spawnFreeChild(()->new DezFinalMouth.Button(this));
                    state().art().queueModule(services(),0x182BE6,0x4D0);
                    routine=0x16; walkStep=1;
                }
            }
            case 0x16 -> { if(!fatal()) { fire(); walk(); lockMovingCamera(); } }
            case 0x18 -> { if(!fatal()) { fire(); writeX(getX()+1); lockMovingCamera(); waitCallback(); } }
            default -> throw new IllegalStateException("DEZ final root routine "+routine);
        }
        // Obj_DEZ3_Boss's wrapper always publishes after the selected routine,
        // including the fatal callback that skips the rest of its caller.
        state().bossPosition(getX(),getY()); updateDynamicSpawn(getX(),getY());
    }
    private AbstractPlayableSprite p1() { return services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p?p:null; }
    private AbstractPlayableSprite p2() { return services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite p?p:null; }
    private void initialize() {
        routine=2; state().escapeCameraSpeed(0);
        state().art().queueModule(services(),0x181D44,0x38F);
        spawnFreeChild(DezFinalEntrySprite::robotnik);
        spawnFreeChild(()->DezFinalEntrySprite.cover(this));
        positionPlayer(p1(),0x30); positionPlayer(p2(),0x10);
    }
    private void positionPlayer(AbstractPlayableSprite player,int x) {
        if(player==null) return;
        NativePositionOps.writeXPosPreserveSubpixel(player,x);
        NativePositionOps.writeYPosPreserveSubpixel(player,0xCD+(CharacterKey.TAILS.equals(player.characterKey())?4:0));
        // Native object_control=$81: full script ownership suppresses CPU and physics.
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(0); player.setXSpeed((short)0x600); player.setGSpeed((short)0x600);
    }
    private int walkPlayers() {
        var second=p2(); if(second!=null) NativePositionOps.writeXPosPreserveSubpixel(second,second.getCentreX()+6);
        var first=p1(); if(first==null) return 0;
        NativePositionOps.writeXPosPreserveSubpixel(first,first.getCentreX()+6); return first.getCentreX()&0xFFFF;
    }
    private void stop(AbstractPlayableSprite player) {
        if(player==null) return;
        player.setXSpeed((short)0); player.setYSpeed((short)0); player.setGSpeed((short)0); player.setAnimationId(5);
    }
    private void release(AbstractPlayableSprite player) { if(player!=null) ObjectControlState.none().applyTo(player); }
    private void walk() {
        writeX(getX()+walkStep); walkAngle=(walkAngle+2)&0x7F;
        if(walkAngle!=0) { writeY(savedY-(TrigLookupTable.sinHex(walkAngle)>>4)); return; }
        // The zero-angle branch deliberately leaves the previous Y word in place.
        routine=0xE; timer=0x1F; state().screenShake().writeFlag(0x14); services().playSfx(Sonic3kSfx.THUMP_BOSS.id);
        if(walkMode==0) { callback=0x7FFD8; if(getX()>=0x540) walkMode=4; }
        else if(walkMode==4) callback=0x8000E;
        else { routine=0x18; timer=0xF; callback=0x800D0; }
    }
    private void trackPlayer() {
        routine=0xC; walkStep=1; var player=p1(); if(player==null) return;
        int x=player.getCentreX()&0xFFFF, threshold=(getX()+(x<0x610?-0x80:0x80))&0xFFFF;
        if(x<threshold) walkStep=-1;
    }
    private void waitCallback() {
        timer=(short)(timer-1); if(timer>=0) return;
        switch(callback) {
            case 0x7FFD8 -> trackPlayer();
            case 0x8000E -> { routine=0x10; control|=2; }
            case 0x800D0 -> { routine=0x16; walkStep=1; }
            default -> throw new IllegalStateException("DEZ root callback "+callback);
        }
    }
    private void fire() {
        if((control&4)!=0) return;
        fireClock=(short)(fireClock-1);
        if(fireClock<0) { fireClock=0x140; spawnFreeChild(()->new DezFinalFireball(this)); }
    }
    private void lockMovingCamera() {
        var camera=services().camera(); short limit=(short)(getX()+0x40);
        camera.setMinX(limit); camera.setMaxX(limit); camera.setXAfterRenderCopy((short)(camera.getX()+1));
    }
    private boolean fatal() {
        if((status&0x80)==0) return false;
        codePointer=0x80102; xVelocity=yVelocity=0x80; control&=~0x20;
        spawnFreeChild(DezFinalArenaSignal::quake);
        // sub_80F3A's CreateChild6_Simple keeps parent3 through the sinking sweep.
        spawnChild(()->new DezMinibossExplosionController(this,0x16));
        spawnChild(()->new DezMinibossExplosionController(this,0x16));
        state().art().loadRaw(services(),0xD771E,0x52E); return true;
    }
    private void sinkAndHandOff() {
        move(0);
        if(getY()<0x18F) state().bossPosition(getX(),getY());
        else {
            state().screenShake().writeFlag(0); state().windowBase(0); control|=0x20;
            spawnFreeChild(DezFinalEscapeShip::new);
            // Queue_Kos_Module is global and survives Go_Delete_Sprite_2. The
            // captured zone art owner retains claims after this root disappears.
            state().art().queueModule(services(),0x1607D8,0x49D);
            state().art().queueModule(services(),0x182ED8,0x100);
            control|=0x10; pendingDelete=true;
        }
        updateDynamicSpawn(getX(),getY());
    }
    int routineForTest() { return routine; }
    @Override public int handControl() { return control; }
    @Override public void handControl(int value) { control=value&0xFF; }
    @Override public void handDestroyed(int subtype) { destroyedHands|=subtype==0?0xFF00:0xFF; }
}
