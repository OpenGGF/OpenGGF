package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.RecentFrameLog;
import com.openggf.tests.route.SidekickAudit;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HCZ1 pilot of the AIZ recipe, using a fresh production start and recorded pad inputs only.
 * Trace rows provide input-window boundaries, never engine gameplay state or timing admission.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.hcz1.pilot", matches = "true")
class TestS3kHcz1RoutePilot {
    @Test
    void recordedProgramReachesTheAct2Reload() throws Exception {
        var saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            Path directory = Path.of("src/test/resources/traces/s3k/hcz_completerun");
            TraceData trace = TraceData.load(directory);
            assertNotNull(trace.metadata().sourceBk2(), "HCZ fixture must name its shared movie");
            var movie = new Bk2MovieLoader().load(directory.getParent().resolve("_movies")
                    .resolve(trace.metadata().sourceBk2()));
            int prefix = TraceReplayBootstrap.preLevelFrameCountForTraceReplay(trace);
            int end = -1;
            for (int row = prefix; row < trace.frameCount() && end < 0; row++) {
                for (TraceEvent event : trace.getEventsForFrame(row)) {
                    if (event instanceof TraceEvent.ZoneActState state
                            && Integer.valueOf(1).equals(state.actualAct())
                            && Integer.valueOf(12).equals(state.gameMode())) {
                        end = row;
                        break;
                    }
                }
            }
            assertTrue(end > prefix, "HCZ fixture must record the act-2 reload");
            int offset = trace.metadata().bk2FrameOffset();
            var runs = InputProgram.fromRecording(movie, offset + prefix, offset + end);
            var input = new InputProgram.Cursor(runs, prefix);
            TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).withFreshLevelStartLifecycle().build();
            assertEquals(320, fixture.camera().getWidth());
            var audit = new SidekickAudit(0x100);
            var recent = new RecentFrameLog(12);
            int frames = 0;
            boolean waterSeen = false;
            int limit = InputProgram.frames(runs) + 300;
            while (frames < limit && GameServices.level().getCurrentAct() == 0) {
                int mask = input.next();
                InputProgram.step(fixture, mask);
                frames++;
                var player = fixture.sprite();
                recent.record(frames, mask, player);
                waterSeen |= player.isInWater();
                audit.observe(player, false, false, fixture.camera().getX() & 0xFFFF);
                assertFalse(player.getDead(), "HCZ1 P1 died at frame=" + frames + " row=" + input.row()
                        + " waterSeen=" + waterSeen + " recent=" + recent);
            }
            System.out.printf("HCZEVIDENCE frames=%d row=%d act=%d water=%s p=(%04X,%04X)%n",
                    frames, input.row(), GameServices.level().getCurrentAct(), waterSeen,
                    fixture.sprite().getCentreX() & 0xFFFF, fixture.sprite().getCentreY() & 0xFFFF);
            assertTrue(waterSeen, "HCZ1 pilot must exercise water gameplay");
            assertEquals(1, GameServices.level().getCurrentAct(),
                    "HCZ1 reload absent at frame=" + frames + " row=" + input.row() + " recent=" + recent);
            assertTrue(audit.identityOrderPreserved());
            assertTrue(audit.controllerEveryFrame());
            assertTrue(audit.leaderChainEveryFrame());
            assertTrue(audit.respawnedAfterEveryDeath(), audit.deathEvidence());
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }
}
