package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_5F94C / loc_5F986 / loc_5FA58: V-int emission, allocation before Random_Number. */
public final class EndingCloudObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private int role,counter,xFixed,yFixed,xVelocity,yVelocity,mappingFrame;
    private boolean initialized;
    public EndingCloudObjectInstance(){this(new ObjectSpawn(0,0,0,0,0,false,0));}
    private EndingCloudObjectInstance(ObjectSpawn spawn){super(spawn,"EndingCloud");role=spawn.subtype();}
    private static EndingCloudObjectInstance cloud(){return new EndingCloudObjectInstance(new ObjectSpawn(0,0,0,1,0,false,0));}
    @Override public EndingCloudObjectInstance recreateForRewind(RewindRecreateContext c){return new EndingCloudObjectInstance(c.spawn());}
    private S3kEndingState ending(){return S3kEndingCoordinator.state(services());}
    private boolean knuckles(){return ((S3kZoneRuntimeState)services().zoneRuntimeState()).playerCharacter()==PlayerCharacter.KNUCKLES;}
    @Override public int getX(){return xFixed>>>16;}
    @Override public int getY(){return yFixed>>>16;}
    @Override public boolean isPersistent(){return true;}
    @Override public int getPriorityBucket(){return 6;}
    public boolean emitter(){return role==0;}
    @Override public void update(int vIntRunCount,PlayableEntity player){
        var clouds=ending().clouds();
        if(role==0){
            if(!initialized){initialized=true;services().rng().setSeed(vIntRunCount&0xFFFFFFFFL);
                try{S3kPaletteWriteSupport.applyContiguousPatch(services().paletteOwnershipRegistryOrNull(),
                        services().currentLevel(),services().graphicsManager(),"s3k-ending-clouds",
                        S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,knuckles()?0:1,0,services().rom().readBytes(0x60B3A,16));}
                catch(IOException failure){throw new UncheckedIOException(failure);}
                ending().art().queueModule(services(),Sonic3kConstants.ART_KOSM_FBZ_CLOUD_ADDR,0x3B6);return;
            }
            if(clouds.mask()==0){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if((vIntRunCount&clouds.mask())!=0)return;
            counter=(counter+1)&0xFF;
            // AllocateObject must succeed before consuming the shared Random_Number.
            var child=services().objectManager().createDynamicObject(EndingCloudObjectInstance::cloud);
            if(child==null)return;int random=services().rng().nextRaw();
            int dx=clouds.xVelocity(),dy=clouds.yVelocity();
            child.xVelocity=dx==0?0:(short)(dx+(dx<0?-(random&0x3F):random&0x3F));
            child.yVelocity=dy==0?0:(short)(dy+(dy<0?-((random>>>16)&0x3F):(random>>>16)&0x3F));
            if(dx!=0&&(dy==0||(counter&1)==0)){
                child.setX(dx<0?0x1F0:0x50);child.setY((random&0xFF)+0x80);
            }else{
                child.setY(dy<0?0x180:0x60);int x=random&0x1FF;
                if(x>=0x160)x=(x-0x160)*2+0x10;child.setX(x+0x70);
            }
            child.mappingFrame=(short)random<0?3:1;
        }else{
            initialized=true;
            if(clouds.xVelocity()!=0)xFixed+=((short)(xVelocity+clouds.xVelocity()))<<8;
            if(clouds.yVelocity()!=0)yFixed+=((short)(yVelocity+clouds.yVelocity()))<<8;
            if(getX()>=0x200||getY()>=0x200)ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }
    private void setX(int x){xFixed=(x&0xFFFF)<<16|(xFixed&0xFFFF);}
    private void setY(int y){yFixed=(y&0xFFFF)<<16|(yFixed&0xFFFF);}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(role==0||isDestroyed())return;var camera=services().camera();
        EndingSpriteRenderSupport.draw(services(),"ending-cloud",0x53518,0x3B6,knuckles()?0:1,mappingFrame,
                EndingScreenProjection.worldX(camera,getX()),EndingScreenProjection.worldY(camera,getY()),false,false,6);
    }
}
