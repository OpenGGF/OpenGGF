package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.run.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestCheckpointRespawnPolicy {
    @AfterEach void close() { SessionManager.clear(); }

    @Test void loopAndDirectOwnerRestoreTheSameCheckpointAndPreserveAttemptOrdinal() {
        var direct = run(false);
        SessionManager.clear();
        var throughLoop = run(true);
        assertEquals(direct.core(),throughLoop.core());
        assertEquals(direct.owners().keySet(),throughLoop.owners().keySet());
        direct.owners().forEach((key,value) -> assertEquals(java.util.List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,value,throughLoop.owners().get(key)),key));
    }

    private record Core(int x,int y,long timer,int rings,int lives,int checkpoint) { }
    private record Result(Core core,java.util.Map<String,Object> owners) { }
    private Result run(boolean loopPath) {
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var context = SessionManager.getCurrentGameplayMode();
        var policy = GameplayRunPolicy.competition();
        context.beginGameplayRun(policy,0,0);
        var loop = new GameLoop(new InputHandler()); loop.setGameMode(GameMode.LEVEL);
        var starts = new ArrayList<RunLevelStart>();
        var steps = new ArrayList<RunStep>();
        loop.beginHostedRun(new RunSpec("s2",0,0,"sonic",policy),new RunHost() {
            @Override public void onLevelReady(RunLevelStart start) { starts.add(start); }
            @Override public void afterStep(RunStep step) { steps.add(step); }
        },ignored -> {});
        assertTrue(loop.hostedRuns.ownsStart(GameMode.LEVEL),"Competition host owns pause without a scene overlay");
        for(int i=0;i<4;i++) loop.step();
        var level = GameServices.level();
        var player = (AbstractPlayableSprite)GameServices.camera().getFocusedSprite();
        var checkpoint = assertInstanceOf(CheckpointState.class,level.getCheckpointState());
        var state = GameServices.gameState();
        int initialLives = state.getLives();
        level.getLevelGamestate().setTimerFrames(600);
        checkpoint.saveCheckpoint(1,player.getCentreX()+128,player.getCentreY(),false);
        assertTrue(player.applyCrushDeath());
        if (loopPath) {
            for(int frame=0;frame<600 && starts.isEmpty();frame++) loop.step();
            assertEquals(1,starts.size());
            assertEquals(LevelStartCause.CHECKPOINT_RESPAWN,starts.getFirst().cause());
            assertTrue(steps.getLast().ordinal()>4,"checkpoint respawn must retain the attempt step ordinal");
        } else {
            // Native death decision runs before the reload owner, on both live and replay paths.
            for(int frame=0;frame<600 && state.getLives()==initialLives;frame++) loop.step();
            level.restartCurrentLevelFromCheckpointAfterDeath();
        }
        player = (AbstractPlayableSprite)GameServices.camera().getFocusedSprite();
        assertFalse(player.getDead());
        assertFalse(level.isTitleCardRequested());
        assertEquals(initialLives-1,state.getLives());
        return new Result(new Core(player.getCentreX(),player.getCentreY(),level.getLevelGamestate().getTimerFrames(),
                level.getLevelGamestate().getRings(),state.getLives(),level.getCheckpointState().getLastCheckpointIndex()),
                java.util.Map.of("checkpoint-player",player.captureRewindState(),
                        "checkpoint-camera",GameServices.camera().capture(),
                        "checkpoint-bank",((CheckpointState)level.getCheckpointState()).captureRewindState(),
                        "checkpoint-game-state",state.capture()));
    }
}
