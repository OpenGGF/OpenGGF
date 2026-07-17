package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;

/** Robotnik/EggRobo ChildObjDat_703D0 member, loc_7002A. */
final class Fbz2SubbossCharacterChild extends AbstractFbz2SubbossChild implements RewindRecreatable, RomWorldPositionedObject {
    private int characterOrdinal;
    private int frame;
    private boolean running;
    private int animTimer;
    private int animCursor;
    private int rawPlcAttempted;
    private int rawPlcApplied;
    private String rawPlcFailure;
    Fbz2SubbossCharacterChild(Fbz2SubbossInstance r,PlayerCharacter c){this(new ObjectSpawn(r.getX()+0xD8,r.getY()+0x74,0xAB,2,0,false,0));root=r;familySlot=r.getSlotIndex();characterOrdinal=c.ordinal();}
    private Fbz2SubbossCharacterChild(ObjectSpawn s){super(s,"FBZ2SubbossCharacter");}
    private PlayerCharacter character(){return PlayerCharacter.values()[Math.max(0,Math.min(characterOrdinal,PlayerCharacter.values().length-1))];}
    static String standArtKey(PlayerCharacter c){return c==PlayerCharacter.KNUCKLES?Sonic3kObjectArtKeys.FBZ_EGGROBO_STAND:Sonic3kObjectArtKeys.FBZ_ROBOTNIK_STAND;}
    static String runArtKey(PlayerCharacter c){return c==PlayerCharacter.KNUCKLES?Sonic3kObjectArtKeys.FBZ_EGGROBO_RUN:Sonic3kObjectArtKeys.FBZ_ROBOTNIK_RUN;}
    int frameForTest(){return frame;}
    boolean runningForTest(){return running;}
    Sonic3kPlcLoader.RawPlcApplyResult rawPlcResultForTest(){return new Sonic3kPlcLoader.RawPlcApplyResult(rawPlcAttempted,rawPlcApplied,rawPlcFailure);}
    @Override public void update(int f,PlayableEntity p){
        if(!running&&root!=null&&root.controlBit(Fbz2SubbossInstance.CONTROL_CHARACTER_ESCAPE)){
            running=true;y-=4;frame=0;animCursor=0;animTimer=0;return;
        }
        if(running){
            x+=2;
            if(--animTimer<0){
                int[] frames={0,1,2,1};
                animCursor++;
                if(animCursor>=frames.length)animCursor=0;
                frame=frames[animCursor];animTimer=5;
            }
            if(tryServices()!=null&&!isWithinSolidContactBounds()){
                Sonic3kPlcLoader.RawPlcApplyResult result=Sonic3kPlcLoader.applyRawQuietly(Sonic3kPlcLoader.monitorSpikesSpringsPlcEntries(),services());
                rawPlcAttempted=result.attemptedEntries();rawPlcApplied=result.appliedEntries();rawPlcFailure=result.failure();
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
            return;
        }
        // Animate_RawMultiDelay byte_703F4 executes before the parent status overrides.
        if(--animTimer<0){
            int[] frames={0,1,0,1,0,1};
            int[] delays={7,0x17,7,0x0F,0x3F,7};
            animCursor++;
            if(animCursor>=frames.length)animCursor=0;
            frame=frames[animCursor];animTimer=delays[animCursor];
        }
        if(root!=null){
            if(root.statusBit(Fbz2SubbossInstance.STATUS_CHARACTER_DEFEAT))frame=3;
            else if(root.statusBit(Fbz2SubbossInstance.STATUS_CHARACTER_FACE))frame=2;
        }
    }
    @Override public Fbz2SubbossCharacterChild recreateForRewind(RewindRecreateContext c){return new Fbz2SubbossCharacterChild(c.spawn());}
    @Override public int getPriorityBucket(){return 5;}
    @Override public int getOnScreenHalfWidth(){return 0x20;}
    @Override public int getOnScreenHalfHeight(){return 0x20;}
    @Override public void appendRenderCommands(List<GLCommand> c){String key=running?runArtKey(character()):standArtKey(character());PatternSpriteRenderer r=getRenderer(key);if(r!=null&&r.isReady())r.drawFrameIndex(frame,x,y,running,false);}
}
