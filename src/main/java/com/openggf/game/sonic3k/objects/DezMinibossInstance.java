package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.S3kSpriteMaskSupport;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.bosses.S3kSharedBossCameraGate;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Obj_DEZMiniboss $A6: independent eye publishes both eight-hit phases. */
public final class DezMinibossInstance extends DezMinibossSprite implements SpawnRewindRecreatable {
    private final S3kSharedBossCameraGate cameraGate=new S3kSharedBossCameraGate();
    private final DezMinibossArtState art=new DezMinibossArtState();
    private final DezMinibossPaletteState palette=new DezMinibossPaletteState();
    private int routine;
    private int timer;
    private int callback;
    private int bounceCount;
    private int bounceVelocity;
    private int seenHits;
    private int angularMagnitude;
    private int baseY;
    private int arenaMinY=0x28C;
    private int arenaMinX=0x3680;
    private int arenaMaxX=0x36C0;

    public DezMinibossInstance(ObjectSpawn spawn) { super(spawn,"DEZMiniboss"); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false;
        if(codePointer==0) { initialize(); return; }
        art.service(services());
        switch(codePointer) {
            case 0x7DE28 -> {
                if(cameraGate.update(services().camera(),()->services().playMusic(Sonic3kMusic.MINIBOSS_S3.id), nativeFramedCameraX())) {
                    codePointer=0x7DE46; arenaMinX+=0x40; arenaMaxX+=0x100;
                }
            }
            case 0x7DE46 -> {
                word3C=((((word3C>>>8)+4)&0xFF)<<8)|(word3C&0xFF);
                if((short)word3C>=0) { codePointer=0x7DE6E; callback(0x7DE54); }
            }
            case 0x7DE6E -> {
                phaseOne();
                if((vIntRunCount&0x1F)==0) services().playSfx(Sonic3kSfx.GRAVITY_TUNNEL.id);
                if(collisionProperty!=seenHits) {
                    seenHits=collisionProperty; control|=2;
                    if(collisionProperty>=8) {
                        codePointer=0x7DF8C; routine=0; timer=0x7F; callback=0x7DFB8; status|=0x40;
                        // ROM $7EE70 is bsr.w to its own return address: call AND fallthrough.
                        spawnChild(()->new DezMinibossExplosionController(this,0xE));
                        spawnChild(()->new DezMinibossExplosionController(this,0xE));
                    }
                }
            }
            case 0x7DF8C -> {
                if(routine==0) {
                    if((vIntRunCount&7)==0) spawnChild(()->new DezMinibossDebris(this,DezMinibossDebris.SPARK,0,0,0));
                    waitCallback();
                } else { if(routine==4) move(0); waitCallback(); visible=true; }
            }
            case 0x7E0A6 -> {
                if((vIntRunCount&0x3F)==0) services().playSfx(Sonic3kSfx.WAVE_HOVER.id);
                phaseTwoHit();
                // Even the fatal hit changes only the next code pointer; this dispatch still runs.
                phaseTwo(vIntRunCount); visible=true;
            }
            case 0x85668 -> {
                if(--timer<0) {
                    spawnFreeChild(()->SongFadeTransitionInstance.createNativeLevelMusicFade(services().getCurrentLevelMusicId()));
                    finishEncounter();
                } else visible=true;
            }
            default -> throw new IllegalStateException("Unknown DEZ miniboss routine "+codePointer);
        }
        updateDynamicSpawn(getX(),getY());
    }
    private void initialize() {
        var camera=services().camera();
        int x=nativeFramedCameraX(),y=camera.getY()&0xFFFF;
        if(y<0x18C || y>0x38C || x<0x3400 || x>0x3780) {
            if(isCoarseXOutOfRange(getX(),x,coarseXCullRange())) ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            return;
        }
        var state=runtime(); state.setBossFlag(true);
        state.setCameraStoredMinX(camera.getMinX()); state.setCameraStoredMaxX(camera.getMaxX());
        state.setCameraStoredMinY(camera.getMinY()); state.setCameraStoredMaxY(camera.getMaxYTarget());
        services().fadeOutMusic();
        cameraGate.begin(camera,new S3kSharedBossCameraGate.LockBounds(0x28C,0x28C,0x3680,0x36C0),120,x);
        codePointer=0x7DE28; word3A=0x4000; word3C=0x8000;
        spawnChild(()->new DezMinibossEye(this));
        for(int i=0;i<8;i++) {
            int subtype=i*2; var orb=spawnChild(()->new DezMinibossOrb(this,subtype,false));
            if(orb==null || orb.isDestroyed()) break;
        }
        art.submit(services());
        try {
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                    services().graphicsManager(),S3kPaletteOwners.DEZ_MINIBOSS,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1,services().rom().readBytes(0x7EFFC,32));
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private int nativeFramedCameraX() {
        var camera = services().camera();
        int excess = Math.max(0, com.openggf.camera.DeadzoneGeometry.rightEdge(camera.getWidth())
                - com.openggf.camera.DeadzoneGeometry.rightEdge(320));
        return (camera.getX() + excess) & 0xFFFF;
    }
    private void phaseOne() {
        switch(routine) {
            case 0,4,8,12 -> waitCallback();
            case 2 -> {
                int velocity=(bounceVelocity+0x20)&0xFFFF;
                int offset=(word3C+velocity)&0xFFFF;
                if(offset>=0x7000) {
                    offset=0x7000; velocity=(-(velocity>>>1))&0xFFFF;
                    bounceCount=(byte)(bounceCount-1); if(bounceCount<0) callback(0x7DEF6);
                }
                bounceVelocity=velocity; word3C=offset;
            }
            case 6 -> {
                int radius=((word3A>>>8)+2)&0xFF;
                if((byte)radius<0) { routine=8; timer=0x1F; callback=0x7DF38; }
                else word3A=(radius<<8)|(word3A&0xFF);
            }
            case 10 -> {
                int radius=((word3A>>>8)-4)&0xFF;
                if(radius<=0x40) { radius=0x40; routine=12; timer=0x9F; callback=0x7DF6E; }
                word3A=(radius<<8)|(word3A&0xFF);
            }
            case 14 -> { word3C=((((word3C>>>8)-4)&0xFF)<<8)|(word3C&0xFF); waitCallback(); }
            default -> throw new IllegalStateException("Unknown first DEZ phase "+routine);
        }
    }
    private void waitCallback() { timer=(short)(timer-1); if(timer<0) callback(callback); }
    private void callback(int address) {
        switch(address) {
            case 0x7DE54 -> { routine=0; word3C=0; timer=0x7F; callback=0x7DEA6; }
            case 0x7DEA6 -> { routine=2; timer=0xF; callback=0x7DEF6; bounceCount=2; bounceVelocity=0; }
            case 0x7DEF6 -> { routine=4; timer=0x9F; callback=0x7DF0C; }
            case 0x7DF0C -> routine=6;
            case 0x7DF38 -> routine=10;
            case 0x7DF6E -> { routine=14; timer=0x1B; callback=0x7DE54; }
            case 0x7DFB8 -> {
                routine=2; timer=0x5F; callback=0x7DFDE; runtime().setEventsFg4(0xFF);
                for(int i=0;i<2;i++) {
                    int subtype=i*2,dx=i==0?-0x28:0x28;
                    var child=spawnChild(()->new DezMinibossDebris(this,DezMinibossDebris.COVER,subtype,dx,0));
                    if(child==null || child.isDestroyed()) break;
                }
                visible=true;
            }
            case 0x7DFDE -> { routine=4; control|=0x20; xVelocity=-0x80; yVelocity=0x40; timer=0x1F; callback=0x7E016; }
            case 0x7E016 -> {
                routine=6; timer=0x1F; callback=0x7E044; word3A=0; word3C=0x200; angularMagnitude=0x100;
                DezMinibossArm.spawnPair(this);
            }
            case 0x7E044 -> {
                codePointer=0x7E0A6; routine=0; status&=~0x40; collisionProperty=seenHits=0;
                baseY=getY(); control&=~0x20; spawnFreeChild(()->new Mask(this)); resetSwing();
            }
            default -> throw new IllegalStateException("Unknown DEZ callback "+address);
        }
    }
    private void phaseTwoHit() {
        if(collisionProperty==seenHits) return;
        seenHits=collisionProperty;
        if(collisionProperty<8) {
            angularMagnitude=0x800; palette.start(services()); boolean active=(control&2)!=0; control|=2;
            if(!active) spawnChild(()->new DezMinibossBeam(this,arenaMinY));
        } else {
            status|=0x80; codePointer=0x85668;
            // CreateBossExp00 runs Obj_BossExpControl1 at the copied position;
            // unlike CreateBossExp0E it never follows the soon-deleted boss.
            spawnChild(()->new DezMinibossExplosionController(getX(),getY(),0));
            services().levelGamestate().pauseTimer(); timer=0x3F; services().gameState().addScore(1000);
        }
    }
    private void phaseTwo(int vIntRunCount) {
        switch(routine) {
            case 0 -> {
                swing(); move(0);
                if(xVelocity>0 && getX()>arenaMaxX || xVelocity<0 && getX()<arenaMinX) xVelocity=-xVelocity;
                target(vIntRunCount); if((control&2)!=0) startAttack();
            }
            case 2 -> {
                if(palette.tick(services())) { routine=4; xVelocity=0; control=(control&~2)|8; }
                swing(); move(0);
                var p=services().playerQuery().mainPlayerOrNull();
                xVelocity=p!=null && (p.getCentreX()&0xFFFF)>getX()?0x80:-0x80;
                if(xVelocity>0 && getX()>=arenaMaxX || xVelocity<0 && getX()<=arenaMinX) xVelocity=0;
                target(vIntRunCount);
            }
            case 4 -> {
                swing(); move(0);
                if((control&2)!=0) { startAttack(); control&=~8; }
                else if((control&8)==0) { routine=6; xVelocity=0; yVelocity=-0x400; control|=4; }
            }
            case 6 -> {
                if((control&2)!=0) routine=8;
                else { yVelocity=(short)(yVelocity+0x20); move(0); if(yVelocity>=0) routine=8; }
            }
            case 8 -> {
                if((control&4)==0) {
                    routine=10; yVelocity=0x100; word3A=0;
                    if((control&2)==0) { control|=0x40; angularMagnitude=word3C=0x100; }
                    else { control|=0x80; angularMagnitude=word3C=0x800; }
                }
            }
            case 10 -> {
                move(0);
                if(getY()>=baseY) {
                    writeY(baseY); routine=0; control&=~0x40;
                    xVelocity=primaryToRight()?0x80:-0x80; resetSwing();
                }
            }
            default -> throw new IllegalStateException("Unknown second DEZ phase "+routine);
        }
    }
    private void startAttack() { routine=2; control|=0x80; }
    private void resetSwing() { yVelocity=0x80; control&=~1; }
    private void swing() {
        int velocity=(short)yVelocity;
        if((control&1)==0) {
            velocity=(short)(velocity-8);
            if(velocity>-0x80) { yVelocity=velocity; return; }
            control|=1;
        }
        velocity=(short)(velocity+8);
        if(velocity>=0x80) { control&=~1; velocity=(short)(velocity-8); }
        yVelocity=velocity;
    }
    private boolean primaryToRight() {
        var p=services().playerQuery().mainPlayerOrNull();
        return p!=null && (short)(getX()-p.getCentreX())<0;
    }
    private void target(int vIntRunCount) {
        var p=services().playerQuery().mainPlayerOrNull();
        if(p!=null && p.isOnObject()) word3C=angularMagnitude;
        else if((vIntRunCount&0x1F)==0) word3C=(primaryToRight()?-angularMagnitude:angularMagnitude)&0xFFFF;
    }
    private void finishEncounter() {
        spawnFreeChild(DezMinibossTransport::new); control|=0x20;
        int[] dx={-16,16,-12,12},dy={0,0,24,24};
        for(int i=0;i<4;i++) {
            int subtype=i*2,x=dx[i],y=dy[i];
            var child=spawnChild(()->new DezMinibossDebris(this,DezMinibossDebris.BODY,subtype,x,y));
            if(child==null || child.isDestroyed()) break;
        }
        control|=0x10; services().gameState().setEndOfLevelActive(true);
        int slot=ObjectLifetimeOps.detachSlotForTransfer(this); ObjectLifetimeOps.deleteNoRespawn(this);
        var flow=new S3kBossDefeatSignpostFlow(getX(),0,S3kBossDefeatSignpostFlow.CleanupAction.NONE,
                1,0,0,0,false,false,true).withNativeControlSlot(slot);
        ObjectLifetimeOps.addReplacementAtTransferredSlot(services().objectManager(),flow,slot);
    }
    private S3kDezZoneRuntimeState runtime() { return (S3kDezZoneRuntimeState)services().zoneRuntimeState(); }
    int routineForTest() { return routine; }

    /** Obj_SpriteMask $89: no draw on init, then parent bit 5 owns immediate deletion. */
    static final class Mask extends DezMinibossSprite implements RewindRecreatable {
        private DezMinibossSprite parent;
        private boolean initialized;
        private Mask(ObjectSpawn spawn) { super(spawn,"DEZMinibossMask"); }
        Mask(DezMinibossSprite parent) { this(new ObjectSpawn(0x3740,0x360,0,0x89,0,false,0)); this.parent=parent; }
        @Override public Mask recreateForRewind(RewindRecreateContext context) { return new Mask(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) {
            visible=false;
            if(!initialized) { initialized=true; priority=1; halfWidth=0x20; halfHeight=0x20; frame=8; return; }
            if(parent!=null && (parent.control&0x20)!=0) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            visible=true;
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if(!visible || isDestroyed()) return;
            try { S3kSpriteMaskSupport.submitFrame(services().graphicsManager(),services().rom(),8,getX(),getY()); }
            catch(IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
}
