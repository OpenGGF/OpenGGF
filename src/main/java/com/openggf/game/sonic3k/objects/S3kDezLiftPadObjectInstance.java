package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import java.util.List;
import java.io.IOException;

/** SKL $4E, Obj_DEZLiftPad / sub_4748E / sub_4757A (sonic3k.asm:93091-93315). */
public final class S3kDezLiftPadObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider, RomObjectCodePointerProvider {
    private Arm arm;
    private boolean initialized;
    private int anchorX, anchorY, currentX, currentY;
    private int angle, angularVelocity;
    private int pause;
    private int phase;
    private boolean moving;
    private int previousAngle = 0xFF;

    public S3kDezLiftPadObjectInstance(ObjectSpawn spawn) {
        super(spawn,"DEZLiftPad");
        anchorX=currentX=spawn.x();anchorY=currentY=spawn.y();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if(!initialized) {
            initialized=true;
            var child=spawnChild(()->new Arm(new ObjectSpawn(anchorX,anchorY,spawn.objectId(),
                    spawn.subtype(),spawn.renderFlags(),false,spawn.rawYWord())));
            if(child!=null&&!child.isDestroyed())arm=child;
        }
        advanceAngle();
        int value=(angle>>>8)&0xFF;
        if(value!=previousAngle) {
            previousAngle=value;
            updateArm(value);
        }
        updateDynamicSpawn(currentX,currentY);
    }

    private void advanceAngle() {
        var p=services().playerQuery().mainPlayerOrNull();
        boolean standing=p!=null&&services().objectManager().hasObjectStandingBit(p,this);
        if(pause!=0) { if(!standing)pause=(pause-1)&0xFFFF;return; }
        if(!moving) {
            // sub_4757A tests only native Player_1 standing and Debug_placement_mode.
            if(!standing||p.isDebugMode())return;
            moving=true;
            services().playSfx(Sonic3kSfx.GRAVITY_LIFT.id);
        }
        if(phase==0) {
            angularVelocity=(short)(angularVelocity+8);
            angle=(angle+angularVelocity)&0xFFFF;
            if(angularVelocity==0)moving=false;
            if((angle>>>8)>=0x20)phase=1;
        } else {
            angularVelocity=(short)(angularVelocity-8);
            angle=(angle+angularVelocity)&0xFFFF;
            if(angularVelocity==0)pause=30;
            if((angle>>>8)<0x20)phase=0;
        }
    }

    private void updateArm(int value) {
        if((spawn.subtype()&0x10)!=0) {
            value=-value;
            if((spawn.subtype()&0x20)!=0)value=-value+0xC0;
        } else if((spawn.subtype()&0x20)!=0)value=-value+0x40;
        value+=0x80;
        if((spawn.renderFlags()&1)!=0)value=-value+0x80;
        int sinStep=TrigLookupTable.sinHex(value&0xFF)<<12;
        int cosStep=TrigLookupTable.cosHex(value&0xFF)<<12;
        if(arm==null) {
            // FixBugs=0: failed allocation leaves $3E at zero. sub_4748E
            // reads mainspr_childsprites ($16) from ROM, then its child
            // writes target read-only ROM. Only the parent's final position
            // changes. Read the vector-table word instead of inventing an arm.
            try {
                int count=services().romReader().readU16BE(0x16);
                if(count>=2)setPadPosition(sinStep*(count+1),cosStep*(count+1));
            } catch(IOException e) {
                throw new IllegalStateException("Cannot read DEZ lift's null-arm ROM word",e);
            }
            return;
        }
        if(arm.isDestroyed()||arm.jointX.length<2)return;
        int sin=sinStep,cos=cosStep;
        for(int i=1;i<arm.jointX.length;i++) {
            arm.jointX[i]=(anchorX+(cos>>16))&0xFFFF;
            arm.jointY[i]=(anchorY+(sin>>16))&0xFFFF;
            sin+=sinStep;cos+=cosStep;
        }
        // $24/angle($26) alias sub4_x_pos/sub4_y_pos in a multisprite.
        // The loop just wrote the second link there. Move it to the main
        // sprite, then put the end joint into sub4: this is SAT ordering,
        // not a previous-frame position. With count two, sub4 is outside
        // the active child list and retains its old words instead.
        if(arm.jointX.length>=3){arm.sub4X=arm.jointX[2];arm.sub4Y=arm.jointY[2];}
        arm.currentX=arm.sub4X;arm.currentY=arm.sub4Y;
        arm.sub4X=(anchorX+(cos>>16))&0xFFFF;
        arm.sub4Y=(anchorY+(sin>>16))&0xFFFF;
        if(arm.jointX.length>=3){arm.jointX[2]=arm.sub4X;arm.jointY[2]=arm.sub4Y;}
        sin+=sinStep;cos+=cosStep;
        setPadPosition(sin,cos);
    }

    private void setPadPosition(int sin,int cos) {
        currentY=(anchorY+(sin>>16))&0xFFFF;
        currentX=(anchorX+(cos>>16)-0x20+((spawn.renderFlags()&1)!=0?0x40:0))&0xFFFF;
    }

    @Override public void onUnload(){if(arm!=null)arm.setDestroyed(true);setDestroyed(true);}
    @Override public int getX(){return currentX;}
    @Override public int getY(){return currentY;}
    @Override public SolidObjectParams getSolidParams(){return SolidObjectParams.of(0x18,9,9);}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.topSolid(false);}
    @Override public boolean isTopSolidOnly(){return true;}
    @Override public boolean allowsObjectControlledSolidContacts(){return true;}
    @Override public int romObjectCodePointerHighWord(){return 4;}
    @Override public boolean checksOutOfRangeAfterRoutine(){return true;}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return isCoarseXOutOfRange(anchorX,cameraX,coarseXCullRange());}
    @Override public int getOnScreenHalfWidth(){return 0x20;}
    @Override public int getOnScreenHalfHeight(){return 0x10;}
    @Override public int getPriorityBucket(){return 5;}
    @Override public void appendRenderCommands(List<GLCommand> commands){draw(0,getX(),getY());}
    private void draw(int frame,int x,int y){var renderer=getRenderer(Sonic3kObjectArtKeys.DEZ_LIFT_PAD);if(renderer!=null&&renderer.isReady())renderer.drawFrameIndex(frame,x,y,(spawn.renderFlags()&1)!=0,(spawn.renderFlags()&2)!=0);}
    @Override public S3kDezLiftPadObjectInstance recreateForRewind(RewindRecreateContext context){return new S3kDezLiftPadObjectInstance(context.spawn());}
    public Arm armForTest(){return arm;}
    public int angleForTest(){return angle;}
    public int angularVelocityForTest(){return angularVelocity;}
    public int pauseForTest(){return pause;}
    public boolean movingForTest(){return moving;}
    public int phaseForTest(){return phase;}

    /** Independent forward-allocated SST entry running Draw_Sprite; parent owns deletion. */
    public static final class Arm extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int currentX,currentY,sub4X,sub4Y;
        private int[] jointX,jointY;
        public Arm(ObjectSpawn spawn){
            super(spawn,"DEZLiftArm");currentX=spawn.x();currentY=spawn.y();
            jointX=new int[spawn.subtype()&0xF];jointY=new int[jointX.length];
            java.util.Arrays.fill(jointX,currentX);java.util.Arrays.fill(jointY,currentY);
            if(jointX.length>=3){sub4X=currentX;sub4Y=currentY;}
        }
        @Override public void update(int vIntRunCount,PlayableEntity player) { }
        @Override public int getX(){return currentX;}
        @Override public int getY(){return currentY;}
        @Override public boolean usesCustomOutOfRangeCheck(){return true;}
        @Override public boolean isCustomOutOfRange(int cameraX){return false;}
        @Override public int getOnScreenHalfWidth(){return 0x60;}
        @Override public int getOnScreenHalfHeight(){return 0x60;}
        @Override public int getPriorityBucket(){return 5;}
        @Override public void appendRenderCommands(List<GLCommand> commands){
            var renderer=getRenderer(Sonic3kObjectArtKeys.DEZ_LIFT_PAD);
            if(renderer==null||!renderer.isReady())return;
            boolean fx=(spawn.renderFlags()&1)!=0,fy=(spawn.renderFlags()&2)!=0;
            renderer.drawFrameIndex(1,currentX,currentY,fx,fy);
            for(int i=0;i<jointX.length;i++)renderer.drawFrameIndex(i==0?2:1,jointX[i],jointY[i],fx,fy);
        }
        public int countForTest(){return jointX.length;}
        public int jointXForTest(int i){return jointX[i];}
        public int jointYForTest(int i){return jointY[i];}
        public int sub4XForTest(){return sub4X;}
        public int sub4YForTest(){return sub4Y;}
    }
}
