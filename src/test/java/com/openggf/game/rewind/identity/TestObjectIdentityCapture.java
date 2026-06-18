package com.openggf.game.rewind.identity;

import com.openggf.game.GameId;
import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards that every captured object gets a stable, distinct {@link ObjectRefId} after
 * Task 2 wires {@code ObjectManager.rewindCaptureContext()} to register live objects
 * in the {@link RewindIdentityTable}.
 *
 * <h2>What this tests</h2>
 * <ol>
 *   <li>Two dynamic objects spawned in the same harness receive DIFFERENT ids (distinct).</li>
 *   <li>The id is non-null (the object IS registered).</li>
 *   <li>Capturing again returns the SAME id for the same object (stable across captures).</li>
 * </ol>
 *
 * <p>This is a TDD Red→Green test: the test is written FIRST against the desired API and fails
 * until the production registration is wired up.
 */
class TestObjectIdentityCapture {

    /**
     * Two dynamic objects spawned into the same harness must each receive a non-null,
     * mutually-distinct {@link ObjectRefId}; the id must remain stable across two
     * separate captures of the same live scene.
     */
    @Test
    void everyCapturedObjectGetsAStableId() {
        RewindRoundTripHarness h = RewindRoundTripHarness.build(GameId.S3K);
        ObjectSpawn spawnA = new ObjectSpawn(0x100, 0x100, 1, 0, 0, false, 0, 0);
        ObjectSpawn spawnB = new ObjectSpawn(0x200, 0x100, 1, 0, 0, false, 0, 1);
        var a = h.spawnDynamic(spawnA);
        var b = h.spawnDynamic(spawnB);

        RewindCaptureContext ctx1 = h.captureContext();
        RewindIdentityTable table1 = ctx1.requireIdentityTable();

        ObjectRefId idA = table1.idFor(a);
        ObjectRefId idB = table1.idFor(b);

        assertNotNull(idA, "object A must be registered in the identity table");
        assertNotNull(idB, "object B must be registered in the identity table");
        assertNotEquals(idA, idB, "two distinct objects must receive distinct ids");

        // Re-capture: the same live objects must mint the same ids
        RewindCaptureContext ctx2 = h.captureContext();
        RewindIdentityTable table2 = ctx2.requireIdentityTable();

        assertEquals(idA, table2.idFor(a), "id for object A must be stable across captures");
        assertEquals(idB, table2.idFor(b), "id for object B must be stable across captures");
    }
}
