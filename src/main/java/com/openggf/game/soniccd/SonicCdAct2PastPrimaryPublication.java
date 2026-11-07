package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;

import java.util.*;

/**
 * Pure external-value staging for an ORIGINAL completed routine2/4 Frame publication.
 * Caller authenticates producer identity, actual closed Frame/pool pair and one invocation.
 * No value here grants execution, helper, Frame, World, callback or publication authority.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: ObjSonic_Main/ObjSonic_Display
 * (Level/_Objects/Sonic/Main.asm:285-351,373-386); ObjSonic_Hurt (Main.asm:2351-2366); Player_ObjCollide
 * (Level/Palmtree Panic/Player Object Collision.asm:7-98).
 * J07615047f Act2LoadingVisit.preparePrimary:3218-3328.
 */
final class SonicCdAct2PastPrimaryPublication {
    private SonicCdAct2PastPrimaryPublication() { }
    enum Boundary {
        NON_MAIN_RESULT, TIME_WARP_SAVE_PUBLICATION, TIME_WARP_RESERVED_PUBLICATION,
        TIME_WARP_RESTART_PUBLICATION, TIME_WARP_SCALAR_PUBLICATION, PRIMARY_LIFECYCLE_PUBLICATION
    }
    static final class UnsupportedPrimaryPublication extends UnsupportedOperationException {
        private final Boundary boundary;
        private UnsupportedPrimaryPublication(Boundary boundary) { super("R12B primary publication " + boundary); this.boundary=boundary; }
        Boundary boundary() { return boundary; }
    }
    record Plan(CdObjectPool.Snapshot pool, SonicCdAct2PastWorldHost.Globals globals,
                CdLevelGlobals level, CdAssetBytes reserved, CdPlayerBody.DisplayEligibility display,
                List<SonicCdGameModule.PastSound> sounds) {
        Plan {
            Objects.requireNonNull(pool);Objects.requireNonNull(globals);Objects.requireNonNull(level);Objects.requireNonNull(display);
            if(Objects.requireNonNull(reserved).length()!=1984)throw new IllegalArgumentException("Complete reserved1984 required");
            sounds=List.copyOf(sounds);
        }
    }

    static Plan prepare(CdPlayerFrameRuntime.BodyPublication publication, CdObjectPool.Snapshot expectedPool,
                        SonicCdAct2PastWorldHost.Globals globals, CdLevelGlobals level, CdAssetBytes reserved) {
        Objects.requireNonNull(publication);Objects.requireNonNull(expectedPool);Objects.requireNonNull(globals);Objects.requireNonNull(level);
        if(Objects.requireNonNull(reserved).length()!=1984)throw new IllegalArgumentException("Complete reserved1984 required");
        var projected=new Values(globals.ring(),globals.score(),globals.contact());
        var plan=prepareValues(publication,expectedPool,projected,level,reserved);
        var v=plan.values();var next=globals;
        if(v!=projected)next=new SonicCdAct2PastWorldHost.Globals(v.ring(),globals.live(),v.contact(),v.score(),globals.projectorByte(),globals.checkpointSaved(),globals.drownTimerWord(),globals.debugBlockWord(),globals.pauseMusicBit(),globals.bonus(),globals.tail(),globals.animations(),globals.paletteSteps7(),globals.paletteTimers7());
        return new Plan(plan.pool(),next,plan.level(),plan.reserved(),plan.display(),plan.sounds());
    }
    /** Pure external values only; no source or invocation capability. Shared by original Main/Hurt callers. */
    record Values(CdRingGlobals ring,CdOpeningObjectRuntime.ScoreGlobals score,CdPlayerBody.ContactGlobals contact){
        Values{Objects.requireNonNull(ring);Objects.requireNonNull(score);Objects.requireNonNull(contact);}
    }
    record ValuePlan(CdObjectPool.Snapshot pool,Values values,CdLevelGlobals level,CdAssetBytes reserved,
                     CdPlayerBody.DisplayEligibility display,List<SonicCdGameModule.PastSound> sounds){
        ValuePlan{sounds=List.copyOf(sounds);}
    }
    static ValuePlan prepareValues(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot expectedPool,
                                   Values values,CdLevelGlobals level,CdAssetBytes reserved){
        return prepareValues(publication,expectedPool,values,level,reserved,false);
    }
    // BEGIN R13 COMPLETED BOSS PRIMARY VALUES 20261106
    /** Pure final-value projection only. The actual World must authenticate the ORIGINAL Frame
     * publication/private call and full cut. Neither this result nor public effects grant authority. */
    static ValuePlan prepareBossValues(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot expectedPool,
                                       Values values,CdLevelGlobals level,CdAssetBytes reserved){
        return prepareValues(publication,expectedPool,values,level,reserved,true);
    }
    private static ValuePlan prepareValues(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot expectedPool,
                                          Values values,CdLevelGlobals level,CdAssetBytes reserved,boolean bossEnabled){
        Objects.requireNonNull(publication);Objects.requireNonNull(expectedPool);Objects.requireNonNull(values);Objects.requireNonNull(level);
        if(Objects.requireNonNull(reserved).length()!=1984)throw new IllegalArgumentException("Complete reserved1984 required");
        var result=publication.result();
        // The completed Body boundary identifies the incoming dispatch, not the outgoing actor routine.
        if(result.boundary()!=CdPlayerBody.Boundary.MAIN_RETURN && result.boundary()!=CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN
                && result.boundary()!=CdPlayerBody.Boundary.HURT_RETURN)
            throw new UnsupportedPrimaryPublication(Boundary.NON_MAIN_RESULT);
        // Reject the owning TimeWarp component before a preceding restart LevelWrite obscures its boundary.
        CdPlayerTimeWarp.Result warp=null;
        for(var effect:result.effects())if(effect instanceof CdPlayerBody.TimeWarpCall call) {
            if(warp!=null)throw malformed("Multiple TimeWarp calls");warp=call.result();
        }
        if(warp!=null)requireNoWriteWarp(warp);
        var changed=new LinkedHashMap<Integer,CdObjectState>();
        var ring=values.ring();var score=values.score();var contact=values.contact();
        CdPlayerBody.DisplayEligibility display=null;boolean touchSeen=false;
        for(var effect:result.effects()) {
            if(effect instanceof CdPlayerBody.ModeEffect mode && mode.effect() instanceof CdPlayerMovement.ObjectPatch write) {
                if(touchSeen)throw malformed("MODE object patch after touch");
                var patch=write.patch();requireCurrent(expectedPool,changed,patch.before());checkImage(expectedPool,patch.after());
                if(!patch.before().identity().equals(patch.after().identity()))throw malformed("MODE changed physical identity");
                changed.put(patch.after().identity().wholeIndex(),patch.after());
            } else if(effect instanceof CdPlayerBody.TimeWarpCall call) {
                if(touchSeen)throw malformed("TimeWarp call after touch");
                int timeWarp=call.result().state().timeWarp();
                if(timeWarp!=contact.timeWarp())contact=new CdPlayerBody.ContactGlobals(timeWarp,contact.shield(),contact.combineRing());
            } else if(effect instanceof CdPlayerBody.DisplayEligibility selected) {
                if(display!=null)throw malformed("Multiple Display cuts");display=selected; // false is a real source cut too.
            } else if(effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                if(touchSeen)throw malformed("Multiple touch results");touchSeen=true;
                var selected=touch.result();
                if(selected instanceof CdPlayerObjectTouch.HandlerBoundary)throw malformed("Unanswered selected touch handler");
                selected.selectedSlot().ifPresent(image->requireCurrent(expectedPool,changed,image));
                if(selected.changedSlot().isPresent()) {
                    var image=selected.changedSlot().orElseThrow();checkImage(expectedPool,image);
                    if(selected.selectedSlot().isEmpty()||!selected.selectedSlot().orElseThrow().identity().equals(image.identity()))
                        throw malformed("Touch changed another physical identity");
                    changed.put(image.identity().wholeIndex(),image);
                }
            } else if(effect instanceof CdPlayerBody.ObjectTouchResponse touch) {
                if(touchSeen)throw malformed("Multiple touch results");touchSeen=true;
                var response=touch.result();CdObjectPool.overlay(expectedPool,response.changedSlots());
                var world=response.world();
                ring=new CdRingGlobals(world.rings(),world.lives(),ring.livesFlags(),ring.updateHudRings(),world.updateHudLives(),
                        ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame());
                score=new CdOpeningObjectRuntime.ScoreGlobals(world.score(),world.nextLifeScore(),world.updateHudScore());contact=touch.contact();
                // The later touch publishes FINAL slot images, superseding earlier MODE images.
                for(var image:response.changedSlots())changed.put(image.identity().wholeIndex(),image);
            } else if(effect instanceof CdPlayerBody.ObjectBossTouchResponse touch) {
                if(!bossEnabled)throw malformed("Boss response requires explicit R13 final-value projection");
                if(touchSeen)throw malformed("Multiple touch results");touchSeen=true;
                // No Exit/common result is invented; final SPECIAL values include the actual tail.
                var response=touch.result().values();CdObjectPool.overlay(expectedPool,response.changedSlots());var world=response.world();
                if(world.rings()!=ring.rings()||world.lives()!=ring.lives()||world.updateHudLives()!=ring.updateHudLives())
                    ring=new CdRingGlobals(world.rings(),world.lives(),ring.livesFlags(),ring.updateHudRings(),world.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame());
                if(world.score()!=score.score()||world.nextLifeScore()!=score.nextLifeScore()||world.updateHudScore()!=score.updateHudScore())score=new CdOpeningObjectRuntime.ScoreGlobals(world.score(),world.nextLifeScore(),world.updateHudScore());
                var player=response.player();var nextContact=new CdPlayerBody.ContactGlobals(player.timeWarp(),player.shield(),player.combineRing());if(!nextContact.equals(contact))contact=nextContact;
                for(var image:response.changedSlots())changed.put(image.identity().wholeIndex(),image);
            } else if(effect instanceof CdPlayerBody.LevelWrite || effect instanceof CdPlayerBody.DeadCheckGone
                    || effect instanceof CdPlayerBody.RestartCut || effect instanceof CdPlayerBody.ResetSavedObjectFlagsRequest
                    || effect instanceof CdPlayerBody.UncheckedAliasWrite || effect instanceof CdPlayerBody.Z80RamWrite) {
                throw new UnsupportedPrimaryPublication(Boundary.PRIMARY_LIFECYCLE_PUBLICATION);
            }
            // Actor/global/MODE/animation/call effects already belong to the adopted Frame result; never replay them.
        }
        if(display==null)throw malformed("Completed Main omitted Display");
        if(display.eligible()!=result.display().orElseThrow().queued())throw malformed("Display eligibility differs from the returned pose");
        var pool=CdObjectPool.overlay(expectedPool,List.copyOf(changed.values()));
        var next=ring==values.ring()&&score==values.score()&&contact==values.contact()?values:new Values(ring,score,contact);
        var sounds=new ArrayList<SonicCdGameModule.PastSound>(publication.sounds().size());
        for(var sound:publication.sounds())sounds.add(new SonicCdGameModule.PastFrameSound(sound));
        return new ValuePlan(pool,next,level,reserved,display,sounds);
    }
    private static void requireNoWriteWarp(CdPlayerTimeWarp.Result warp) {
        if(warp.saveResult().isPresent())throw new UnsupportedPrimaryPublication(Boundary.TIME_WARP_SAVE_PUBLICATION);
        if(!warp.reservedPatches().isEmpty()||warp.stores().stream().anyMatch(CdPlayerTimeWarp.ReservedWrite.class::isInstance))
            throw new UnsupportedPrimaryPublication(Boundary.TIME_WARP_RESERVED_PUBLICATION);
        if(warp.restartResult().isPresent())throw new UnsupportedPrimaryPublication(Boundary.TIME_WARP_RESTART_PUBLICATION);
        if(!warp.stores().isEmpty())throw new UnsupportedPrimaryPublication(Boundary.TIME_WARP_SCALAR_PUBLICATION);
    }
    private static void requireCurrent(CdObjectPool.Snapshot base,Map<Integer,CdObjectState> changed,CdObjectState image) {
        checkImage(base,image);var current=changed.getOrDefault(image.identity().wholeIndex(),base.slots().get(image.identity().dynamicIndex()));
        if(!current.equals(image))throw malformed("Stale complete primary object preimage");
    }
    private static void checkImage(CdObjectPool.Snapshot base,CdObjectState image) { CdObjectPool.overlay(base,List.of(image)); }
    private static IllegalArgumentException malformed(String detail) { return new IllegalArgumentException(detail); }
}
