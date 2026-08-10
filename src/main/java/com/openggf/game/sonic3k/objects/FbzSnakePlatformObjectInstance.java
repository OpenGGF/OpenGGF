package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZSnakePlatform} ($75), sonic3k.asm $3B52E-$3B6CE. */
public final class FbzSnakePlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    private static final int[][] ROUTES = {
            {0x10F1,0x118C,0x118C,0x770,0x10F1,0x770,-1,0x3C},
            {0x1174,0x120F,0x1174,0x7F0,0x120F,0x7F0,-1,0x3C},
            {0xC56,0xD64,0xD64,0x420,0xC56,0x420,0xC56,0x510,0xD64,0x510,0xD64,0x420,-1,0},
            {0xB64,0xCE3,0xC1A,0x317,0xCE3,0x317,0xCE3,0x3C8,0xB64,0x3C8,0xB64,0x317,0xC1A,0x317,-1,0},
            {0xB64,0xCE3,0xC1B,0x3C8,0xB65,0x3C8,0xB65,0x317,0xCE3,0x317,0xCE3,0x3C8,0xC1B,0x3C8,-1,0},
            {0xC56,0xD64,0xC56,0x510,0xD64,0x510,0xD64,0x420,0xC56,0x420,0xC56,0x510,-1,0},
            {0x1D79,0x1EEB,0x1EEB,0x213,0x1EEB,0x400,0x1D79,0x400,0x1D79,0x319,0x1E82,0x319,0x1E82,0x369,0x1E1E,0x369,0x1E1E,0x213,0x1EEB,0x213,-1,0},
            {0x1D79,0x1EEB,0x1E82,0x319,0x1E82,0x369,0x1E1E,0x369,0x1E1E,0x213,0x1EEB,0x213,0x1EEB,0x400,0x1D79,0x400,0x1D79,0x319,0x1E82,0x319,-1,0}
    };
    private final int route;
    private int delay;
    private boolean child;
    private int familySlot=-1;
    private FbzSnakePlatformObjectInstance parent;
    private boolean childrenSpawned;
    private int delayRemaining;
    private int routeIndex;
    private int x,y,xFixed,yFixed,xVelocity,yVelocity,wait;
    private int cullRight,cullWidth;

    public FbzSnakePlatformObjectInstance(ObjectSpawn spawn){this(spawn,1,false,null);}
    private FbzSnakePlatformObjectInstance(ObjectSpawn spawn,int delay,boolean child,FbzSnakePlatformObjectInstance parent){
        super(spawn,"FBZSnakePlatform");this.route=spawn.subtype()&7;this.delay=delay;this.delayRemaining=delay;this.child=child;this.parent=parent;this.familySlot=parent==null?-1:parent.familySlot;
        int[] r=ROUTES[route];cullRight=r[1];cullWidth=((r[1]-r[0])&0xFF80)+0x300;routeIndex=2;snapToFirstAndTarget();
    }
    @Override public void update(int vIntRunCount,PlayableEntity player){
        if(child&&parent!=null&&parent.isDestroyed()){ObjectLifetimeOps.expireDynamic(this);return;}
        if(!child&&!childrenSpawned){childrenSpawned=true;if(familySlot<0)familySlot=getSlotIndex();int nextDelay=0x19;for(int attempt=0;attempt<3;attempt++){final int d=nextDelay;FbzSnakePlatformObjectInstance made=spawnChild(()->new FbzSnakePlatformObjectInstance(spawn,d,true,this));if(!made.isDestroyed())nextDelay+=0x18;}}
        if(delayRemaining>0){delayRemaining--;if(delayRemaining>0)return;}if(wait>0){wait--;if(wait>0)return;}
        xFixed+=xVelocity;yFixed+=yVelocity;x=xFixed>>8;y=yFixed>>8;
        int[] r=ROUTES[route];if(routeIndex<r.length-1&&x==r[routeIndex]&&y==r[routeIndex+1]){routeIndex+=2;selectTarget();}
        updateDynamicSpawn(x,y);if(isCustomOutOfRange(cameraX()))setDestroyedByOffscreen();
    }
    @Override public FbzSnakePlatformObjectInstance recreateForRewind(RewindRecreateContext ctx){FbzSnakePlatformObjectInstance restored=new FbzSnakePlatformObjectInstance(ctx.spawn(),1,false,null);if(ctx.state()!=null&&ctx.state().compactGenericState()!=null)GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored,ctx.state().compactGenericState());if(restored.child)restored.parent=findRestoredParent(ctx.objectManager(),restored.familySlot);return restored;}
    private static FbzSnakePlatformObjectInstance findRestoredParent(ObjectManager manager,int familySlot){if(manager==null)return null;for(ObjectInstance object:manager.getActiveObjects())if(object instanceof FbzSnakePlatformObjectInstance candidate&&!candidate.child&&!candidate.isDestroyed()&&candidate.familySlot==familySlot)return candidate;return null;}
    @Override protected void afterRewindRestoreSettled(){if(child&&parent==null)parent=findRestoredParent(services().objectManager(),familySlot);}
    private void snapToFirstAndTarget(){int[] r=ROUTES[route];x=r[2];y=r[3];xFixed=x<<8;yFixed=y<<8;routeIndex=4;selectTarget();}
    private void selectTarget(){int[] r=ROUTES[route];if(routeIndex>=r.length||r[routeIndex]<0){wait=r[Math.min(routeIndex+1,r.length-1)];snapToFirstAndTarget();return;}int tx=r[routeIndex],ty=r[routeIndex+1];xVelocity=Integer.compare(tx,x)*0x140;yVelocity=Integer.compare(ty,y)*0x140;}
    private int cameraX(){try{return services().camera()!=null?services().camera().getX():0;}catch(IllegalStateException e){return 0;}}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}@Override public int getOutOfRangeReferenceX(){return cullRight;}
    @Override public boolean isCustomOutOfRange(int cameraX){int coarseBack=(cameraX-0x80)&0xFF80;return ((cullRight&0xFF80)-coarseBack&0xFFFF)>cullWidth;}
    int[] segmentDelays(){return new int[]{1,0x19,0x31,0x49};} int requiredSlotCount(){return 4;} int routeSpeed(){return 0x140;} int routeWordCount(){return ROUTES[route].length;}
    int segmentDelay(){return delay;}
    boolean childMember(){return child;} FbzSnakePlatformObjectInstance parentMember(){return parent;}
    int routeWait(){return wait;} int routeIndex(){return routeIndex;} int xVelocity(){return xVelocity;} int yVelocity(){return yVelocity;}
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getPriorityBucket(){return 5;}
    @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x17,0x0C,0x0D);}
    @Override public boolean isSolidFor(PlayableEntity player){return delayRemaining==0&&!isDestroyed();}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false);}
    @Override public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_SNAKE_PLATFORM);if(r!=null&&r.isReady())r.drawFrameIndex(0,x,y,false,false);}
}
