package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Five {@code loc_89D78} flicker-move fragments created by a broken FBZ prison. */
public final class FbzEggPrisonFragmentInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int[][] VELOCITIES = {{0x100,-0x100},{-0x200,-0x200},{0x200,-0x200},{-0x300,-0x200},{0x300,-0x200}};
    private static final int[] FRAMES = {2,3,0xA,4,0xB};
    private int x, y, xSub, ySub, xVelocity, yVelocity;
    private boolean drawVisible;
    private boolean initialized;
    private int activeUpdates;

    public FbzEggPrisonFragmentInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEggPrisonFragment");
        x=spawn.x(); y=spawn.y();
        int index=(spawn.subtype()>>>1)%VELOCITIES.length;
        xVelocity=VELOCITIES[index][0]; yVelocity=VELOCITIES[index][1];
    }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) {
            initialized=true;
            drawVisible=true; // loc_89D78 tail-jumps to Draw_Sprite on its creation SST entry.
            return;
        }
        activeUpdates++;
        SubpixelMotion.State motion=new SubpixelMotion.State(x,y,xSub,ySub,xVelocity,yVelocity);
        SubpixelMotion.moveSprite(motion,SubpixelMotion.S3K_GRAVITY);
        x=motion.x;y=motion.y;xSub=motion.xSub;ySub=motion.ySub;yVelocity=motion.yVel;
        coarseXCull(x,0x280);
        if(tryServices()!=null&&services().camera()!=null
                &&(((y-services().camera().getY()+0x80)&0xFFFF)>0x200)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        drawVisible=!drawVisible;
    }
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getPriorityBucket(){return 2;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if (!drawVisible) return;
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE);
        if(r!=null&&r.isReady())r.drawFrameIndex(FRAMES[(spawn.subtype()>>>1)%FRAMES.length],x,y,false,false);
    }
    int activeUpdatesForTest(){return activeUpdates;}
    boolean drawVisibleForTest(){return drawVisible;}
    int xVelocityForTest(){return xVelocity;}
    int yVelocityForTest(){return yVelocity;}
}
