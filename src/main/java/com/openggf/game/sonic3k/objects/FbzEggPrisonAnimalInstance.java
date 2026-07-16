package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/** Exact {@code loc_89CE2/loc_89D02/loc_89D18} FBZ prison animal child. */
public final class FbzEggPrisonAnimalInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int[] Y_VELOCITIES = {-0x380,-0x300,-0x280,-0x200};
    private static final int[] X_OFFSETS = {0,0x10,-0x10,0x1C,-0x1C};
    private int x, y, xSub, ySub, xVelocity, yVelocity, savedYVelocity, waitTimer;
    private boolean active;
    private int mappingFrame;
    private int mappingBase;
    private boolean initialized;

    public FbzEggPrisonAnimalInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEggPrisonAnimal");
        x=spawn.x(); y=spawn.y();
    }

    private void initializeNative() {
        initialized=true;
        int subtype=spawn.subtype();
        yVelocity=Y_VELOCITIES[(subtype&6)>>>1];
        savedYVelocity=yVelocity;
        waitTimer=subtype<<2;
        xVelocity=X_OFFSETS[(subtype>>>1)%X_OFFSETS.length]<0?-0x200:0x200;
        ObjectRenderManager renderManager=getRenderManager();
        int artVariant=(subtype&2)>>>1;
        int animalIndex=0;
        if(renderManager!=null)animalIndex=artVariant==0?renderManager.getAnimalTypeA():renderManager.getAnimalTypeB();
        AnimalType definition=AnimalType.fromIndex(animalIndex);
        mappingBase=((definition.mappingSet().ordinal()*2)+artVariant)*3;
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) { initializeNative(); return; }
        if (!active) {
            if (--waitTimer < 0) active=true;
            coarseXCull(x,0x280);
            return;
        }
        SubpixelMotion.State motion=new SubpixelMotion.State(x,y,xSub,ySub,xVelocity,yVelocity);
        SubpixelMotion.objectFallXY(motion,0x20); x=motion.x;y=motion.y;xSub=motion.xSub;ySub=motion.ySub;yVelocity=motion.yVel;
        var floor=ObjectTerrainUtils.checkFloorDist(x,y,8);
        if(floor.distance()<0){y+=floor.distance();yVelocity=savedYVelocity;}
        mappingFrame=(frameCounter&8)==0?1:0;
        coarseXCull(x,0x280);
    }
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getOnScreenHalfWidth(){return 0x20;} @Override public int getOnScreenHalfHeight(){return 0x28;}
    @Override public int getPriorityBucket(){return active?1:5;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        PatternSpriteRenderer r=services().renderManager()==null?null:services().renderManager().getAnimalRenderer();
        if(r!=null&&r.isReady())r.drawFrameIndex(mappingBase+mappingFrame,x,y,xVelocity<0,false);
    }
    int yVelocityForTest(){return yVelocity;}
}
