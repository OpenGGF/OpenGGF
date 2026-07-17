package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;

/** Two SolidObjectFull side walls from ChildObjDat_703E4, loc_70118. */
final class Fbz2SubbossSolidSideChild extends AbstractFbz2SubbossChild
        implements SolidObjectProvider, RewindRecreatable, RomWorldPositionedObject {
    private static final int[] SUBTYPES={0,2};
    private static final SolidObjectParams SOLID=new SolidObjectParams(0x13,0x50,0x60);
    private int nativeSubtype;
    private Fbz2SubbossCornerChild corner;
    private int lastFrameCounter;
    Fbz2SubbossSolidSideChild(Fbz2SubbossInstance root,int subtype){this(spawn(root,subtype));this.root=root;familySlot=root.getSlotIndex();corner=subtype==0?root.upperLeft():root.upperRight();}
    private Fbz2SubbossSolidSideChild(ObjectSpawn spawn){super(spawn,"FBZ2SubbossSolidSide");nativeSubtype=spawn.subtype()&2;}
    private static ObjectSpawn spawn(Fbz2SubbossInstance r,int s){return new ObjectSpawn(r.getX()+(s==0?-0xB0:0xB0),r.getY()+0x60,0xAB,s,0,false,0);}
    static Fbz2SubbossSolidSideChild forTest(Fbz2SubbossInstance r,int s){return new Fbz2SubbossSolidSideChild(r,s);}
    static int[] nativeSubtypes(){return SUBTYPES.clone();}
    int nativeSubtype(){return nativeSubtype;} Fbz2SubbossCornerChild corner(){return corner;} void setCorner(Fbz2SubbossCornerChild c){corner=c;}
    @Override public void update(int frameCounter,PlayableEntity p){lastFrameCounter=frameCounter;if(root!=null&&root.controlBit(Fbz2SubbossInstance.CONTROL_RELEASE_SOLIDS)){ObjectLifetimeOps.deleteNoRespawn(this);return;}if(corner!=null)x=corner.getX();}
    @Override public SolidObjectParams getSolidParams(){return SOLID;}
    @Override public boolean isSolidFor(PlayableEntity p){return root==null||!root.controlBit(Fbz2SubbossInstance.CONTROL_RELEASE_SOLIDS);}
    @Override public Fbz2SubbossSolidSideChild recreateForRewind(RewindRecreateContext c){return new Fbz2SubbossSolidSideChild(c.spawn());}
    @Override public int getPriorityBucket(){return 1;}
    @Override public void appendRenderCommands(List<GLCommand> c){if((lastFrameCounter&1)!=0)return;PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);if(r!=null&&r.isReady())r.drawFrameIndex(4,x,y,false,false);}
}
