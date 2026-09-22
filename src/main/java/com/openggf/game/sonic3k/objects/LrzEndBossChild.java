package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.LrzBossActState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** ROM LRZ end-boss child routines: crest, pilot, mine, launch plume and airborne mine trail. */
public final class LrzEndBossChild extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider, RomObjectCodePointerProvider {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0,0,0,0,0,0);
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private AbstractObjectInstance parent;
    private int code, dx, dy, priority=5, palette=1, width, height, collision, sampleIndex;
    private boolean initialized, pendingDelete, hidden, flipped, displayed, renderOnScreen;
    private transient S3kRawAnimation raw;

    public LrzEndBossChild(ObjectSpawn spawn) {
        super(spawn,"LRZEndBossChild"); motion.x=spawn.x(); motion.y=spawn.y(); code=switch(spawn.subtype()) {
            // Rewind schema discovery constructs an inert zero-spawn probe.
            case 0 -> 0;
            case 0xE6 -> 0x79BE6;
            case 0x0C -> 0x79C0C;
            case 0xAE -> 0x79AAE;
            case 0x7E -> 0x79B7E;
            case 0xAC -> 0x79BAC;
            default -> throw new IllegalArgumentException("Unknown LRZ child identity " + spawn.subtype());
        };
    }
    LrzEndBossChild(AbstractObjectInstance parent,int code,int dx,int dy) {
        this(new ObjectSpawn(parent.getX()+dx,parent.getY()+dy,0,code,0,false,0));
        this.parent=code==0x79BAC?null:parent; this.dx=dx; this.dy=dy;
    }
    private LrzBossActState state() {
        return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct();
    }
    private LrzEndBossObjectInstance boss() { return (LrzEndBossObjectInstance)parent; }
    @Override public void update(int clock,PlayableEntity player) {
        if(pendingDelete) { ObjectLifetimeOps.expireDynamic(this); return; }
        if(!initialized) initialize();
        displayed=true;
        switch(code) {
            case 0x79BF6 -> {
                follow(false); raw().animateNoSst(animation,0x7A1C2,()->pendingDelete=true);
                if(boss().childrenReleased()) { pendingDelete=true; hidden=true; }
            }
            case 0x79C1C -> {
                follow(true); animation.mappingFrame=boss().hitPending()?0x10:boss().defeated()?0x11:0xF;
                if(boss().childrenReleased()) { pendingDelete=true; hidden=true; }
            }
            case 0x79B96 -> {
                follow(false); animation.script=0x7A1C7;
                raw().animateMultiDelay(animation,()->pendingDelete=true);
            }
            case 0x79BCA -> {
                animation.script=0x7A1D8; raw().animateMultiDelay(animation,()->pendingDelete=true);
                motion.yVel=(short)(motion.yVel-0x10); SubpixelMotion.moveSprite2(motion);
            }
            case 0x79AC4 -> airborneMine(clock);
            case 0x79B22 -> fallingMine();
            case 0x79B54 -> floatingMine();
            default -> throw new IllegalStateException("LRZ child code " + Integer.toHexString(code));
        }
    }
    private void initialize() {
        initialized=true;
        switch(code) {
            case 0x79BE6 -> { attributes(0x7A15E); code=0x79BF6; }
            case 0x79C0C -> { attributes(0x7A166); palette=0; code=0x79C1C; }
            case 0x79AAE -> { attributes(0x7A14C); motion.yVel=-0x800; code=0x79AC4; }
            case 0x79B7E -> { attributes(0x7A152); code=0x79B96; }
            case 0x79BAC -> { attributes(0x7A158); motion.yVel=0x100; code=0x79BCA; }
            default -> throw new IllegalStateException("LRZ child initializer " + Integer.toHexString(code));
        }
    }
    private void attributes(int address) {
        try {
            byte[] bytes=services().rom().readBytes(address,6);
            priority=(((bytes[0]&255)<<8)|(bytes[1]&255))/0x80;
            width=bytes[2]&255; height=bytes[3]&255; animation.mappingFrame=bytes[4]&255; collision=bytes[5]&255;
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private S3kRawAnimation raw() {
        if(raw==null) try { raw=S3kRawAnimation.load(RomByteReader.fromRom(services().rom()),0x7A1C2,0x2C); }
        catch(IOException failure) { throw new UncheckedIOException(failure); }
        return raw;
    }
    private void follow(boolean adjusted) {
        flipped=adjusted && boss().flipped();
        motion.x=parent.getX()+(flipped?-dx:dx); motion.y=parent.getY()+dy;
    }
    private boolean parentDefeated() {
        if(!boss().defeated()) return false;
        explode(); return true;
    }
    private void airborneMine(int clock) {
        if(parentDefeated()) return;
        if(motion.yVel>=0) {
            code=0x79B22; priority=2;
            int offset=boss().flipped()?0x140:0x40;
            motion.x=0x9E0+offset; sampleIndex=offset>>>1;
        }
        if((clock&3)==0) spawnChild(()->new LrzEndBossChild(this,0x79BAC,0,0x10));
        SubpixelMotion.moveSprite(motion,0x20);
        cull();
    }
    private void fallingMine() {
        if(parentDefeated()) return;
        SubpixelMotion.moveSprite(motion,0x20);
        int surface=0x612-(state().lavaHeight(sampleIndex)-0x30);
        if((getY()&65535)<(surface&65535)) { cull(); return; }
        code=0x79B54; motion.yVel=0;
        floatingMine(); // loc_79B4A falls through, including the second movement call.
    }
    private void floatingMine() {
        if(parentDefeated()) return;
        if((state().streamDirection()&255)==0 && !renderOnScreen) { ObjectLifetimeOps.expireDynamic(this); return; }
        int velocity=(state().lavaAmplitude()+(state().driftClock()>>>2))*2;
        motion.xVel=(short)((state().streamDirection()&0xFF00)==0?-velocity:velocity);
        SubpixelMotion.moveSprite2(motion);
        motion.y=0x612-(state().lavaHeight(((getX()-0x9E0)&65535)>>>1)-0x30);
        if(!boss().hitPending() && ((getX()-boss().getX()+0x30)&65535)<0x60
                && ((getY()-boss().getY()+0x30)&65535)<0x60 && boss().publishMineHit()) {
            explode(); return;
        }
        cull();
    }
    private void explode() {
        spawnChild(()->new LrzEndBossExplosion(this,6));
        ObjectLifetimeOps.expireDynamic(this);
        services().playSfx(Sonic3kSfx.THUMP_BOSS.id);
    }
    private void cull() {
        int back=(services().camera().getX()-0x80)&0xFF80;
        if((((getX()&0xFF80)-back)&65535)>0x280) { pendingDelete=true; hidden=true; }
    }
    @Override public void refreshPostCameraRenderState() {
        if(displayed) { displayed=false; renderOnScreen=!hidden && isWithinRenderSpriteBounds(width,height); }
    }
    @Override public int getX() { return (short)motion.x; }
    @Override public int getY() { return (short)motion.y; }
    @Override public int getPriorityBucket() { return priority; }
    @Override public int getOnScreenHalfWidth() { return width; }
    @Override public int getOnScreenHalfHeight() { return height; }
    @Override public int getCollisionFlags() { return hidden?0:collision; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int romObjectCodePointerHighWord() { return 7; }
    // These ROM routines own their deletion; none calls placement-range unloading.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(hidden) return;
        var renderer=getRenderer(Sonic3kObjectArtKeys.LRZ_END_BOSS);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexWithPaletteBase(animation.mappingFrame,getX(),getY(),flipped,false,palette);
    }
}
