package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.mmz.MmzBumper;
import com.openggf.game.soniccd.objects.mmz.MmzCollapseFloor;
import com.openggf.game.soniccd.objects.mmz.MmzDoor;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Metallic Madness objects the objects-3 lane added, on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/objects/r8}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestMmzObjects3 {
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

    private CdLevel level() {
        return (CdLevel) GameServices.level().getCurrentLevel();
    }


    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    @Test
    void collapseFloorInitialisesPositiveAndNegativeSubtypes() throws Exception {
        boot(CdStagePayload.R83C);
        // CollapseFloorObject_1_Routine0 (objects/r8/collapse_floor.asm:22-54): subtype 5 is 5 columns of 8 pixels, top-solid
        // (routine 2, priority 3); the negative subtype $FB is |subtype| columns, plain solid (routine 8, priority 1).
        MmzCollapseFloor collapsing = add(new MmzCollapseFloor(at(0x100, -0x80, 0x25, 5)));
        MmzCollapseFloor solid = add(new MmzCollapseFloor(at(0x180, -0x80, 0x25, 0xFB)));
        runner.stepIdleFrames(1);
        assertEquals(2, collapsing.routine());
        assertEquals(0x28, collapsing.solidWidth(), "lsl.b #3 of the column count into obj.width (:41-43)");
        assertEquals(0x11, collapsing.solidYRadius(), "move.b #$11,obj.height (:25)");
        assertEquals(8, solid.routine());
        assertEquals(0x28, solid.solidWidth(), "neg.b d0 first: |$FB| = 5 columns (:33-43)");
    }

    @Test
    void collapseFloorDropsOneColumnEveryNineFramesAfterTheFirstTen() throws Exception {
        boot(CdStagePayload.R81A);
        MmzCollapseFloor floor = add(new MmzCollapseFloor(at(0, 0x22, 0x25, 5)));
        int landed = -1;
        for (int frame = 1; frame <= 12 && landed < 0; frame++) {
            runner.stepIdleFrames(1);
            if (floor.routine() == 4) landed = frame;
        }
        assertTrue(landed > 0, "TopSolidObject seats Sonic and Routine2 starts the collapse (:59-73)");
        int startX = floor.getX();
        // Routine4 counts var_2a from 8 down to -1 (nine calls, :81-86); Routine6 then drops a column on the next call.
        runner.stepIdleFrames(9);
        assertEquals(6, floor.routine());
        assertEquals(5, floor.columns());
        runner.stepIdleFrames(1);
        assertEquals(4, floor.columns(), "subq.b #1,obj.subtype (:129)");
        assertEquals(4, floor.routine(), "subq.b #2,obj.routine (:131)");
        assertEquals(startX + 8, floor.getX(), "the centre moves 8 towards a player with x_speed >= 0 (:65-71, :136-137)");
        assertEquals(3, live(0x25).size(), "the floor and its two falling pieces (:106-120)");
        // Routine4 now counts var_2a from 7 (:135): eight calls to -1, then the drop.
        runner.stepIdleFrames(8);
        assertEquals(6, floor.routine());
        runner.stepIdleFrames(1);
        assertEquals(3, floor.columns());
    }

    @Test
    void collapseFloorWithNegativeSubtypeStaysSolid() throws Exception {
        boot(CdStagePayload.R81A);
        MmzCollapseFloor floor = add(new MmzCollapseFloor(at(0, 0x22, 0x25, 0xFB)));
        runner.stepIdleFrames(20);
        assertEquals(8, floor.routine(), "Routine8 only runs SolidObject and draws (:144-148)");
        assertTrue(sonic.isOnObject());
    }

    @Test
    void doorSpawnsItsTopHalfAndOpensTwoPixelsAFrameWhenAsked() throws Exception {
        boot(CdStagePayload.R81A);
        ObjectSpawn spawn = at(0x200, -0x40, 0x29, 0);
        MmzDoor door = add(new MmzDoor(spawn));
        door.requestOpen();
        runner.stepIdleFrames(1);
        // DoorObject_0_Routine0 / sub_20CFF6 (objects/r8/door.asm:22-63): the bottom half stays at Y, the top half is $20 above.
        List<ObjectInstance> halves = live(0x29);
        assertEquals(2, halves.size(), "SpawnObjectAfter allocates the top half (:32)");
        assertEquals(spawn.y(), halves.stream().mapToInt(ObjectInstance::getY).max().orElseThrow());
        assertEquals(spawn.y() - 0x20, halves.stream().mapToInt(ObjectInstance::getY).min().orElseThrow());
        // Routine2 takes the request (:74-79, :97-98); Routine4 moves 2 pixels a frame until the open Y, centre + $31 (:114-124):
        // 17 calls from Y to Y + $21.
        runner.stepIdleFrames(1 + 17);
        assertEquals(spawn.y() + 0x21, halves.stream().mapToInt(ObjectInstance::getY).max().orElseThrow());
        assertEquals(spawn.y() - 0x41, halves.stream().mapToInt(ObjectInstance::getY).min().orElseThrow());
    }

    @Test
    void bumperSetsItsSlideFromTheSubtype() throws Exception {
        boot(CdStagePayload.R83C);
        // BumperObject_0_Routine0 (objects/r3/bumper_flipper.asm:30-50): bit 1 adds $40, bit 0 adds $20 to the range; the
        // step count is half of it; bit 6 set slides in the positive direction ($10000), clear in the negative.
        MmzBumper still = add(new MmzBumper(at(0x100, -0x80, 0x1D, 0)));
        MmzBumper slider = add(new MmzBumper(at(0x180, -0x80, 0x1D, 0x43)));
        runner.stepIdleFrames(1);
        assertEquals(0, still.velocity(), "subtype 0 stands still (:46)");
        assertEquals(5, still.prizes(), "move.w #5,obj.var_34 (:28)");
        assertEquals(0x30 - 1, slider.steps(), "range $60, half of it as steps, one spent by the first Routine2 call (:62)");
        assertEquals(0x10000, slider.velocity(), "bit 6 set: move.l #$10000 (:47-49)");
    }

    @Test
    void bumperThrowsAPlayerAwayAndSpendsAPrize() throws Exception {
        boot(CdStagePayload.R83C);
        MmzBumper bumper = add(new MmzBumper(at(0, 0x0C, 0x1D, 0)));
        runner.stepIdleFrames(3);
        assertTrue(bumper.prizes() < 5, "a touch spends a prize (:70-76)");
        assertTrue(sonic.getAir(), "bset #1,obj.flags(a1) (:99)");
        // Bumper below the player: Atan2 gives $40, so the throw is -$700 straight up (:87-98), less a frame or two of gravity.
        assertTrue(sonic.getYSpeed() < -0x600 && sonic.getYSpeed() >= -0x700, "y speed " + sonic.getYSpeed());
    }

    @Test
    void dollarOneDIsTheBumperOnActThree() throws Exception {
        boot(CdStagePayload.R83C);
        var registry = GameModuleRegistry.getCurrent().createObjectRegistry();
        assertTrue(registry.create(at(0x100, -0x80, 0x1D, 0)) instanceof MmzBumper,
                "r8/object_index_3.asm:32 starts BumperObject at $1D");
    }

    @Test
    void dollarOneDIsTheLaserOnActTwo() throws Exception {
        boot(CdStagePayload.R82A);
        var registry = GameModuleRegistry.getCurrent().createObjectRegistry();
        assertTrue(registry.create(at(0x100, -0x80, 0x1D, 0)) instanceof com.openggf.game.soniccd.objects.mmz.MmzLaser,
                "r8/object_index_2.asm:32 starts LaserObject at $1D");
    }
}
