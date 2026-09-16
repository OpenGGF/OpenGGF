package com.openggf.game.sonic3k.objects;

import com.openggf.game.CheckpointState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Knuckles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** SKL $AC, Obj_SOZHyudoroCapsule and the shared loc_89C14 prison opening prefix. */
public final class SozHyudoroCapsuleObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider,SpawnRewindRecreatable {
    private boolean initialized,triggered,opened;
    private int entries;
    public SozHyudoroCapsuleObjectInstance(ObjectSpawn spawn){super(spawn,"SOZHyudoroCapsule");}
    @Override public void update(int vIntRunCount,PlayableEntity leader){
        entries++;
        if(!initialized){
            initialized=true;
            var checkpoint=services().checkpointState();boolean checkpointUsed=checkpoint!=null&&checkpoint.getLastCheckpointIndex()>0;
            opened=checkpointUsed||leader instanceof Knuckles;
            spawnAfterCurrentSibling(()->new Button(new ObjectSpawn(getX(),getY()-0x24,0,0,0,false,0),this,checkpointUsed));
            return;
        }
        if(triggered&&!opened)open();
    }
    private void open(){
        opened=true;
        if(services().objectManager()!=null)services().objectManager().setSpawnStateBit(spawn,0);
        // set_Hyudoro saves the SOZ2 start position, not the capsule position.
        if(services().checkpointState() instanceof CheckpointState checkpoint){
            try{
                int x=services().rom().read16BitAddr(0x1E3C5C),y=services().rom().read16BitAddr(0x1E3C5E);
                int oldMark=checkpoint.getStarPostActivationMark();checkpoint.saveCheckpoint(1,x,y,false);
                // Save_Level_Data writes Last_star_post_hit but no star-post respawn bit.
                checkpoint.restoreStarPostActivationMark(oldMark);
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        int[] dx={-8,8,16,-16,24,-24};
        for(int i=0;i<dx.length;i++){int subtype=i*2,x=getX()+dx[i];spawnChild(()->new EscapeGhost(new ObjectSpawn(x,getY()-4,0,subtype,0,false,0)));}
        // This is the same CreateBossExp08 prefix as the FBZ prison.
        spawnChild(()->new FbzEggPrisonExplosionController(getX(),getY()));
        int[] fragmentX={0,-16,16,-24,24};
        for(int i=0;i<fragmentX.length;i++){int subtype=i*2,x=getX()+fragmentX[i];spawnChild(()->new Fragment(new ObjectSpawn(x,getY()-8,0,subtype,0,false,0)));}
    }
    public boolean opened(){return opened;}
    @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x2B,0x18,0x18);}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false,true,false);}
    @Override public boolean isSolidFor(PlayableEntity p){return entries>=2;}
    @Override public int getPriorityBucket(){return 3;}
    @Override public int getOnScreenHalfWidth(){return 0x30;}
    @Override public int getOnScreenHalfHeight(){return 0x20;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        var r=getRenderer(Sonic3kObjectArtKeys.SOZ_GHOST_CAPSULE);
        if(r!=null&&r.isReady())r.drawFrameIndexForcedPriority(opened?1:0,getX(),getY(),false,false,-1,true);
    }
    public static final class Button extends AbstractObjectInstance implements SolidObjectProvider,SolidObjectListener,SpawnRewindRecreatable {
        private SozHyudoroCapsuleObjectInstance parentRef;private boolean recessed;private int entries;
        public Button(ObjectSpawn spawn){this(spawn,null,false);}
        private Button(ObjectSpawn spawn,SozHyudoroCapsuleObjectInstance parent,boolean recessed){super(spawn,"SOZHyudoroCapsuleButton");parentRef=parent;this.recessed=recessed;}
        @Override public void update(int vIntRunCount,PlayableEntity p){entries++;if(parentRef==null||parentRef.isDestroyed())ObjectLifetimeOps.deleteNoRespawn(this);}
        @Override public void onSolidContact(PlayableEntity p,SolidContact c,int vIntRunCount){if(!recessed&&c.standing()&&parentRef!=null){recessed=true;parentRef.triggered=true;}}
        @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x1B,4,6);}
        @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false,true,false);}
        @Override public boolean isSolidFor(PlayableEntity p){return entries>=2;}
        @Override public int getPriorityBucket(){return 4;}
        @Override public int getOnScreenHalfWidth(){return 0x10;}
        @Override public int getOnScreenHalfHeight(){return 8;}
        @Override public void appendRenderCommands(List<GLCommand> commands){var r=getRenderer(Sonic3kObjectArtKeys.SOZ_GHOST_CAPSULE);if(r!=null&&r.isReady())r.drawFrameIndexForcedPriority(recessed?12:5,getX(),getY(),false,false,-1,true);}
    }
    public static final class EscapeGhost extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private static final int[] SPEED={-0x200,0x200,-0x280,0x280,-0x300,0x300};
        private static final int[] ACCEL={-0x20,-0x20,-0x18,-0x18,-0x10,-0x10};
        private byte[] animation;
        private int xFixed,yFixed,yVelocity,animationIndex,frame;private boolean initialized;
        public EscapeGhost(ObjectSpawn spawn){super(spawn,"SOZCapsuleEscapedGhost");xFixed=spawn.x()<<16;yFixed=spawn.y()<<16;}
        @Override public void update(int vIntRunCount,PlayableEntity p){if(!initialized){initialized=true;
                try{animation=services().rom().readBytes(0x8F68E,7);}catch(IOException e){throw new UncheckedIOException(e);}
                return;}
            // The shipped capsule calls Animate_RawNoSST on Hyudoro_pg00's bytes,
            // not Animate_RawMultiDelay: timer byte zero, frames 3,1,3,2,4.
            animationIndex++;frame=animation[animationIndex+1]&255;
            if(frame==0xFC){animationIndex=0;frame=animation[1]&255;}
            int i=spawn.subtype()>>>1;yVelocity=(short)(yVelocity+ACCEL[i]);xFixed+=SPEED[i]<<8;yFixed+=yVelocity<<8;
            coarseXCullViewport(getX());
        }
        @Override public int getX(){return (xFixed>>16)&0xFFFF;}@Override public int getY(){return (yFixed>>16)&0xFFFF;}
        @Override public int getPriorityBucket(){return spawn.subtype()==0?0:4;}
        @Override public void appendRenderCommands(List<GLCommand> commands){var r=getRenderer(Sonic3kObjectArtKeys.SOZ_GHOSTS);if(r!=null&&r.isReady())r.drawFrameIndexForcedPriority(frame,getX(),getY(),SPEED[spawn.subtype()>>>1]>=0,false,-1,true);}
    }
    public static final class Fragment extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private static final int[][] VELOCITY={{0x100,-0x100},{-0x200,-0x200},{0x200,-0x200},{-0x300,-0x200},{0x300,-0x200}};
        private static final int[] FRAME={2,3,10,4,11};
        private int xFixed,yFixed,yVelocity;private boolean initialized,visible=true;
        public Fragment(ObjectSpawn spawn){super(spawn,"SOZHyudoroCapsuleFragment");xFixed=spawn.x()<<16;yFixed=spawn.y()<<16;yVelocity=VELOCITY[spawn.subtype()>>>1][1];}
        @Override public void update(int vIntRunCount,PlayableEntity p){if(!initialized){initialized=true;return;}
            xFixed+=VELOCITY[spawn.subtype()>>>1][0]<<8;yFixed+=yVelocity<<8;yVelocity=(short)(yVelocity+0x38);visible=!visible;
            if(((getY()-services().camera().getY()+0x80)&0xFFFF)>0x200)ObjectLifetimeOps.deleteNoRespawn(this);
        }
        @Override public int getX(){return (xFixed>>16)&0xFFFF;}@Override public int getY(){return (yFixed>>16)&0xFFFF;}
        @Override public int getPriorityBucket(){return 2;}
        @Override public void appendRenderCommands(List<GLCommand> commands){var r=getRenderer(Sonic3kObjectArtKeys.SOZ_GHOST_CAPSULE);if(visible&&r!=null&&r.isReady())r.drawFrameIndexForcedPriority(FRAME[spawn.subtype()>>>1],getX(),getY(),false,false,-1,true);}
    }
}
