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
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State childAnimation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State tailsAnimation = new S3kRawAnimation.State();
    private final S3kRawAnimation.State knucklesAnimation = new S3kRawAnimation.State();
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
                         S3kRawAnimation.State.Value animation, S3kRawAnimation.State.Value childAnimation,
                         S3kRawAnimation.State.Value tailsAnimation, S3kRawAnimation.State.Value knucklesAnimation,
                         SszMechaPalette.Value mechaPalette)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new Extra(phase, timer, afterBanner,
                bannerInitializer, fadeWait, fadeSteps, fadeAddress, frame, screenX, screenY,
                motionY, motionVelocity, childFrame, childTimer, childStage,
                animation.captureRewindStateValue(), childAnimation.captureRewindStateValue(),
                tailsAnimation.captureRewindStateValue(),knucklesAnimation.captureRewindStateValue(),mechaPalette.captureRewindStateValue()));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof Extra e) {
            phase=e.phase(); timer=e.timer(); afterBanner=e.afterBanner(); bannerInitializer=e.bannerInitializer();
            fadeWait=e.fadeWait(); fadeSteps=e.fadeSteps(); fadeAddress=e.fadeAddress(); frame=e.frame();
            screenX=e.screenX(); screenY=e.screenY(); motionY=e.motionY(); motionVelocity=e.motionVelocity();
            childFrame=e.childFrame(); childTimer=e.childTimer(); childStage=e.childStage();
            animation.restoreRewindStateValue(e.animation()); childAnimation.restoreRewindStateValue(e.childAnimation());
            tailsAnimation.restoreRewindStateValue(e.tailsAnimation());knucklesAnimation.restoreRewindStateValue(e.knucklesAnimation());
            mechaPalette.restoreRewindStateValue(e.mechaPalette());
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
                if (timer < 0) { phase=2; fadeWait=0; fadeSteps=7; rotationDisabled(true); }
            }
            case 2 -> {
                if (--fadeWait < 0) {
                    fadeWait=5; S3kEndingPalette.decreaseAll(services());
                    if (--fadeSteps < 0) {
                        rotationDisabled(false); ending().setFlag(3); // loc_5F18A
                        initializeAfterBanner();
                    }
                }
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
        try {
            var dispatch=S3kPostCreditsData.resolve(services().romReader(),character(),ending().progress());
            bannerInitializer=dispatch.bannerInitializer();afterBanner=dispatch.afterBanner();
            boolean tails=character()==PlayerCharacter.TAILS_ALONE, knux=character()==PlayerCharacter.KNUCKLES;
            screenX=tails?0xD0:0x120; screenY=knux?0xF0:tails?0xB8:0xB0;
            fadeAddress=S3kEndingResources.PAL_S3K_LOGO;
            ending().art().queueModule(services(), S3kEndingResources.S3_BANNER, 0x415);
            S3kEndingPalette.targetLine(services(),1,fadeAddress); S3kEndingPalette.blackLine(services(),1);
            if (knux && ending().progress()>=0) {
                ending().art().loadRaw(services(),S3kEndingResources.KNUCKLES_POSE,0x2C1);
                S3kEndingPalette.loadLine(services(),0,S3kEndingResources.PAL_KNUCKLES_POSE);
                S3kRawAnimation.set(animation, 0x602CA);
            }
            timer=0x1FF; fadeWait=0; fadeSteps=7; phase=1;
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void stepBannerFade() {
        if (fadeSteps>=0 && --fadeWait<0) {
            fadeWait=3; S3kEndingPalette.towardsTarget(services(),1,fadeAddress); fadeSteps--;
        }
    }
    private void stepKnucklesPose() {
        if (character()==PlayerCharacter.KNUCKLES && ending().progress()>=0) {
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
        S3kEndingPalette.targetLine(services(),0,Sonic3kConstants.SONIC_PALETTE_ADDR);
        S3kEndingPalette.targetLine(services(),2,S3kEndingResources.PAL_EYECATCH_KNUCKLES);
        S3kRawAnimation.set(knucklesAnimation,0x66771);knucklesAnimation.mappingFrame=0xD8;
        tailsAnimation.mappingFrame=0xA;
        reveal(S3kEndingResources.PAL_S3K_LOGO);
    }
    private void stepS3Eyecatch() {
        stepRevealFade();
        if(fadeSteps>=0&&fadeWait==5){
            S3kEndingPalette.towardsTarget(services(),0,Sonic3kConstants.SONIC_PALETTE_ADDR);
            S3kEndingPalette.towardsTarget(services(),2,S3kEndingResources.PAL_EYECATCH_KNUCKLES);
        }
        scripts().animateNoSst(tailsAnimation,0x6026B,()->{});
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
    private void finish() {
        phase=8; services().levelManager().requestGameOverExit(GameOverExit.TITLE_SCREEN);
        ObjectLifetimeOps.deleteNoRespawn(this);
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
        if(isDestroyed() || phase==0 || phase==8) return;
        if(phase<=2) {
            draw(S3kEndingArt.S3_BANNER,0,screenX,screenY,false,-1);
            draw(S3kEndingArt.AND_KNUCKLES,0,screenX,screenY+0x14,false,-1);
            if(character()==PlayerCharacter.KNUCKLES && ending().progress()>=0)
                draw(S3kEndingArt.KNUCKLES_POSE,frame,0x120,0x108,false,-1);
        } else if(phase==4) draw(S3kEndingArt.EGGMAN,frame,screenX,screenY,false,-1);
        else if(phase==5) {
            draw(S3kEndingArt.EGG_ROBO,frame,screenX,screenY,false,-1);
            if(childStage==1 || childStage==2) draw(S3kEndingArt.EGG_ROBO,childFrame,screenX,screenY+0x1C,false,-1);
        } else if(phase==6) {
            draw(S3kEndingArt.S3_GRAPHICS,0,screenX,screenY,false,-1);
            draw(S3kEndingArt.AND_KNUCKLES,1,screenX,screenY+0x48,false,-1);
            boolean tails=character()==PlayerCharacter.TAILS_ALONE,knux=character()==PlayerCharacter.KNUCKLES;
            draw(S3kEndingArt.S3_GRAPHICS,childFrame,screenX+(tails||knux?-0x1C:0),screenY+(tails||knux?0x2C:0x40),false,0);
            draw(S3kEndingArt.S3_GRAPHICS,tailsAnimation.mappingFrame,screenX+(tails?8:0x24),screenY+(tails?0x40:0x2C),false,0);
            draw(S3kEndingArt.EYECATCH_KNUCKLES,knucklesAnimation.mappingFrame,screenX+(knux?0:tails?0x1C:-0x1C),screenY+(knux?0x40:0x2C),false,2);
        } else if(phase==7) draw(Sonic3kObjectArtKeys.MECHA_SONIC,frame,screenX,screenY,false,-1);
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
