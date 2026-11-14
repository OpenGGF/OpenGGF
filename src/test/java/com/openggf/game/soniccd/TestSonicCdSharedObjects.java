package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.shared.ObjBlock;
import com.openggf.game.soniccd.objects.shared.ObjCollapseFloor;
import com.openggf.game.soniccd.objects.shared.ObjCrusher;
import com.openggf.game.soniccd.objects.shared.ObjFallingBlock;
import com.openggf.game.soniccd.objects.shared.ObjQqzSpikes;
import com.openggf.game.soniccd.objects.shared.ObjSwitch;
import com.openggf.game.soniccd.objects.shared.ObjWwzDoor;
import com.openggf.game.soniccd.objects.shared.ObjWwzPlatform;
import com.openggf.game.soniccd.objects.shared.ObjRollTunnel;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the shared objects (owner {@code shared}) on stages loaded from the disc.
 * Each expectation cites the routine it checks; registry IDs are each stage's ObjectIndex
 * (Palmtree Panic/Object Index (Act 1 Present).asm:41, r3/object_index_1b_1c_1d.asm:56,
 * r4/object_index_2c_2d.asm, r5/object_index_1_2.asm, r7/object_index_1_2.asm). Native
 * comparisons: {@link TestSonicCdFixtureFrontier} over the stages each object reaches.
 */
class TestSonicCdSharedObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private void boot(CdStagePayload payload) throws Exception {
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        sonic = CdFixtureReplay.boot(tempDir, disc, payload);
        runner = new HeadlessTestRunner(sonic);
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private ObjectSpawn at(int dx, int dy, int id, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, id, subtype, 0, false, 0);
    }

    private ObjectInstance create(int id, int subtype) {
        return SessionManager.requireCurrentGameModule().createObjectRegistry().create(at(0x100, 0, id, subtype));
    }

    @Test
    void registryResolvesSharedRoutinesAtEachStagesIndexId() throws Exception {
        boot(CdStagePayload.R31B);
        // RollTunnelObject is $35 in Collision Chaos (r3/object_index_1b_1c_1d.asm:56).
        assertInstanceOf(ObjRollTunnel.class, create(0x35, 0));
        cleanup();
        reset();
        boot(CdStagePayload.R11A);
        // ObjSpinTunnel, $34 - Spin tunnel tag (Palmtree Panic/Object Index (Act 1 Present).asm:59).
        assertInstanceOf(ObjRollTunnel.class, create(0x34, 0));
        cleanup();
        reset();
        boot(CdStagePayload.R42C);
        // BlockObject is $34 in Tidal Tempest (r4/object_index_2c_2d.asm); $35 there is not the roll tunnel.
        assertInstanceOf(ObjBlock.class, create(0x34, 0));
        assertFalse(create(0x35, 0) instanceof ObjRollTunnel);
        cleanup();
        reset();
        boot(CdStagePayload.R51C);
        // Quartz Quadrant's BlockObject is $24 (r5/object_index_1_2.asm).
        assertInstanceOf(ObjFallingBlock.class, create(0x24, 0));
    }

    @Test
    void rollTunnelForcesARollAtTheZoneMinimumSpeed() throws Exception {
        boot(CdStagePayload.R31B);
        add(new ObjRollTunnel(at(0x10, 0, 0x35, 0)));
        sonic.setXSpeed((short) 0x600);
        sonic.setGSpeed((short) 0x600);
        int y = sonic.getCentreY();
        runner.stepIdleFrames(1);
        // .GetMaxSpeed: move.w #$A00,d1 outside zone 5 (Roll Tunnel/Main.asm:52-61).
        assertTrue(sonic.getRolling(), ".Activate: bset #2,oFlags(a1) (Roll Tunnel/Main.asm:104)");
        assertEquals(0xA00, sonic.getXSpeed());
        assertEquals(0xA00, sonic.getGSpeed());
        assertEquals(0xE, sonic.getYRadius(), "move.b #$E,oYRadius(a1) (Roll Tunnel/Main.asm:106)");
        assertEquals(7, sonic.getXRadius(), "move.b #7,oXRadius(a1), USA (Roll Tunnel/Main.asm:118-119)");
        assertTrue(sonic.getCentreY() >= y + 5 - 1, "addq.w #5,oY(a1) (Roll Tunnel/Main.asm:124)");
    }

    @Test
    void rollTunnelUsesTheStardustSpeedwayMinimum() throws Exception {
        boot(CdStagePayload.R72A);
        add(new ObjRollTunnel(at(0x10, 0, 0x2B, 0)));
        sonic.setXSpeed((short) 0x600);
        sonic.setGSpeed((short) 0x600);
        runner.stepIdleFrames(1);
        // cmpi.b #5,zone; bne.s; move.w #$D00,d1 (objects/roll_tunnel.asm:48-51).
        assertTrue(sonic.getRolling());
        assertEquals(0xD00, sonic.getXSpeed());
    }

    @Test
    void rollTunnelLeavesAPlayerOutsideItsRange() throws Exception {
        boot(CdStagePayload.R31B);
        // ObjRollTunnel_CheckPlayer: dx + $28 must be below $50 (Roll Tunnel/Main.asm:193-198).
        add(new ObjRollTunnel(at(0x30, 0, 0x35, 0)));
        sonic.setXSpeed((short) 0);
        sonic.setGSpeed((short) 0);
        runner.stepIdleFrames(1);
        assertFalse(sonic.getRolling());
    }

    @Test
    void blockTakesItsSizeFromTheStageTable() throws Exception {
        boot(CdStagePayload.R42C);
        // Size table entry 2: $20,$10 (byte_20EDC2, objects/r4/block.asm:30-33); BlockObject priority 1 (objects/r4/block.asm:3-8).
        ObjBlock block = add(new ObjBlock(at(0x80, 0, 0x34, 2)));
        runner.stepIdleFrames(1);
        assertEquals(0x20, block.solidWidth());
        assertEquals(0x10, block.solidYRadius());
        assertEquals(2, block.mapFrame());
        assertEquals(1, block.getPriorityBucket());
    }

    @Test
    void stardustSpeedwayBlockTakesItsOwnTable() throws Exception {
        boot(CdStagePayload.R72A);
        // objects/r7/block.asm:30-31: entry 1 is $1C,$11; priority 3 (:8).
        ObjBlock block = add(new ObjBlock(at(0x80, 0, 0x24, 1)));
        runner.stepIdleFrames(1);
        assertEquals(0x1C, block.solidWidth());
        assertEquals(0x11, block.solidYRadius());
        assertEquals(3, block.getPriorityBucket());
    }

    @Test
    void blockStopsTheWalkingPlayer() throws Exception {
        boot(CdStagePayload.R42C);
        // The pad reaches the player only once the title card has released the controls.
        CdFixtureReplay.releaseTitleCard(runner);
        add(new ObjBlock(at(0x40, -0x3, 0x34, 0)));
        for (int i = 0; i < 60; i++) runner.stepFrame(false, false, false, true, false);
        // SolidObject .HaltOnX: the player's left side meets the block's (Object Solid.asm:303-315).
        assertTrue(sonic.getPushing(), "pushing the block's side");
        assertEquals(0, sonic.getXSpeed());
    }

    @Test
    void fallingBlockFallsWhenThePlayerIsBelowAndBreaks() throws Exception {
        boot(CdStagePayload.R51C);
        // BlockCheckPlayer: player 8..$5F below, |dx| < 8 (objects/r5/block.asm:74-87).
        ObjFallingBlock block = add(new ObjFallingBlock(at(0, -0x40, 0x24, 1)));
        int y = block.getY();
        runner.stepIdleFrames(6);
        // Frame 1 inits and starts the fall; BlockFall then adds the speed before the $4000 gravity
        // (objects/r5/block.asm:97-99): 0 + 1/4 + 2/4 + 3/4 + 4/4 = 2.5 pixels after five falls.
        assertEquals(y + 2, block.getY());
        runner.stepIdleFrames(60);
        // .Break: the block's slot becomes an explosion (objects/r5/block.asm:107-143).
        assertTrue(block.isDestroyed(), "the block breaks on the player or the floor");
    }

    @Test
    void crusherFollowsItsSineFromTheStageTables() throws Exception {
        boot(CdStagePayload.R82A);
        // Subtype $14: phase word_20BB04[1] = $100, amplitude $20, step word_20BB10[1] = 8
        // (objects/r8/crusher.asm:28-48, 62-74, 78-87): Y = base + (sin($40) * $20) >> 8 = base + $20.
        ObjCrusher crusher = add(new ObjCrusher(at(0x100, -0x100, 0x34, 0x14)));
        int base = crusher.getY();
        runner.stepIdleFrames(1);
        assertEquals(base + 0x20, crusher.getY());
        assertEquals(0x100, crusher.phase());
        runner.stepIdleFrames(1);
        // loc_20BBDE: phase += 8; Y from sin($42) (objects/r8/crusher.asm:159-169).
        assertEquals(0x108, crusher.phase());
        int expected = base + ((com.openggf.physics.TrigLookupTable.sinHex(0x42) * 0x20) >> 8);
        assertEquals(expected, crusher.getY());
        // swap d0; sub.l (sp)+,d0; asr.l #8,d0: Y velocity = move << 8 (:171-176).
        assertEquals((short) ((expected - base - 0x20) << 8), crusher.ySpeedWord());
    }

    @Test
    void collapseFloorDropsAColumnEveryEightFramesOnceStoodOn() throws Exception {
        boot(CdStagePayload.R53C);
        // Four columns: oWidth = 4 << 3 = $20, oYRadius $11 (objects/r5/collapse_floor.asm:26-40).
        ObjCollapseFloor floor = add(new ObjCollapseFloor(at(0, 0x13 + 0x11 - 1, 0x28, 4)));
        runner.stepIdleFrames(1);
        assertEquals(0x20, floor.solidWidth());
        int frames = 0;
        while (floor.routine() == 2 && frames < 10) { runner.stepIdleFrames(1); frames++; }
        assertEquals(4, floor.routine(), "TopSolidObject contact starts the collapse (:43-48)");
        // var_2a = 8: nine more frames of routine 4 (bpl at 0), then routine 6 drops a column (:65-71, 77-124).
        runner.stepIdleFrames(10);
        assertEquals(3, floor.columns());
        assertEquals(0x18, floor.solidWidth());
    }

    @Test
    void qqzSpikesTakeTheirPropertiesFromTheStageTable() throws Exception {
        boot(CdStagePayload.R51C);
        // SpikesProperties (objects/r5/spikes.asm:61-65): down is $12,$C,$83; right $A,$E,$82.
        ObjQqzSpikes down = add(new ObjQqzSpikes(at(0x80, -0x40, 0x26, 1)));
        ObjQqzSpikes right = add(new ObjQqzSpikes(at(0xC0, -0x40, 0x26, 2)));
        runner.stepIdleFrames(1);
        assertEquals(0x12, down.solidWidth());
        assertEquals(0xC, down.solidYRadius());
        assertEquals(0x83, down.getCollisionFlags());
        assertEquals(0xA, right.solidWidth());
        assertEquals(0xE, right.solidYRadius());
        assertEquals(0x82, right.getCollisionFlags());
    }

    @Test
    void qqzMovingSpikesSlideByEightToTwenty() throws Exception {
        boot(CdStagePayload.R51C);
        // Subtype 4 (up, moving): SpikesCheckMove adds 8 a frame to $20 (objects/r5/spikes.asm:222-246).
        ObjQqzSpikes spikes = add(new ObjQqzSpikes(at(0x80, -0x40, 0x26, 4)));
        int y = spikes.getY();
        runner.stepIdleFrames(1);
        assertEquals(8, spikes.offset());
        assertEquals(y + 8, spikes.getY(), "SpikesUpMove: Y = origin + offset (:170-177)");
        runner.stepIdleFrames(3);
        assertEquals(0x20, spikes.offset());
        // 60-frame rest, then back out by 8.
        runner.stepIdleFrames(60);
        assertEquals(0x18, spikes.offset());
    }

    @Test
    void qqzUpwardSpikesHurtAPlayerStandingOnThem() throws Exception {
        boot(CdStagePayload.R51C);
        // .Upwards: SolidObject, stood on, then HurtPlayer (objects/r5/spikes.asm:101-126).
        add(new ObjQqzSpikes(at(0, 0x13 + 0xC - 1, 0x26, 0)));
        runner.stepIdleFrames(3);
        assertTrue(sonic.isHurt() || sonic.getDead(), "standing on upward spikes hurts");
    }

    @Test
    void switchSinksFourPixelsWhilePressed() throws Exception {
        boot(CdStagePayload.R61B);
        // SwitchObject: oYRadius 8 (objects/r6/switch.asm:6-13); it sits on the floor and Sonic drops onto it.
        int floor = sonic.getCentreY() + 0x13;
        ObjSwitch button = add(new ObjSwitch(at(0, 0x13 - 8, 0x24, 0)));
        sonic.setCentreY((short) (floor - 0x10 - 0x13 - 2));
        sonic.setAir(true);
        int y = button.getY();
        runner.stepIdleFrames(4);
        // The press frame: the switch down 4, frame +1, oYRadius - 4 (objects/r6/switch.asm:45-50).
        assertTrue(button.pressed(), "SolidObject top contact");
        assertEquals(y + 4, button.getY());
        assertEquals(4, button.solidYRadius());
    }

    @Test
    void wwzDoorStaysClosedWithoutItsSwitchAndBlocks() throws Exception {
        boot(CdStagePayload.R61B);
        // DoorObject_0_Routine0: oYRadius $20, oWidth 8 (objects/r6/door.asm:37-38); var_3c $FF keeps it shut.
        ObjWwzDoor door = add(new ObjWwzDoor(at(0x30, -0x13 + 0x20 - 4, 0x25, 0)));
        for (int i = 0; i < 40; i++) runner.stepFrame(false, false, false, true, false);
        assertEquals(0, door.lift());
        assertEquals(2, door.routine());
        assertTrue(sonic.getCentreX() < door.getX(), "the shut door blocks");
    }

    @Test
    void wwzPlatformSwingsOnTheLevelFrameSine() throws Exception {
        boot(CdStagePayload.R63C);
        // Subtype 2 moves up (PlatformObject_1_Routine4, objects/r6/platform.asm:58-63) by sin(levelFrames & $FF) * 4 >> 4 (PlatformCalculateOffset, objects/r6/platform.asm:102-110).
        ObjWwzPlatform platform = add(new ObjWwzPlatform(at(0x80, -0x40, 0x22, 2)));
        int y = platform.getY();
        runner.stepIdleFrames(1);
        int frames = GameServices.level().getFrameCounter();
        int expected = y - ((short) (com.openggf.physics.TrigLookupTable.sinHex(frames & 0xFF) * 4) >> 4);
        assertEquals(expected, platform.getY());
    }

    @Test
    void wwzPlatformCarriesItsRiderInTheFrameItMoves() throws Exception {
        boot(CdStagePayload.R63C);
        // TopSolidObject after the move (objects/r6/platform.asm:58-71): a standing player's Y follows
        // the platform's new top in the same frame (StandOnObject, Object Solid.asm).
        // Placed $80 above the floor so its +-$40 swing stays clear of it; Sonic drops onto it.
        ObjWwzPlatform platform = add(new ObjWwzPlatform(at(0, -0x80, 0x22, 3)));
        runner.stepIdleFrames(1);
        sonic.setCentreY((short) (platform.getY() - 0xC - 0x13 - 0x10));
        sonic.setAir(true);
        for (int i = 0; i < 60 && !sonic.isOnObject(); i++) runner.stepIdleFrames(1);
        assertTrue(sonic.isOnObject(), "the player rides the platform");
        int gap = platform.getY() - sonic.getCentreY();
        int moves = 0;
        for (int i = 0; i < 64; i++) {
            int before = platform.getY();
            runner.stepIdleFrames(1);
            if (platform.getY() != before) moves++;
            assertEquals(gap, platform.getY() - sonic.getCentreY(), "frame " + i);
        }
        assertTrue(moves > 4, "the platform moved");
    }

    @Test
    void stillFallingBlockStaysPut() throws Exception {
        boot(CdStagePayload.R51C);
        // Subtype 0: BlockMain branches straight to SolidObject (objects/r5/block.asm:51-52).
        ObjFallingBlock block = add(new ObjFallingBlock(at(0, -0x40, 0x24, 0)));
        int y = block.getY();
        runner.stepIdleFrames(5);
        assertEquals(y, block.getY());
        assertFalse(block.isDestroyed());
    }
}
