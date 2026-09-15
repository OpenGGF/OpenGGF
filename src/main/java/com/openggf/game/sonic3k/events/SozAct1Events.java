package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayableEntity;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.SozAct1ArenaController;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Native SOZ1 arena and the SOZ2 seamless-entry stages (sonic3k.asm $55A9A–$563A6). */
final class SozAct1Events extends Sonic3kZoneEvents {
    private static final String PALETTE_OWNER="s3k.soz.arenaFade";
    private final Sonic3kSOZEvents parent;
    SozAct1Events(Sonic3kSOZEvents parent){this.parent=parent;}
    @Override public void update(int act,int levelFrameCounter){
        var state=S3kRuntimeStates.currentSoz(zoneRuntimeRegistry()).orElseThrow();
        var events=state.events();
        if(act==1){updateSeamlessEntry(state,levelFrameCounter);return;}
        var player=spriteManager().getMainPlayable();
        switch(events.backgroundRoutine()){
            case 0 -> {
                if(state.consumeSandCorkBackgroundFlag()==0){
                    int bottom=(player.getCentreX()&65535)<0x4000?0xB20:0x960;
                    camera().setMaxY((short)bottom);
                    if(bottom==0x960 && (camera().getY()&65535)>=bottom){
                        camera().setMinY((short)bottom);
                        if((camera().getX()&65535)>=0x4310){camera().setMinX((short)0x4180);state.requestSandCorkRelease(false);}
                    }
                    return;
                }
                events.sandPosition(-8<<16);events.screenShakeFlag(-1);events.backgroundRoutine(4);events.redrawRemaining(15);
                redrawArena(events);
            }
            case 4 -> redrawArena(events);
            case 8 -> {
                if(readyToEnterDoor(state)){
                    events.fadePasses(0x15);events.fadeDelay(0x10);events.backgroundRoutine(0xC);
                    if(paletteRegistryOrNull()!=null)paletteRegistryOrNull().setPaletteRotationDisabled(true);
                    fadeExitFirstLine(events,levelFrameCounter);
                }else raiseBackground(events,levelFrameCounter);
            }
            case 0xC -> fadeExitFirstLine(events,levelFrameCounter);
            case 0x10 -> fadeExitBackground(events,levelFrameCounter);
            case 0x14 -> {
                if(events.artJobOrdinal()<0 && events.blockJobOrdinal()<0)requestAct2Reload();
            }
            default -> { }
        }
        rumble(events,levelFrameCounter);
        replaceDoorRow(events);
        if(events.backgroundRoutine()<=8)parent.updateShake(events,levelFrameCounter);
    }
    private void redrawArena(SozEventState events){
        // Draw_PlaneVertBottomUp dispatches two rows, including on the initializer's entry.
        events.redrawRemaining(events.redrawRemaining()-2);
        if(events.redrawRemaining()>=0)return;
        parent.queueCustomResources(events,0x1AD05C,0x1AD24C);
        var controller=spawnObject(()->new SozAct1ArenaController(new ObjectSpawn(0,0,0,0,0,false,0)));
        if(controller!=null && !controller.isDestroyed())controller.allocateDoorSiblings();
        events.backgroundRoutine(8);
    }
    private void raiseBackground(SozEventState events,int levelFrameCounter){
        if(events.screenShakeFlag()<0 && (levelFrameCounter&3)!=0){
            events.sandPosition(events.sandPosition()+0x10000);
            if(events.sandHeight()>=0x280)events.screenShakeFlag(8);
        }
    }
    private void rumble(SozEventState events,int levelFrameCounter){
        if(events.screenShakeFlag()<0 && ((levelFrameCounter-1)&15)==0)audio().playSfx(Sonic3kSfx.RUMBLE_2.id);
    }
    private boolean readyToEnterDoor(SozZoneRuntimeState state){
        if(state.sandCorkBackgroundFlag()!=0x55)return false;
        var player=spriteManager().getMainPlayable();
        if((player.getCentreX()&65535)<0x4378 || (player.getCentreY()&65535)<0x9A8)return false;
        PlayableEntity second=objectServices().playerQuery().nativeP2OrNull();
        // Player_mode=0 is the native Sonic-and-Tails route. Other modes gate only P1.
        if(state.playerCharacter()!=com.openggf.game.PlayerCharacter.SONIC_AND_TAILS || second==null)return true;
        return (second.getCentreX()&65535)>0x4378 || (second.getCentreY()&65535)>=0x9A8;
    }
    private void fadeExitFirstLine(SozEventState events,int levelFrameCounter){
        if(events.fadeDelay()!=0){events.fadeDelay(events.fadeDelay()-1);if(events.fadeDelay()==0)events.doorSignal(-1);}
        else if(events.doorSignal()==0 && (levelFrameCounter&1)!=0){
            fadePalette(0,1,false);
            events.fadePasses(events.fadePasses()-1);
            if(events.fadePasses()<0){events.fadePasses(0x15);events.fadeDelay(8);events.backgroundRoutine(0x10);fadeExitBackground(events,levelFrameCounter);return;}
        }
        raiseBackground(events,levelFrameCounter);
    }
    private void fadeExitBackground(SozEventState events,int levelFrameCounter){
        if(events.fadeDelay()!=0){events.fadeDelay(events.fadeDelay()-1);return;}
        if((levelFrameCounter&1)==0)return;
        fadePalette(2,2,false);events.fadePasses(events.fadePasses()-1);
        if(events.fadePasses()<0){parent.queueCustomResources(events,0x1AD68E,0x1AD81E);applyPlc(0x2C);events.backgroundRoutine(0x14);}
    }
    private void replaceDoorRow(SozEventState events){
        int block=events.backgroundRowReplacement();if(block==0)return;events.backgroundRowReplacement(0);
        zoneLayoutMutationPipeline().queue(context->{context.surface().setBlockInMap(1,13,6,block);return MutationEffects.redrawAllTilemaps();});
    }
    private void requestAct2Reload(){
        var player=spriteManager().getMainPlayable();
        var handoff=seamlessTransitionResourceHandoffs().register(new SozActTransitionHandoff(
                0x140-(player.getCentreX()&65535),0x3AC-(player.getCentreY()&65535)));
        levelManager().requestSeamlessTransition(SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(8,1).deactivateLevelNow(false).preserveMusic(true).preserveLevelGamestate(true)
                .showInLevelTitleCard(false).runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .objectSurvivalPolicy(SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .preserveOffsetCameraPosition(true).cameraOffset(0xA0-(camera().getX()&65535),0x34C-(camera().getY()&65535))
                .postTransitionMinX(0xA0).postTransitionMaxX(0xA0).postTransitionMinXTarget(0xA0).postTransitionMaxXTarget(0xA0)
                .postTransitionMinY(0x34C).postTransitionMaxY(0x34C).postTransitionMaxYTarget(0x34C).postTransitionMinYTarget(0x34C)
                .resourceHandoff(handoff).build());
    }
    private void updateSeamlessEntry(SozZoneRuntimeState state,int levelFrameCounter){
        var events=state.events();
        if(events.foregroundRoutine()<8){
            if(events.foregroundRoutine()==0){camera().setVerticalWrapEnabled(true,0x800);events.foregroundRoutine(4);events.redrawRemaining(15);}
            events.redrawRemaining(events.redrawRemaining()-2);
            if(events.redrawRemaining()<0)events.foregroundRoutine(8);
        }
        switch(events.backgroundRoutine()){
            case 0 -> {
                if(events.foregroundRoutine()<8)return;
                parent.clearAct2BackgroundColumns();events.redrawRemaining(15);events.backgroundRoutine(4);
                redrawEntry(events,levelFrameCounter);
            }
            case 4 -> redrawEntry(events,levelFrameCounter);
            case 8 -> fadeEntryFirstLine(events,levelFrameCounter);
            case 0xC -> fadeEntryBackground(events,levelFrameCounter);
            default -> { }
        }
    }
    private void redrawEntry(SozEventState events,int levelFrameCounter){
        events.redrawRemaining(events.redrawRemaining()-2);
        if(events.redrawRemaining()<0){events.fadePasses(0x15);events.backgroundRoutine(8);fadeEntryFirstLine(events,levelFrameCounter);}
    }
    private void fadeEntryFirstLine(SozEventState events,int levelFrameCounter){
        if((levelFrameCounter&1)==0)return;
        if(events.fadePasses()==5)levelManager().requestInLevelTitleCard(8,1,true);
        fadePalette(0,1,true);events.fadePasses(events.fadePasses()-1);
        if(events.fadePasses()<0){events.fadePasses(0x15);events.backgroundRoutine(0xC);fadeEntryBackground(events,levelFrameCounter);}
    }
    private void fadeEntryBackground(SozEventState events,int levelFrameCounter){
        if((levelFrameCounter&1)==0)return;
        fadePalette(2,2,true);events.fadePasses(events.fadePasses()-1);
        if(events.fadePasses()<0){
            camera().setMinX((short)0);camera().setMaxX((short)0x6000);camera().setMinXTarget((short)0);camera().setMaxXTarget((short)0x6000);
            camera().setMinY((short)-0x100);camera().setMaxY((short)0x800);
            spriteManager().getMainPlayable().setControlLocked(false);
            if(paletteRegistryOrNull()!=null)paletteRegistryOrNull().setPaletteRotationDisabled(false);
            events.backgroundRoutine(0x10);events.seamlessEntry(false);
        }
    }
    /** Pal_DecColor removes R,G,B; Pal_AddColor restores B,G,R, one component per pass. */
    private void fadePalette(int firstLine,int count,boolean fromBlack){
        var level=levelManager().getCurrentLevel();var registry=paletteRegistryOrNull();
        for(int line=firstLine;line<firstLine+count;line++){
            byte[] target=fromBlack?registry.targetSegaData(line,0,16):null;byte[] bytes=new byte[32];
            for(int color=0;color<16;color++){
                int value=PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color));
                if(fromBlack){int end=((target[color*2]&255)<<8)|(target[color*2+1]&255);if(value!=end){if(value+0x200<=end)value+=0x200;else if(value+0x20<=end)value+=0x20;else value+=2;}}
                else if((value&14)!=0)value-=2;else if((value&0xE0)!=0)value-=0x20;else if((value&0xE00)!=0)value-=0x200;
                bytes[color*2]=(byte)(value>>8);bytes[color*2+1]=(byte)value;
            }
            S3kPaletteWriteSupport.applyLine(registry,level,graphics(),PALETTE_OWNER,S3kPaletteOwners.PRIORITY_ZONE_EVENT,line,bytes,true);
        }
    }
}
