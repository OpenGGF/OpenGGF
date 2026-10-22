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
        ram.foregroundX=0x200;ram.foregroundY=0x200;
        // Ending_ScreenInit and Ending_BackgroundInit refresh the resident ROM planes.
        state().ending().scene().foreground().fill(0x200,0x200,levelManager()::getForegroundTileDescriptorAtWorld);
        com.openggf.game.sonic3k.scroll.SwScrlEnding.initializeBackground(services);
        state().ending().scene().background().fill(0x200,ram.backgroundCameraY,levelManager()::getBackgroundTileDescriptorAtWorld);
        levelManager().setForceHudSuppressed(true);services.levelGamestate().pauseTimer();
        camera().setLevelStarted(false); // loc_6460: D01 keeps the ordinary level timer inactive
        if(services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite player)
            ObjectControlState.nativeBit7FullControl().applyTo(player);
        // sub_5B11C replaces only the Sky and Pal_SSZ2+$20 target lines.
        S3kEndingPalette.targetLine(services,1,0x5B16E);
        S3kEndingPalette.targetLine(services,2,0xA97BC);
        // LoadLevelLoadBlock2 entry27 palette37 is Pal_Ending2; sub_5B11C
        // replaces its first two lines, retaining this third line in Target_palette.
        S3kEndingPalette.targetLine(services,3,S3kEndingResources.PAL_ENDING_GOOD_ISLAND+0x40);
        // Level inherited white from Pal_FadeToWhite; only character line0 was replaced.
        // Its following loc_64DC Pal_FillWhite has the same lines1..3 result.
        S3kEndingPalette.fillInitialSceneWhite(services);
    }
    public void animateInitialPaletteBeforeObjects(){
        if(levelManager().getAnimatedPatternManager() instanceof
                com.openggf.game.sonic3k.Sonic3kLevelAnimationManager animation)
            animation.advanceEndingInitialPalette(objectServices());
    }
    @Override public void update(int act,int frameCounter){
        var services=objectServices();var state=state();var ram=state.screen();
        state.ending().art().service(services);
        switch(ram.foregroundRoutine){
            case 0 -> {if(ram.foregroundSignal){ram.foregroundSignal=false;ram.backgroundAnchorX=ram.scrollXFixed>>16;
                ram.foregroundRoutine=4;foregroundPosition();
                // loc_5AB36 resets rounded position before its DrawTilesAsYouMove tail.
                state.ending().scene().foreground().resetScrollPosition(camera().getXCopy(),camera().getYCopy());}}
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
            case 24 -> {if(ram.backgroundRoutine>=16){ram.foregroundRoutine=28;ram.fadeTimer=0x15;fadeAizIn();}}
            case 28 -> fadeAizIn();
            case 32 -> holdAiz();
            case 36 -> fadeAizOut();
            case 40 -> {if(EndingSceneResourceHandoff.ready(services)){
                EndingSceneResourceHandoff.installSsz(services);ram.foregroundRoutine=44;
                camera().setX((short)0x200);camera().setY((short)0x200);
                camera().setXCopy((short)0x200);camera().setYCopy((short)0x200);drawForeground(false);}}
            case 44 -> drawForeground(false);
            // loc_5AE90 has no rts after addq Events_routine_fg: it falls into loc_5AEA8,
            // so the first Pal_FromBlack/subq runs on the 48->52 frame (sonic3k.asm:121202-121221).
            case 48 -> {if(ram.backgroundRoutine<=4){ram.foregroundRoutine=52;ram.fadeTimer=0x15;fadeReunionIn();}}
            case 52 -> fadeReunionIn();
            case 56 -> S3kEndingCoordinator.updateCredits(services);
            default -> throw new IllegalStateException("Unknown Ending_ScreenEvent routine "+ram.foregroundRoutine);
        }
        if(ram.foregroundRoutine<56)state.ending().scene().foreground().drawAsYouMove(
                camera().getXCopy(),camera().getYCopy(),levelManager()::getForegroundTileDescriptorAtWorld);
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
            setPaletteAnimationBlocked(true);
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
    private void fadeAizIn(){var ram=state().screen();fadeIn();
        if((ram.fadeTimer=(short)(ram.fadeTimer-1))>=0)return;
        setPaletteAnimationBlocked(false);ram.aizPalette.beginHold();ram.foregroundRoutine=32;holdAiz(); // loc_5AD30 -> 5AD56
    }
    private void holdAiz(){var ram=state().screen();
        if(!ram.foregroundSignal){ram.aizBackgroundScrollFixed+=0x1000;ram.aizPalette.tick(objectServices());return;}
        ram.foregroundSignal=false;ram.fadeTimer=0x15;setPaletteAnimationBlocked(true);ram.foregroundRoutine=36;
        fadeAizOut(); // loc_5AD72 falls through Pal_ToBlack in this same ScreenEvents dispatch
    }
    private void fadeAizOut(){var ram=state().screen();S3kEndingPalette.toBlackAll(objectServices());
        if((ram.fadeTimer=(short)(ram.fadeTimer-1))<0){clearSprites();beginReunion();}
    }
    private void setPaletteAnimationBlocked(boolean blocked){
        if(blocked)state().screen().aizPalette.beginFade();
        if(levelManager().getAnimatedPatternManager() instanceof
                com.openggf.game.sonic3k.Sonic3kLevelAnimationManager animation)
            animation.setEndingScreenPaletteFadeBlocked(blocked);
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
        // _unkFAA4 is outside the cleared Player_2..Kos_decomp_buffer range.
        // Its physical parent address survives both native dynamic-sprite clears.
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
    /** loc_5AEA8: its expiry falls into loc_5AED4, so sub_5B18E's first dispatch (loc_5B1A2
     * credits init) runs on the same 52->56 frame and skips loc_5AEE4 (sonic3k.asm:121209-121228, loc_5B1A2 121487). */
    private void fadeReunionIn(){var ram=state().screen();fadeIn();
        if((ram.fadeTimer=(short)(ram.fadeTimer-1))>=0)return;
        ram.foregroundRoutine=56;setPaletteAnimationBlocked(false);ram.aizPalette.beginHold();
        camera().setY((short)0);camera().setYCopy((short)0);
        S3kEndingCoordinator.updateCredits(objectServices());
    }
    private void updateBackground(){var ram=state().screen();int backgroundRoutineAtEntry=ram.backgroundRoutine;
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
            case 4 -> { }
            case 16 -> com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareAizBackgroundEvent(objectServices());
            case 8 -> {com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareBackgroundForRedraw(objectServices());
                ram.backgroundRoutine=12;ram.backgroundRedrawRows=15;ram.cloudDriftFixed=0;
                // loc_5AFC4 clears HScroll_table after the first AIZ deformation.
                state().ending().scene().background().begin(0,ram.backgroundCameraY);
                drawBackground(16);
            }
            case 12 -> {com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareAizBackgroundEvent(objectServices());drawBackground(16);}
            case 20 -> {
                // loc_5B03A: a3=Level_layout_main+2, row7 pointer+$1C,
                // then four bytes starting at column4. These low-priority sea
                // chunks allow the existing dolphin sprites to emerge in credits.
                var context=new com.openggf.game.mutation.LayoutMutationContext(
                        com.openggf.game.mutation.LevelMutationSurface.forLevel(levelManager().getCurrentLevel()),
                        levelManager()::applyMutationEffects);
                objectServices().zoneLayoutMutationPipeline().applyImmediatelyWithoutRedraw(c->{
                    for(int x=4;x<8;x++)c.surface().setBlockInMapWithoutRedraw(1,x,7,(x&1)==0?0x1A:0x1B);
                    return com.openggf.game.mutation.MutationEffects.NONE;
                },context);
                com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareBackgroundForRedraw(objectServices());
                ram.backgroundRoutine=24;ram.backgroundRedrawRows=15;
                state().ending().scene().background().begin(0x200,ram.backgroundCameraY);drawBackground(4);}
            case 24 -> drawBackground(4);
            default -> throw new IllegalStateException("Unknown Ending_BackgroundEvent routine "+ram.backgroundRoutine);
        }
        if(ram.backgroundRoutine>=8&&ram.backgroundRoutine<=16){
            // loc_5B020 Draw_BG publishes incoming rows even after the delayed redraw.
            // Its two source bands select X per row; no layout refill or healing.
            state().ending().scene().background().streamRowsComplex(
                    y->com.openggf.game.sonic3k.scroll.SwScrlEnding.backgroundSourceX(objectServices(),y),
                    ram.backgroundCameraY,32,levelManager()::getBackgroundTileDescriptorAtWorld);
            // Draw_BG keeps the two native horizontal rounded words in this same bank.
            ram.hScrollWords[1]=(short)(ram.hScrollWords[0]&0xFFF0);
            ram.hScrollWords[3]=(short)(ram.hScrollWords[2]&0xFFF0);
        }
        if(ram.backgroundRoutine==0||ram.backgroundRoutine==4){
            var services=objectServices();
            com.openggf.game.sonic3k.scroll.SwScrlEnding.prepareBackgroundEvent(services);
            state().ending().scene().background().streamRows(0x200,ram.backgroundCameraY,32,
                    levelManager()::getBackgroundTileDescriptorAtWorld);
            prepareApplyDeformation(objectServices(),backgroundRoutineAtEntry);
            if(ram.backgroundRoutine==0)ram.aizPalette.tickEndingSky(services);
            else ram.aizPalette.tickEndingWater(services);
        }
    }
    /** loc_5AFAA: distinct from HScroll parameter preparation and before palette cycling. */
    public static void prepareApplyDeformation(com.openggf.level.objects.ObjectServices services,int routineAtEntry){
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        // A delayed-redraw entry may publish state4 on this call; it is still outside this bounded producer.
        if((routineAtEntry!=0&&routineAtEntry!=4)||(ram.backgroundRoutine!=0&&ram.backgroundRoutine!=4))return;
        int table=ram.backgroundRoutine==0?
                com.openggf.game.sonic3k.timing.S3kEndingApplyDeformationWork.ISLAND_BANDS:
                com.openggf.game.sonic3k.timing.S3kEndingApplyDeformationWork.CLOUD_BANDS;
        try {ram.applyDeformationWork.recordExecution(services.romReader(),services.levelManager().getFrameCounter(),
                table,ram.backgroundCameraY);}
        catch(java.io.IOException unavailable){ram.applyDeformationWork.recordExecution(null,
                services.levelManager().getFrameCounter(),table,ram.backgroundCameraY);}
    }
    private void drawBackground(int next){var ram=state().screen();
        boolean finished=EndingSceneResourceHandoff.drawDelayedRow(objectServices(),true);
        ram.backgroundRedrawRows=state().ending().scene().background().remaining();
        if(finished)ram.backgroundRoutine=next;}
    @Override public int getEventRoutine(){return state().screen().foregroundRoutine;}
}
