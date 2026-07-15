package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Locked-on {@code Obj_FBZFloatingPlatform} ($71), sonic3k.asm $3A56C-$3A742. */
public final class FbzFloatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider, SpawnRewindRecreatable {
    private final int movementMode;
    private final int phase;
    private final int anchorX;
    private int anchorY;
    private int x;
    private int y;
    private int localAngle;
    private int animationFrame;
    private int animationTimer;
    private boolean riderSeen;
    private boolean mode4Waiting;
    private boolean mode4Active;
    /** ROM callback $40(a0) was permanently replaced with loc_3A620. */
    private boolean mode4Completed;
    private boolean dropDirection;
    private int dropAccumulator;
    private int dropVelocity;

    public FbzFloatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZFloatingPlatform");
        movementMode = (spawn.subtype() & 0x70) >>> 4;
        phase = (spawn.subtype() & 0x0F) << 4;
        anchorX = x = spawn.x(); anchorY = y = spawn.y();
    }
    @Override public void update(int frameCounter, PlayableEntity player) {
        switch (movementMode) {
            case 0 -> y = anchorY + (TrigLookupTable.sinHex(localAngle++) >> 5);
            case 1 -> y = anchorY + signedOscillation(0x0A,0x20);
            case 2 -> y = anchorY + signedOscillation(0x1E,0x40);
            case 3 -> { int counter=frameCounter&0xFF;if((spawn.renderFlags()&1)!=0)counter=(-counter)&0xFF;int a=(counter+phase)&0xFF;x=anchorX+(TrigLookupTable.sinHex(a)>>2);y=anchorY+(TrigLookupTable.cosHex(a)>>2); }
            case 4 -> {
                if (mode4Completed) {
                    y = anchorY + (TrigLookupTable.sinHex(localAngle++) >> 5);
                } else {
                    updateRiderDrop();
                }
            }
            default -> { }
        }
        if (--animationTimer < 0) { animationTimer=1; animationFrame=(animationFrame+1)%3; }
        updateDynamicSpawn(x,y);
        if (!isOnScreen(0x180)) setDestroyedByOffscreen();
    }
    private int signedOscillation(int offset,int bias){int d= com.openggf.game.OscillationManager.getByte(offset)-bias;return (spawn.renderFlags()&1)!=0?-d:d;}
    private void updateRiderDrop(){
        if(!mode4Waiting&&!mode4Active&&!riderSeen){y=anchorY+(TrigLookupTable.sinHex(localAngle++)>>5);return;}
        if(!mode4Waiting&&!mode4Active)mode4Waiting=true;
        if(mode4Waiting){int old=localAngle&0xFF;localAngle=(localAngle+1)&0xFF;y=anchorY+(TrigLookupTable.sinHex(old)>>5);if((old&0x7F)!=0)return;mode4Waiting=false;mode4Active=true;localAngle=0;}
        int target=0x28+((spawn.subtype()&0x0F)<<1);int limit=target-1;
        if(!dropDirection){dropVelocity+=4;dropAccumulator+=dropVelocity;if((dropAccumulator&0xFF)>=limit)dropDirection=true;}
        else{dropVelocity-=4;dropAccumulator+=dropVelocity;if((dropAccumulator&0xFF)<limit)dropDirection=false;}
        int offset=(dropAccumulator&0xFFFF)>>>6;if((spawn.renderFlags()&1)!=0)offset=-offset;y=anchorY+offset;
        if(dropVelocity==0){anchorY=y;mode4Active=false;mode4Waiting=false;riderSeen=false;mode4Completed=true;localAngle=0;}
    }
    @Override public void onSolidContact(PlayableEntity player,SolidContact contact,int frameCounter){if(contact.standing())riderSeen=true;}
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getPriorityBucket(){return 5;}@Override public int getCollisionFlags(){return 0x8C;}@Override public int getCollisionProperty(){return 0;}
    @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x2B,0x0C,0x0D);}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false);}
    int movementMode(){return movementMode;} int phase(){return phase;} int romGroundOffset(){return -0x0D;}
    int dropAccumulator(){return dropAccumulator;} int dropVelocity(){return dropVelocity;} boolean dropDirection(){return dropDirection;}
    boolean mode4Completed(){return mode4Completed;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_FLOATING_PLATFORM);
        if(r!=null&&r.isReady())r.drawFrameIndex(animationFrame,x,y,false,false);
    }
}
