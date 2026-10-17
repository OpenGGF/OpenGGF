package com.openggf.game.sonic3k.ending;

import com.openggf.game.GameOverExit;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.objects.bosses.SszMechaPalette;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Obj_5EF68's banner hold/fade and its locked-on terminal dispatch. Native
 * sprite animations are interpreted from ROM; the outgoing level owns the
 * same timeline until the selected Sega/title exit.
 */
public final class S3kPostCreditsObjectInstance extends AbstractObjectInstance implements RewindRecreatable, com.openggf.game.sonic3k.objects.bosses.SszMechaSparkHost {
    private int phase;
    private int timer;
    private int afterBanner;
    private int bannerInitializer;
    private int fadeWait;
    private int fadeSteps;
    private int fadeAddress;
    private int frame;
    private int screenX = 0x120;
    private int screenY = 0xF0;
    private int motionY;
    private int motionVelocity;
    private int childFrame;
    private int childTimer;
    private int childStage;
    // The phase whose picture stays drawn after loc_5F314 (phase 8 runs nothing).
    private int drawPhase;
    // loc_5FBA0's own SST: alive flag, anim_frame_timer byte and $3A table offset.
    private boolean poseFlash;
    private int poseFlashTimer;
    private int poseFlashOffset;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State childAnimation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State tailsAnimation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State knucklesAnimation = new S3kRawAnimation.State();
    // loc_5F3EA/loc_5F400: the Tails child's own tail child (ChildObjDat_60204).
    private final S3kRawAnimation.State tailAnimation = new S3kRawAnimation.State();
    private final SszMechaPalette mechaPalette = new SszMechaPalette();
    private transient S3kRawAnimation scripts;
    private transient S3kRawAnimation mechaScripts;
    private transient S3kRawAnimation knucklesScripts;

    public S3kPostCreditsObjectInstance() { this(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)); }
    private S3kPostCreditsObjectInstance(ObjectSpawn spawn) { super(spawn, "S3KPostCredits"); }
    @Override public S3kPostCreditsObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new S3kPostCreditsObjectInstance(context.spawn());
    }
    private S3kEndingState ending() { return S3kEndingCoordinator.state(services()); }
    private PlayerCharacter character() { return ((S3kZoneRuntimeState) services().zoneRuntimeState()).playerCharacter(); }
    private record Extra(int phase, int timer, int afterBanner, int bannerInitializer, int fadeWait,
                         int fadeSteps, int fadeAddress, int frame, int screenX, int screenY,
                         int motionY, int motionVelocity, int childFrame, int childTimer, int childStage,
                         boolean poseFlash, int poseFlashTimer, int poseFlashOffset,
                         S3kRawAnimation.State.Value animation, S3kRawAnimation.State.Value childAnimation,
                         S3kRawAnimation.State.Value tailsAnimation, S3kRawAnimation.State.Value knucklesAnimation,
                         S3kRawAnimation.State.Value tailAnimation, SszMechaPalette.Value mechaPalette, int drawPhase)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new Extra(phase, timer, afterBanner,
                bannerInitializer, fadeWait, fadeSteps, fadeAddress, frame, screenX, screenY,
                motionY, motionVelocity, childFrame, childTimer, childStage,
                poseFlash, poseFlashTimer, poseFlashOffset,
                animation.captureRewindStateValue(), childAnimation.captureRewindStateValue(),
                tailsAnimation.captureRewindStateValue(),knucklesAnimation.captureRewindStateValue(),
                tailAnimation.captureRewindStateValue(),mechaPalette.captureRewindStateValue(),drawPhase));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof Extra e) {
            phase=e.phase(); timer=e.timer(); afterBanner=e.afterBanner(); bannerInitializer=e.bannerInitializer();
            fadeWait=e.fadeWait(); fadeSteps=e.fadeSteps(); fadeAddress=e.fadeAddress(); frame=e.frame();
            screenX=e.screenX(); screenY=e.screenY(); motionY=e.motionY(); motionVelocity=e.motionVelocity();
            childFrame=e.childFrame(); childTimer=e.childTimer(); childStage=e.childStage();
            poseFlash=e.poseFlash(); poseFlashTimer=e.poseFlashTimer(); poseFlashOffset=e.poseFlashOffset();
            animation.restoreRewindStateValue(e.animation()); childAnimation.restoreRewindStateValue(e.childAnimation());
            tailsAnimation.restoreRewindStateValue(e.tailsAnimation());knucklesAnimation.restoreRewindStateValue(e.knucklesAnimation());
            tailAnimation.restoreRewindStateValue(e.tailAnimation());
            mechaPalette.restoreRewindStateValue(e.mechaPalette()); drawPhase=e.drawPhase();
        }
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        ending().art().service(services());
        switch (phase) {
            case 0 -> initializeBanner();
            case 1 -> {
                stepBannerFade(); stepKnucklesPose();
                timer = (short) (timer - 1);
                if (timer == 0x40) ending().setFlag(2); // loc_5F144
                if (timer < 0) {
                    phase=2; fadeWait=0; fadeSteps=7; rotationDisabled(true); stopPaletteCycles();
                    stepObjectFade(); // the allocated fade runs after loc_5F15A in this pass
                }
                stepPoseFlash(); // slot 8 runs after slot 4's loc_5F15A
            }
            case 2 -> {
                // loc_5F18A sees the deleted fade's status bit 7 on the pass after its eighth step.
                if (fadeSteps < 0) { ending().setFlag(3); initializeAfterBanner(); }
                else stepObjectFade();
            }
            case 3 -> { // loc_5FD5A: 32 ticks before the final S3 pose banner
                if ((timer=(short)(timer-1)) < 0) initializeS3Eyecatch();
            }
            case 4 -> { // Eggman, loc_5F5B4
                stepRevealFade(); scripts().animateNoSst(animation, 0x602C6, () -> { });
                frame=animation.mappingFrame; terminalHold();
            }
            case 5 -> stepEggRobo();
            case 6 -> stepS3Eyecatch();
            case 7 -> stepMechaEyecatch();
            case 8 -> { }
            default -> throw new IllegalStateException("Native terminal phase " + phase);
        }
    }
    private void initializeBanner() {
        // Obj_5EF68 (044fa46 sonic3k.asm:125517-125520): clr.b (Super_palette_status).w before the dispatch.
        if (services().playerQuery().mainPlayerOrNull() instanceof com.openggf.sprites.playable.AbstractPlayableSprite player
                && player.getSuperStateController() instanceof com.openggf.game.sonic3k.Sonic3kSuperStateController powered)
            powered.clearEndingPaletteStatus();
        try {
            var rom=services().romReader();
            var dispatch=S3kPostCreditsData.resolve(rom,character(),ending().progress());
            bannerInitializer=dispatch.bannerInitializer();afterBanner=dispatch.afterBanner();
            // sub_5FF1C: loc_5F20A's subtype selects its word_5F260 position.
            screenX=S3kPostCreditsData.bannerX(rom,dispatch); screenY=S3kPostCreditsData.bannerY(rom,dispatch);
            fadeAddress=S3kEndingResources.PAL_S3K_LOGO;
            ending().art().queueModule(services(), S3kEndingResources.S3_BANNER, 0x415);
            S3kEndingPalette.targetLine(services(),1,fadeAddress); S3kEndingPalette.blackLine(services(),1);
            if (dispatch.knucklesPose()) {
                ending().art().loadRaw(services(),S3kEndingResources.KNUCKLES_POSE,0x2C1);
                S3kEndingPalette.loadLine(services(),0,S3kEndingResources.PAL_KNUCKLES_POSE);
                S3kRawAnimation.set(animation, 0x602CA);
                // loc_5F480 (sonic3k.asm:125947): tst.b (_unkFA88).w; beq/bmi skip, so only
                // +2 allocates loc_5FBA0. That slot follows the pose and runs this pass.
                poseFlash=ending().progress()>0; poseFlashTimer=0; poseFlashOffset=0;
            }
            timer=0x1FF; fadeWait=0; fadeSteps=15; phase=1;
            stepPoseFlash(); stepBannerFade();
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    /**
     * Obj_FadeSelectedToBlack (044fa46 sonic3k.asm:179589-179612): its first pass falls into
     * loc_8555E with $2E clear, so DecColor_Obj runs at once, then every sixth pass, eight times.
     * The eighth step clears Palette_rotation_disable and deletes the fade.
     */
    private void stepObjectFade() {
        if (--fadeWait >= 0) return;
        fadeWait=5; S3kEndingPalette.decreaseAll(services());
        if (--fadeSteps < 0) rotationDisabled(false);
    }
    private void stepBannerFade() { stepThroughWhite(false); }
    /**
     * loc_5FF62 (044fa46 sonic3k.asm:126999), allocated by sub_5FF1C after the logo: every fourth
     * pass, from the allocating one, sub_85EB4 raises the 16 line-1 colours a level toward
     * $EEE, eight times. loc_5FF94 then resets $2E to 3 and falls into loc_5FFA6's subq,
     * and sub_85F2A lowers them toward Target_palette_line_2, eight more times.
     * The S3 eyecatch has three loc_5FF62 instances: sub_5FF1C's line 1 (16 colours),
     * loc_5F31C's Normal_palette+2 and loc_5F410's Normal_palette_line_3+2 ($3A=$E, colours
     * 1-15). Each lands after its allocator (native slots 4 -> 5, 8 -> 11, 10 -> 13), so all
     * three step on the initialization pass and share this countdown.
     */
    private void stepThroughWhite(boolean eyecatch) {
        if (fadeSteps<0 || (fadeWait=(short)(fadeWait-1))>=0) return;
        fadeWait=3;
        boolean raise=fadeSteps>=8;
        fadeThroughWhite(1,0,raise);
        if (eyecatch) { fadeThroughWhite(0,1,raise); fadeThroughWhite(2,1,raise); }
        if (raise) { if (--fadeSteps==7) fadeWait=2; }
        else fadeSteps--;
    }
    private void fadeThroughWhite(int line,int first,boolean raise) {
        if (raise) S3kEndingPalette.towardsWhite(services(),line,first);
        else S3kEndingPalette.downTowardsActualTarget(services(),line,first);
    }
    /**
     * loc_5F15A (044fa46 sonic3k.asm:125704-125706) sets Palette_cycle_counters+0 beside the object fade.
     * Nothing in the terminal clears it, so D01's sub_5928C water cycle stays stopped and the
     * faded line 3 stays black (native PAL Sonic/+2, poll 13311).
     */
    private void stopPaletteCycles() {
        if (services().zoneRuntimeState() instanceof com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState zone)
            zone.screen().aizPalette.beginFade();
    }
    /**
     * loc_5FBA0 (sonic3k.asm:126562): deleted once Palette_cycle_counters+0 is set; otherwise
     * every third pass ($E after the wrap) it steps $3A by 6 through Pal_KnuxEndPoseSuper.
     * move.l (a1,d0.w),(a2)+ writes colours 2-3 and move.w 4(a1,d0.w),2(a2) lands on
     * colour 5, so colour 4 keeps Pal_KnuxEndPose.
     */
    private void stepPoseFlash() {
        if (!poseFlash) return;
        var registry=services().paletteOwnershipRegistryOrNull();
        if (registry!=null && registry.isPaletteRotationDisabled()) { poseFlash=false; return; }
        if ((poseFlashTimer=(byte)(poseFlashTimer-1))>=0) return;
        poseFlashTimer=2;
        poseFlashOffset+=6;
        if (poseFlashOffset>=0x3C) { poseFlashOffset=0; poseFlashTimer=0xE; }
        S3kEndingPalette.loadColors(services(),0,S3kEndingResources.PAL_KNUCKLES_POSE_SUPER+poseFlashOffset,2,3,5);
    }
    private boolean knucklesPose() { return new S3kPostCreditsData.Dispatch(bannerInitializer,afterBanner).knucklesPose(); }
    private void stepKnucklesPose() {
        if (knucklesPose()) {
            // loc_5F4E4 uses Animate_Raw (one delay, then frame bytes),
            // and its jump updates the stored $30 script pointer.
            scripts().animateNoSst(animation,animation.script, () -> { });
            frame=animation.mappingFrame;
        }
    }
    private void initializeAfterBanner() {
        screenX=0x120; screenY=0x108; frame=0;
        switch(afterBanner) {
            case 0x5F0DA -> finish();
            case 0x5F116 -> { phase=3; timer=31; }
            case 0x5F0E2 -> {
                phase=4; screenX=0x110; screenY=0xF0; timer=0x4AF;
                reveal(S3kEndingResources.PAL_EGGMAN_EYECATCH);
                ending().art().queueModule(services(),S3kEndingResources.EGGMAN_EYECATCH,0x300);
                services().objectManager().createDynamicObject(()->new EndingTerminalMasterObjectInstance(4));
            }
            case 0x5F106 -> {
                phase=5; childStage=0; childTimer=119; timer=0xEF;
                reveal(S3kEndingResources.PAL_EGG_ROBO_EYECATCH);
                ending().art().queueModule(services(),S3kEndingResources.EGG_ROBO_EYECATCH,0x300);
                S3kRawAnimation.set(childAnimation,0x602A7);
            }
            case 0x5F126 -> {
                phase=7; childStage=0; timer=0xEF; frame=0xF;
                screenY=0xF8; reveal(0x7D850); // Pal_SSZGHZMisc: loc_5F74E's actual LEA pointer
                ending().art().queueModule(services(),Sonic3kConstants.ART_KOSM_MECHA_SONIC_EXTRA_ADDR,0x41C);
                services().objectManager().createDynamicObject(()->new EndingTerminalMasterObjectInstance(0));
                com.openggf.game.sonic3k.objects.bosses.SszMechaSonicSparkChild.spawnFor(
                        services(),getSlotIndex(),screenX,screenY,0x80);
                mechaPalette.install(services(),0x7D842);
            }
            default -> throw new IllegalStateException("Unknown native post-credit pointer $"+Integer.toHexString(afterBanner));
        }
    }
    @Override public boolean stopsSparks(){return phase==7&&childStage!=0;}
    @Override public boolean sparkFlipX(){return false;}
    /** Native dispatches that actually call terminalHold and consume held Start. */
    public boolean terminalInputReady(){return phase==4||phase==6
            ||(phase==5&&childStage==3)||(phase==7&&childStage==5);}
    private void reveal(int palette) {
        fadeAddress=palette; fadeWait=5; fadeSteps=7;
        S3kEndingPalette.targetLine(services(),1,palette); S3kEndingPalette.blackLine(services(),1);
        rotationDisabled(true);
    }
    private void stepRevealFade() {
        if (fadeSteps>=0 && --fadeWait<0) {
            fadeWait=5; S3kEndingPalette.towardsTarget(services(),1,fadeAddress);
            if (--fadeSteps<0) rotationDisabled(false);
        }
    }
    private void initializeS3Eyecatch() {
        phase=6; screenX=0x120; screenY=0xF0; timer=0x4AF; childStage=0; childFrame=1;
        ending().clearFlag(2);
        ending().art().loadRaw(services(),S3kEndingResources.S3_ENDING_GRAPHICS,0x45D);
        // sub_5FF1C blacks line 1 before loc_5F294 loads its target; loc_5F31C and loc_5F410
        // load Target_palette lines 0 and 2. None of them touches Palette_rotation_disable.
        S3kEndingPalette.blackLine(services(),1);
        S3kEndingPalette.targetLine(services(),1,S3kEndingResources.PAL_S3K_LOGO);
        S3kEndingPalette.targetLine(services(),0,Sonic3kConstants.SONIC_PALETTE_ADDR);
        S3kEndingPalette.targetLine(services(),2,S3kEndingResources.PAL_EYECATCH_KNUCKLES);
        S3kRawAnimation.set(knucklesAnimation,0x66771);knucklesAnimation.mappingFrame=0xD8;
        tailsAnimation.mappingFrame=0xA;
        // loc_5F3AA's CreateChild1_Normal(ChildObjDat_60204) lands after the Tails child, and
        // loc_5F3EA (word_60170 frame $A) falls into loc_5F400's first Animate_RawNoSST this pass.
        S3kRawAnimation.set(tailAnimation,0x6026F);tailAnimation.mappingFrame=0xA;
        scripts().animateNoSst(tailAnimation,0x6026F,()->{});
        fadeWait=0; fadeSteps=15; stepThroughWhite(true);
    }
    private void stepS3Eyecatch() {
        stepThroughWhite(true);
        scripts().animateNoSst(tailsAnimation,0x6026B,()->{});
        scripts().animateNoSst(tailAnimation,0x6026F,()->{}); // loc_5F400: AniRaw_6026F
        if(knucklesScripts==null)try{knucklesScripts=S3kRawAnimation.load(services().romReader(),0x66771,0x100);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        knucklesScripts.animateRaw2MultiDelay(knucklesAnimation,()->{});
        if (childStage==0) {
            scripts().animateNoSst(childAnimation,0x6025C,()->{});
            if (ending().flag(2)) { childStage=1; S3kRawAnimation.set(childAnimation,0x60260); }
        } else scripts().animateNoSst(childAnimation,childAnimation.script,()->{}); // loc_5F39E: Animate_Raw
        childFrame=childAnimation.mappingFrame;
        terminalHold();
    }
    private void stepEggRobo() {
        stepRevealFade();
        switch(childStage) {
            case 0 -> { if ((childTimer=(short)(childTimer-1))<0) childStage=1; }
            case 1 -> scripts().animateMultiDelay(childAnimation,()->{ childStage=2; timer=31; });
            case 2 -> {
                screenY--;
                if ((timer=(short)(timer-1))<0) { childStage=3; timer=0x4AF; }
            }
            case 3 -> { scripts().animateNoSst(animation,0x602C2,()->{}); frame=animation.mappingFrame; terminalHold(); }
            default -> throw new IllegalStateException("Native EggRobo eyecatch stage " + childStage);
        }
        childFrame=childAnimation.mappingFrame;
    }
    private void stepMechaEyecatch() {
        stepRevealFade();
        switch(childStage) {
            case 0 -> {
                if ((timer=(short)(timer-1))<0) {childStage=6;frame=0xE;
                    S3kEndingPalette.loadLine(services(),1,0x7D850);
                    EndingMechaChargeObjectInstance.spawn(services(),this);
                }else mechaPalette.tick(services(),()->{});
            }
            case 1 -> mechaScripts().animateMultiDelay(animation,()->{
                childStage=2; S3kRawAnimation.set(animation,0x7D5F6);
            });
            case 2 -> mechaScripts().animateMultiDelay(animation,()->{
                childStage=3; motionVelocity=-0x400; motionY=screenY<<16;
            });
            case 3 -> {
                motionY+=motionVelocity<<8; motionVelocity=(short)(motionVelocity+0x20); screenY=motionY>>16;
                if (motionVelocity>=0 && screenY>=0xC8) {
                    screenY=0xCC; childStage=4; S3kRawAnimation.set(animation,0x7D5AB);
                    services().playSfx(Sonic3kSfx.MECHA_LAND.id);
                }
            }
            case 4 -> mechaScripts().animateMultiDelay(animation,()->{childStage=5;timer=0x4AF;});
            case 5 -> terminalHold();
            case 6 -> { } // Native bit6 is released by the allocated charge child, not a timer.
            case 8 -> {
                // loc_5F802 observes the released child bit, initializes the
                // parent's animation and returns. loc_5F82C animates next pass.
                childStage=1;S3kRawAnimation.set(animation,0x7D5EF);animation.mappingFrame=0xE;
            }
            default -> throw new IllegalStateException("Native Mecha eyecatch stage " + childStage);
        }
        if(childStage>0&&childStage!=6) frame=animation.mappingFrame;
    }
    void finishMechaCharge(){if(phase==7&&childStage==6)childStage=8;}
    int mappingFrame(){return frame;}
    int mechaStage(){return childStage;}
    int mechaAnimationTimer(){return animation.animFrameTimer;}
    private void terminalHold() {
        if (timer==0x40) ending().setFlag(2);
        if (ending().startHeld() || (timer=(short)(timer-1))<0) finish();
    }
    /**
     * loc_5F314 / loc_5F0DA (044fa46 sonic3k.asm:125858, 125642) only write Game_mode 0: the SST
     * stays, so the last sprite table stays on screen through Title_Screen's entry fade.
     */
    private void finish() {
        drawPhase=phase; phase=8; ending().beginTitleEntryFade();
        services().levelManager().requestGameOverExit(GameOverExit.TITLE_SCREEN);
    }
    private void rotationDisabled(boolean value) {
        var registry=services().paletteOwnershipRegistryOrNull(); if(registry!=null) registry.setPaletteRotationDisabled(value);
    }
    private S3kRawAnimation scripts() {
        if(scripts==null) try { scripts=S3kRawAnimation.load(services().romReader(),0x60236,0xA0); }
        catch(IOException failure){throw new UncheckedIOException(failure);} return scripts;
    }
    private S3kRawAnimation mechaScripts() {
        if(mechaScripts==null) try { mechaScripts=S3kRawAnimation.load(services().romReader(),0x7D590,0x120); }
        catch(IOException failure){throw new UncheckedIOException(failure);} return mechaScripts;
    }
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return true;}
    @Override public int getPriorityBucket(){return 3;} // ObjDat3_60124: priority=$180
    @Override public int getX(){return screenX;}
    @Override public int getY(){return screenY;}
    public int phase(){return phase;}
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        int phase=this.phase==8?drawPhase:this.phase; // the retained picture after loc_5F314
        if(isDestroyed() || phase==0 || phase==8) return;
        // loc_5F2F8 (044fa46 sonic3k.asm:125848-125861), shared by every terminal hold, takes
        // loc_5F314 instead of Draw_Sprite: the holder's own sprite leaves the last table while
        // its separately allocated children, TM and caption keep theirs.
        boolean held=this.phase==8&&drawPhase>=4;
        if(phase<=2) {
            // Each initializer returns before Draw_Sprite; the logo, pose and
            // Child_Draw_Sprite children first draw on the next pass.
            if(phase==1&&timer==0x1FF)return;
            draw(S3kEndingArt.S3_BANNER,0,screenX,screenY,false,-1);
            draw(S3kEndingArt.AND_KNUCKLES,0,screenX,screenY+0x14,false,-1);
            drawTrademark(2);
            if(knucklesPose())
                draw(S3kEndingArt.KNUCKLES_POSE,frame,0x120,0x108,false,-1);
        } else if(phase==4) { if(!held) draw(S3kEndingArt.EGGMAN,frame,screenX,screenY,false,-1); }
        else if(phase==5) {
            if(!held) draw(S3kEndingArt.EGG_ROBO,frame,screenX,screenY,false,-1);
            if(childStage==1 || childStage==2) draw(S3kEndingArt.EGG_ROBO,childFrame,screenX,screenY+0x1C,false,-1);
        } else if(phase==6) {
            boolean tails=character()==PlayerCharacter.TAILS_ALONE,knux=character()==PlayerCharacter.KNUCKLES;
            // On the initialization pass only loc_5F3EA falls into Draw_Sprite: loc_5F26C, the TM
            // and the three children return first (stepThroughWhite has just left 14/3).
            boolean initPass=fadeSteps==14&&fadeWait==3;
            if(!initPass) {
                if(!held) draw(S3kEndingArt.S3_GRAPHICS,0,screenX,screenY,false,-1);
                draw(S3kEndingArt.AND_KNUCKLES,1,screenX,screenY+0x48,false,-1);
                drawTrademark(4);
            }
            // Native SAT order is the slot order Sonic 8, Tails 9, Knuckles 10, tail 12 (earlier
            // link in front); each call here paints over the previous one, so the order is reversed.
            // loc_5F400 shares the Tails child's mappings/art at (-$10,+8).
            draw(S3kEndingArt.S3_GRAPHICS,tailAnimation.mappingFrame,screenX+(tails?8:0x24)-0x10,screenY+(tails?0x40:0x2C)+8,false,0);
            if(initPass)return;
            draw(S3kEndingArt.EYECATCH_KNUCKLES,knucklesAnimation.mappingFrame,screenX+(knux?0:tails?0x1C:-0x1C),screenY+(knux?0x40:0x2C),false,2);
            draw(S3kEndingArt.S3_GRAPHICS,tailsAnimation.mappingFrame,screenX+(tails?8:0x24),screenY+(tails?0x40:0x2C),false,0);
            draw(S3kEndingArt.S3_GRAPHICS,childFrame,screenX+(tails||knux?-0x1C:0),screenY+(tails||knux?0x2C:0x40),false,0);
        } else if(phase==7&&!held) draw(Sonic3kObjectArtKeys.MECHA_SONIC,frame,screenX,screenY,false,-1);
    }
    /**
     * loc_5F4FA (sonic3k.asm:125982): ObjDat3_60130's TM, mapping frame 2, placed by
     * loc_5FFD4 at byte_5F520[subtype] from its parent. loc_5F20A passes subtype 2 and
     * loc_5F26C subtype 4. Its Graphics_flags bit-7 gate deletes it on Japanese
     * hardware; like the title screen's TM, the engine models overseas hardware.
     */
    private void drawTrademark(int subtype) {
        try {
            var rom=services().romReader();
            int offset=S3kEndingResources.TM_OFFSETS+subtype;
            draw(S3kEndingArt.AND_KNUCKLES,2,screenX+(byte)rom.readU8(offset),screenY+(byte)rom.readU8(offset+1),false,-1);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void draw(String key,int mappingFrame,int x,int y,boolean flip,int palette) {
        var renderer=getRenderer(key); if(renderer==null||!renderer.isReady())return;
        var camera=services().camera();
        int bucket=key.equals(S3kEndingArt.KNUCKLES_POSE)||key.equals(S3kEndingArt.EYECATCH_KNUCKLES)
                ||key.equals(S3kEndingArt.S3_GRAPHICS)&&mappingFrame!=0?2:
                key.equals(S3kEndingArt.AND_KNUCKLES)&&mappingFrame==1?6:
                key.equals(S3kEndingArt.EGGMAN)||key.equals(S3kEndingArt.EGG_ROBO)||key.equals(Sonic3kObjectArtKeys.MECHA_SONIC)?4:3;
        services().graphicsManager().setCurrentSpriteSatBucket(bucket);
        renderer.drawFrameIndex(mappingFrame,EndingScreenProjection.worldX(camera,x),EndingScreenProjection.worldY(camera,y),flip,false,palette);
    }
}
