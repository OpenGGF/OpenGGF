package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import java.io.IOException;
import java.util.List;

/** SKL $53, Obj_DEZConveyorPad / loc_479F0..sub_47B58 (sonic3k.asm:93619-93865). */
public final class S3kDezConveyorPadObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int currentX;
    private int yFixed;
    private int yVelocity;
    private int remaining;
    private int routine;
    private int floorRoutine;
    private int previousStanding;
    private int animation = 2;
    private int previousAnimation;
    private int animationFrame;
    private int animationTimer;
    private int mappingFrame;
    private boolean renderedOnScreen;

    public S3kDezConveyorPadObjectInstance(ObjectSpawn spawn) {
        super(spawn,"DEZConveyorPad");
        currentX=spawn.x();yFixed=spawn.y()<<16;
        remaining=(spawn.subtype()&0x7F)<<3;
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        int standing=standingBits();
        boolean horizontalPass=routine==2;
        if(routine==0) {
            if(standing!=0) {
                animation=spawn.renderFlags()&1;
                // The low seven bits choose width, but the FULL subtype byte
                // chooses the active routine: $80 is narrow yet vertical.
                routine=spawn.subtype()==0?2:1;
            }
        } else if(routine==1) {
            if(remaining!=0) {
                remaining=(remaining-1)&0xFFFF;
                addY((spawn.subtype()&0x80)==0?1:-1);
                soundOnLevelClock(); // no render-visibility gate on the vertical route
            }
            carryConveyor(services().playerQuery().mainPlayerOrNull(),standing&1);
            carryConveyor(services().playerQuery().nativeP2OrNull(),standing&2);
            changeDirectionOnNewStanding(standing);
        } else {
            followFloor();
            currentX=(currentX+(animation==0?1:-1))&0xFFFF;
            // Helpers accept the physical probe point. The left helper's
            // distance formula already accounts for SK's EOR.W #$F,d3.
            int distance=ObjectTerrainUtils.checkLeftWallDist(services().levelManager(),currentX-0x40,getY()).distance();
            if(distance<0){currentX=(currentX-distance)&0xFFFF;animation^=1;}
            distance=ObjectTerrainUtils.checkRightWallDist(services().levelManager(),currentX+0x40,getY()).distance();
            if(distance<0){currentX=(currentX+distance)&0xFFFF;animation^=1;}
            changeDirectionOnNewStanding(standing);
        }
        animate();
        updateDynamicSpawn(currentX,getY());
        services().solidExecution().resolveSolidNowAll();
        // loc_47B12 resolves contacts before the horizontal sound call.
        if(horizontalPass&&renderedOnScreen)soundOnLevelClock();
    }
    private void changeDirectionOnNewStanding(int standing) {
        if((standing&~previousStanding)!=0)animation^=1;
        previousStanding=standing;
    }
    private int standingBits() {
        var manager=services().objectManager();var q=services().playerQuery();int bits=0;
        var p=q.mainPlayerOrNull();if(p!=null&&manager.hasObjectStandingBit(p,this))bits|=1;
        p=q.nativeP2OrNull();if(p!=null&&manager.hasObjectStandingBit(p,this))bits|=2;
        return bits;
    }
    private void carryConveyor(PlayableEntity p,int bit) {
        if(p==null||bit==0)return;
        int dx=animation==0?2:-2;
        if(services().gameState().isReverseGravityActive())dx=-dx;
        NativePositionOps.writeXPosPreserveSubpixel(p,p.getCentreX()+dx);
    }
    private void followFloor() {
        if(floorRoutine==0) {
            addY(1);
            int distance=floorDistance(currentX);
            if(distance<0){addY(distance);floorRoutine=1;}
        } else if(floorRoutine==1) {
            int distance=Math.min(floorDistance(currentX-0x30),floorDistance(currentX+0x30));
            if(distance<=0xE)addY(distance);
            else floorRoutine=2; // falling starts on the next dispatch
        } else {
            // MoveSprite uses old velocity, then accelerates its signed word.
            yFixed+=yVelocity<<8;yVelocity=(short)(yVelocity+0x38);
            if((short)getY()>=(short)(services().camera().getMaxY()+0x120)) {
                currentX=0x7F00;return;
            }
            int distance=floorDistance(currentX);
            if(distance<0){addY(distance);yVelocity=0;floorRoutine=1;}
        }
    }
    private int floorDistance(int x) {
        return ObjectTerrainUtils.checkFloorDist(services().levelManager(),x,getY()+0xF).distance();
    }
    private void addY(int pixels){yFixed+=pixels<<16;}
    private void soundOnLevelClock() {
        if((services().levelManager().getFrameCounter()&0xF)==0)services().playSfx(Sonic3kSfx.CONVEYOR_PLATFORM.id);
    }
    private void animate() {
        if(animation!=previousAnimation){previousAnimation=animation;animationFrame=0;animationTimer=0;}
        // Animate_Sprite tests byte borrow, not sign; the ROM scripts here
        // have durations 1 and $0F and only the $FF loop command.
        int oldTimer=animationTimer;animationTimer=(animationTimer-1)&0xFF;
        if(oldTimer!=0)return;
        try {
            var rom=services().romReader();int table=0x47BF4;
            int script=table+(short)rom.readU16BE(table+animation*2);
            animationTimer=rom.readU8(script);
            int frame=rom.readU8(script+1+animationFrame);
            if(frame==0xFF){animationFrame=0;frame=rom.readU8(script+1);}
            if(frame>=0x80)throw new IllegalStateException("Unexpected DEZ conveyor animation command: "+frame);
            mappingFrame=frame;animationFrame=(animationFrame+1)&0xFF;
        } catch(IOException e){throw new IllegalStateException("Cannot load DEZ conveyor pad animation",e);}
    }
    private boolean wide(){return (spawn.subtype()&0x7F)!=0;}
    @Override public int getX(){return currentX;}
    @Override public int getY(){return (yFixed>>>16)&0xFFFF;}
    @Override public int romObjectCodePointerHighWord(){return 4;}
    @Override public SolidExecutionMode solidExecutionMode(){return SolidExecutionMode.MANUAL_CHECKPOINT;}
    @Override public SolidObjectParams getSolidParams(){return SolidObjectParams.of((wide()?0x80:0x40)+0xB,0x10,0x11);}
    @Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.fullSolid(false);}
    @Override public boolean allowsObjectControlledSolidContacts(){return true;}
    @Override public boolean checksOutOfRangeAfterRoutine(){return true;}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return isCoarseXOutOfRange(currentX,cameraX,coarseXCullRange());}
    @Override public int getOnScreenHalfWidth(){return wide()?0x80:0x40;}
    @Override public int getOnScreenHalfHeight(){return 0x10;}
    @Override public int getPriorityBucket(){return 5;}
    @Override public void refreshPostCameraRenderState(){renderedOnScreen=isWithinRenderSpriteBounds(getOnScreenHalfWidth(),0x10);}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        var renderer=getRenderer(wide()?Sonic3kObjectArtKeys.DEZ_CONVEYOR_PAD_WIDE:Sonic3kObjectArtKeys.DEZ_CONVEYOR_PAD);
        // Init clears status X-flip after saving the travel direction;
        // Animate_Sprite copies the resulting status low bits to render flags.
        if(renderer!=null&&renderer.isReady())renderer.drawFrameIndex(mappingFrame,getX(),getY(),false,(spawn.renderFlags()&2)!=0);
    }
    public int routineForTest(){return routine;}
    public int floorRoutineForTest(){return floorRoutine;}
    public int animationForTest(){return animation;}
    public int mappingFrameForTest(){return mappingFrame;}
    public int remainingForTest(){return remaining;}
    public int yVelocityForTest(){return yVelocity;}
    public int yFixedForTest(){return yFixed;}
}
