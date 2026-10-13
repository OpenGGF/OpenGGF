package com.openggf.game.sonic3k.ending;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Create_Continue_Sprite / loc_5EDAC: native IslandLift sprite family, subtypes $41..$47. */
public final class EndingIslandLiftObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int nativeOrdinalOffset, routine, baseX, baseY, xFixed, yFixed, yVelocity;
    private boolean worldCoordinates, highPriority=true, drawAdmitted;
    private final S3kRawAnimation.State animation=new S3kRawAnimation.State();
    private transient S3kRawAnimation scripts;
    private transient List<SpriteMappingFrame> mappings;

    public EndingIslandLiftObjectInstance(int subtype,int ordinal,int x,int y) {
        this(new ObjectSpawn(x,y,0,subtype,0,false,0)); nativeOrdinalOffset=ordinal*4;
    }
    private EndingIslandLiftObjectInstance(ObjectSpawn spawn) {
        super(spawn,"EndingIslandLift"); xFixed=(spawn.x()&0xFFFF)<<16; yFixed=(spawn.y()&0xFFFF)<<16;
    }
    /** Native helper scans the lowest free slot once per table entry, without a retry pass. */
    public static void createGroup(ObjectServices services,int table,int subtype,int count) {
        try {
            for(int i=0;i<count;i++) {
                int offset=table+i*4;
                int x=services.romReader().readU16BE(offset), y=services.romReader().readU16BE(offset+2);
                int ordinal=i;
                services.objectManager().createDynamicObject(()->new EndingIslandLiftObjectInstance(subtype,ordinal,x,y));
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public int getX() { return xFixed>>>16; }
    @Override public int getY() { return yFixed>>>16; }
    public int mappingFrame() { return animation.mappingFrame; }
    public boolean worldCoordinates() { return worldCoordinates; }
    public boolean drawAdmitted() { return drawAdmitted; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public int getPriorityBucket() { return 6; } // loc_5EDAC: priority=$300
    @Override public void update(int vInt,PlayableEntity player) {
        drawAdmitted=false;
        if(routine==0) { initialize(); return; } // init dispatch has no Draw_Sprite
        if(routine==1 || routine==2) {
            if(routine==1 && (short)((services().camera().getYCopy()&0xFFFF)-baseX)>=0x10) {
                routine=2; worldCoordinates=false; yVelocity=0x10;
                setX(getX()-(services().camera().getXCopy()&0xFFFF)+0x80);
                setY(getY()-(services().camera().getYCopy()&0xFFFF)+0x80);
            } else if(routine==2) yFixed+=yVelocity<<8;
            animate(0x60276); drawAdmitted=(vInt&1)==0; return;
        }
        if(routine==3) {
            animate(0x6027C);
            var state=(EndingZoneRuntimeState)services().zoneRuntimeState();
            if(worldCoordinates) { // Player_mode3 skips sub_5FE48
                if(state.ending().flag(3)) ObjectLifetimeOps.deleteNoRespawn(this); else drawAdmitted=true;
                return;
            }
            int nextX=(baseX-(state.screen().scrollXFixed>>16)+0x80)&0xFFFF;
            setX(nextX);
            boolean hidden=nextX>=0x200;
            if(!hidden) { int nextY=(baseY-(state.screen().scrollYFixed>>16)+0x80)&0xFFFF;
                setY(nextY); hidden=nextY>=0x200; }
            if(state.ending().flag(3)) ObjectLifetimeOps.deleteNoRespawn(this); else drawAdmitted=!hidden;
            return;
        }
        animate(animation.script); drawAdmitted=true;
    }
    private void initialize() {
        baseX=getX(); baseY=getY(); int subtype=getSpawn().subtype();
        if(subtype<=0x41) { routine=1; worldCoordinates=true; highPriority=false;
            baseX=services().camera().getYCopy()&0xFFFF; }
        else if(subtype==0x42) { routine=3; worldCoordinates=((EndingZoneRuntimeState)services().zoneRuntimeState()).playerCharacter()==com.openggf.game.PlayerCharacter.KNUCKLES; animation.mappingFrame=4;
            animation.animFrameTimer=nativeOrdinalOffset; }
        else { routine=4; animation.mappingFrame=subtype<=0x44?0x1A:0;
            if(subtype==0x43) { animation.script=0x60281; if(nativeOrdinalOffset!=0)animation.animFrameTimer=0x18; }
            else if(subtype==0x44) { animation.script=0x60289; if(nativeOrdinalOffset>=8)animation.animFrameTimer=0x10; }
            else { worldCoordinates=true; highPriority=false;
                try { animation.script=services().romReader().readU32BE(0x5EEB8+(subtype-0x45)*4); }
                catch(IOException failure) { throw new UncheckedIOException(failure); }
                if(nativeOrdinalOffset!=0)animation.animFrameTimer=0x0C;
            }
        }
    }
    private void animate(int script) {
        try { if(scripts==null)scripts=S3kRawAnimation.load(services().romReader(),0x60276,0x2B); }
        catch(IOException failure) { throw new UncheckedIOException(failure); }
        scripts.animateNoSst(animation,script,()->{});
    }
    private void setX(int value) { xFixed=(value&0xFFFF)<<16|(xFixed&0xFFFF); }
    private void setY(int value) { yFixed=(value&0xFFFF)<<16|(yFixed&0xFFFF); }
    static int screenSatX(int raw,int pieceOffset){
        int sat=(raw+pieceOffset)&0x1FF;return (sat==0?1:sat)-pieceOffset;
    }
    static int screenSatY(int raw,int pieceOffset){return ((raw+pieceOffset)&0x1FF)-pieceOffset;}
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!drawAdmitted || isDestroyed())return;
        var camera=services().camera();
        int x,y;
        if(worldCoordinates){x=getX()+NativeViewportFraming.inset(camera.getWidth());y=getY();}
        else {
            try { if(mappings==null)mappings=S3kSpriteDataLoader.loadMappingFrames(services().romReader(),S3kEndingResources.MAP_ISLAND_LIFT); }
            catch(IOException failure){throw new UncheckedIOException(failure);}
            var pieces=mappings.get(animation.mappingFrame).pieces();if(pieces.isEmpty())return;
            // All27 ROM frames contain one piece or are empty. loc_1AF76 masks
            // the completed piece X, not its center; X0 becomes1. Mode5 Y is9-bit.
            // Keep captured raw words/fractions; change only the derived draw origin.
            var piece=pieces.getFirst();
            x=EndingScreenProjection.worldX(camera,screenSatX(getX(),piece.xOffset()));
            y=EndingScreenProjection.worldY(camera,screenSatY(getY(),piece.yOffset()));
        }
        EndingSpriteRenderSupport.draw(services(),"ending-island-lift",S3kEndingResources.MAP_ISLAND_LIFT,
                0x2FF,3,animation.mappingFrame,x,y,false,highPriority,6);
    }
}
