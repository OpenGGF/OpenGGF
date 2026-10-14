package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Obj_5D86A / Obj_5DFEE / Obj_Ending, in the native $D01 level. */
public final class EndingRescueDirectorObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    public static final int RESCUE = 0, AIZ_RETURN = 1, REUNION = 2;
    private int sequence, routine, timer, xFixed, yFixed, xVelocity, yVelocity;
    private int baseY, bobPhase, swingMaximum = 0x80, mappingFrame, animationCounter, animationFrame;
    private int nativeVInt;
    private boolean swingReverse, planeReleased, smallPlane, retired, hidePlayer, flipX, paletteHelperAdmitted;
    private transient S3kRawAnimation scripts;
    private transient S3kRawAnimation knucklesScripts;
    // Scalars are captured by AbstractObjectInstance's generic subclass schema.
    // Native object pointers are resolved from the live managed role slots.
    public EndingRescueDirectorObjectInstance() { this(RESCUE); }
    public EndingRescueDirectorObjectInstance(int sequence) {
        this(new ObjectSpawn(0,0,0,sequence,0,false,0));
    }
    private EndingRescueDirectorObjectInstance(ObjectSpawn spawn) {
        super(spawn,"EndingRescue");sequence=spawn.subtype();
    }
    @Override public EndingRescueDirectorObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new EndingRescueDirectorObjectInstance(c.spawn());
    }
    private EndingZoneRuntimeState zone() { return (EndingZoneRuntimeState) services().zoneRuntimeState(); }
    private EndingZoneRuntimeState.ScreenRam ram() { return zone().screen(); }
    private S3kEndingState ending() { return zone().ending(); }
    private boolean tails() { return zone().playerCharacter()==PlayerCharacter.TAILS_ALONE; }
    public int routine() { return routine; }
    public int sequence() { return sequence; }
    public boolean retired() { return retired; }
    public boolean planeReleased() { return planeReleased; }
    @Override public int getX() { return xFixed>>>16; }
    @Override public int getY() { return yFixed>>>16; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return !smallPlane; }
    @Override public int getPriorityBucket() { return sequence==AIZ_RETURN?7:4; }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        nativeVInt=vIntRunCount;
        ending().art().service(services());
        if(sequence==RESCUE) rescue(); else if(sequence==AIZ_RETURN) aizReturn(); else reunion();
        if(sequence==REUNION) ram().scrollXFixed-=2<<16;
        if(sequence!=AIZ_RETURN)publishPlayer();
    }
    private void rescue() {
        switch(routine) {
            case 0 -> initializeRescue();
            case 2 -> { admitMasterPalette(1);ram().scrollYFixed+=0x8000;if(ram().scrollYFixed>=0x800000){routine=4;
                services().objectManager().createDynamicObject(EndingCloudObjectInstance::new);
                ending().clouds().configure(0x1F,-0x100,-0x100);} }
            case 4 -> { ram().scrollYFixed+=0x8000;if(ram().scrollYFixed>=0x1000000){routine=6;planeReleased=true;} }
            case 6 -> {
                var plane=actor(Actor.TORNADO);
                if(plane==null || (short)(plane.getX()+0x20)<=(short)getX()) ram().scrollYFixed+=0x8000;
                else {
                    routine=8;ending().setFlag(0);timer=0x20;setX(plane.getX()+0x20);
                    setY(plane.getY()-(tails()?0x27:0x2B));baseY=getY();
                    var p=mainPlayer();if(p!=null){ObjectControlState.nativeBit7FullControl().applyTo(p);
                        if(ending().progress()>0&&p.getSuperStateController() instanceof Sonic3kSuperStateController powered){
                            // loc_5DB1C clears powered flags and restores normal mappings in this pass.
                            powered.beginEndingRescueRevert();powered.completeEndingRescueRevertMappings();
                        }
                        p.setObjectMappingFrameControl(true);p.setMappingFrame(tails()?0xAD:0xBA);p.setAnimationId(5);}
                    tickRescueRide(); // loc_5DA96 falls through loc_5DB2C in the same dispatch
                }
            }
            case 8 -> tickRescueRide();
            case 10 -> {if(ram().catcherReady){routine=12;timer=299;tickRescueHold();}else bob();}
            case 12 -> {
                tickRescueHold();
            }
            case 14 -> {
                ram().scrollXFixed+=2<<16;ram().scrollYFixed+=1<<16;
                if((timer=(short)(timer-1))<0){routine=16;timer=299;}bob();
            }
            case 16 -> { ram().scrollXFixed+=2<<16;if((timer=(short)(timer-1))<0){routine=18;timer=0x8F;}bob(); }
            case 18 -> {
                if(getX()>0x60)setX(getX()-2);ram().scrollXFixed+=2<<16;bob();
                if((timer=(short)(timer-1))<0){routine=20;retired=true;ram().foregroundSignal=true;timer=0x8F;
                    var p=mainPlayer();if(p!=null)p.setAnimationId(0);}
            }
            case 20 -> {
                ram().scrollXFixed+=2<<16;
                if((timer=(short)(timer-1))<0){routine=22;mappingFrame=1;setX(0x40);setY(0x110);
                    timer=119;xVelocity=0x100;yVelocity=0x80;swingMaximum=0x80;swingReverse=false;hidePlayer=true;}
            }
            case 22 -> { if((timer=(short)(timer-1))<0)routine=24; }
            case 24 -> {
                swing(8);move();if(getX()>=0x140)animatePlane();
                if(getX()>0x220){routine=26;smallPlane=true;flipX=true;setY(getY()-8);
                    xVelocity=-0x80;yVelocity=0;mappingFrame=tails()?6:5;
                    S3kEndingPalette.loadLine(services(),0,S3kEndingResources.PAL_SMALL_PLANE);}
            }
            case 26 -> { move();if(getX()<0x120){routine=28;timer=59;} }
            case 28 -> { if((timer=(short)(timer-1))<0){routine=30;ram().foregroundSignal=true;
                if(!knucklesMode()&&ending().progress()>=0)createIslandLiftGroups();} }
            case 30 -> { move();if(getX()<0x60)routine=32; }
            case 32 -> {
                if(ending().progress()<0 || (ram().foregroundY&0xFFFF)>=0x78){ram().foregroundSignal=true;
                    services().fadeOutMusic();ram().masterPaletteMode=0;ending().clearFlag(0);ending().clearFlag(1);ObjectLifetimeOps.deleteNoRespawn(this);}
            }
            default -> { }
        }
    }
    private void initializeRescue() {
        S3kEndingCoordinator.initializeProgress(services());routine=2;setX(0x100);setY(0xE0);
        services().camera().setScrollLocked(true);ending().clearFlag(0);ending().clearFlag(1);
        S3kEndingPalette.targetLine(services(),2,0xA97BC); // loc_5D9B8 Pal_SSZ2+$20
        // loc_5D8BC clears Player_2; retain only the screen-controlled P1 roster.
        com.openggf.sprites.managers.SpriteManagerInternalAccess
                .retireAllSidekicksForRewind(services().spriteManager());
        var p=mainPlayer();if(p!=null){ObjectControlState.nativeBit7FullControl().applyTo(p);
            com.openggf.sprites.playable.PlayableSpriteInternalAccess.clearScriptedNativeStatus(p);
            p.setHighPriority(true);
            p.setAnimationId(0xF);p.setGSpeed((short)0x800);
            if(p.getSuperStateController() instanceof Sonic3kSuperStateController powered){
                powered.holdEndingRingDrain();
                if(ending().progress()==0&&!tails()){
                    powered.initializeEndingSuperSonic();services().objectManager().createDynamicObject(EndingRescueRevertObjectInstance::new);
                }else if(ending().progress()>0){p.setAnimationId(0);
                    if(tails())powered.initializeEndingSuperTails();else powered.initializeEndingHyperSonic();}
            }
        }
        // loc_5D96A writes Dynamic_object_RAM+object_size*5 and *45.
        // That base follows three Object_RAM slots (constants.asm:303-307),
        // so ObjectManager's global SST indices are 8 and 48.
        services().objectManager().createDynamicObjectAtSlot(()->new Actor(Actor.TORNADO),8);
        services().objectManager().createDynamicObjectAtSlot(()->new Actor(Actor.MASTER),48);
        ending().art().queueModule(services(),S3kEndingResources.MASTER_EMERALD,0x52E);
        ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE,0x1E3);
        ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE_EXTRA,0x26E);
    }
    private void tickRescueRide(){
        if((timer=(short)(timer-1))==0)ending().setFlag(1);setX(getX()+2);
        if(getX()>=0x160){routine=10;if(ending().progress()<0){routine=12;timer=299;}}bob();
    }
    private void tickRescueHold(){
        if((timer=(short)(timer-1))<0){routine=14;timer=0xBF;ram().backgroundSignal=true;
            ending().clouds().configure(0,ending().clouds().xVelocity(),-0x100);
            EndingIslandLiftObjectInstance.createGroup(services(),0x5DBB6,0x42,2);
            ending().art().queueModule(services(),S3kEndingResources.ISLAND_LIFT,0x2FF);}bob();
    }
    private boolean knucklesMode(){return zone().playerCharacter()==PlayerCharacter.KNUCKLES;}
    /** loc_5D9EE / loc_5E08A set their local guard before the one allocation attempt. */
    private void admitMasterPalette(int mode){
        int white=com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                services().currentLevel().getPalette(0).getColor(1));
        if(white!=0xEEE||paletteHelperAdmitted)return;
        paletteHelperAdmitted=true;ram().masterPaletteMode=mode;
        services().objectManager().createDynamicObject(EndingMasterPaletteObjectInstance::new);
    }
    private void createIslandLiftGroups(){
        // loc_5DD6A: ordinal $2D restarts at0 for each source table.
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DDD2,0x41,6);
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DDEA,0x43,2);
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DDF2,0x44,4);
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DE02,0x45,2);
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DE0A,0x46,2);
        EndingIslandLiftObjectInstance.createGroup(services(),0x5DE12,0x47,2);
    }
    private void aizReturn() {
        switch(routine) {
            case 0 -> {
                routine=2;mappingFrame=1;flipX=true;setX(0x220);setY(0xB0);xVelocity=-0x100;
                yVelocity=0x80;swingMaximum=0x80;hidePlayer=true;
                services().playMusicMailboxNativeRequest(0xDC); // Obj_5DFEE, mus_CreditsK
                ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE_EXTRA,0x26E);
                ending().clearFlag(0);
                services().objectManager().createDynamicObject(()->new Actor(Actor.MASTER_AIZ));
            }
            case 2 -> { admitMasterPalette(-1);swing(8);move();if(getX()<0x60)routine=3; }
            case 3 -> {routine=4;setX(0x1E0);setY(0xE0);
                xVelocity=-0x200;yVelocity=0;mappingFrame=7;animationCounter=animationFrame=0;
                S3kEndingPalette.loadLine(services(),0,0x5CC2A); // Pal_ContinueScreen+$60
            }
            case 4 -> { animateKnucklesReturn();move();if(getX()<0x120){routine=6;mappingFrame=0x55;timer=119;} }
            case 6 -> { if((timer=(short)(timer-1))<0){routine=8;ram().masterPaletteMode=0;ram().foregroundSignal=true;} }
            default -> { }
        }
    }
    private void reunion() {
        switch(routine) {
            case 0 -> {
                S3kEndingCoordinator.initializeProgress(services());routine=2;setX(0xC0);setY(0xE0);
                swingMaximum=0xC0;yVelocity=0xC0;flipX=true;
                if(ending().progress()!=2)services().playMusicMailboxNativeRequest(0xDC); // loc_5E1F0
                var p=mainPlayer();if(p!=null){ObjectControlState.nativeBit7FullControl().applyTo(p);
                    p.setDirection(com.openggf.physics.Direction.LEFT);p.setHighPriority(false);
                    p.setObjectMappingFrameControl(true);p.setAnimationId(5);p.setMappingFrame(tails()?0xAD:0xBA);}
                services().objectManager().createDynamicObject(()->new Actor(Actor.REUNION_PLANE));
                if(ending().progress()>=0){
                    for(int i=0;i<5;i++){int index=i;spawnChild(()->new EndingAnimalObjectInstance(false,index,getX(),getY()));}
                    for(int i=0;i<2;i++){int index=i;spawnChild(()->new EndingAnimalObjectInstance(true,index,getX(),getY()));}
                }
                ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE,0x1E3);
                ending().art().queueModule(services(),S3kEndingResources.ENDING_PLANE_EXTRA,0x26E);
                ending().art().queueModule(services(),0x160D8C,0x2FF); // loc_5E278 ArtKosM_EndingAnimals
            }
            case 2 -> {
                swing(0x10);move();if(ending().credits().complete()){routine=4;timer=0x1F;
                    var p=mainPlayer();if(p!=null){p.setMappingFrame(0x58);p.setAnimationId(0);}}
            }
            case 4 -> {
                if((timer=(short)(timer-1))<0){routine=6;planeReleased=true;flipX=false;xVelocity=0x300;yVelocity=-0x600;
                    var p=mainPlayer();if(p!=null){p.setDirection(com.openggf.physics.Direction.RIGHT);
                        p.setAnimationId(2);p.setObjectMappingFrameControl(false);}
                    int source=tails()?S3kEndingResources.TAILS_POSE:ending().progress()<0?
                            S3kEndingResources.SONIC_POSE:S3kEndingResources.SUPER_SONIC_POSE;
                    ending().art().queueModule(services(),source,0x347);}
            }
            case 6 -> {
                move();yVelocity=(short)(yVelocity+0x38);
                if(yVelocity>=0&&getY()>=0xE0){mappingFrame=tails()?3:0;hidePlayer=true;
                    S3kEndingPalette.loadLine(services(),2,tails()?S3kEndingResources.PAL_TAILS_POSE:
                            ending().progress()<0?S3kEndingResources.PAL_SONIC_POSE:S3kEndingResources.PAL_SUPER_SONIC_POSE);
                    // The powered owner, rather than a time estimate, releases native object_control.
                    if(ending().progress()>=0&&(!tails()||ending().progress()!=0)){
                        routine=10;hidePlayer=false;
                        var p=mainPlayer();if(p!=null&&p.getSuperStateController() instanceof Sonic3kSuperStateController powered)
                            powered.startEndingReunionTransformation();
                    }
                    else{routine=8;timer=5;flipX=tails();}}
            }
            case 8 -> { if((timer=(short)(timer-1))<0){routine=18;mappingFrame++;S3kEndingCoordinator.beginPostCredits(services());} }
            case 10 -> {
                var p=mainPlayer();if(p!=null&&!p.isObjectControlled()){
                    if(tails()){routine=8;timer=5;flipX=true;hidePlayer=true;}
                    else{routine=12;ObjectControlState.nativeBit7FullControl().applyTo(p);
                        p.setAnimationId(0);p.setXSpeed((short)0x800);p.setGSpeed((short)0x800);}
                }
            }
            case 12 -> {setX(getX()+4);if(getX()>=0x200){routine=14;timer=0x1F;}}
            case 14 -> {if((timer=(short)(timer-1))<0){routine=16;mappingFrame=2;flipX=false;hidePlayer=true;}}
            case 16 -> {setX(getX()-8);if(getX()<=0x160){routine=18;setX(0x160);S3kEndingCoordinator.beginPostCredits(services());}}
            case 18 -> {if(ending().flag(3))ObjectLifetimeOps.deleteNoRespawn(this);}
            default -> { }
        }
    }
    private void publishPlayer() {
        var p=mainPlayer();if(p==null)return;
        NativePositionOps.writeXPosPreserveSubpixel(p,getX());NativePositionOps.writeYPosPreserveSubpixel(p,getY());
        // Native screen-relative sprite coordinates include the VDP $80 bias.
        // Offsets are derived each pass and after rewind, never gameplay coordinates.
        refreshPlayerProjection();
        if(hidePlayer){p.setObjectMappingFrameControl(true);p.setMappingFrame(0);}
    }
    private void refreshPlayerProjection(){
        var p=mainPlayer();if(p==null)return;var camera=services().camera();
        p.setRenderOffsets((short)EndingScreenProjection.xOffset(camera),
                (short)EndingScreenProjection.yOffset(camera));
    }
    @Override public void refreshPostCameraRenderState(){
        // ScreenEvents can publish copy words after Process_Sprites. Refresh only
        // derived offsets at the existing final Render_Sprites boundary.
        if(sequence!=AIZ_RETURN&&routine!=0)refreshPlayerProjection();
    }
    @Override protected void afterRewindRestoreSettled(){
        // Routine0 has not taken native P1 ownership. Rebuilding its presentation
        // must preserve the header/entry coordinates before the first object pass.
        if(sequence!=AIZ_RETURN) {
            if(routine==0) {
                // Derived screen offsets from a later dispatch are not native RAM.
                // The pre-entry owner has not claimed a screen-relative P1 yet.
                var player=mainPlayer();
                if(player!=null)player.setRenderOffsets((short)0,(short)0);
            }else publishPlayer();
        }
    }
    private AbstractPlayableSprite mainPlayer(){return services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p?p:null;}
    private Actor actor(int role){return services().objectManager().activeObjectsOfType(Actor.class).stream()
            .filter(a->a.role==role&&!a.isDestroyed()).findFirst().orElse(null);}
    private void setX(int x){xFixed=(x&0xFFFF)<<16|(xFixed&0xFFFF);}
    private void setY(int y){yFixed=(y&0xFFFF)<<16|(yFixed&0xFFFF);}
    private void move(){xFixed+=xVelocity<<8;yFixed+=yVelocity<<8;}
    private void bob(){bobPhase=(bobPhase+4)&0xFF;setY(baseY+(com.openggf.physics.TrigLookupTable.sinHex(bobPhase)>>6));}
    private void swing(int acceleration){
        int maximum=swingMaximum;
        if(!swingReverse){acceleration=-acceleration;yVelocity=(short)(yVelocity+acceleration);maximum=-maximum;
            if(yVelocity>maximum)return;swingReverse=true;acceleration=-acceleration;maximum=-maximum;}
        yVelocity=(short)(yVelocity+acceleration);if(yVelocity<maximum)return;
        swingReverse=false;yVelocity=(short)(yVelocity-acceleration);
    }
    private void animatePlane(){
        if(scripts==null)try{scripts=S3kRawAnimation.load(services().romReader(),0x60253,9);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        var animation=new S3kRawAnimation.State();animation.script=0x60253;animation.animFrame=animationFrame;
        animation.animFrameTimer=animationCounter;animation.mappingFrame=mappingFrame;
        scripts.animateMultiDelay(animation,()->{});animationFrame=animation.animFrame;
        animationCounter=animation.animFrameTimer;mappingFrame=animation.mappingFrame;
    }
    private void animateKnucklesReturn(){
        if(knucklesScripts==null)try{knucklesScripts=S3kRawAnimation.load(services().romReader(),0x66824,11);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        var a=new S3kRawAnimation.State();a.script=0x66824;a.animFrame=animationFrame;
        a.animFrameTimer=animationCounter;a.mappingFrame=mappingFrame;
        knucklesScripts.animateCheckResult(a,()->{});animationFrame=a.animFrame;
        animationCounter=a.animFrameTimer;mappingFrame=a.mappingFrame;
    }
    private void draw(int map,int tile,int palette,int frame,int x,int y,boolean flip,boolean high,int bucket,String key){
        var camera=services().camera();
        EndingSpriteRenderSupport.draw(services(),key,map,tile,palette,frame,
                EndingScreenProjection.worldX(camera,x),EndingScreenProjection.worldY(camera,y),flip,high,bucket);
    }

    /** Obj_Ending_RevertSuperSonic is independently allocated and runs after its owner. */
    public static final class EndingRescueRevertObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
        private int routine,timer;
        public EndingRescueRevertObjectInstance(){this(new ObjectSpawn(0,0,0,0,0,false,0));}
        private EndingRescueRevertObjectInstance(ObjectSpawn spawn){super(spawn,"EndingRescueRevert");}
        @Override public EndingRescueRevertObjectInstance recreateForRewind(RewindRecreateContext c){return new EndingRescueRevertObjectInstance(c.spawn());}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            if(!(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p)
                    ||!(p.getSuperStateController() instanceof Sonic3kSuperStateController powered))return;
            if(routine==0){routine=2;timer=119;}
            if(routine==2){if((timer=(short)(timer-1))<0){routine=4;powered.beginEndingRescueRevert();}}
            else{powered.completeEndingRescueRevertMappings();ObjectLifetimeOps.deleteNoRespawn(this);}
        }
        @Override public void appendRenderCommands(List<GLCommand> commands){ }
    }
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(isDestroyed())return;
        if(sequence==AIZ_RETURN&&routine>=4){
            var renderer=getRenderer(S3kEndingArt.EYECATCH_KNUCKLES);
            if(renderer!=null&&renderer.isReady()){
                var camera=services().camera();
                services().graphicsManager().setCurrentSpriteSatBucket(3);
                services().graphicsManager().setCurrentSpriteHighPriority(true);
                renderer.drawFrameIndex(mappingFrame,EndingScreenProjection.worldX(camera,getX()),
                        EndingScreenProjection.worldY(camera,getY()),false,false,0);
            }
            return;
        }
        if(sequence==REUNION){if(routine==8||routine>=16)draw(S3kEndingResources.MAP_SONIC_TAILS_POSE,0x347,2,
                mappingFrame,getX(),getY(),flipX,false,2,"ending-pose");return;}
        draw(S3kEndingResources.MAP_ENDING_PLANE_EXTRA,0x26E,smallPlane?1:0,mappingFrame,
                getX(),getY(),flipX,!smallPlane,getPriorityBucket(),"ending-rescue-extra");
        if((sequence==AIZ_RETURN&&routine==2||sequence==RESCUE&&routine>=22&&routine<=24)){
            int sign=flipX?-1:1;
            if((nativeVInt&2)==0)draw(S3kEndingResources.MAP_ENDING_PLANE_EXTRA,0x26E,0,4,
                    getX()+sign*0x1C,getY(),flipX,true,getPriorityBucket(),"ending-rescue-extra");
            int frame=mappingFrame-1;
            int dx,dy;
            try{var reader=services().romReader();dx=(byte)reader.readU8(0x5EC8E+frame*2);
                dy=(byte)reader.readU8(0x5EC8F+frame*2);}
            catch(IOException failure){throw new UncheckedIOException(failure);}
            draw(S3kEndingResources.MAP_ENDING_PLANE_EXTRA,0x26E,0,0x16+((nativeVInt&2)==0?0:1),
                    getX()+sign*dx,getY()+dy,flipX,true,3,"ending-rescue-extra");
        }
    }

    /** Native fixed plane/master and allocated catcher, each with a managed SST identity. */
    public static final class Actor extends AbstractObjectInstance implements RewindRecreatable {
        static final int TORNADO=0,MASTER=1,CATCHER=2,REUNION_PLANE=3,MASTER_AIZ=4;
        private int role,routine,xFixed,yFixed,xVelocity,yVelocity,baseY,phase,timer,dx=-0x40;
        private int noseCounter, nativeVInt;
        public Actor(int role){this(new ObjectSpawn(0,0,0,role,0,false,0));}
        private Actor(ObjectSpawn spawn){super(spawn,"EndingActor");role=spawn.subtype();}
        @Override public Actor recreateForRewind(RewindRecreateContext c){return new Actor(c.spawn());}
        @Override public int getX(){return xFixed>>>16;}
        @Override public int getY(){return yFixed>>>16;}
        @Override public boolean isPersistent(){return true;}
        @Override public int getPriorityBucket(){return role==MASTER?5:role==MASTER_AIZ?6:4;}
        private EndingRescueDirectorObjectInstance owner(){return services().objectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).stream().filter(o->!o.isDestroyed()).findFirst().orElse(null);}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            nativeVInt=vIntRunCount;
            var owner=owner();if(owner==null){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if(role==TORNADO||role==REUNION_PLANE)plane(owner);
            else if(role==MASTER||role==MASTER_AIZ)master(owner);else catcher(owner);
        }
        private void plane(EndingRescueDirectorObjectInstance o){
            noseCounter=(noseCounter+1)%6; // sub_60038 increments the object's $44 byte
            if(routine==0){routine=4;setX(0x40);setY(0x10B);baseY=getY();
                if(role==REUNION_PLANE){routine=2;setX(o.getX()-0x20);setY(o.getY()+(o.tails()?0x27:0x2B));}
                else if(o.ending().progress()>=0)spawnChild(()->new Actor(CATCHER));return;}
            if(o.retired){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if(routine==4){if(o.planeReleased)routine=6;return;} // loc_5EB02 returns after selecting6
            if(routine==6){if(o.ending().flag(0)){routine=2;return;}setX(getX()+2);bob();return;}
            if(routine==2){
                if(role==REUNION_PLANE&&o.planeReleased){routine=8;baseY=getY();return;}
                setX(o.getX()-0x20);setY(o.getY()+(o.tails()?0x27:0x2B));return;}
            if(routine==8)bob();
        }
        private void master(EndingRescueDirectorObjectInstance o){
            if(routine==0){routine=2;setX(role==MASTER_AIZ?0x190:0x11E);setY(role==MASTER_AIZ?0xDC:0xCF);
                if(role==MASTER&&o.ending().progress()<0){routine=8;timer=119;}return;}
            if(routine==2&&o.ending().flag(0)){routine=4;xVelocity=-0x200;yVelocity=0x100;}
            if(routine==4){if(o.ram().catcherReady)routine=6;else move();}
            if(routine==6){var c=o.actor(CATCHER);if(c==null){ObjectLifetimeOps.deleteNoRespawn(this);return;}
                setX(c.getX()-0x20);setY(c.getY()+1);
                if(getX()<=0x50||o.retired)ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if(routine==8){if((timer=(short)(timer-1))<0){routine=10;yVelocity=-0x80;}return;}
            if(routine==10){move();if(getY()<0x40)ObjectLifetimeOps.deleteNoRespawn(this);}
        }
        private void catcher(EndingRescueDirectorObjectInstance o){
            var plane=o.actor(TORNADO);if(plane==null||o.retired){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            if(routine==0){routine=2;setX(plane.getX()+dx);setY(plane.getY()-4);return;}
            if(routine==2){if(o.ending().flag(1))routine=4;
                setX(plane.getX()+dx);setY(plane.getY()-4);return;}
            if(routine==4){dx=(byte)(dx-2);if(dx<=-0x74){routine=6;o.ram().catcherReady=true;}}
            setX(plane.getX()+dx);setY(plane.getY()-4);
            if(routine==6&&getX()<=0x50)ObjectLifetimeOps.deleteNoRespawn(this);
        }
        private void setX(int x){xFixed=(x&0xFFFF)<<16|(xFixed&0xFFFF);}
        private void setY(int y){yFixed=(y&0xFFFF)<<16|(yFixed&0xFFFF);}
        private void move(){xFixed+=xVelocity<<8;yFixed+=yVelocity<<8;}
        private void bob(){phase=(phase+4)&0xFF;setY(baseY+(com.openggf.physics.TrigLookupTable.sinHex(phase)>>6));}
        record Subsprite(int frame,int x,int y) { }
        List<Subsprite> subsprites(){
            try{
                var reader=services().romReader();int offsets=S3kEndingResources.TORNADO_SUBSPRITE_OFFSETS;
                // sub_86458 adds signed byte offsets to the same SST's main coordinates.
                return List.of(new Subsprite(reader.readU8(S3kEndingResources.TORNADO_NOSE_FRAMES+noseCounter),
                                getX()+(byte)reader.readU8(offsets),getY()+(byte)reader.readU8(offsets+1)),
                        new Subsprite(6+(nativeVInt&1),getX()+(byte)reader.readU8(offsets+2),
                                getY()+(byte)reader.readU8(offsets+3)));
            }catch(IOException failure){throw new UncheckedIOException(failure);}
        }
        @Override public void appendRenderCommands(List<GLCommand> commands){
            var o=owner();if(o==null||isDestroyed())return;
            if(role==MASTER||role==MASTER_AIZ)o.draw(S3kEndingResources.MAP_MASTER_EMERALD,0x52E,
                    role==MASTER_AIZ?1:2,2,getX(),getY(),false,role==MASTER,getPriorityBucket(),"ending-master");
            else if(role==CATCHER)o.draw(S3kEndingResources.MAP_ENDING_PLANE_EXTRA,0x26E,0,7,getX(),getY(),false,true,4,"ending-catcher");
            else{
                o.draw(S3kEndingResources.MAP_ENDING_PLANE,0x1E3,0,1,getX(),getY(),false,true,4,"ending-tornado");
                // sub_60038: six-pass nose cycle and alternating propeller piece.
                for(var part:subsprites())o.draw(S3kEndingResources.MAP_ENDING_PLANE,0x1E3,0,
                        part.frame(),part.x(),part.y(),false,true,4,"ending-tornado");
                if(o.tails())o.draw(S3kEndingResources.MAP_ENDING_PLANE_EXTRA,0x26E,0,0xB,
                        getX(),getY()-0x10,false,true,4,"ending-tornado-pilot");
                else o.draw(S3kEndingResources.MAP_ENDING_PLANE,0x1E3,0,0xC,
                        getX(),getY()-0x10,false,true,4,"ending-tornado");
            }
        }
    }
}
