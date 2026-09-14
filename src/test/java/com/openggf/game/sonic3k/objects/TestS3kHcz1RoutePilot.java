package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HCZ1 production-entry pilot: recorded opening followed by an authored, live-state pad route.
 * Trace rows provide input-window boundaries, never engine gameplay state or timing admission.
 * The recorded opening first differs at its lag row 1163 (the ROM clock remains 1163 while
 * the ordinary engine advances). The continuation below therefore reacts to production
 * state before the first fan rather than retiming inputs from comparison rows.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.hcz1.pilot", matches = "true")
class TestS3kHcz1RoutePilot {
    @Test
    void recordedOpeningAndLiveRouteReachTheAct2Reload() throws Exception {
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
            // Route addresses select ROM-loaded placements, never trace physics rows. Offsets below
            // are authored approach/clearance distances; they steer pads and do not alter gameplay.
            var spawns = GameServices.level().getObjectManager().getAllSpawns();
            var entryFan = landmark(spawns, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x44, 0);
            var firstShield = landmark(spawns, Sonic3kObjectIds.MONITOR, 7, 1);
            var firstBridge = landmark(spawns, Sonic3kObjectIds.COLLAPSING_BRIDGE, 0x82, 0);
            var secondBridge = landmark(spawns, Sonic3kObjectIds.COLLAPSING_BRIDGE, 0x81, 0);
            var lowerLoop = landmark(spawns, Sonic3kObjectIds.PATH_SWAP, 1, 2);
            var upperRings = landmark(spawns, Sonic3kObjectIds.MONITOR, 3, 10);
            var upperBridge = landmark(spawns, Sonic3kObjectIds.COLLAPSING_BRIDGE, 0x83, 0);
            var lowerBridge = landmark(spawns, Sonic3kObjectIds.COLLAPSING_BRIDGE, 0x84, 0);
            var lowerShield = landmark(spawns, Sonic3kObjectIds.MONITOR, 7, 2);
            var exitSpring = landmark(spawns, Sonic3kObjectIds.SPRING, 2, 3);
            var liftFan = landmark(spawns, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x69, 0);
            var liftButton = landmark(spawns, Sonic3kObjectIds.BUTTON, 0x20, 1);
            var finalCurve = landmark(spawns, Sonic3kObjectIds.PATH_SWAP, 5, 2);
            var runupStart = landmark(spawns, Sonic3kObjectIds.PATH_SWAP, 0x11, 8);
            long padHash = 1;
            int frames = 0;
            boolean recovery = false;
            boolean jumpHeld = false;
            Stage stage = Stage.FIRST_FAN;
            int charge = 0;
            int dashPhase = 0;
            boolean lowerTrack = false;
            boolean bossJumpHeld = false;
            boolean bossDefeated = false;
            boolean waterSeen = false;
            // Bound a failed controller without tying successful play to the recording duration.
            int limit = 20000;
            while (frames < limit && GameServices.level().getCurrentAct() == 0) {
                int mask = recovery ? 0 : input.next();
                var live = fixture.sprite();
                recovery |= live.getCentreX() >= (entryFan.x() - 160);
                if (recovery) {
                    mask = 8;
                    if ((!live.getAir() && !jumpHeld) || (jumpHeld && live.getAir() && live.getYSpeed() < 0)) {
                        mask |= 16;
                    }
                    if (stage == Stage.FIRST_FAN && live.getCentreX() >= firstShield.x() && live.hasShield()) {
                        stage = Stage.FIRST_BRIDGE;
                    }
                    if (stage == Stage.FIRST_BRIDGE && live.getCentreY() >= (firstBridge.y() + 16)) {
                        stage = Stage.SECOND_BRIDGE;
                    }
                    if (stage == Stage.SECOND_BRIDGE && live.getCentreY() >= (secondBridge.y() + 240)) {
                        stage = Stage.LOWER_TUNNEL;
                    }
                    // Descend through the two Blastoid-triggered bridges after collecting air protection.
                    if (stage == Stage.FIRST_BRIDGE) {
                        int jump = live.getCentreX() > (firstBridge.x() + 40) ? (mask & 16) : 0;
                        mask = steer(live.getCentreX(), firstBridge.x() - 28, 4) | jump;
                    }
                    if (stage == Stage.SECOND_BRIDGE) {
                        mask = steer(live.getCentreX(), secondBridge.x(), 8);
                    }
                    if (stage == Stage.LOWER_TUNNEL && live.getCentreX() >= (upperRings.x() - 16) && live.getRingCount() >= 10) {
                        stage = Stage.UPPER_BRIDGE;
                    }
                    if (stage == Stage.UPPER_BRIDGE && live.getCentreY() >= (upperBridge.y() + 64)) {
                        stage = Stage.LOWER_BRIDGE;
                    }
                    if (stage == Stage.LOWER_BRIDGE && live.getCentreY() >= (lowerBridge.y() + 80)) {
                        stage = Stage.BUBBLE_MONITOR;
                    }
                    if (stage == Stage.BUBBLE_MONITOR && live.hasShield()) {
                        stage = Stage.LOWER_SPIKES;
                    }
                    if (stage == Stage.BUBBLE_MONITOR) {
                        mask = steer(live.getCentreX(), lowerShield.x(), 8)
                                | (live.getCentreX() < lowerShield.x() + 33 ? (mask & 16) : 0);
                    }
                    if (stage == Stage.UPPER_BRIDGE) {
                        mask = steer(live.getCentreX(), upperBridge.x(), 8)
                                | (live.getCentreX() > upperBridge.x() + 64 ? (mask & 16) : 0);
                    }
                    if (stage == Stage.LOWER_BRIDGE) {
                        mask = steer(live.getCentreX(), lowerBridge.x(), 8);
                    }
                    if (stage == Stage.LOWER_SPIKES && live.getCentreX() >= (exitSpring.x() - 65)) {
                        stage = Stage.FAN_BUTTON;
                    }
                    if (stage == Stage.FAN_BUTTON && live.getCentreX() >= (liftButton.x() - 8)) {
                        stage = Stage.FAN_LIFT;
                    }
                    if (stage == Stage.FAN_LIFT && live.getCentreY() < (lowerBridge.y() - 16)) {
                        stage = Stage.CONVEYOR_EXIT;
                    }
                    // Obj_HCZCGZFan lifts into the conveyor span. Release the belt with a fresh
                    // jump before drowning starts; the spring must be approached from above.
                    if (stage == Stage.FAN_LIFT) {
                        mask = steer(live.getCentreX() + live.getXSpeed() / 16, liftFan.x(), 8);
                        if (live.isObjectControlled() && !jumpHeld || jumpHeld && live.getYSpeed() < 0) {
                            mask |= 16;
                        }
                    }
                    if (stage == Stage.CONVEYOR_EXIT && !live.isJumping() && live.getYSpeed() < -1024) {
                        stage = Stage.SPRING_ASCENT;
                    }
                    if (stage == Stage.CONVEYOR_EXIT) {
                        mask = steer(live.getCentreX() + live.getXSpeed() / 16, exitSpring.x(), 8);
                        if (live.isObjectControlled() && live.getCentreX() < (exitSpring.x() + 47)
                                && !jumpHeld || jumpHeld && live.getYSpeed() < 0) {
                            mask |= 16;
                        }
                    }
                    if (stage == Stage.SPRING_ASCENT && live.getCentreX() >= (finalCurve.x() + 32)
                            && live.getCentreY() < (finalCurve.y() - 16)) {
                        stage = Stage.CURVE_RUNUP;
                    }
                    if (stage == Stage.CURVE_RUNUP && live.getCentreX() < runupStart.x()) {
                        stage = Stage.CURVE_BRAKE;
                    }
                    // The final curve needs a flat-ground, capability-backed spindash run-up.
                    if (stage == Stage.CURVE_RUNUP) {
                        mask = 4;
                    }
                    if (stage == Stage.CURVE_BRAKE) {
                        mask = 8;
                        if (!live.getAir() && live.getGSpeed() >= 0 && live.getGSpeed() < 256
                                && ((live.getAngle() & 255) < 16 || (live.getAngle() & 255) >= 240)) {
                            stage = Stage.FINAL_CURVE;
                            dashPhase = 2;
                        }
                    }
                    if (stage == Stage.LOWER_TUNNEL || stage == Stage.LOWER_SPIKES || stage == Stage.FAN_BUTTON || stage == Stage.SPRING_ASCENT || stage == Stage.FINAL_CURVE) {
                        mask = 8 | (live.isInWater() || lowerTrack && dashPhase == 0 && (jumpHeld || Math.abs(live.getGSpeed()) < 128) ? (mask & 16) : 0);
                        var capability = live.getGameRules().playerCapability();
                        int angle = live.getAngle() & 255;
                        if (dashPhase == 0 && !live.isInWater() && !live.getAir() && angle >= 192 && angle <= 224
                                && live.getGSpeed() < 0) {
                            dashPhase = 1;
                        }
                        if (dashPhase == 1 && !lowerTrack) {
                            mask = 2;
                            if (live.getCentreY() >= lowerLoop.y()) lowerTrack = true;
                        }
                        if (dashPhase == 1 && lowerTrack && !live.getAir() && (angle == 0 || angle >= 240)
                                && live.getGSpeed() >= 0 && live.getGSpeed() < 256 && live.getDirection().name().equals("RIGHT")) {
                            dashPhase = 2;
                        }
                        if (dashPhase == 2) {
                            if (live.getSpindash() && (live.getSpindashCounter() & 65535)
                                    >= (capability.spindashSpeedTable().length - 1) * 256) {
                                dashPhase = 0;
                                mask = 8;
                            } else if (!live.getCrouching() && !live.getSpindash()) {
                                mask = 2;
                                charge = 0;
                            } else {
                                charge++;
                                mask = 2 | (charge % 2 == 0 ? 16 : 0);
                            }
                        }
                        if (live.isObjectControlled() && live.getXSpeed() == 0 && live.getYSpeed() == 0 && !jumpHeld) {
                            mask |= 16;
                        }
                    }
                    if (stage == Stage.UPPER_BRIDGE || stage == Stage.LOWER_BRIDGE) {
                        int enemyX = stage == Stage.UPPER_BRIDGE ? upperBridge.x() : lowerBridge.x();
                        boolean enemyPresent = GameServices.level().getObjectManager().getActiveObjects().stream()
                                .anyMatch(o -> o instanceof BlastoidBadnikInstance && Math.abs(o.getX() - enemyX) < 8);
                        if (enemyPresent && (stage != Stage.LOWER_BRIDGE || live.isInWater()
                                && Math.abs(live.getCentreX() - enemyX) < 96) && ((!live.getAir()
                                        && !jumpHeld) || stage != Stage.LOWER_BRIDGE && jumpHeld && live.getYSpeed() < 0)) {
                            mask |= 16;
                        }
                    }
                    if (stage == Stage.FAN_BUTTON) {
                        mask = 8;
                    }
                    // Keep the route latch separate from the boss override below.
                    jumpHeld = (mask & 16) != 0;
                }
                var boss = GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(o -> o instanceof HczMinibossInstance)
                        .map(o -> (HczMinibossInstance) o).findFirst().orElse(null);
                if (boss != null) {
                    bossDefeated |= boss.getState().defeated && boss.getState().hitCount == 0;
                }
                if (stage == Stage.FINAL_CURVE && boss != null) {
                    // Wait for production arena ownership, then jump beside the descending core.
                    // HczMinibossInstance exposes an engine hazard 36px below an open core:
                    // stay 48px sideways until the player is above it, then steer inward.
                    mask = 2;
                    if (boss.getState().routine >= 4) {
                        int target = live.getCentreY() < boss.getY() ? boss.getX() : boss.getX() + (live.getCentreX() < boss.getX() ? -48 : 48);
                        int projected = live.getCentreX() + live.getXSpeed() / 16;
                        mask = steer(projected, target, 6);
                        if ((!live.getAir() && !bossJumpHeld && boss.getY() > live.getCentreY() - 144) || bossJumpHeld
                                && live.getYSpeed() < 0) {
                            mask |= 16;
                        }
                        bossJumpHeld = (mask & 16) != 0;
                    }
                }
                padHash = 31 * padHash + mask;
                InputProgram.step(fixture, mask);
                frames++;
                var player = fixture.sprite();
                recent.record(frames, mask, player);
                waterSeen |= player.isInWater();
                audit.observe(player, false, false, fixture.camera().getX() & 0xFFFF);
                assertFalse(player.getDead() || player.isDrowningDeath(), "HCZ1 P1 died at frame=" + frames + " openingRow=" + input.row()
                        + " waterSeen=" + waterSeen + " recent=" + recent);
            }
            System.out.printf("HCZEVIDENCE frames=%d openingRow=%d act=%d water=%s p=(%04X,%04X)%n",
                    frames, input.row(), GameServices.level().getCurrentAct(), waterSeen,
                    fixture.sprite().getCentreX() & 0xFFFF, fixture.sprite().getCentreY() & 0xFFFF);
            System.out.println("HCZPADHASH " + padHash);
            assertTrue(bossDefeated, "HCZ1 must defeat the six-hit miniboss through gameplay");
            assertTrue(waterSeen, "HCZ1 pilot must exercise water gameplay");
            assertEquals(1, GameServices.level().getCurrentAct(),
                    "HCZ1 reload absent at frame=" + frames + " openingRow=" + input.row() + " recent=" + recent);
            assertTrue(audit.identityOrderPreserved());
            assertTrue(audit.controllerEveryFrame());
            assertTrue(audit.leaderChainEveryFrame());
            assertTrue(audit.respawnedAfterEveryDeath(), audit.deathEvidence());
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }
    private static int steer(int projectedX, int targetX, int tolerance) {
        return projectedX < targetX - tolerance ? 8 : projectedX > targetX + tolerance ? 4 : 0;
    }

    private enum Stage {
        FIRST_FAN, FIRST_BRIDGE, SECOND_BRIDGE, LOWER_TUNNEL, UPPER_BRIDGE, LOWER_BRIDGE,
        BUBBLE_MONITOR, LOWER_SPIKES, FAN_BUTTON, FAN_LIFT, CONVEYOR_EXIT, SPRING_ASCENT,
        CURVE_RUNUP, CURVE_BRAKE, FINAL_CURVE
    }

    private static ObjectSpawn landmark(List<ObjectSpawn> spawns, int objectId, int subtype, int ordinal) {
        return spawns.stream().filter(spawn -> spawn.objectId() == objectId && spawn.subtype() == subtype)
                .skip(ordinal).findFirst().orElseThrow(() -> new AssertionError(
                        "HCZ1 route landmark absent: object=" + objectId + " subtype=" + subtype + " ordinal=" + ordinal));
    }
}
