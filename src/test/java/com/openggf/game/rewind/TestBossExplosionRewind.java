package com.openggf.game.rewind;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native explosion spawn identity must survive fresh generic recreation. */
class TestBossExplosionRewind {
    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"0x58,0", "0x3F,1", "0,0"})
    void nativeSpawnAndActiveAnimationRestoreThroughRetirement(int objectId, int priority) {
        var sounds = new ArrayList<Integer>();
        var services = new StubObjectServices() {
            @Override
            public void playSfx(int soundId) { sounds.add(soundId); }
        }.withIsolatedObjectManager();
        var objects = services.objectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var explosion = objects.createDynamicObject(() -> objectId == 0
                ? new BossExplosionObjectInstance(0x2AE0, 0x430, 0xC4)
                : new BossExplosionObjectInstance(0x2AE0, 0x430, objectId, 0xC4, priority));
        var rewind = new RewindRegistry();
        rewind.register(objects.rewindSnapshottable());
        for (int frame = 0; frame < 14; frame++) explosion.update(frame, null);
        assertEquals(List.of(0xC4), sounds);
        assertFalse(explosion.isDestroyed());
        var active = rewind.capture();
        for (int frame = 14; frame < 57; frame++) {
            assertFalse(explosion.isDestroyed(), "all seven animation frames remain visible for eight updates");
            explosion.update(frame, null);
        }
        assertTrue(explosion.isDestroyed());
        var retired = rewind.capture();
        rewind.restore(active);
        var restored = objects.getActiveObjects().stream().filter(BossExplosionObjectInstance.class::isInstance)
                .map(BossExplosionObjectInstance.class::cast).findFirst().orElseThrow();
        assertNotSame(explosion, restored);
        assertEquals(explosion.getSpawn(), restored.getSpawn());
        assertEquals(objectId, restored.getSpawn().objectId());
        assertEquals(priority, restored.getPriorityBucket());
        assertSnapshotEquals(active, rewind.capture());
        for (int frame = 14; frame < 57; frame++) {
            assertFalse(restored.isDestroyed());
            restored.update(frame, null);
        }
        assertTrue(restored.isDestroyed());
        assertEquals(List.of(0xC4), sounds, "restoring an initialized explosion must not replay its sound");
        assertSnapshotEquals(retired, rewind.capture());
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (var entry : expected.entries().entrySet())
            assertEquals(List.of(), RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    actual.get(entry.getKey())), entry.getKey());
    }
}
