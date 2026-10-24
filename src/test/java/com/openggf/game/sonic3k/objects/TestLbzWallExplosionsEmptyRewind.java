package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Empty public-object capture/restore needs neither an identity table nor services. */
class TestLbzWallExplosionsEmptyRewind {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            0x3C40, 0x936, Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX, 0, 0, false, 50);

    @Test
    void emptyPublicObjectRestoresSameInstanceWithoutServicesOrIdentityTable() throws Exception {
        var box = new LbzMinibossBoxKnuxInstance(SPAWN);
        box.onKnucklesMinibossDefeated(0);
        var snapshot = box.captureRewindState();
        box.onKnucklesMinibossDefeated(2);
        assertEquals(5, box.getDefeatBitsForTest());

        assertDoesNotThrow(() -> box.restoreRewindState(snapshot));

        assertEquals(1, box.getDefeatBitsForTest(), "default owner scalars still restore");
        assertTrue(entries(box).isEmpty());
    }

    @Test
    void emptySnapshotRestoresFreshPublicObjectWithoutServicesOrIdentityTable() throws Exception {
        var source = new LbzMinibossBoxKnuxInstance(SPAWN);
        source.onKnucklesMinibossDefeated(2);
        var snapshot = source.captureRewindState(RewindCaptureContext.none());
        var fresh = new LbzMinibossBoxKnuxInstance(SPAWN);

        assertDoesNotThrow(() -> fresh.restoreRewindState(snapshot, RewindCaptureContext.none()));

        assertEquals(4, fresh.getDefeatBitsForTest());
        assertTrue(entries(fresh).isEmpty());
    }

    @Test
    void emptySnapshotClearsDivergentControllersBeforeSkippingRngLookup() throws Exception {
        var box = new LbzMinibossBoxKnuxInstance(SPAWN);
        var empty = box.captureRewindState();
        // Seed divergent helper state without giving the owner an RNG it can use.
        var controller = new S3kBossExplosionController(0x3C40, 0xA20, 0,
                new GameRng(GameRng.Flavour.S3K));
        controller.dispatchCreation();
        entries(box).add(controller);
        assertEquals(1, entries(box).size());
        box.setServices(new StubObjectServices() {
            @Override
            public GameRng rng() {
                throw new AssertionError("empty restore must not look up the service RNG");
            }
        });

        assertDoesNotThrow(() -> box.restoreRewindState(empty));

        assertTrue(entries(box).isEmpty(), "empty restoration must still clear divergent controllers");
    }

    @SuppressWarnings("unchecked")
    private static List<S3kBossExplosionController> entries(LbzMinibossBoxKnuxInstance box) throws Exception {
        Field holderField = LbzMinibossBoxKnuxInstance.class.getDeclaredField("wallExplosions");
        holderField.setAccessible(true);
        Object holder = holderField.get(box);
        Field entriesField = holder.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        return (List<S3kBossExplosionController>) entriesField.get(holder);
    }
}
