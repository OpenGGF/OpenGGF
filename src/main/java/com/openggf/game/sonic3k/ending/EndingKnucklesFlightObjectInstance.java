package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.NativeViewportFraming;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_5E6C0: Knuckles jumps from the collapsing island and leaves on the Tornado. */
public final class EndingKnucklesFlightObjectInstance extends AbstractObjectInstance implements RewindRecreatable, EndingFlightChildHost, NativeSstStateSource {
    private int routine;
    private int timer;
    private int xFixed, yFixed;
    private int xVelocity, yVelocity;
    private int planeMode;
    private int swingBaseY;
    private int swingSpeed = 0xC0;
    private boolean swingReverse;
    private boolean flipX;
    private boolean retireFlag;
    private boolean renderFlagOnScreen;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private final S3kEndingMasterPaletteState masterPalette = new S3kEndingMasterPaletteState();
    private transient S3kRawAnimation scripts;

    public EndingKnucklesFlightObjectInstance() { this(new ObjectSpawn(0,0,0,0,0,false,0)); }
    private EndingKnucklesFlightObjectInstance(ObjectSpawn spawn) { super(spawn,"KnucklesEndingFlight"); }
    @Override public EndingKnucklesFlightObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new EndingKnucklesFlightObjectInstance(context.spawn());
    }
    private record Extra(int routine,int timer,int xFixed,int yFixed,int xVelocity,int yVelocity,
                         int planeMode,int swingBaseY,int swingSpeed,boolean swingReverse,
                         boolean flipX,boolean retireFlag,boolean renderFlagOnScreen,S3kRawAnimation.State.Value animation,
                         S3kEndingMasterPaletteState.Value masterPalette)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new Extra(routine,timer,xFixed,yFixed,
                xVelocity,yVelocity,planeMode,swingBaseY,swingSpeed,swingReverse,flipX,retireFlag,renderFlagOnScreen,
                animation.captureRewindStateValue(),masterPalette.captureRewindStateValue()));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot,RewindCaptureContext context) {
        super.restoreRewindState(snapshot,context);
        if(snapshot.objectSubclassExtra() instanceof Extra e) {
            routine=e.routine();timer=e.timer();xFixed=e.xFixed();yFixed=e.yFixed();xVelocity=e.xVelocity();
            yVelocity=e.yVelocity();planeMode=e.planeMode();swingBaseY=e.swingBaseY();swingSpeed=e.swingSpeed();
            swingReverse=e.swingReverse();flipX=e.flipX();retireFlag=e.retireFlag();renderFlagOnScreen=e.renderFlagOnScreen();
            animation.restoreRewindStateValue(e.animation());
            masterPalette.restoreRewindStateValue(e.masterPalette());
        }
    }
    private SszZoneRuntimeState state(){return (SszZoneRuntimeState)services().zoneRuntimeState();}
    private S3kEndingState ending(){return state().ending();}
    @Override public int getX(){return xFixed>>>16;}
    @Override public int getY(){return yFixed>>>16;}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return planeMode<2;}
    @Override public int getPriorityBucket(){return 4;} // ObjDat3_600C2=$200
    public int routine(){return routine;}

    @Override public NativeSstState nativeSstState() {
        int flags=(routine==0?0:4)|(flipX?1:0);
        int control=(swingReverse?1:0)|(planeMode>=2?0x10:0)|(routine>=18?2:0);
        return new NativeSstState(flags,xFixed&0xFFFF,yFixed&0xFFFF,animation.mappingFrame,
                animation.animFrame,animation.animFrameTimer,control,retireFlag?0x80:0,routine==0?0:0x200,
                routine==0?0:planeMode==0?0x8310:planeMode==1?0x826E:0x226E);
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        ending().art().service(services());
        int dispatchedRoutine=routine;
        switch(routine) {
            case 0 -> initialize();
            case 2 -> {
                // loc_5E80A calls Play_SFX_Continuous before animation and timer
                // expiry; the helper reads V_int_run_count's low nibble.
                if ((vIntRunCount & 0xF) == 0) services().playSfx(Sonic3kSfx.WIND_QUIET.id);
                animate();
                if((timer=(short)(timer-1))<0){routine=4;yVelocity=-0x6C0;
                    services().playMusic(services().getApparentLevelMusicId());}
            }
            case 4 -> {
                animate(); move(); yVelocity=(short)(yVelocity+0x20); // MoveSprite_LightGravity
                if(yVelocity>=0){routine=6;yVelocity=0xC0;swingReverse=false;timer=179;
                    if(ending().progress()>=0)S3kRawAnimation.set(animation,0x60244);}
            }
            case 6 -> {
                swing();move();animate();
                if((timer=(short)(timer-1))<0){routine=8;xVelocity=0x400;}
            }
            case 8 -> {
                swing();move();animate();
                if(getX()>=((nativeCameraX(false)+0x200)&0xFFFF)) {
                    routine=10;planeMode=1;flipX=true;animation.mappingFrame=1;
                    retireFlag=true;setY((services().camera().getY()&0xFFFF)+0x50);xVelocity=-0x200;
                    swingBaseY=getY();S3kRawAnimation.set(animation,0x60253);
                    makeChild(EndingKnucklesFlightChildObjectInstance.PASSENGER,0,-0xA,0xC);
                    // CreateChild6_Simple stops its own list at allocation failure.
                    if(makeChild(EndingKnucklesFlightChildObjectInstance.FLAME,0,0,0)!=null)
                        makeChild(EndingKnucklesFlightChildObjectInstance.FLAME,2,0,0);
                    makeChild(EndingKnucklesFlightChildObjectInstance.PROPELLER,0,0x1C,0);
                }
            }
            case 10 -> {
                swing();move(); int limit=(nativeCameraX(false)+0x60)&0xFFFF;
                if(getX()<=limit) animate();
                // CMP.W followed by BLO: preserve unsigned 16-bit wrap of
                // Camera_X+$60-$A0 even when the native camera is zero.
                if(getX()<=((limit-0xA0)&0xFFFF)) {
                    routine=12;planeMode=2;flipX=false;animation.mappingFrame=6;
                    setY(state().cloudOscillator()+0x448);xVelocity=0x100;yVelocity=0;
                    makeChild(EndingKnucklesFlightChildObjectInstance.SMALL_PROPELLER,0,-6,7);
                    S3kEndingPalette.loadLine(services(),1,S3kEndingResources.PAL_SMALL_PLANE); // loc_5E984 PalLoad_Line1 writes Normal_palette_line_2.
                }
            }
            case 12 -> {
                move();
                if(getX()>((nativeCameraX(false)+0xA0)&0xFFFF)) {
                    routine=14;animation.mappingFrame=0;state().setEventsFg4(0xFFFF);
                    if(ending().progress()>=0)
                        services().objectManager().createDynamicObject(EndingKnucklesPaletteFlashObjectInstance::new);
                }
            }
            case 14 -> {
                if(state().endingCloudAligned()){routine=16;animation.mappingFrame=0x1A;setY(state().cloudOscillator()+0x448);}
            }
            case 16 -> {
                move();
                // loc_5E9E2 reads the bit left by the preceding Render_Sprites,
                // after moving. Recomputing from this pass's X reverts one pass early.
                if(!renderFlagOnScreen) {routine=18;timer=359;
                    var p1=services().playerQuery().mainPlayerOrNull();
                    if(p1 instanceof AbstractPlayableSprite playerSprite
                            &&playerSprite.getSuperStateController() instanceof com.openggf.game.sonic3k.Sonic3kSuperStateController powered)
                        powered.beginEndingKnucklesFlightRevert();}
            }
            case 18 -> {
                if((timer=(short)(timer-1))<0) {
                    // loc_5EA1A changes only the cloud emitter; the SSZ camera and
                    // ScreenEvents retain ownership of the credits handoff.
                    services().objectManager().createDynamicObject(EndingCloudObjectInstance::new);
                    ending().clouds().configure(0x3F,0,ending().progress()<0?-0x80:0x80);
                    routine=20;ObjectLifetimeOps.deleteNoRespawn(this);
                }
            }
            default -> { }
        }
        // The loc_5E6C0 outer dispatch rotates after the owning routine,
        // including its init and offscreen edge. loc_5EA1A is a new pointer
        // and does not enter this outer dispatch during the final hold.
        if(dispatchedRoutine<=16&&ending().progress()>0)masterPalette.tick(services());
        if(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1) {
            NativePositionOps.writeXPosPreserveSubpixel(p1,getX());
            NativePositionOps.writeYPosPreserveSubpixel(p1,getY());
        }
    }
    @Override public void refreshPostCameraRenderState() {
        // Render_Sprites loc_1AD54 uses Camera_*_pos_copy, a fixed 320x224
        // native window and this owner's $20 half-extents, including exclusive
        // right/bottom edges. The wider presentation must not delay this script.
        var camera=services().camera();
        int x=(short)(getX()-nativeCameraX(true));
        int y=(getY()-camera.getYCopy()+0x20)&camera.screenYWrapValue();
        renderFlagOnScreen=x+0x20>=0&&x-0x20<320&&y<224+0x40;
    }
    private void initialize() {
        S3kEndingCoordinator.initializeProgress(services());ending().clearFlag(0);ending().clearFlag(1);
        routine=2;timer=299;animation.mappingFrame=5;
        S3kRawAnimation.set(animation,ending().progress()<0?0x60236:0x6023F);
        setX(nativeCameraX(false)+0xC0);setY((services().camera().getY()&0xFFFF)+0x110);
        makeChild(EndingKnucklesFlightChildObjectInstance.TORNADO,0,-0x20,0x2B);
        services().camera().setScrollLocked(true);state().setEndingRunning(true);
        services().camera().setLevelStarted(false);
        // loc_5E70E: clr.w (Screen_shake_flag).w ends loc_8642E's final-defeat rumble.
        state().screenShake().writeFlag(0);
        services().levelGamestate().setTimerFrames(0);
        services().levelGamestate().pauseTimer();
        var player=services().playerQuery().mainPlayerOrNull();
        if(player instanceof AbstractPlayableSprite p1) {
            ObjectControlState.nativeBit7FullControl().applyTo(p1);p1.setObjectMappingFrameControl(true);p1.setMappingFrame(0);
        }
        if(ending().progress()>=0){animation.mappingFrame=4;
            makeChild(EndingKnucklesFlightChildObjectInstance.MASTER,0,4,-0x10);}
        if(ending().progress()>0&&player instanceof AbstractPlayableSprite p1
                &&p1.getSuperStateController() instanceof com.openggf.game.sonic3k.Sonic3kSuperStateController powered)
            powered.initializeEndingKnucklesFlight();
        S3kEndingPalette.targetLine(services(),1,Sonic3kConstants.SONIC_PALETTE_ADDR);
        if(services().paletteOwnershipRegistryOrNull()!=null)
            services().paletteOwnershipRegistryOrNull().applyTargetPatch("s3k-ending",1,5,new byte[]{0, (byte)0xEE, 0, (byte)0x88});
        if(ending().progress()>=0)ending().art().queueModule(services(),S3kEndingResources.MASTER_EMERALD,0x52E);
        ending().art().queueModule(services(),S3kEndingResources.KNUCKLES_FLIGHT,0x310);
        ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE,0x1E3);
        ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE_EXTRA,0x26E);
    }
    private int nativeCameraX(boolean renderCopy) {
        var camera=services().camera();int left=renderCopy?camera.getXCopy():camera.getX();
        if(state().centerNativeArenaCamera())left=NativeViewportFraming.nativeLeft(left,camera.getWidth());
        return left&0xFFFF;
    }
    private EndingKnucklesFlightChildObjectInstance makeChild(int role,int subtype,int dx,int dy){
        return spawnChild(()->new EndingKnucklesFlightChildObjectInstance(this,role,subtype,dx,dy));
    }
    @Override public int flightPlayerMode(){return 3;}
    @Override public boolean flightFlipX(){return flipX;}
    @Override public boolean flightChildRetired(){return retireFlag;}
    @Override public boolean flightChildSmall(){return planeMode==2;}
    @Override public boolean flightChildHidden(){return routine>=18;}
    @Override public int flightFrame(){return animation.mappingFrame;}
    @Override public boolean flightPowered(){return ending().progress()>=0;}
    private void setX(int x){xFixed=((x&0xFFFF)<<16)|(xFixed&0xFFFF);}
    private void setY(int y){yFixed=((y&0xFFFF)<<16)|(yFixed&0xFFFF);}
    private void move(){xFixed+=xVelocity<<8;yFixed+=yVelocity<<8;}
    /** Swing_Setup1/$40=$10; Swing_UpAndDown's threshold branches fall through. */
    private void swing(){
        int acceleration=0x10,maximum=swingSpeed;
        if(!swingReverse) {
            acceleration=-acceleration; yVelocity=(short)(yVelocity+acceleration); maximum=-maximum;
            if(yVelocity>maximum)return;
            swingReverse=true;acceleration=-acceleration;maximum=-maximum;
        }
        yVelocity=(short)(yVelocity+acceleration);
        if(yVelocity<maximum)return;
        swingReverse=false; yVelocity=(short)(yVelocity-acceleration);
    }
    private void animate(){scripts().animateMultiDelay(animation,()->{});}
    private S3kRawAnimation scripts(){
        if(scripts==null)try{scripts=S3kRawAnimation.load(services().romReader(),0x60236,0x9D);}
        catch(IOException failure){throw new UncheckedIOException(failure);}return scripts;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(isDestroyed()||routine>=18)return;
        // Native mapping/tile selection. Resident queue uploads are the art owner.
        int map=planeMode==0?S3kEndingResources.MAP_KNUCKLES_FLIGHT:S3kEndingResources.MAP_ENDING_PLANE_EXTRA;
        int tile=planeMode==0?0x310:0x26E,palette=planeMode==2?1:0;
        EndingSpriteRenderSupport.draw(services(),"s3k-knuckles-flight-"+planeMode,map,tile,palette,
                animation.mappingFrame,getX(),getY(),flipX,isHighPriority(),getPriorityBucket());
    }
}
