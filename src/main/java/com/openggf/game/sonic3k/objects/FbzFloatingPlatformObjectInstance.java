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
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider,
        SpawnRewindRecreatable, RomObjectCodePointerProvider {
    // OscillationManager addresses the data after Oscillating_table's two-byte
    // control word, so ROM offsets $0A/$1E become engine offsets $08/$1C.
    private static final int MODE_1_OSCILLATION_DATA_OFFSET = 0x08;
    private static final int MODE_2_OSCILLATION_DATA_OFFSET = 0x1C;
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
    @Override public int romObjectCodePointerHighWord() { return 0x0003; }
    @Override public void update(int frameCounter, PlayableEntity player) {
        switch (movementMode) {
            case 0 -> y = anchorY + (TrigLookupTable.sinHex(localAngle++) >> 5);
            case 1 -> y = anchorY + signedOscillation(MODE_1_OSCILLATION_DATA_OFFSET,0x20);
            case 2 -> y = anchorY + signedOscillation(MODE_2_OSCILLATION_DATA_OFFSET,0x40);
            case 3 -> {
                int counter=resolveLevelFrameCounter(frameCounter)&0xFF;
                if((spawn.renderFlags()&1)!=0)counter=(-counter)&0xFF;
                int a=(counter+phase)&0xFF;
                x=anchorX+(TrigLookupTable.sinHex(a)>>2);
                y=anchorY+(TrigLookupTable.cosHex(a)>>2);
            }
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
    }
    private int signedOscillation(int offset,int bias){int d= com.openggf.game.OscillationManager.getByte(offset)-bias;return (spawn.renderFlags()&1)!=0?-d:d;}
    private int resolveLevelFrameCounter(int fallbackFrameCounter){
        ObjectServices objectServices=tryServices();
        return objectServices!=null&&objectServices.levelManager()!=null
                ? objectServices.levelManager().getFrameCounter()+1
                : fallbackFrameCounter;
    }
    private void updateRiderDrop(){
        if(!mode4Waiting&&!mode4Active&&!riderSeen){y=anchorY+(TrigLookupTable.sinHex(localAngle++)>>5);return;}
        if(!mode4Waiting&&!mode4Active)mode4Waiting=true;
        if(mode4Waiting){int old=localAngle&0xFF;localAngle=(localAngle+1)&0xFF;y=anchorY+(TrigLookupTable.sinHex(old)>>5);if((old&0x7F)!=0)return;mode4Waiting=false;mode4Active=true;localAngle=0;}
        int target=0x28+((spawn.subtype()&0x0F)<<1);int limit=target-1;
        // cmp.b $32(a0),d2 aliases the high byte of the big-endian word that
        // loc_3A712 later reads whole. Comparing the low byte reverses the
        // acceleration almost immediately and puts the platform a frame behind.
        if(!dropDirection){dropVelocity+=4;dropAccumulator+=dropVelocity;if(((dropAccumulator>>>8)&0xFF)>=limit)dropDirection=true;}
        else{dropVelocity-=4;dropAccumulator+=dropVelocity;if(((dropAccumulator>>>8)&0xFF)<limit)dropDirection=false;}
        int offset=(dropAccumulator&0xFFFF)>>>6;if((spawn.renderFlags()&1)!=0)offset=-offset;y=anchorY+offset;
        if(dropVelocity==0){anchorY=y;mode4Active=false;mode4Waiting=false;riderSeen=false;mode4Completed=true;localAngle=0;}
    }
    @Override public void onSolidContact(PlayableEntity player,SolidContact contact,int frameCounter){if(contact.standing())riderSeen=true;}
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public int getOutOfRangeReferenceX(){return anchorX;}
    @Override public int getBalanceWidthPixels(){
        // Object init writes width_pixels=$20, independently of the $2B d1
        // passed to SolidObjectFull_Offset. Sonic_Balance reads width_pixels.
        return 0x20;
    }
    @Override public int getPriorityBucket(){return 5;}@Override public int getCollisionFlags(){return 0x8C;}@Override public int getCollisionProperty(){return 0;}
    @Override public SolidObjectParams getSolidParams(){
        // loc_3A5DA calls SolidObjectFull_Offset with d2=$C and d3=-$D.
        // d3 shifts the collision anchor; it is not a second surface radius.
        return new SolidObjectParams(0x2B,0x0C,0x0C,0,-0x0D);
    }
    @Override public SolidRoutineProfile getSolidRoutineProfile(){
        return SolidRoutineProfile.fullSolid(false,true,true);
    }
    @Override public boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly(PlayableEntity player) {
        // SolidObjectFull_Offset_1P builds the lower reject bound by doubling
        // d2 after adding the live y_radius(a1) (sonic3k.asm:41303-41316).
        // Unlike SolidObject_cont, it never adds default_y_radius(a1).
        return true;
    }
    @Override public boolean usesInstanceSolidStateLatchKey(){
        // Obj71 rewrites its position every frame, but the ROM's standing and
        // pushing bits remain owned by this live SST slot. updateDynamicSpawn
        // must not turn each moving coordinate into a different latch owner.
        return true;
    }
    int movementMode(){return movementMode;} int phase(){return phase;} int romGroundOffset(){return -0x0D;}
    int dropAccumulator(){return dropAccumulator;} int dropVelocity(){return dropVelocity;} boolean dropDirection(){return dropDirection;}
    boolean mode4Completed(){return mode4Completed;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_FLOATING_PLATFORM);
        if(r!=null&&r.isReady())r.drawFrameIndex(animationFrame,x,y,false,false);
    }
}
