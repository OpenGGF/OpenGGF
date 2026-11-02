package com.openggf.tests.trace.kis2;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceReplayTestSession;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Checks native leader-record dispatch independently of comparison snapshots. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2SoloHistoryPrelude {
    @Test
    void soloTitleCardDispatchRecordsLeaderOncePerPassWithoutMovingPlayer() throws Exception {
        Path directory = Path.of("src/test/resources/traces/kis2/ehz1");
        TraceData trace = TraceData.load(directory);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
        SharedLevel shared = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        try {
            TraceReplayTestSession.reopenForRecordedTeam(trace.metadata());
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(shared)
                    .withRecording(directory.resolve("kis2-ehz1.bk2"))
                    .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                    .withFreshLevelStartLifecycle().build();
            try {
                TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
                var player = fixture.sprite();
                assertInstanceOf(Knuckles.class, player);
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                int before = player.historyPos();
                int frameCounter = GameServices.sprites().getFrameCounter();
                short x = player.getCentreX(), y = player.getCentreY();
                short[] oldX = player.copyXHistory(), oldY = player.copyYHistory();
                int passes = TraceReplayBootstrap.s2GenericObjectTitleCardPreludeFramesForTraceReplay(trace);
                assertEquals(1 + 5 + 11 + 9, passes,
                        "Leading RunObjects plus left/bottom/background title-card exits");
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
                assertEquals((before + passes) & 63, player.historyPos());
                assertEquals(frameCounter, GameServices.sprites().getFrameCounter(),
                        "Pre-counted title-card dispatch must not spend gameplay frames");
                assertEquals(x, player.getCentreX());
                assertEquals(y, player.getCentreY());
                short[] actualX = player.copyXHistory(), actualY = player.copyYHistory();
                for (int i = 1; i <= 64; i++) {
                    int slot = (before + i) & 63;
                    assertEquals(i <= passes ? x : oldX[slot], actualX[slot], "X slot " + slot);
                    assertEquals(i <= passes ? y : oldY[slot], actualY[slot], "Y slot " + slot);
                }
            } finally {
                fixture.closeHardwareTimingReplayRun();
            }
        } finally {
            shared.dispose();
        }
    }
}
