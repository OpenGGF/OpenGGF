package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.arena.*;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.objects.*;
import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestTeleportSwapApply {
    private record Run(GameplayModeContext mode,AbstractPlayableSprite player,Sonic2RaceSwapApplier swaps,
            GameLoop loop,RunHandle handle,RecordingFrameDriver driver,List<RunStep> steps,AtomicBoolean admitted) { }
    @AfterEach void close() { SessionManager.clear(); GameModuleRegistry.reset(); }
    private Run open(boolean live) {
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var mode=SessionManager.getCurrentGameplayMode();
        var base=GameplayRunPolicy.competition();
        var policy=new GameplayRunPolicy(false,false,base.actCompletion(),false,false,base.deathRestart(),
                false,false,MonitorRule.RANDOMISED,true);
        mode.beginGameplayRun(policy,0,0);
        var player=(AbstractPlayableSprite)mode.getCamera().getFocusedSprite();
        var swaps=(Sonic2RaceSwapApplier)mode.getWorldSession().getGameModule().getGameService(RaceSwapApplier.class);
        swaps.reset();
        assertEquals(mode.getLevelManager().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId()==0x26).count(),swaps.maxSwapSources(mode));
        assertTrue(swaps.maxSwapSources(mode)>0);
        var admitted=new AtomicBoolean(true);
        var steps=new ArrayList<RunStep>();
        GameLoop loop=null; RunHandle handle=null; RecordingFrameDriver driver=null;
        if (live) {
            loop=new GameLoop(new InputHandler()); loop.setGameMode(GameMode.LEVEL);
            handle=loop.beginHostedRun(new RunSpec("s2",0,0,"sonic",policy),new RunHost() {
                @Override public boolean admitStep(RunInput input) { return admitted.get(); }
                @Override public void afterStep(RunStep step) { steps.add(step); }
            },ignored->{});
            loop.announceHostedRunLevelReady(false);
        } else driver=new RecordingFrameDriver(player);
        var result=new Run(mode,player,swaps,loop,handle,driver,steps,admitted);
        for (int frame=0;frame<4;frame++) step(result);
        return result;
    }
    private void step(Run run) {
        if (run.loop()!=null) {
            int before=run.steps().size();
            for (int retry=0;retry<20 && run.steps().size()==before;retry++) run.loop().step();
            assertEquals(before+1,run.steps().size(),"one executed live step must be observed");
        } else {
            for(int retry=0;retry<20;retry++) {
                run.driver().stepFrame(false,false,false,false,false);
                if (run.driver().didLastFrameRunGameplay()) return;
            }
            fail("replay did not execute its next gameplay step");
        }
    }
    private RaceEvent.SwapApply command() {
        return new RaceEvent.SwapApply(17,new SwapTarget(0xA00,0x100,0x180,-0x100,true,true,5,true,2,25,15));
    }
    private void localBank(Run run) {
        run.mode().getLevelManager().getLevelGamestate().setRings(17);
        run.mode().getGameStateManager().addScore(1234);
        ((CheckpointState)run.mode().getLevelManager().getCheckpointState())
                .saveCheckpoint(1,run.player().getCentreX(),run.player().getCentreY(),false);
        run.player().giveShield(); run.player().giveSpeedShoes(500); run.player().giveInvincibility();
        run.mode().getLevelManager().getRingManager().restoreRingStatusTable(new long[]{1});
    }
    @Test void appliesOnceAtTheBoundaryAndFreezesObjectsWhileNativeClocksContinue() {
        var run=open(true); localBank(run);
        var player=run.player(); var level=run.mode().getLevelManager();
        int lives=run.mode().getGameStateManager().getLives();
        int score=run.mode().getGameStateManager().getScore();
        int invincibility=player.getInvincibleFrames();
        int shoes=player.captureRewindState().playerExtra().speedShoesRemainingTicks();
        int vint=level.getObjectManager().getVblaCounter();
        int frame=level.getFrameCounter();
        long timer=level.getLevelGamestate().getTimerFrames();
        var rings=level.getRingManager().captureRingStatusTable();
        var event=command(); run.handle().queueRaceEvent(event);
        assertThrows(IllegalStateException.class,()->run.handle().queueRaceEvent(event));
        step(run);
        assertEquals(new RaceEventResult(event,RaceEventResult.Outcome.APPLIED),run.steps().getLast().applied());
        assertEquals(0xA00,player.getCentreX()); assertEquals(0x100,player.getCentreY());
        assertEquals(0x180,player.getXSpeed()); assertEquals(-0x100,player.getYSpeed()); assertEquals(0x180,player.getGSpeed());
        assertTrue(player.getAir()); assertFalse(player.getRolling()); assertFalse(player.isOnObject());
        assertEquals(player.getStandXRadius(),player.getXRadius()); assertEquals(player.getStandYRadius(),player.getYRadius());
        assertEquals(0,player.getAngle()); assertEquals(0,player.getXSubpixelRaw()); assertEquals(0,player.getYSubpixelRaw());
        assertEquals(0xE,player.getTopSolidBit()); assertEquals(0xF,player.getLrbSolidBit());
        assertTrue(run.steps().getLast().player().secondaryPath());
        assertEquals(0,run.steps().getLast().player().shoeKind(),"act shoes are not arena shoe metadata");
        assertEquals(5,player.getPriorityBucket()); assertTrue(player.isHighPriority()); assertTrue(player.getRenderHFlip());
        assertTrue(run.mode().getCamera().getX()>0x500,"placement owner must snap to the target window");
        assertEquals(0x40,run.swaps().teleportTimer()); assertFalse(run.swaps().white());
        for(int elapsed=1;elapsed<64;elapsed++) {
            step(run);
            assertEquals(0xA00,player.getCentreX()); assertEquals(0x100,player.getCentreY());
            assertEquals(64-elapsed,run.swaps().teleportTimer());
            assertEquals(elapsed<=48,run.swaps().white(),"native white upload is timer >= $10 after VBlank");
            assertEquals(RaceEventResult.NONE,run.steps().getLast().applied());
        }
        assertEquals(vint+64,level.getObjectManager().getVblaCounter());
        assertEquals((frame+64)&0xFFFF,level.getFrameCounter()); assertEquals(timer+64,level.getLevelGamestate().getTimerFrames());
        assertEquals(lives,run.mode().getGameStateManager().getLives()); assertEquals(score,run.mode().getGameStateManager().getScore());
        assertEquals(17,level.getLevelGamestate().getRings()); assertEquals(1,level.getCheckpointState().getLastCheckpointIndex());
        assertArrayEquals(rings,level.getRingManager().captureRingStatusTable());
        assertTrue(player.hasShield()); assertTrue(player.hasSpeedShoes());
        assertEquals(shoes,player.captureRewindState().playerExtra().speedShoesRemainingTicks());
        assertEquals(invincibility,player.getInvincibleFrames());
        var frozen=run.swaps().capture(); run.swaps().reset(); run.swaps().restore(frozen);
        assertEquals(frozen,run.swaps().capture(),"teleport timer/display phase must restore");
        step(run); assertEquals(0,run.swaps().teleportTimer());
        assertNotEquals(0xA00,player.getCentreX(),"ordinary physics resumes after 64 inhibited passes");
    }
    @Test void unsafeCommandFallsBackButDeadAndFinishedRecipientsStayUnchanged() {
        var run=open(true); var player=run.player();
        int x=player.getCentreX(),y=player.getCentreY();
        var invalid=new RaceEvent.SwapApply(18,new SwapTarget(0xFFFF,0xFFFF,0,0,false,false,2,false,0,0,0));
        var result=run.swaps().apply(invalid,run.mode());
        assertEquals(new RaceEvent.SwapFallback(18),result.event()); assertEquals(RaceEventResult.Outcome.FALLBACK,result.outcome());
        assertEquals(x,player.getCentreX()); assertEquals(y,player.getCentreY()); assertTrue(player.getInvincibleFrames()>0);
        player.setInvincibleFrames(0); player.setObjectControlled(true);
        assertEquals(RaceEventResult.Outcome.FALLBACK,run.swaps().apply(command(),run.mode()).outcome());
        assertEquals(x,player.getCentreX()); assertEquals(y,player.getCentreY());
        player.setObjectControlled(false);
        SwapTarget buried=null;
        var planes=run.mode().getBackgroundPlaneCollisionProvider();
        for(int probeY=0x200;probeY<0x700 && buried==null;probeY+=8)
            for(int probeX=0x100;probeX<0x1000;probeX+=8) {
                var left=com.openggf.physics.ObjectTerrainUtils.checkLeftWallDist(run.mode().getLevelManager(),planes,false,
                        probeX-player.getStandXRadius(),probeY);
                var right=com.openggf.physics.ObjectTerrainUtils.checkRightWallDist(run.mode().getLevelManager(),planes,false,
                        probeX+player.getStandXRadius(),probeY);
                if(left.foundSurface() && left.distance()<-player.getStandXRadius()
                        || right.foundSurface() && right.distance()<-player.getStandXRadius()) {
                    buried=new SwapTarget(probeX,probeY,0,0,false,false,2,false,0,0,0); break;
                }
            }
        assertNotNull(buried,"native EHZ height maps must supply a deep wall-penetration control");
        assertEquals(RaceEventResult.Outcome.FALLBACK,run.swaps().apply(new RaceEvent.SwapApply(21,buried),run.mode()).outcome());
        var beforeDeath=player.captureRewindState(); player.applyCrushDeath();
        var dead=player.captureRewindState(); run.swaps().apply(new RaceEvent.SwapFallback(19),run.mode());
        assertEquals(List.of(),RewindSnapshotDiff.diffKey("dead-player",dead,player.captureRewindState()));
        player.restoreRewindState(beforeDeath);
        run.mode().getLevelManager().getLevelGamestate().pauseTimer();
        var finished=player.captureRewindState();
        run.swaps().apply(new RaceEvent.SwapFallback(22),run.mode());
        assertEquals(List.of(),RewindSnapshotDiff.diffKey("finished-player",finished,player.captureRewindState()));
    }
    @Test void eventRemainsQueuedUntilTheHostAdmitsAnExecutedStep() {
        var run=open(true); var before=run.player().captureRewindState(); int steps=run.steps().size();
        run.admitted().set(false);run.handle().queueRaceEvent(command());
        for(int frame=0;frame<5;frame++) run.loop().step();
        assertEquals(steps,run.steps().size());
        assertEquals(List.of(),RewindSnapshotDiff.diffKey("held-player",before,run.player().captureRewindState()));
        run.admitted().set(true);step(run);
        assertEquals(RaceEventResult.Outcome.APPLIED,run.steps().getLast().applied().outcome());
    }
    @Test void risingTeleportRequestsAndDelayedNoPartnerUsesInvincibility() {
        var run=open(true); var level=run.mode().getLevelManager();
        while ((level.getFrameCounter()&7)!=6) step(run); // Obj2E_Init samples the next frame: (7&7)+1 = teleport.
        var icon=new MonitorContentsObjectInstance(run.player().getCentreX(),run.player().getCentreY(),4,run.player());
        level.getObjectManager().addDynamicObject(icon);
        for(int frame=0;frame<33;frame++) {
            step(run);
            assertEquals(frame==32 ? RaceSignal.SWAP_REQUEST : RaceSignal.NONE,run.steps().getLast().signal());
        }
        assertEquals(0,run.swaps().teleportTimer()); assertEquals(0,run.player().getInvincibleFrames());
        run.handle().queueRaceEvent(new RaceEvent.SwapFallback(20)); step(run);
        assertEquals(RaceEventResult.Outcome.FALLBACK,run.steps().getLast().applied().outcome());
        assertTrue(run.player().getInvincibleFrames()>0);
    }
    @Test void liveQueueAndRecordedEventReplayHaveEqualSimulationOwnersEveryStep() {
        var expected=new ArrayList<Map<String,Object>>();
        var signals=new ArrayList<RaceSignal>();
        var invalid=new RaceEvent.SwapApply(38,new SwapTarget(0xFFFF,0xFFFF,0,0,false,false,2,false,0,0,0));
        var live=open(true); nativeRequestPrelude(live); localBank(live); live.handle().queueRaceEvent(command());
        for(int frame=0;frame<80;frame++) {
            if(frame==70) live.handle().queueRaceEvent(invalid);
            step(live); expected.add(capture(live)); signals.add(live.steps().getLast().signal());
        }
        SessionManager.clear(); GameModuleRegistry.reset();
        var replay=open(false); nativeRequestPrelude(replay); localBank(replay); replay.swaps().queueForStep(command());
        for(int frame=0;frame<80;frame++) {
            // A rejected recorded fallback is rechecked against the logged original target.
            if(frame==70) replay.swaps().queueForStep(invalid);
            step(replay);
            assertEquals(signals.get(frame),replay.swaps().consumeSignal());
            var actual=capture(replay);
            assertEquals(expected.get(frame).keySet(),actual.keySet());
            final int at=frame;
            expected.get(frame).forEach((key,value)->assertEquals(List.of(),RewindSnapshotDiff.diffKey(key,value,actual.get(key)),key+" at "+at));
            assertEquals(frame==0 ? RaceEventResult.Outcome.APPLIED : frame==70 ? RaceEventResult.Outcome.FALLBACK : RaceEventResult.Outcome.NONE,
                    replay.swaps().consumeApplied().outcome());
        }
    }
    @Test void comparisonRetainsResourceContentsAndEveryCompactBlobByte() {
        var a=new com.openggf.sprites.animation.SpriteAnimationSet();
        var b=new com.openggf.sprites.animation.SpriteAnimationSet();
        var script=new com.openggf.sprites.animation.SpriteAnimationScript(5,List.of(2,3),
                com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,0,List.of(255,4));
        a.addScript(1,script); b.addScript(1,script);
        var left=new com.openggf.game.rewind.schema.RewindObjectStateBlob(3,getClass(),new byte[]{1,2},new Object[]{a});
        var right=new com.openggf.game.rewind.schema.RewindObjectStateBlob(3,getClass(),new byte[]{1,2},new Object[]{b});
        assertEquals(canonical(left),canonical(right),"equal resources have different Java identities across boots");
        b.addScript(1,new com.openggf.sprites.animation.SpriteAnimationScript(5,List.of(2,4),
                com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,0,List.of(255,4)));
        assertNotEquals(canonical(left),canonical(right),"changing a resource frame must fail equality");
        b.addScript(1,script);
        var scalarChanged=new com.openggf.game.rewind.schema.RewindObjectStateBlob(3,getClass(),new byte[]{1,3},new Object[]{b});
        assertNotEquals(canonical(left),canonical(scalarChanged),"compact gameplay bytes remain authoritative");
        var blockA=new com.openggf.level.Block(); var blockB=new com.openggf.level.Block();
        assertEquals(canonical(blockA),canonical(blockB));
        blockB.setChunkDesc(0,0,new com.openggf.level.ChunkDesc(0x2001));
        assertNotEquals(canonical(blockA),canonical(blockB),"a changed terrain descriptor must fail equality");
        var chunkA=new com.openggf.level.Chunk(); var chunkB=new com.openggf.level.Chunk();
        assertEquals(canonical(chunkA),canonical(chunkB));
        chunkB.setSolidTileAltIndex(2);
        assertNotEquals(canonical(chunkA),canonical(chunkB),"the alternate collision index must remain authoritative");
    }
    private void nativeRequestPrelude(Run run) {
        var level=run.mode().getLevelManager();
        while ((level.getFrameCounter()&7)!=6) step(run); // Obj2E_Init samples the next frame: (7&7)+1 = teleport.
        level.getObjectManager().addDynamicObject(new MonitorContentsObjectInstance(
                run.player().getCentreX(),run.player().getCentreY(),4,run.player()));
        for(int frame=0;frame<33;frame++) {
            step(run);
            var signal=run.loop()==null ? run.swaps().consumeSignal() : run.steps().getLast().signal();
            assertEquals(frame==32 ? RaceSignal.SWAP_REQUEST : RaceSignal.NONE,signal,
                    "live and input-only replay must independently reproduce the native request");
        }
    }
    private Map<String,Object> capture(Run run) {
        var level=run.mode().getLevelManager();
        var values=new TreeMap<String,Object>();
        values.put("player",run.player().captureRewindState()); values.put("camera",run.mode().getCamera().capture());
        values.put("game-state",run.mode().getGameStateManager().capture());
        values.put("checkpoint",((CheckpointState)level.getCheckpointState()).captureRewindState());
        values.put("object-manager",level.getObjectManager().rewindSnapshottable().capture()); values.put("rings",level.getRingManager().captureRingStatusTable());
        values.put("swaps",run.swaps().capture()); values.put("act-timer",level.getLevelGamestate().getTimerFrames());
        values.put("level-clock",level.getFrameCounter());
        var canonical=new TreeMap<String,Object>(); values.forEach((key,value)->canonical.put(key,canonical(value)));
        return canonical;
    }
    private Object canonical(Object value) { return RaceStateSnapshots.canonical(value); }

}
