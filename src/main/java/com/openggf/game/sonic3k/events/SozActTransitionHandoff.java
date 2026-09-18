package com.openggf.game.sonic3k.events;

import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.sprites.NativePositionOps;

/** Immutable loc_55C84/55EFC tail, applied after the normal target Load_Level owner. */
record SozActTransitionHandoff(int playerDeltaX,int playerDeltaY, Sonic3kZoneEvents eventAccess) implements SeamlessTransitionResourceHandoff {
    @Override public DeferredLevelResourceManifest deferredResources(){return DeferredLevelResourceManifest.EMPTY;}
    @Override public void transferAfterTargetInit(){
        var state=S3kRuntimeStates.currentSoz(eventAccess.zoneRuntimeRegistry()).orElseThrow();
        if(state.actIndex()!=1)throw new IllegalStateException("SOZ transition did not install Act2");
        var events=state.events();events.initialized(true);events.foregroundRoutine(0);events.backgroundRoutine(0);events.seamlessEntry(true);
        state.lighting().initializeSeamlessDarkness();
        var main=eventAccess.spriteManager().getMainPlayable();
        NativePositionOps.writeXPosPreserveSubpixel(main,(main.getCentreX()+playerDeltaX)&65535);
        NativePositionOps.writeYPosPreserveSubpixel(main,(main.getCentreY()+playerDeltaY)&65535);
        for(var follower:eventAccess.spriteManager().getSidekicks()){
            NativePositionOps.writeXPosPreserveSubpixel(follower,(follower.getCentreX()+playerDeltaX)&65535);
            NativePositionOps.writeYPosPreserveSubpixel(follower,(follower.getCentreY()+playerDeltaY)&65535);
        }
        var registry=eventAccess.paletteRegistryOrNull();var level=eventAccess.levelManager().getCurrentLevel();
        String owner="s3k.soz.seamlessPalette";
        try{
            for(int line:new int[]{0,2,3}){
                byte[] target=new byte[32];
                for(int color=0;color<16;color++){
                    int value=PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color));
                    target[color*2]=(byte)(value>>8);target[color*2+1]=(byte)value;
                }
                if(line==2)System.arraycopy(eventAccess.rom().readBytes(0x560EA,22),0,target,2,22);
                if(line==3)System.arraycopy(eventAccess.rom().readBytes(0x56100,30),0,target,2,30);
                registry.applyTargetPatch(owner,line,0,target);
                S3kPaletteWriteSupport.applyLine(registry,level,eventAccess.graphics(),owner,
                        S3kPaletteOwners.PRIORITY_ZONE_EVENT,line,new byte[32],true);
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("SOZ seamless darkness palette",failure);}
        registry.setPaletteRotationDisabled(true);
    }
}
