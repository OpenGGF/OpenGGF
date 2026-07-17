package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.Arrays;
import java.util.List;

/** Locked-on {@code Obj_FBZRotatingPlatform} ($77), sonic3k.asm $3B776-$3B91A. */
public final class FbzRotatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, TouchResponseProvider, RewindRecreatable,
        RomWorldPositionedObject {
    private static final int[][] RADII={{0x5C,0x44,0x2C,0xD4,0xBC,0xA4},{0x44,0x2C}};
    private int anchorX,anchorY;
    private int radius;
    private boolean special,child;
    private int familySlot=-1;
    private FbzRotatingPlatformObjectInstance parent;
    private boolean childrenSpawned;
    private int angle,x,y;
    public FbzRotatingPlatformObjectInstance(ObjectSpawn spawn){this(spawn,definitions(spawn)[0],isSpecial(spawn,0),false,null);}
    private FbzRotatingPlatformObjectInstance(ObjectSpawn spawn,int radius,boolean special,boolean child,FbzRotatingPlatformObjectInstance parent){super(spawn,"FBZRotatingPlatform");anchorX=x=spawn.x();anchorY=y=spawn.y();this.radius=radius;this.special=special;this.child=child;this.parent=parent;this.familySlot=parent==null?-1:parent.familySlot;}
    @Override public void update(int frameCounter,PlayableEntity player){
        if(child&&parent!=null&&parent.isDestroyed()){ObjectLifetimeOps.expireDynamic(this);return;}
        if(!child&&!childrenSpawned){childrenSpawned=true;if(familySlot<0)familySlot=getSlotIndex();int[] rr=definitions(spawn);int member=1;for(int attempt=1;attempt<rr.length;attempt++){final int j=member;FbzRotatingPlatformObjectInstance made=spawnChild(()->new FbzRotatingPlatformObjectInstance(spawn,rr[j],isSpecial(spawn,j),true,this));if(!made.isDestroyed())member++;}}
        x=positionX(anchorX,radius,angle);y=positionY(anchorY,radius,angle);angle=(angle+angleStep())&255;updateDynamicSpawn(x,y);if(!isOnScreen(0x380))setDestroyedByOffscreen();
    }
    @Override public FbzRotatingPlatformObjectInstance recreateForRewind(RewindRecreateContext ctx){FbzRotatingPlatformObjectInstance restored=new FbzRotatingPlatformObjectInstance(ctx.spawn(),0,false,false,null);if(ctx.state()!=null&&ctx.state().compactGenericState()!=null)GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored,ctx.state().compactGenericState());if(restored.child)restored.parent=findRestoredParent(ctx.objectManager(),restored.familySlot);return restored;}
    private static FbzRotatingPlatformObjectInstance findRestoredParent(ObjectManager manager,int familySlot){if(manager==null)return null;for(ObjectInstance object:manager.getActiveObjects())if(object instanceof FbzRotatingPlatformObjectInstance candidate&&!candidate.child&&!candidate.isDestroyed()&&candidate.familySlot==familySlot)return candidate;return null;}
    @Override protected void afterRewindRestoreSettled(){if(child&&parent==null)parent=findRestoredParent(services().objectManager(),familySlot);}
    static int positionX(int anchor,int radius,int angle){return anchor+((TrigLookupTable.cosHex(angle)*radius)>>8);}static int positionY(int anchor,int radius,int angle){return anchor+((TrigLookupTable.sinHex(angle)*radius)>>8);}
    private static int[] definitions(ObjectSpawn s){return (s.subtype()&0x0F)==0x0C?RADII[1]:RADII[0];}
    private static boolean isSpecial(ObjectSpawn s,int member){return (s.subtype()&0x0F)==0x0C&&member==0;}
    int[] memberRadii(){return Arrays.copyOf(definitions(spawn),definitions(spawn).length);} boolean[] specialMembers(){boolean[] result=new boolean[definitions(spawn).length];for(int i=0;i<result.length;i++)result[i]=isSpecial(spawn,i);return result;} int angleStep(){return (spawn.renderFlags()&1)!=0?-1:1;}
    int memberRadius(){return radius;} boolean specialMember(){return special;}
    boolean childMember(){return child;} FbzRotatingPlatformObjectInstance parentMember(){return parent;}
    int renderFrameIndex(){return 0;}
    @Override public int getX(){return x;}@Override public int getY(){return y;}
    @Override public void offsetNativePositionWordsPreserveSubpixel(int deltaX,int deltaY){
        x=(x+deltaX)&0xFFFF;y=(y+deltaY)&0xFFFF;
    }
    @Override public void afterRomWorldTransitionOffset(int deltaX,int deltaY){
        anchorX=(anchorX+deltaX)&0xFFFF;anchorY=(anchorY+deltaY)&0xFFFF;
    }
    @Override public int getPriorityBucket(){return 5;}
    @Override public int getOutOfRangeReferenceX(){return anchorX;}
    @Override public int getCollisionFlags(){return special?0x86:0;}@Override public int getCollisionProperty(){return 0;}
    @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x17,0x0C,0x0D);}@Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false);}
    @Override public boolean usesInstanceSolidStateLatchKey(){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(special?Sonic3kObjectArtKeys.FBZ_ROTATING_PLATFORM_SPECIAL:Sonic3kObjectArtKeys.FBZ_ROTATING_PLATFORM);if(r!=null&&r.isReady())r.drawFrameIndex(renderFrameIndex(),x,y,false,false);}
}
