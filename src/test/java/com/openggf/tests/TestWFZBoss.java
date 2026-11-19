package com.openggf.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the WFZ Laser Platform Boss (ObjC5).
 * Tests initial state, collision flags, hit count, and bounds calculation.
 * No ROM or OpenGL required.
 */
public class TestWFZBoss {

    private static final int BOSS_X = 0x2900;
    private static final int BOSS_Y = 0x0420;

    private Sonic2WFZBossInstance boss;

    @BeforeEach
    public void setUp() {
        ObjectServices services = new TestObjectServices();
        boss = ObjectConstructionContext.construct(services,
                () -> new Sonic2WFZBossInstance(
                        new ObjectSpawn(BOSS_X, BOSS_Y, Sonic2ObjectIds.WFZ_BOSS, 0x92, 0, false, 0)));
        boss.setServices(services);
    }

    @Test
    public void initialStateMatchesRom() {
        // Boss should start in wait-for-player routine (0x02)
        assertEquals(0x02, boss.getCurrentRoutine(), "Initial routine should be WAIT_PLAYER (0x02)");
        // HP should be 8
        assertEquals(8, boss.getState().hitCount, "Initial HP should be 8");
        // Not defeated
        assertFalse(boss.getState().defeated, "Should not be defeated initially");
        // Not invulnerable
        assertFalse(boss.getState().invulnerable, "Should not be invulnerable initially");
    }

    @Test
    public void initialPositionMatchesSpawn() {
        assertEquals(BOSS_X, boss.getX(), "Initial X should match spawn");
        assertEquals(BOSS_Y, boss.getY(), "Initial Y should match spawn");
    }

    @Test
    public void boundsCalculatedFromSpawnX() {
        // ROM: left bound = spawn_x - $60, right bound = spawn_x + $60
        assertEquals(BOSS_X - 0x60, boss.getLeftBound(), "Left bound should be spawn_x - 0x60");
        assertEquals(BOSS_X + 0x60, boss.getRightBound(), "Right bound should be spawn_x + 0x60");
    }

    @Test
    public void spawnXPreserved() {
        assertEquals(BOSS_X, boss.getSpawnX(), "Spawn X should be preserved");
    }

    @Test
    public void collisionDisabledInitially() {
        // Collision should be 0 before collision-active phase
        assertEquals(0, boss.getCollisionFlags(), "Collision should be 0 initially");
    }

    @Test
    public void objectIdIsCorrect() {
        assertEquals(0xC5, Sonic2ObjectIds.WFZ_BOSS, "Object ID should be 0xC5");
    }

    @Test
    public void priorityBucketIsFour() {
        assertEquals(4, boss.getPriorityBucket(), "Priority bucket should be 4");
    }

    @Test
    public void spawnCoordinatesMatchSpec() {
        assertEquals(BOSS_X, boss.getSpawn().x());
        assertEquals(BOSS_Y, boss.getSpawn().y());
        assertEquals(0x92, boss.getSpawn().subtype());
    }

    @Test
    public void currentFrameStartsAsClosed() {
        assertEquals(0, boss.getCurrentFrame(), "Initial frame should be CASE_CLOSED (0)");
    }

    @Test
    void loweringTheShooterEnablesCollisionAndAHitClosesIt() {
        // Enter the shooter signal phase so the engine drives the lowering countdown.
        boss.getState().routine = 0x0E;
        boss.update(0, null);
        for (int frame = 1; frame < 14; frame++) {
            boss.update(frame, null);
            assertEquals(0, boss.getCollisionFlags(), "the closed case is unhittable");
        }
        boss.update(14, null);
        assertEquals(0x06, boss.getCollisionFlags());
        boss.onPlayerAttack(null, null);
        assertEquals(7, boss.getCollisionProperty());
        assertEquals(0x20, boss.getState().invulnerabilityTimer);
        assertEquals(0, boss.getCollisionFlags());
    }
}
