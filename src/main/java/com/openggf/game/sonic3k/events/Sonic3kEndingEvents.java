package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.ending.EndingSceneResourceHandoff;
import com.openggf.game.sonic3k.ending.S3kEndingCoordinator;
import com.openggf.game.sonic3k.ending.S3kEndingPalette;
import com.openggf.game.sonic3k.ending.S3kEndingResources;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

/** Ending_ScreenInit / Ending_ScreenEvent, distinct from the SSZ campaign route. */
public final class Sonic3kEndingEvents extends Sonic3kZoneEvents {
    private EndingZoneRuntimeState state(){return (EndingZoneRuntimeState)zoneRuntimeRegistry().current();}
    public void initializeScreen(){
        var ram=state().screen();if(ram.initialized)return;ram.initialized=true;
        var services=objectServices();services.objectManager().createDynamicObject(EndingRescueDirectorObjectInstance::new);
        camera().setScrollLocked(true);camera().setX((short)0x200);camera().setY((short)0x200);
        camera().setXCopy((short)0x200);camera().setYCopy((short)0x200);
        levelManager().setForceHudSuppressed(true);services.levelGamestate().pauseTimer();
        camera().setLevelStarted(false); // loc_6460: D01 keeps the ordinary level timer inactive
        if(services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite player)
            ObjectControlState.nativeBit7FullControl().applyTo(player);
        // sub_5B11C replaces only the Sky and Pal_SSZ2+$20 target lines.
        S3kEndingPalette.targetLine(services,1,0x5B16E);
        S3kEndingPalette.targetLine(services,2,0xA97BC);
    }
    @Override public void update(int act,int frameCounter){
        var services=objectServices();var state=state();var ram=state.screen();
        state.ending().art().service(services);
        switch(ram.foregroundRoutine){
            case 0 -> {if(ram.foregroundSignal){ram.foregroundSignal=false;ram.backgroundAnchorX=ram.scrollXFixed>>16;
                ram.foregroundRoutine=4;foregroundPosition();}}
            case 4 -> {if(ram.foregroundSignal){ram.foregroundSignal=false;ram.foregroundRoutine=8;foregroundDeparture();}
                else foregroundPosition();}
            case 8 -> foregroundDeparture();
            case 12 -> {foregroundPosition();fadeRescueToBlack();}
            // Resource publication and delayed Plane A/B rows retain $D01 runtime RAM.
            case 16 -> {if(EndingSceneResourceHandoff.ready(services)){
                EndingSceneResourceHandoff.installAiz(services);ram.foregroundRoutine=20;
                camera().setX((short)0x1EB8);camera().setY((short)0x1E0);
                camera().setXCopy((short)0x1EB8);camera().setYCopy((short)0x1E0);drawForeground(true);}}
            case 20 -> drawForeground(true);
            case 24 -> {if(ram.backgroundRoutine>=16){ram.foregroundRoutine=28;ram.fadeTimer=0x15;}}
            case 28 -> {fadeIn();if((ram.fadeTimer=(short)(ram.fadeTimer-1))<0)ram.foregroundRoutine=32;}
            case 32 -> {ram.aizBackgroundScrollFixed+=0x1000;if(ram.foregroundSignal){ram.foregroundSignal=false;
                ram.fadeTimer=0x15;ram.foregroundRoutine=36;}}
            case 36 -> {S3kEndingPalette.toBlackAll(services);if((ram.fadeTimer=(short)(ram.fadeTimer-1))<0){clearSprites();beginReunion();}}
            case 40 -> {if(EndingSceneResourceHandoff.ready(services)){
                EndingSceneResourceHandoff.installSsz(services);ram.foregroundRoutine=44;
                camera().setX((short)0x200);camera().setY((short)0x200);
                camera().setXCopy((short)0x200);camera().setYCopy((short)0x200);drawForeground(false);}}
            case 44 -> drawForeground(false);
            case 48 -> {if(ram.backgroundRoutine<=4){ram.foregroundRoutine=52;ram.fadeTimer=0x15;}}
            case 52 -> {fadeIn();if((ram.fadeTimer=(short)(ram.fadeTimer-1))<0){ram.foregroundRoutine=56;
                camera().setY((short)0);camera().setYCopy((short)0);}}
            case 56 -> S3kEndingCoordinator.updateCredits(services);
            default -> throw new IllegalStateException("Unknown Ending_ScreenEvent routine "+ram.foregroundRoutine);
        }
        updateBackground();
        eventRoutine=ram.foregroundRoutine;
    }
    private void foregroundPosition(){var ram=state().screen();
        ram.foregroundX=(ram.scrollXFixed>>16)-ram.backgroundAnchorX+0x140;
        ram.foregroundY=(ram.scrollYFixed>>16)-(ram.backgroundYFixed>>16);
        camera().setXCopy((short)ram.foregroundX);camera().setYCopy((short)ram.foregroundY);
    }
    private void foregroundDeparture(){var ram=state().screen();foregroundPosition();
        if(objectServices().gameState().getEmeraldCount()>=7){
            ram.backgroundAcceleration=Math.min(0x8000,ram.backgroundAcceleration+0x10);
            ram.backgroundYFixed-=ram.backgroundAcceleration;}
        if(ram.foregroundSignal){ram.foregroundSignal=false;ram.foregroundRoutine=12;ram.fadeTimer=0x15;
            fadeRescueToBlack();} // loc_5AB96 falls through first Pal_ToBlack dispatch
    }
    private void fadeRescueToBlack(){var ram=state().screen();var services=objectServices();
        S3kEndingPalette.toBlackAll(services);
        if((ram.fadeTimer=(short)(ram.fadeTimer-1))>=0)return;clearSprites();
        if(services.gameState().hasAllSuperEmeralds()){
            services.objectManager().createDynamicObject(()->new EndingRescueDirectorObjectInstance(EndingRescueDirectorObjectInstance.AIZ_RETURN));
            EndingSceneResourceHandoff.queueAiz(services);ram.foregroundRoutine=16;
        }else beginReunion();
    }
    private void clearSprites(){
        var services=objectServices();
        var manager=services.objectManager();
        // loc_5ABC0 / loc_5AD8E erase the SST immediately after Process_Sprites.
        // Do not leave destroyed dynamic entries alive until next frame's cleanup:
        // P1's persistent Shield owner is also erased before this boundary can rewind.
        for(var object:java.util.List.copyOf(manager.getActiveObjects())) {
            if(object instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
                ObjectLifetimeOps.deleteNoRespawn(aoi);
                manager.removeDynamicObject(object);
            }
        }
        // loc_5ABC0 / loc_5AD8E clear Player_2 through Kos_decomp_buffer,
        // including Shield's fixed SST slot. P1 survives, but must not retain
        // registration intent for the auxiliary object that native RAM erased.
        if(services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite player)
            com.openggf.sprites.playable.PlayableSpriteInternalAccess.retirePersistentInstaShield(player);
        state().screen().catcherReady=false;
    }
    private void beginReunion(){objectServices().objectManager().createDynamicObject(
            ()->new EndingRescueDirectorObjectInstance(EndingRescueDirectorObjectInstance.REUNION));
        EndingSceneResourceHandoff.queueSsz(objectServices());
        state().screen().foregroundRoutine=40;}
    private void drawForeground(boolean aiz){var ram=state().screen();
        boolean finished=EndingSceneResourceHandoff.drawDelayedRow(objectServices(),false);
        ram.redrawRows=state().ending().scene().foreground().remaining();
        if(finished){ram.backgroundRoutine=aiz?8:20;ram.foregroundRoutine=aiz?24:48;}}
    private void fadeIn(){S3kEndingPalette.fromBlackAll(objectServices());}
    private void updateBackground(){var ram=state().screen();
        switch(ram.backgroundRoutine){
            case 0 -> {if(ram.backgroundSignal){ram.backgroundSignal=false;ram.backgroundRoutine=4;
                ram.scrollYFixed=(ram.scrollYFixed&0xFFFF)|(0x220<<16);
                for(int line=1;line<4;line++)S3kEndingPalette.loadLine(objectServices(),line,
                        S3kEndingResources.PAL_ENDING_ISLAND+(line-1)*32);
                // sub_5B116 writes both normal and target palette through a6/a5.
                S3kEndingPalette.loadLine(objectServices(),1,0x5B16E);
                S3kEndingPalette.loadLine(objectServices(),2,0xA97BC);
                S3kEndingPalette.targetLine(objectServices(),1,0x5B16E);
                S3kEndingPalette.targetLine(objectServices(),2,0xA97BC);}}
            case 4,16 -> { }
            case 8 -> {com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareBackgroundForRedraw(objectServices());
                ram.backgroundRoutine=12;ram.backgroundRedrawRows=15;ram.cloudDriftFixed=0;
                // loc_5AFC4 clears HScroll_table after the first AIZ deformation.
                state().ending().scene().background().begin(0,ram.backgroundCameraY);
                drawBackground(16);
            }
            case 12 -> drawBackground(16);
            case 20 -> {com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareBackgroundForRedraw(objectServices());
                ram.backgroundRoutine=24;ram.backgroundRedrawRows=15;
                state().ending().scene().background().begin(0x200,ram.backgroundCameraY);drawBackground(4);}
            case 24 -> drawBackground(4);
            default -> throw new IllegalStateException("Unknown Ending_BackgroundEvent routine "+ram.backgroundRoutine);
        }
    }
    private void drawBackground(int next){var ram=state().screen();
        boolean finished=EndingSceneResourceHandoff.drawDelayedRow(objectServices(),true);
        ram.backgroundRedrawRows=state().ending().scene().background().remaining();
        if(finished)ram.backgroundRoutine=next;}
    @Override public int getEventRoutine(){return state().screen().foregroundRoutine;}
}
