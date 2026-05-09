package com.openggf.game.sonic1.objects.badniks;

import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EngineContext;
import com.openggf.level.objects.ObjectManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestBuzzBomberMissileInstance {

    @Test
    public void missileStaysHarmlessForThirtyCreationFrameTicks() {
        Sonic1BuzzBomberMissileInstance missile =
                new Sonic1BuzzBomberMissileInstance(0, 0, 0x200, 0x200, false, null);

        assertEquals(0, missile.getCollisionFlags(), "Fresh missile should start harmless");

        for (int i = 0; i < 30; i++) {
            missile.update(i + 1, null);
        }

        assertEquals(0, missile.getCollisionFlags(),
                "Missile should still be in its flare window after 30 execution ticks");

        missile.update(31, null);

        assertEquals(0x87, missile.getCollisionFlags(),
                "Missile should arm on the 31st execution tick");
    }

    @Test
    public void dynamicMissileRestoresAcrossObjectManagerRewind() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
        try {
            ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
            Sonic1BuzzBomberMissileInstance missile =
                    new Sonic1BuzzBomberMissileInstance(0x240, 0x380, 0x200, 0x200, false, null);
            manager.addDynamicObject(missile);

            for (int i = 0; i < 34; i++) {
                missile.update(i + 1, null);
            }
            int capturedX = missile.getX();
            int capturedY = missile.getY();
            int capturedFlags = missile.getCollisionFlags();
            var snapshot = manager.rewindSnapshottable().capture();

            missile.update(35, null);
            manager.rewindSnapshottable().restore(snapshot);

            Sonic1BuzzBomberMissileInstance restored = manager.getActiveObjects().stream()
                    .filter(Sonic1BuzzBomberMissileInstance.class::isInstance)
                    .map(Sonic1BuzzBomberMissileInstance.class::cast)
                    .findFirst()
                    .orElse(null);

            assertNotNull(restored, "Dynamic Buzz Bomber missile should be recreated on rewind restore");
            assertEquals(capturedX, restored.getX());
            assertEquals(capturedY, restored.getY());
            assertEquals(capturedFlags, restored.getCollisionFlags());
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }
}
