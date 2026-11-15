package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.shared.ObjHDoor;
import com.openggf.game.soniccd.objects.shared.ObjHDoorSplash;
import com.openggf.game.soniccd.objects.shared.ObjMovingSpring;
import com.openggf.game.soniccd.objects.shared.ObjSpikeBall;
import com.openggf.game.soniccd.objects.shared.ObjSpikeChain;
import com.openggf.game.soniccd.objects.shared.ObjTunnelSplash;
import com.openggf.game.soniccd.objects.shared.ObjWwzPohBee;
import com.openggf.game.soniccd.objects.shared.ObjWwzPohMissile;
import com.openggf.game.soniccd.objects.shared.ObjWwzTubeDoor;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the second pass of the shared objects (owner {@code shared}) on stages loaded from the
 * disc. Each expectation cites the routine it checks; registry IDs are each stage's ObjectIndex
 * (r7/object_index_1_2.asm, r6/object_index_2.asm, r3/object_index_1b_1c_1d.asm). Native comparisons:
 * {@link TestSonicCdShared2ObjectTrace} and {@link TestSonicCdFixtureFrontier}.
 */
class TestSonicCdShared2Objects {
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

    private <T> T find(Class<T> type) {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (type.isInstance(o) && !o.isDestroyed()) return type.cast(o);
        }
        return null;
    }

    @Test
    void registryResolvesTheSecondPassRoutinesAtEachStagesIndexId() throws Exception {
        boot(CdStagePayload.R72A);
        // SpikeBallObject is $25 in Stardust Speedway (r7/object_index_1_2.asm:40).
        assertInstanceOf(ObjSpikeBall.class, create(0x25, 0));
        // MoveSpringObject is $F in every index but Metallic Madness's (r7/object_index_1_2.asm:15).
        assertInstanceOf(ObjMovingSpring.class, create(0x0F, 0));
        cleanup();
        reset();
        boot(CdStagePayload.R62B);
        // Wacky Workbench act 2: PohBee $31, SpikeChain $2D, TubeDoor $27 (r6/object_index_2.asm).
        assertInstanceOf(ObjWwzPohBee.class, create(0x31, 0));
        assertInstanceOf(ObjSpikeChain.class, create(0x2D, 0));
        assertInstanceOf(ObjWwzTubeDoor.class, create(0x27, 0));
        cleanup();
        reset();
        boot(CdStagePayload.R31B);
        // HDoorSplash $B, HDoor $D, TunnelSplash $E (r3/object_index_1b_1c_1d.asm:14-17).
        assertInstanceOf(ObjHDoorSplash.class, create(0x0B, 0));
        assertInstanceOf(ObjHDoor.class, create(0x0D, 0));
        assertInstanceOf(ObjTunnelSplash.class, create(0x0E, 0));
    }

    @Test
    void spikeBallMovesRestsAndReturnsOnTheTimeZoneRecord() throws Exception {
        boot(CdStagePayload.R71A);
        // Present (time_zone 1), subtype 0: word_20E506 record $200,0,$30,$18 and the rest word_20E536[1] = $28
        // (objects/r7/spike_ball.asm:90-101): 2 pixels a frame for $18 frames, rest $28, then back for $30.
        ObjSpikeBall ball = add(new ObjSpikeBall(at(0x100, -0x60, 0x25, 0)));
        int x0 = ball.getX();
        runner.stepIdleFrames(1);
        assertEquals(x0 + 2, ball.getX(), "Routine0 falls into Routine2 (:57-61)");
        runner.stepIdleFrames(23);
        assertEquals(x0 + 0x30, ball.getX(), "$18 moves of 2 pixels");
        runner.stepIdleFrames(0x28);
        assertEquals(x0 + 0x30, ball.getX(), "Routine4 rests while var_30 counts $28 down (:75-82)");
        runner.stepIdleFrames(1);
        assertEquals(x0 + 0x2E, ball.getX(), "negated speed after the rest (:62-67)");
        assertEquals(0xB5, ball.getCollisionFlags(), "move.b #$B5,obj.collide_type (:25)");
        assertEquals(x0, ball.originX(), "CheckObjectDespawn2 uses the starting X (:31)");
    }

    @Test
    void pohBeePatrolsAtHalfSpeedAndTheHunterDivesAndFires() throws Exception {
        boot(CdStagePayload.R62B);
        // Subtype 1: Sprites2 and var_2c = -$8000, half a pixel a frame (objects/r6/poh_bee.asm:43-52, 64-68).
        ObjWwzPohBee patrol = add(new ObjWwzPohBee(at(0x100, -0x80, 0x31, 1)));
        int px = patrol.getX();
        runner.stepIdleFrames(5);
        assertEquals(px - 3, patrol.getX(), "five frames of -$8000 (16.16) = -2.5 pixels, the integer part floors");
        assertEquals(0x31, patrol.getCollisionFlags(), "move.b #$31,obj.collide_type (:41)");
        cleanup();
        reset();
        boot(CdStagePayload.R62B);
        // Subtype 0 with the player inside its corridor (dy in -$60..$60, dx in -$78..$78): PohBeeCheckTurnaround
        // sets routine $C (:76-81, 96-119); $1F frames later it dives (RoutineC/E, :158-177).
        ObjWwzPohBee bee = add(new ObjWwzPohBee(at(0x40, -0x20, 0x31, 0)));
        int x0 = bee.getX();
        int y0 = bee.getY();
        runner.stepIdleFrames(1);
        assertEquals(0xC, bee.routine());
        assertEquals(x0 - 1, bee.getX(), "one frame of -$10000 before the check");
        runner.stepIdleFrames(31);
        assertEquals(0x10, bee.routine());
        assertEquals(0x32, bee.getCollisionFlags(), "move.b #$32,obj.collide_type (:168)");
        assertEquals(y0 + 4, bee.getY(), "addq.w #4,obj.y(a0) (:174)");
        assertEquals(x0 - 1 - 8, bee.getX(), "obj.var_30 = -8 added to X (:172-173)");
        // Routine10: $1F frames later the missile appears 7 pixels to the left and $17 below (:181-210).
        runner.stepIdleFrames(31);
        ObjWwzPohMissile missile = find(ObjWwzPohMissile.class);
        assertNotNull(missile, "SpawnObject of the missile");
        assertEquals(bee.getX() - 7, missile.getX());
        assertEquals(bee.getY() + 0x17, missile.getY());
        assertEquals(0x12, bee.routine());
    }

    @Test
    void hDoorOpensForAPlayerRisingThroughItAndThrowsASplash() throws Exception {
        boot(CdStagePayload.R31B);
        // sub_205E3A: y_speed negative and the player's centre inside width_2 by height (objects/r1/h_door.asm:61-76, 141-167).
        ObjHDoor door = add(new ObjHDoor(at(0, 0, 0x0D, 0)));
        sonic.setAir(true);
        sonic.setYSpeed((short) -0x100);
        runner.stepIdleFrames(1);
        assertEquals(4, door.routine());
        ObjHDoorSplash splash = find(ObjHDoorSplash.class);
        assertNotNull(splash, "SpawnObject of object $B (:69-74)");
        assertEquals(door.getY() - 4, splash.getY());
        // HDoorAnim: ten frames, then $FC advances to Routine6 and back to Routine2 (anims/r1/h_door.asm, :123-133).
        runner.stepIdleFrames(12);
        assertEquals(2, door.routine());
    }

    @Test
    void tunnelSplashPlaysOnceAndDeletesItself() throws Exception {
        boot(CdStagePayload.R31B);
        // TunnelSplashAnims: delay 3, frames 0-2, $FC (anims/r1/tunnel_splash.asm); Routine4 deletes (tunnel_splash.asm:37-38).
        ObjTunnelSplash splash = add(new ObjTunnelSplash(at(0x40, 0x20, 0x0E, 0)));
        runner.stepIdleFrames(2);
        assertTrue(!splash.isDestroyed());
        runner.stepIdleFrames(20);
        assertTrue(splash.isDestroyed());
    }

    @Test
    void spikeChainLaysItsBallsAlongTheSineAtSixteenPixelSteps() throws Exception {
        boot(CdStagePayload.R62A);
        // Present: five balls (STAGE_TIME<>0, objects/r6/spike_chain.asm:73-82). The angle grows $100 a frame, so after 64
        // frames it is $40: sine $100, cosine 0; ball i is (i+1)*16 pixels below the centre (:102-161).
        ObjSpikeChain chain = add(new ObjSpikeChain(at(0x100, -0x100, 0x2D, 0)));
        int x0 = chain.getX();
        int y0 = chain.getY();
        runner.stepIdleFrames(64);
        assertEquals(5, chain.balls().size());
        for (int i = 0; i < 5; i++) {
            ObjSpikeChain.Ball ball = chain.balls().get(i);
            assertEquals(x0, ball.getX(), "ball " + i);
            assertEquals(y0 + 16 * (i + 1), ball.getY(), "ball " + i);
            assertEquals(0xB9, ball.getCollisionFlags(), "move.b #$B9,obj.collide_type (:202)");
        }
    }

    @Test
    void tubeDoorOpensForAPlayerOnItsSideAndClosesBehindHim() throws Exception {
        boot(CdStagePayload.R62B);
        // Subtype 0 opens when 0 <= x - player x < $40 and |dy| < $40 (objects/r6/tube_door.asm:51-79).
        ObjWwzTubeDoor door = add(new ObjWwzTubeDoor(at(0x20, 0, 0x27, 0)));
        runner.stepIdleFrames(1);
        assertEquals(4, door.routine());
        // TubeDoorObject_0_Routine4 (objects/r6/tube_door.asm:86-94): var_3a += $40 each frame, a map frame on each carry; frame 3 hands over to Routine6.
        runner.stepIdleFrames(12);
        assertEquals(3, door.mapFrame());
        assertEquals(6, door.routine());
    }

    @Test
    void movingSpringSettlesOnTheFloorCarriesItsSpringAndTurnsAtADrop() throws Exception {
        boot(CdStagePayload.R62B);
        // sub_2082C2 sinks one pixel a frame until the floor probe reports contact, then routine 4; the child
        // spring (object $A, $10 pixels up) is spawned at once (objects/spring.asm:46-61, 63-77).
        ObjMovingSpring spring = add(new ObjMovingSpring(at(0x80, -0x20, 0x0F, 0)));
        runner.stepIdleFrames(1);
        assertEquals(2, spring.routine());
        int y = spring.getY();
        for (int i = 0; i < 80 && spring.routine() != 4; i++) runner.stepIdleFrames(1);
        assertEquals(4, spring.routine(), "settled on the floor");
        assertTrue(spring.getY() > y, "sank to the floor");
        // sub_2082E2: 1.5 pixels a frame ($180) along the floor (:80-97).
        int x = spring.getX();
        runner.stepIdleFrames(2);
        assertEquals(x + 3, spring.getX());
        int children = 0;
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.getClass().getSimpleName().equals("SharedAttachedSpring")) {
                children++;
                assertEquals(spring.getX(), o.getX());
                assertEquals(spring.getY() - 0x10, o.getY());
            }
        }
        assertEquals(1, children, "the spring riding on top");
    }
}
