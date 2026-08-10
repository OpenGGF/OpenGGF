package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/** One of the first nine entries in {@code ChildObjDat_86B9A}. */
public final class FbzEndEggCapsuleAnimalInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int[] OFFSETS = {0,-8,8,0x10,-0x10,-0x18,0x18,-4,4};
    private static final int[] Y_VELOCITIES = {-0x380,-0x300,-0x280,-0x200};
    private int x, y, xSub, ySub, xVelocity, yVelocity, savedYVelocity, waitTimer;
    private boolean initialized;
    private boolean active;
    private int mappingFrame;
    private int mappingBase;
    private int activeUpdates;

    public FbzEndEggCapsuleAnimalInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEndEggCapsuleAnimal");
        x=spawn.x(); y=spawn.y();
    }

    private void initializeNative() {
        initialized = true;
        int subtype=spawn.subtype();
        int index=(subtype>>>1)%OFFSETS.length;
        yVelocity=Y_VELOCITIES[(subtype&6)>>>1];
        savedYVelocity=yVelocity;
        waitTimer=subtype<<2;
        xVelocity=OFFSETS[index]<0?-0x200:0x200;
        ObjectRenderManager renderManager=getRenderManager();
        int artVariant=(subtype&2)>>>1;
        int animalIndex=0;
        if(renderManager!=null)animalIndex=artVariant==0?renderManager.getAnimalTypeA():renderManager.getAnimalTypeB();
        AnimalType definition=AnimalType.fromIndex(animalIndex);
        mappingBase=((definition.mappingSet().ordinal()*2)+artVariant)*3;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) { initializeNative(); return; }
        activeUpdates++;
        if (!active) {
            if (--waitTimer < 0) active=true;
            coarseXCull(x,0x280);
            return;
        }
        SubpixelMotion.State motion=new SubpixelMotion.State(x,y,xSub,ySub,xVelocity,yVelocity);
        SubpixelMotion.objectFallXY(motion,0x20);
        x=motion.x;y=motion.y;xSub=motion.xSub;ySub=motion.ySub;yVelocity=motion.yVel;
        var floor=ObjectTerrainUtils.checkFloorDist(x,y,8);
        if(floor.distance()<0){
            y+=floor.distance();
            yVelocity=savedYVelocity;
            retargetAfterNegativeFloorHit();
        }
        mappingFrame=(vIntRunCount&8)==0?1:0;
        coarseXCull(x,0x280);
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public int getOnScreenHalfWidth(){return 0x20;}
    @Override public int getOnScreenHalfHeight(){return 0x28;}
    @Override public int getPriorityBucket(){return active?1:5;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        PatternSpriteRenderer renderer=services().renderManager()==null?null:services().renderManager().getAnimalRenderer();
        if(renderer!=null&&renderer.isReady())renderer.drawFrameIndex(mappingBase+mappingFrame,x,y,xVelocity<0,false);
    }
    int activeUpdatesForTest(){return activeUpdates;}
    int xVelocityForTest(){return xVelocity;}
    int yVelocityForTest(){return yVelocity;}

    void retargetAfterNegativeFloorHit() {
        xVelocity=-0x200;
        if (tryServices()==null || services().gameState()==null
                || !services().gameState().isEndOfLevelActive()
                || services().playerQuery()==null) return;
        PlayableEntity nearest=null;
        int nearestDistance=Integer.MAX_VALUE;
        for (PlayableEntity candidate : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            int distance=Math.abs((short)(candidate.getCentreX()-x));
            if (distance<nearestDistance) {
                nearest=candidate;
                nearestDistance=distance;
            }
        }
        if (nearest!=null && (short)(nearest.getCentreX()-x)>0) xVelocity=0x200;
    }
}
