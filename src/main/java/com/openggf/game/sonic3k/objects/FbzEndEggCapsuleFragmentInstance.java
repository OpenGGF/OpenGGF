package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One of the five constant-velocity {@code ChildObjDat_86B7A} fragments. */
public final class FbzEndEggCapsuleFragmentInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int[][] VELOCITIES = {{0x100,-0x100},{-0x200,-0x200},{0x200,-0x200},{-0x300,-0x200},{0x300,-0x200}};
    private static final int[] FRAMES = {2,3,0xA,4,0xB};
    private int x, y, xSub, ySub, xVelocity, yVelocity;
    private boolean initialized;
    private boolean drawVisible;
    private int activeUpdates;

    public FbzEndEggCapsuleFragmentInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEndEggCapsuleFragment");
        x = spawn.x(); y = spawn.y();
        int index = (spawn.subtype() >>> 1) % VELOCITIES.length;
        xVelocity = VELOCITIES[index][0]; yVelocity = VELOCITIES[index][1];
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            drawVisible = true; // loc_867D6 tail-jumps to Draw_Sprite on its creation SST entry.
            return;
        }
        activeUpdates++;
        SubpixelMotion.State motion = new SubpixelMotion.State(x,y,xSub,ySub,xVelocity,yVelocity);
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
        x=motion.x; y=motion.y; xSub=motion.xSub; ySub=motion.ySub; yVelocity=motion.yVel;
        coarseXCull(x, 0x280);
        if (tryServices()!=null && services().camera()!=null
                && (((y-services().camera().getY()+0x80)&0xFFFF)>0x200)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // Obj_FlickerMove starts with bit 6 clear: the first bchg sets it and returns without drawing.
        drawVisible = !drawVisible;
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public int getPriorityBucket(){return 3;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if (!drawVisible) return;
        PatternSpriteRenderer renderer=getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if(renderer!=null&&renderer.isReady())renderer.drawFrameIndex(
                FRAMES[(spawn.subtype()>>>1)%FRAMES.length],x,y,false,false);
    }
    int activeUpdatesForTest(){return activeUpdates;}
    boolean drawVisibleForTest(){return drawVisible;}
    int xVelocityForTest(){return xVelocity;}
    int yVelocityForTest(){return yVelocity;}
}
