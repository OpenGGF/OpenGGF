package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;

/** loc_7E4CE..7E748: two top-solid arms, initially chained, then cross-linked. */
final class DezMinibossArm extends DezMinibossSprite implements RewindRecreatable, SolidObjectProvider {
    private DezMinibossSprite parent;
    /** Native $44: root initially; first arm is rewritten to point at the second. */
    private DezMinibossSprite crossLink;
    private int state;
    private int timer;
    private boolean carryX;
    private boolean debris;

    private DezMinibossArm(ObjectSpawn spawn) { super(spawn,"DEZMinibossArm"); }
    private DezMinibossArm(DezMinibossSprite root,DezMinibossSprite previous,int subtype) {
        this(new ObjectSpawn(root.getX(),root.getY(),0,subtype,0,false,0));
        parent=previous; crossLink=root;
    }
    static void spawnPair(DezMinibossSprite root) {
        DezMinibossSprite previous=root;
        for(int subtype=0;subtype<4;subtype+=2) {
            final int type=subtype; final var preceding=previous;
            var manager=root.encounterServices().objectManager();
            int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,root.getSlotIndex());
            if(slot<0) break;
            try {
                var arm=ObjectConstructionContext.with(root.encounterServices(),slot,
                        ()->new DezMinibossArm(root,preceding,type));
                ObjectLifetimeOps.addDynamicAtReservedSlot(manager,arm,slot);
                previous=arm;
            } catch(RuntimeException | Error failure) {
                manager.releaseDynamicSlot(slot); throw failure;
            }
        }
    }
    @Override public DezMinibossArm recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossArm(context.spawn());
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false; carryX=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(debris) { flickerMove(); return; }
        if(parent==null) return;
        if(state==0) {
            priority=6; halfWidth=0x18; halfHeight=0x10; frame=1;
            writeY(getY()+0xC); childDy=0xC;
            xVelocity=spawn.subtype()==0?-0x200:0x200;
            word3C=spawn.subtype()==0?0xC000:0x4000;
            timer=0x1F; state=0x7E51C;
            spawnChild(()->new Spike(this));
        }
        switch(state) {
            case 0x7E51C -> {
                move(0);
                if(--timer<0) {
                    normalState();
                    if(spawn.subtype()!=0) {
                        ((DezMinibossArm)parent).crossLink=this;
                        parent=crossLink;
                    }
                }
            }
            case 0x7E54C -> {
                if((parent.control&0x80)!=0) beginExpand();
                else if((control&8)!=0) { control&=~8; stop(); }
                else circleSimple();
            }
            case 0x7E566 -> {
                if((parent.control&0x80)!=0) beginExpand();
                else { if((parent.control&8)!=0) state=0x7E598; accelerate(); circleSimple(); }
            }
            case 0x7E598 -> {
                if((parent.control&0x80)!=0) beginExpand();
                else {
                    accelerate();
                    int start=(short)parent.word3C<0?0x30:0x40;
                    if((((word3C>>>8)-start)&0xFF)<=0x10) state=0x7E5D8;
                    circleSimple();
                }
            }
            case 0x7E5D8 -> {
                if((parent.control&0x80)!=0) beginExpand();
                else {
                    boolean negative=(short)parent.word3C<0;
                    int velocity=(short)(parent.word3A+(negative?0x100:-0x100));
                    int angle=(word3C+velocity)&0xFFFF;
                    parent.word3A=velocity&0xFFFF;
                    boolean stop=negative?velocity>=0 && angle>0x4000:velocity<0 && angle<0x4000;
                    if(stop) {
                        word3C=0x4000; parent.control&=~8;
                        // FixBugs=0: with only one arm, $44 still aliases the root.
                        crossLink.control|=8; stop();
                    } else { word3C=angle; circleSimple(); }
                }
            }
            case 0x7E64C -> { if((parent.control&4)!=0) { state=0x7E668; timer=7; } }
            case 0x7E668 -> { if(--timer<0) { state=0x7E67E; yVelocity=-0x400; } }
            case 0x7E67E -> {
                yVelocity=(short)(yVelocity+0x20); move(0);
                int y=(parent.getY()+0xC)&0xFFFF;
                if(yVelocity>=0 || y>=getY()) { writeY(y); state=0x7E6B6; parent.control&=~4; }
            }
            case 0x7E6B6 -> {
                if((parent.control&0x80)!=0) beginExpand();
                else { if((parent.control&0x40)!=0) normalState(); circleSimple(); }
            }
            case 0x7E6E8 -> {
                accelerate(); int radius=((word3A>>>8)+1)&0xFF;
                if(radius>=0x60) { state=0x7E710; timer=0x3F; }
                else word3A=(radius<<8)|(word3A&0xFF);
                circleExpanded();
            }
            case 0x7E710 -> { if(--timer<0) state=0x7E724; accelerate(); circleExpanded(); }
            case 0x7E724 -> {
                accelerate(); int radius=((word3A>>>8)-1)&0xFF;
                if(radius<=0x40) { normalState(); parent.control&=~0x80; radius=0x40; }
                word3A=(radius<<8)|(word3A&0xFF); circleExpanded();
            }
            default -> throw new IllegalStateException("Unknown DEZ arm routine "+state);
        }
        updateDynamicSpawn(getX(),getY());
        if((parent.status&0x80)!=0) {
            // loc_7EC3C releases standing players before installing Obj_FlickerMove.
            services().playerQuery().visitPlayers(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                    this,(arm,rider)-> {
                        var manager=arm.services().objectManager();
                        if(manager.hasObjectStandingBit(rider,arm)) {
                            manager.releaseRidingObject(rider,arm);
                            rider.setOnObject(false); rider.setAir(true);
                        }
                    });
            status|=0x80; debris=true;
            xVelocity=(short)romWord(0x852F4+spawn.subtype()*2);
            yVelocity=(short)romWord(0x852F6+spawn.subtype()*2);
        } else services().solidExecution().resolveSolidNowAll();
        visible=true;
    }
    private void normalState() { state=spawn.subtype()==0?0x7E566:0x7E54C; }
    private void stop() { state=0x7E64C; xVelocity=0; circleSimple(); }
    private void beginExpand() { state=0x7E6E8; word3A=0x4000; accelerate(); circleSimple(); }
    private void accelerate() {
        int target=(short)parent.word3C;
        int velocity=(short)(parent.word3A+(target<0?-8:8));
        velocity=target<0?Math.max(velocity,target):Math.min(velocity,target);
        word3C=(word3C+velocity)&0xFFFF; parent.word3A=velocity&0xFFFF;
    }
    private void circleSimple() {
        if(spawn.subtype()==0) parent.childDy=word3C>>>8;
        else word3C=(((parent.childDy+0x80)&0xFF)<<8)|(word3C&0xFF);
        int angle=word3C>>>8;
        posX=parent.posX+((TrigLookupTable.sinHex(angle)<<16)>>2);
        posY=parent.posY+(childDy<<16)+((TrigLookupTable.cosHex(angle)<<16)>>2);
        carryX=true;
    }
    private void circleExpanded() {
        int angle=word3C>>>8;
        writeX(parent.getX()+((TrigLookupTable.sinHex(angle)*(short)word3A)>>16));
        writeY(parent.getY()+childDy+((TrigLookupTable.cosHex(angle)*(short)word3A)>>16));
        carryX=true;
    }
    private void flickerMove() {
        move(0x38);
        if(isCoarseXOutOfRange(getX(),cameraLeft(),coarseXCullRange())
                || ((getY()-cameraTop()+0x80)&0xFFFF)>0x200) {
            status|=0x80; control|=0x10; pendingDelete=true; return;
        }
        visible=(control&0x40)!=0; control^=0x40; updateDynamicSpawn(getX(),getY());
    }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x23,8,0x15); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.topSolid(false); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !debris && !pendingDelete; }
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return carryX; }
    DezMinibossSprite parentForTest() { return parent; }
    DezMinibossSprite crossLinkForTest() { return crossLink; }
    int stateForTest() { return state; }

    /** loc_7E74A: independently allocated underside spikes follow the arm even as debris. */
    static final class Spike extends DezMinibossSprite implements RewindRecreatable, TouchResponseProvider {
        private DezMinibossArm parent;
        private int animationCursor;
        private int animationTimer;
        private boolean touchPublished;
        private Spike(ObjectSpawn spawn) { super(spawn,"DEZMinibossArmSpike"); }
        Spike(DezMinibossArm parent) {
            this(new ObjectSpawn(parent.getX(),parent.getY()+4,0,0,0,false,0)); this.parent=parent;
            priority=5; halfWidth=0x18; halfHeight=4; frame=0xB;
        }
        @Override public Spike recreateForRewind(RewindRecreateContext context) { return new Spike(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) {
            visible=false; touchPublished=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if(parent==null) return;
            if(--animationTimer<0) {
                int value=romByte(0x7EFED+(++animationCursor));
                if(value==0xFC) { animationCursor=0; value=romByte(0x7EFED); }
                frame=value; animationTimer=romByte(0x7EFEC);
            }
            writeX(parent.getX()); writeY(parent.getY()+4);
            if((parent.control&0x10)!=0) { control|=0x10; pendingDelete=true; return; }
            visible=true; touchPublished=(parent.status&0x80)==0; updateDynamicSpawn(getX(),getY());
        }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
            return TouchResponseProfile.fromProvider(this,multiRegionSource);
        }
        @Override public int getCollisionFlags() { return 0x9E; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
        DezMinibossArm parentForTest() { return parent; }
    }
}
